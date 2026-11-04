/*
 * Backend común de la asignatura Seguridad en Entornos Móviles.
 *
 * Versión en Java del mismo servidor que servidor.py. Los dos programas son
 * equivalentes: mismos endpoints, mismos usuarios, mismos datos y las mismas
 * debilidades intencionadas. Se incluyen los dos para poder compararlos.
 *
 * No requiere ninguna dependencia externa: sólo la biblioteca estándar del JDK.
 * Desde Java 11 un fichero fuente se ejecuta sin compilarlo previamente:
 *
 *     java Servidor.java                       // escucha en 0.0.0.0:8080
 *     PUERTO=9000 java Servidor.java           // Linux y macOS
 *     cmd /c "set PUERTO=9000 && java Servidor.java"   // Windows
 *
 * ADVERTENCIA DOCENTE
 * Este servidor contiene debilidades intencionadas (contraseñas en claro,
 * tokens predecibles, sin caducidad, sin límite de intentos, mensajes de error
 * que distinguen usuario inexistente de contraseña incorrecta, rutas sin
 * comprobación de propiedad ni de rol). Son material de estudio para los
 * Bloques III y IV. No debe usarse fuera del aula.
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Servidor {

    static final int PUERTO = Integer.parseInt(env("PUERTO", "8080"));
    static final String CERT = env("CERT", null);    // ruta al certificado, opcional
    static final String CLAVE = env("CLAVE", null);  // ruta a la clave privada, opcional

    static String env(String nombre, String pordefecto) {
        String v = System.getenv(nombre);
        return (v == null || v.isEmpty()) ? pordefecto : v;
    }

    /**
     * Salida por consola forzada a UTF-8. Sin esto, Java escribe con la
     * codificación que herede del sistema y los acentos salen como «?» en
     * algunos entornos. Python resuelve esto solo; Java no.
     * Si en una consola antigua de Windows vieras caracteres raros, ejecuta
     * antes la orden  chcp 65001.
     */
    static final PrintStream OUT = crearSalida();

    static PrintStream crearSalida() {
        try {
            return new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        } catch (Exception e) {
            return System.out;
        }
    }

    // ---------------------------------------------------------------- datos

    /**
     * Compárala con la data class de Kotlin de la sesión 2.4: campos privados,
     * constructor y un accesor por cada campo, todo escrito a mano.
     */
    static class Usuario {
        private final String clave;
        private final String nombre;
        private final String correo;
        private final String dni;
        private final String rol;

        Usuario(String clave, String nombre, String correo, String dni, String rol) {
            this.clave = clave;
            this.nombre = nombre;
            this.correo = correo;
            this.dni = dni;
            this.rol = rol;
        }

        public String getClave()  { return this.clave; }
        public String getNombre() { return this.nombre; }
        public String getCorreo() { return this.correo; }
        public String getDni()    { return this.dni; }
        public String getRol()    { return this.rol; }
    }

    static class Elemento {
        private final int id;
        private final String titulo;
        private final String contenido;
        private final String fecha;

        Elemento(int id, String titulo, String contenido, String fecha) {
            this.id = id;
            this.titulo = titulo;
            this.contenido = contenido;
            this.fecha = fecha;
        }

        public int getId() { return this.id; }

        /** El mapa que se convierte en JSON. */
        Map<String, Object> aMapa() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", Integer.valueOf(this.id));
            m.put("titulo", this.titulo);
            m.put("contenido", this.contenido);
            m.put("fecha", this.fecha);
            return m;
        }
    }

    static final Map<String, Usuario> USUARIOS = new LinkedHashMap<String, Usuario>();
    static final Map<String, List<Elemento>> ELEMENTOS = new LinkedHashMap<String, List<Elemento>>();
    static final Map<String, String> SESIONES = new ConcurrentHashMap<String, String>();  // token -> usuario

    static final Object CANDADO = new Object();
    static int siguienteId = 4;

    static {
        USUARIOS.put("ana",   new Usuario("Practica2024",  "Ana Ruiz",       "ana@uma.es",   "12345678Z", "usuario"));
        USUARIOS.put("luis",  new Usuario("Practica2024",  "Luis Moreno",    "luis@uma.es",  "87654321X", "usuario"));
        USUARIOS.put("admin", new Usuario("Administrador", "Administración", "admin@uma.es", "11111111H", "administrador"));

        List<Elemento> deAna = new ArrayList<Elemento>();
        deAna.add(new Elemento(1, "Contraseña del router", "wifi-casa: Marzo2019!", "2024-03-11"));
        deAna.add(new Elemento(2, "Cuenta bancaria", "ES91 2100 0418 4502 0005 1332", "2024-04-02"));
        ELEMENTOS.put("ana", deAna);

        List<Elemento> deLuis = new ArrayList<Elemento>();
        deLuis.add(new Elemento(3, "Notas de la reunión", "Revisar el contrato antes del viernes", "2024-05-20"));
        ELEMENTOS.put("luis", deLuis);

        ELEMENTOS.put("admin", new ArrayList<Elemento>());
    }

    /** Token deliberadamente predecible: material de análisis para el Bloque IV. */
    static String crearToken(String usuario) {
        String crudo = usuario + ":" + (System.currentTimeMillis() / 1000L);
        return Base64.getEncoder().encodeToString(crudo.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------- manejador

    static class Manejador implements HttpHandler {

        public void handle(HttpExchange intercambio) {
            int codigo = 500;
            try {
                String ruta = intercambio.getRequestURI().getPath();
                String metodo = intercambio.getRequestMethod();
                if ("GET".equals(metodo)) {
                    codigo = atenderGet(intercambio, ruta);
                } else if ("POST".equals(metodo)) {
                    codigo = atenderPost(intercambio, ruta);
                } else {
                    codigo = responder(intercambio, 404, error("Ruta no encontrada", ruta));
                }
                registrar(intercambio, codigo);
            } catch (Exception e) {
                try { responder(intercambio, 500, error("Error interno", null)); } catch (Exception ignorada) { }
            } finally {
                intercambio.close();
            }
        }

        // ---------- utilidades ----------

        static int responder(HttpExchange intercambio, int codigo, Object cuerpo) throws Exception {
            byte[] datos = Json.escribir(cuerpo).getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            intercambio.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            intercambio.getResponseHeaders().set("Server", "SEM-Practicas/1.0");
            intercambio.sendResponseHeaders(codigo, datos.length);
            OutputStream salida = intercambio.getResponseBody();
            salida.write(datos);
            salida.close();
            return codigo;
        }

        static Map<String, Object> error(String mensaje, String ruta) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("error", mensaje);
            if (ruta != null) m.put("ruta", ruta);
            return m;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> cuerpo(HttpExchange intercambio) {
            try {
                InputStream entrada = intercambio.getRequestBody();
                byte[] crudo = leerTodo(entrada);
                if (crudo.length == 0) return new LinkedHashMap<String, Object>();
                Object valor = Json.leer(new String(crudo, StandardCharsets.UTF_8));
                if (valor instanceof Map) return (Map<String, Object>) valor;
                return new LinkedHashMap<String, Object>();
            } catch (Exception e) {
                return new LinkedHashMap<String, Object>();
            }
        }

        static byte[] leerTodo(InputStream entrada) throws Exception {
            java.io.ByteArrayOutputStream acumulador = new java.io.ByteArrayOutputStream();
            byte[] trozo = new byte[4096];
            int leidos;
            while ((leidos = entrada.read(trozo)) != -1) acumulador.write(trozo, 0, leidos);
            return acumulador.toByteArray();
        }

        /** Devuelve el usuario de la sesión, o null si la cabecera no es válida. */
        static String usuarioAutenticado(HttpExchange intercambio) {
            String cabecera = intercambio.getRequestHeaders().getFirst("Authorization");
            if (cabecera == null || !cabecera.startsWith("Bearer ")) return null;
            return SESIONES.get(cabecera.substring(7));
        }

        static void registrar(HttpExchange intercambio, int codigo) {
            String fecha = new SimpleDateFormat("dd/MMM/yyyy HH:mm:ss", Locale.ENGLISH).format(new Date());
            OUT.println("[" + fecha + "] \"" + intercambio.getRequestMethod() + " "
                    + intercambio.getRequestURI() + " HTTP/1.1\" " + codigo + " -");
        }

        // ---------- rutas ----------

        static final Pattern ELEMENTO_POR_ID = Pattern.compile("^/api/elementos/(\\d+)$");

        static int atenderGet(HttpExchange intercambio, String ruta) throws Exception {

            if (ruta.equals("/api/salud")) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("estado", "ok");
                m.put("servicio", "backend de prácticas");
                return responder(intercambio, 200, m);
            }

            if (ruta.equals("/api/elementos")) {
                String usuario = usuarioAutenticado(intercambio);
                if (usuario == null) return responder(intercambio, 401, error("Token ausente o no válido", null));
                List<Object> lista = new ArrayList<Object>();
                List<Elemento> suyos = ELEMENTOS.get(usuario);
                if (suyos != null) for (Elemento e : suyos) lista.add(e.aMapa());
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("elementos", lista);
                return responder(intercambio, 200, m);
            }

            if (ruta.equals("/api/perfil")) {
                String usuario = usuarioAutenticado(intercambio);
                if (usuario == null) return responder(intercambio, 401, error("Token ausente o no válido", null));
                Usuario u = USUARIOS.get(usuario);
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("usuario", usuario);
                m.put("nombre", u.getNombre());
                m.put("correo", u.getCorreo());
                m.put("dni", u.getDni());
                m.put("rol", u.getRol());
                return responder(intercambio, 200, m);
            }

            // Ruta administrativa sin comprobación de rol: hallazgo previsto para el Bloque IV
            if (ruta.equals("/api/admin/usuarios")) {
                String usuario = usuarioAutenticado(intercambio);
                if (usuario == null) return responder(intercambio, 401, error("Token ausente o no válido", null));
                List<Object> lista = new ArrayList<Object>();
                for (Map.Entry<String, Usuario> par : USUARIOS.entrySet()) {
                    Map<String, Object> uno = new LinkedHashMap<String, Object>();
                    uno.put("usuario", par.getKey());
                    uno.put("nombre", par.getValue().getNombre());
                    uno.put("correo", par.getValue().getCorreo());
                    uno.put("rol", par.getValue().getRol());
                    lista.add(uno);
                }
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("usuarios", lista);
                return responder(intercambio, 200, m);
            }

            Matcher coincidencia = ELEMENTO_POR_ID.matcher(ruta);
            if (coincidencia.matches()) {
                String usuario = usuarioAutenticado(intercambio);
                if (usuario == null) return responder(intercambio, 401, error("Token ausente o no válido", null));
                int idn = Integer.parseInt(coincidencia.group(1));
                // No comprueba que el elemento pertenezca al usuario: intencionado
                for (List<Elemento> lista : ELEMENTOS.values()) {
                    for (Elemento e : lista) {
                        if (e.getId() == idn) return responder(intercambio, 200, e.aMapa());
                    }
                }
                return responder(intercambio, 404, error("No encontrado", null));
            }

            return responder(intercambio, 404, error("Ruta no encontrada", ruta));
        }

        static int atenderPost(HttpExchange intercambio, String ruta) throws Exception {
            Map<String, Object> cuerpo = cuerpo(intercambio);

            if (ruta.equals("/api/login")) {
                Object crudoUsuario = cuerpo.get("usuario");
                Object crudoClave = cuerpo.get("clave");
                String usuario = (crudoUsuario == null ? "" : crudoUsuario.toString()).trim().toLowerCase(Locale.ROOT);
                String clave = (crudoClave == null ? "" : crudoClave.toString());

                if (!USUARIOS.containsKey(usuario)) {
                    // Mensaje que confirma qué cuentas existen: intencionado
                    return responder(intercambio, 404, error("El usuario no existe", null));
                }
                if (!USUARIOS.get(usuario).getClave().equals(clave)) {
                    return responder(intercambio, 401, error("Contraseña incorrecta", null));
                }
                String token = crearToken(usuario);
                SESIONES.put(token, usuario);
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("token", token);
                m.put("usuario", usuario);
                m.put("nombre", USUARIOS.get(usuario).getNombre());
                m.put("rol", USUARIOS.get(usuario).getRol());
                return responder(intercambio, 200, m);
            }

            if (ruta.equals("/api/elementos")) {
                String usuario = usuarioAutenticado(intercambio);
                if (usuario == null) return responder(intercambio, 401, error("Token ausente o no válido", null));
                Object t = cuerpo.get("titulo");
                Object c = cuerpo.get("contenido");
                Elemento nuevo;
                synchronized (CANDADO) {
                    nuevo = new Elemento(
                            siguienteId,
                            t == null ? "(sin título)" : t.toString(),
                            c == null ? "" : c.toString(),
                            new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
                    siguienteId++;
                }
                List<Elemento> suyos = ELEMENTOS.get(usuario);
                if (suyos == null) { suyos = new ArrayList<Elemento>(); ELEMENTOS.put(usuario, suyos); }
                suyos.add(nuevo);
                return responder(intercambio, 201, nuevo.aMapa());
            }

            return responder(intercambio, 404, error("Ruta no encontrada", ruta));
        }
    }

    // ------------------------------------------------------------------ JSON
    // El JDK no trae analizador de JSON, así que aquí va uno mínimo. Es
    // suficiente para lo que envía la aplicación y cabe en una pantalla.

    static class Json {

        // ---- escribir ----

        static String escribir(Object valor) {
            StringBuilder sb = new StringBuilder();
            escribirEn(sb, valor);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        static void escribirEn(StringBuilder sb, Object valor) {
            if (valor == null) { sb.append("null"); return; }
            if (valor instanceof String) { cadena(sb, (String) valor); return; }
            if (valor instanceof Number || valor instanceof Boolean) { sb.append(valor.toString()); return; }
            if (valor instanceof Map) {
                sb.append('{');
                boolean primero = true;
                for (Map.Entry<String, Object> par : ((Map<String, Object>) valor).entrySet()) {
                    if (!primero) sb.append(", ");
                    cadena(sb, par.getKey());
                    sb.append(": ");
                    escribirEn(sb, par.getValue());
                    primero = false;
                }
                sb.append('}');
                return;
            }
            if (valor instanceof List) {
                sb.append('[');
                boolean primero = true;
                for (Object v : (List<Object>) valor) {
                    if (!primero) sb.append(", ");
                    escribirEn(sb, v);
                    primero = false;
                }
                sb.append(']');
                return;
            }
            cadena(sb, valor.toString());
        }

        static void cadena(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"':  sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            sb.append('"');
        }

        // ---- leer ----

        static Object leer(String texto) {
            Analizador a = new Analizador(texto);
            a.espacios();
            Object v = a.valor();
            return v;
        }

        static class Analizador {
            private final String s;
            private int i = 0;

            Analizador(String s) { this.s = s; }

            void espacios() {
                while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
            }

            Object valor() {
                espacios();
                if (i >= s.length()) return null;
                char c = s.charAt(i);
                if (c == '{') return objeto();
                if (c == '[') return lista();
                if (c == '"') return cadena();
                if (s.startsWith("true", i))  { i += 4; return Boolean.TRUE; }
                if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
                if (s.startsWith("null", i))  { i += 4; return null; }
                return numero();
            }

            Map<String, Object> objeto() {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                i++;                                  // {
                espacios();
                if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
                while (i < s.length()) {
                    espacios();
                    String llave = cadena();
                    espacios();
                    if (i < s.length() && s.charAt(i) == ':') i++;
                    m.put(llave, valor());
                    espacios();
                    if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                    if (i < s.length() && s.charAt(i) == '}') { i++; break; }
                    break;
                }
                return m;
            }

            List<Object> lista() {
                List<Object> l = new ArrayList<Object>();
                i++;                                  // [
                espacios();
                if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
                while (i < s.length()) {
                    l.add(valor());
                    espacios();
                    if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                    if (i < s.length() && s.charAt(i) == ']') { i++; break; }
                    break;
                }
                return l;
            }

            String cadena() {
                StringBuilder sb = new StringBuilder();
                if (i < s.length() && s.charAt(i) == '"') i++;
                while (i < s.length()) {
                    char c = s.charAt(i++);
                    if (c == '"') break;
                    if (c == '\\' && i < s.length()) {
                        char e = s.charAt(i++);
                        switch (e) {
                            case 'n': sb.append('\n'); break;
                            case 'r': sb.append('\r'); break;
                            case 't': sb.append('\t'); break;
                            case 'b': sb.append('\b'); break;
                            case 'f': sb.append('\f'); break;
                            case 'u':
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                i += 4;
                                break;
                            default: sb.append(e);
                        }
                    } else {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }

            Object numero() {
                int inicio = i;
                while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
                String t = s.substring(inicio, i);
                if (t.isEmpty()) { i++; return null; }
                if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
                    return Long.valueOf(Long.parseLong(t));
                }
                return Double.valueOf(Double.parseDouble(t));
            }
        }
    }

    // ------------------------------------------------------------------- TLS

    /**
     * Construye el contexto TLS a partir de los dos ficheros PEM que genera
     * generar_certificado.sh. Java no lee PEM directamente, así que hay que
     * montar un almacén de claves en memoria.
     */
    static SSLContext contextoTls(String rutaCert, String rutaClave) throws Exception {
        byte[] derClave = pem(new String(Files.readAllBytes(Paths.get(rutaClave)), StandardCharsets.UTF_8));
        PrivateKey clavePrivada = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(derClave));

        Certificate certificado;
        InputStream entradaCert = Files.newInputStream(Paths.get(rutaCert));
        try {
            certificado = CertificateFactory.getInstance("X.509").generateCertificate(entradaCert);
        } finally {
            entradaCert.close();
        }

        KeyStore almacen = KeyStore.getInstance("PKCS12");
        almacen.load(null, null);
        almacen.setKeyEntry("servidor", clavePrivada, new char[0], new Certificate[]{certificado});

        KeyManagerFactory fabrica = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        fabrica.init(almacen, new char[0]);

        SSLContext contexto = SSLContext.getInstance("TLSv1.2");
        contexto.init(fabrica.getKeyManagers(), null, null);
        return contexto;
    }

    /** Quita las líneas BEGIN/END de un PEM y descodifica el base64. */
    static byte[] pem(String texto) {
        String limpio = texto.replaceAll("-----BEGIN [^-]+-----", "")
                             .replaceAll("-----END [^-]+-----", "")
                             .replaceAll("\\s", "");
        return Base64.getDecoder().decode(limpio);
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        InetSocketAddress direccion = new InetSocketAddress("0.0.0.0", PUERTO);
        HttpServer servidor;
        String esquema;

        if (CERT != null && CLAVE != null) {
            HttpsServer seguro = HttpsServer.create(direccion, 0);
            seguro.setHttpsConfigurator(new HttpsConfigurator(contextoTls(CERT, CLAVE)));
            servidor = seguro;
            esquema = "https";
        } else {
            servidor = HttpServer.create(direccion, 0);
            esquema = "http";
        }

        servidor.createContext("/", new Manejador());
        servidor.setExecutor(Executors.newFixedThreadPool(8));

        OUT.println("Backend de prácticas escuchando en " + esquema + "://0.0.0.0:" + PUERTO);
        if (esquema.equals("http")) {
            OUT.println("MODO SIN CIFRAR: es el punto de partida de la práctica P3.2.");
        } else {
            OUT.println("MODO TLS: certificado " + CERT);
        }
        OUT.println("Desde el emulador de Android, la dirección del anfitrión es 10.0.2.2");
        OUT.println("Usuarios de prueba: ana / luis / admin  ·  contraseña: Practica2024 (admin: Administrador)");

        servidor.start();
    }
}
