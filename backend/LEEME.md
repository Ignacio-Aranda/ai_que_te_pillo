# Backend común · Seguridad en Entornos Móviles

API REST mínima para las prácticas del Bloque II. Sirve por **HTTP sin cifrar**
de forma deliberada: en la práctica P3.2 se comparará este tráfico con el de la
misma aplicación tras aplicar TLS y anclaje de certificados.

## Dos versiones del mismo servidor

El backend se entrega escrito dos veces, en Python y en Java:

    servidor.py      requiere Python 3 (cualquier versión)
    Servidor.java    requiere un JDK 11 o posterior

Los dos programas son **equivalentes**: mismos endpoints, mismos usuarios,
mismos datos de ejemplo, mismos códigos de respuesta y las mismas debilidades
intencionadas. Ninguno de los dos usa bibliotecas externas, de modo que no hay
nada que descargar ni que instalar aparte del intérprete.

Están los dos a propósito. Quien no tenga Python puede usar Java, porque el JDK
viene con Android Studio; y quien quiera ver el mismo programa expresado en dos
lenguajes tiene aquí un ejemplo de tamaño razonable para compararlos.

## Puesta en marcha

Con Python:

    python3 servidor.py        # Linux y macOS
    py servidor.py             # Windows

Con Java, sin compilar nada (el JDK ejecuta el fichero fuente directamente):

    java Servidor.java

Con Docker:

    docker compose up --build

Comprobación:

    curl http://localhost:8080/api/salud       # Linux y macOS
    curl.exe http://localhost:8080/api/salud   # Windows

En PowerShell, `curl` es un alias de `Invoke-WebRequest` y no acepta las mismas
opciones: hay que escribir `curl.exe`.

Para cambiar el puerto:

    PUERTO=9000 python3 servidor.py            # Linux y macOS
    PUERTO=9000 java Servidor.java             # Linux y macOS
    cmd /c "set PUERTO=9000 && java Servidor.java"    # Windows

Si al arrancar la versión de Java vieras los acentos como caracteres extraños,
es la consola de Windows, no el programa: ejecuta antes `chcp 65001`. Las
respuestas de la API van siempre en UTF-8 y no les afecta.

## Dirección desde el dispositivo

| Entorno | Dirección base |
|---|---|
| Emulador de Android | `http://10.0.2.2:8080` |
| Dispositivo físico en la misma red | `http://<IP_del_ordenador>:8080` |
| Navegador del ordenador | `http://localhost:8080` |

El emulador tiene su propia interfaz de red: `localhost` dentro del emulador es
el propio emulador, no el ordenador. Por eso se usa `10.0.2.2`.

## Usuarios de prueba

| Usuario | Contraseña | Rol |
|---|---|---|
| `ana` | `Practica2024` | usuario |
| `luis` | `Practica2024` | usuario |
| `admin` | `Administrador` | administrador |

## Endpoints

| Método | Ruta | Autenticación | Descripción |
|---|---|---|---|
| GET | `/api/salud` | no | Comprobación de disponibilidad |
| POST | `/api/login` | no | Recibe `{usuario, clave}`; devuelve `{token, usuario, nombre, rol}` |
| GET | `/api/elementos` | Bearer | Lista los elementos del usuario autenticado |
| POST | `/api/elementos` | Bearer | Crea un elemento a partir de `{titulo, contenido}` |
| GET | `/api/elementos/{id}` | Bearer | Devuelve un elemento por su identificador |
| GET | `/api/perfil` | Bearer | Datos personales del usuario autenticado |
| GET | `/api/admin/usuarios` | Bearer | Listado de todas las cuentas |

Ejemplo:

    TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
      -H 'Content-Type: application/json' \
      -d '{"usuario":"ana","clave":"Practica2024"}' | grep -o '"token": "[^"]*' | cut -d'"' -f4)

    curl http://localhost:8080/api/elementos -H "Authorization: Bearer $TOKEN"

## Modo TLS (a partir de la práctica P3.2)

El servidor arranca en HTTP sin cifrar por defecto. Para la práctica P3.2 se
pasa a HTTPS con un certificado autofirmado.

    ./generar_certificado.sh
    PUERTO=8443 CERT=cert.pem CLAVE=clave.pem python3 servidor.py

La versión de Java admite las mismas variables y lee los mismos dos ficheros
PEM:

    PUERTO=8443 CERT=cert.pem CLAVE=clave.pem java Servidor.java

El script genera `cert.pem` y `clave.pem`, y muestra por pantalla el resumen
SHA-256 de la clave pública, que es exactamente el valor que hace falta para
declarar el anclaje de certificados en la aplicación.

El certificado incluye como nombres válidos `api.local`, `localhost`, `127.0.0.1`
y `10.0.2.2`, que es la dirección con la que el emulador llega al anfitrión. Para
un dispositivo físico, añade la IP de tu ordenador:

    EXTRA=192.168.1.50 ./generar_certificado.sh

Comprobación desde el ordenador:

    curl --cacert cert.pem --resolve api.local:8443:127.0.0.1 \
         https://api.local:8443/api/salud

Sin `--cacert`, la conexión debe fallar: el certificado es autofirmado y ninguna
autoridad conocida lo respalda. Ese fallo es el comportamiento correcto.

Para volver a arrancar en claro, basta con no definir las variables `CERT` y
`CLAVE`. Conviene conservar ambos modos: la práctica P3.2 consiste precisamente
en comparar uno con otro.

## Debilidades intencionadas

Este servidor es material docente y contiene fallos puestos a propósito. Se usan
en el Bloque III para razonar sobre defensas y en el Bloque IV como hallazgos a
localizar. **No debe usarse fuera del aula.**

| Debilidad | Dónde se trata |
|---|---|
| Todo el tráfico viaja en claro (modo por defecto) | P3.2 · sesión 3.5 |
| Contraseñas almacenadas sin resumen ni sal | sesión 3.3 |
| Token predecible: `base64(usuario:tiempo)` | Bloque IV |
| El token no caduca ni se puede revocar | Bloque IV |
| Sin límite de intentos de autenticación | Bloque IV |
| El error distingue «usuario inexistente» de «contraseña incorrecta» | sesión 2.4 · Bloque IV |
| El certificado del modo TLS es autofirmado | Aceptable en el laboratorio, nunca en producción |
| `/api/elementos/{id}` no comprueba la propiedad del elemento | Bloque IV |
| `/api/admin/usuarios` no comprueba el rol | sesión 2.5 · Bloque IV |

Las dos últimas son referencias directas a la idea de que la autorización debe
comprobarse donde la operación se ejecuta, no donde se ofrece.
