# Backend · AI que te pillo

Parte del backend común de la asignatura, adaptado al dominio del juego: los
elementos del proyecto base son ahora misiones (`/api/misiones`). API REST mínima para las prácticas del Bloque II. Sirve por **HTTP sin cifrar**
de forma deliberada: en la práctica P3.2 se comparará este tráfico con el de la
misma aplicación tras aplicar TLS y anclaje de certificados.

## Requisitos

    servidor.py      requiere Python 3 (cualquier versión), sin bibliotecas externas

La asignatura entrega también una versión equivalente en Java; el equipo amplía
solo la de Python.

## Puesta en marcha

Con Python:

    python3 servidor.py        # Linux y macOS
    py servidor.py             # Windows

Con Docker:

    docker compose up --build

Comprobación:

    curl http://localhost:8080/api/salud       # Linux y macOS
    curl.exe http://localhost:8080/api/salud   # Windows

En PowerShell, `curl` es un alias de `Invoke-WebRequest` y no acepta las mismas
opciones: hay que escribir `curl.exe`.

Para cambiar el puerto:

    PUERTO=9000 python3 servidor.py            # Linux y macOS

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
| GET | `/api/misiones` | Bearer | Lista las misiones del usuario autenticado |
| POST | `/api/misiones` | Bearer | Crea una misión a partir de `{titulo, descripcion, puntos}` |
| GET | `/api/misiones/{id}` | Bearer | Devuelve una misión por su identificador |
| GET | `/api/perfil` | Bearer | Datos personales del usuario autenticado |
| GET | `/api/admin/usuarios` | Bearer | Listado de todas las cuentas |
| GET | `/api/grupos` | Bearer | Lista los grupos del usuario autenticado, con sus miembros |
| POST | `/api/grupos` | Bearer | Crea un grupo a partir de `{nombre, contexto}`; el creador entra como miembro y se genera un código de invitación |
| GET | `/api/invitaciones/{codigo}` | Bearer | Vista previa del grupo de un código, para confirmar antes de unirse |
| POST | `/api/grupos/unirse` | Bearer | Une al usuario autenticado al grupo de `{codigo}` |

Ejemplo:

    TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
      -H 'Content-Type: application/json' \
      -d '{"usuario":"ana","clave":"Practica2024"}' | grep -o '"token": "[^"]*' | cut -d'"' -f4)

    curl http://localhost:8080/api/misiones -H "Authorization: Bearer $TOKEN"

## Modo TLS (a partir de la práctica P3.2)

El servidor arranca en HTTP sin cifrar por defecto. Para la práctica P3.2 se
pasa a HTTPS con un certificado autofirmado.

    ./generar_certificado.sh
    PUERTO=8443 CERT=cert.pem CLAVE=clave.pem python3 servidor.py

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
| Los códigos de invitación se generan con un generador no criptográfico, no caducan y se pueden probar sin límite: `/api/invitaciones/{codigo}` responde 404 a los que no existen | Bloque IV |
| `/api/misiones/{id}` no comprueba la propiedad de la misión | Bloque IV |
| `/api/admin/usuarios` no comprueba el rol | sesión 2.5 · Bloque IV |

Las dos últimas son referencias directas a la idea de que la autorización debe
comprobarse donde la operación se ejecuta, no donde se ofrece.
