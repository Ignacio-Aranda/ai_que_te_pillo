# Backend · AI que te pillo

Parte del backend común de la asignatura, ampliado para el juego: registro con
selfie, grupos con código de invitación, partidas con misiones generadas por IA,
verificación de las fotos y clasificación. Los elementos del proyecto base son
ahora misiones (`/api/misiones`). Sin certificado arranca en **HTTP sin cifrar**,
el punto de partida de la práctica P3.2; desde esa práctica la aplicación solo
habla con él en **modo TLS**, en el puerto 8443 (ver más abajo).

## Requisitos

    servidor.py      requiere Python 3.10 o posterior y el SDK de OpenRouter (`openrouter`)

La asignatura entrega también una versión equivalente en Java; el equipo amplía
solo la de Python.

La IA (generación de misiones y comprobación de fotos) usa el modelo
`anthropic/claude-haiku-5.5` a través de OpenRouter. La clave se pone en un
fichero `backend/.env`, que está excluido del repositorio:

    OPENROUTER_API_KEY=sk-or-...

Sin clave, el servidor arranca en **modo sin IA**: las misiones salen de una
lista fija y las fotos se aceptan sin comprobarlas.

## Puesta en marcha

Con uv, que instala el SDK la primera vez (lo declara la cabecera del script),
en modo TLS, que es el que espera la aplicación:

    PUERTO=8443 CERT=cert.pem CLAVE=clave.pem uv run servidor.py

Sin las variables `CERT` y `CLAVE` arranca en HTTP sin cifrar en el 8080
(`uv run servidor.py`), útil para comparar los dos modos.

Con Python y pip:

    pip install openrouter
    python3 servidor.py        # Linux y macOS
    py servidor.py             # Windows

Con Docker, también en modo TLS (`docker-compose.yml` monta `cert.pem` y
`clave.pem` desde esta carpeta):

    docker compose up --build

Comprobación:

    curl --cacert ca.pem https://localhost:8443/api/salud       # Linux y macOS
    curl.exe --cacert ca.pem https://localhost:8443/api/salud   # Windows

En PowerShell, `curl` es un alias de `Invoke-WebRequest` y no acepta las mismas
opciones: hay que escribir `curl.exe`.

Para cambiar el puerto:

    PUERTO=9000 uv run servidor.py             # Linux y macOS

## Dirección desde el dispositivo

| Entorno | Dirección base |
|---|---|
| Emulador de Android | `https://10.0.2.2:8443` |
| Dispositivo físico en la misma red | `https://<IP_del_ordenador>:8443`, con el certificado emitido para esa IP (`EXTRA=<IP>`) |
| Navegador del ordenador | `https://localhost:8443` |

El emulador tiene su propia interfaz de red: `localhost` dentro del emulador es
el propio emulador, no el ordenador. Por eso se usa `10.0.2.2`.

## Usuarios de prueba

| Usuario | Contraseña |
|---|---|
| `ana` | `Practica2024` |
| `luis` | `Practica2024` |

`ana` y `luis` comparten el grupo «Casa rural» (código `K7P2QX`), con la partida
empezada y una misión cada uno. Las cuentas nuevas se crean desde la aplicación
o con `POST /api/registro`. Todos los datos viven en memoria y se pierden al
reiniciar el servidor.

## Endpoints

| Método | Ruta | Autenticación | Descripción |
|---|---|---|---|
| GET | `/api/salud` | no | Comprobación de disponibilidad |
| POST | `/api/registro` | no | Recibe `{usuario, clave, nombre, selfie}` con el selfie en Base64; crea la cuenta y devuelve lo mismo que el acceso |
| POST | `/api/selfie` | Bearer | Recibe `{selfie}` en Base64 y lo guarda si la cuenta aún no tiene (las de ejemplo). 409 si ya tiene |
| GET | `/api/usuarios/{usuario}/selfie` | Bearer | Devuelve el selfie (JPEG) a su dueño y a quien comparte un grupo con él; 404 para el resto o si no tiene |
| POST | `/api/login` | no | Recibe `{usuario, clave}`; devuelve `{token, usuario, nombre}` |
| GET | `/api/misiones` | Bearer | Lista las misiones activas del usuario autenticado, una por grupo con la partida empezada |
| POST | `/api/misiones` | Bearer | Crea una misión a partir de `{titulo, descripcion, puntos}` |
| GET | `/api/misiones/{id}` | Bearer | Devuelve una misión por su identificador |
| GET | `/api/grupos` | Bearer | Lista los grupos del usuario autenticado, con sus miembros |
| POST | `/api/grupos` | Bearer | Crea un grupo a partir de `{nombre, contexto}`; el creador entra como miembro y se genera un código de invitación |
| GET | `/api/invitaciones/{codigo}` | Bearer | Vista previa del grupo de un código, para confirmar antes de unirse |
| POST | `/api/grupos/unirse` | Bearer | Une al usuario autenticado al grupo de `{codigo}`; si la partida ya ha empezado, le genera su misión |
| POST | `/api/grupos/{id}/empezar` | Bearer | Empieza la partida (solo quien creó el grupo, con dos jugadores o más): la IA genera una misión para cada miembro |
| POST | `/api/misiones/{id}/foto` | Bearer | Recibe `{foto}` en Base64; la IA la compara con la misión y con el selfie del objetivo y devuelve `{valida, motivo, puntos}`. Si es válida, suma los puntos y genera otra misión |
| POST | `/api/misiones/{id}/saltar` | Bearer | Resta un punto y genera otra misión |

Ejemplo:

    TOKEN=$(curl -s -X POST http://localhost:8080/api/login \
      -H 'Content-Type: application/json' \
      -d '{"usuario":"ana","clave":"Practica2024"}' | grep -o '"token": "[^"]*' | cut -d'"' -f4)

    curl http://localhost:8080/api/misiones -H "Authorization: Bearer $TOKEN"

## Modo TLS (a partir de la práctica P3.2)

`generar_certificado.sh` crea una autoridad de laboratorio y dos certificados
de servidor firmados por ella:

| Fichero | Qué es | ¿En el repositorio? |
|---|---|---|
| `ca.pem` | La autoridad del laboratorio. La aplicación la lleva, solo en la variante de depuración, en `app/src/debug/res/raw/cert_laboratorio.pem` | Solo esa copia |
| `ca-clave.pem` | Su clave privada; solo hace falta para emitir certificados | No |
| `cert.pem`, `clave.pem` | El certificado que sirve el backend (hoja seguida de la autoridad) y su clave | No |
| `cert_reserva.pem`, `clave_reserva.pem` | El certificado de reserva, firmado por la misma autoridad | No |

Los certificados valen para `api.local`, `localhost`, `127.0.0.1` y `10.0.2.2`.
El guion imprime al terminar el resumen SHA-256 de la clave pública de los dos
certificados, que es lo que la aplicación ancla en su configuración de red.

**No se vuelve a ejecutar.** Cada ejecución emite dos claves nuevas que no
coinciden con las ancladas, y la aplicación deja de conectar: falla con
`Pin verification failed`. Las claves privadas no están en el repositorio
(`.gitignore` excluye `backend/*.pem`) y se comparten dentro del grupo, igual
que `.env`. Si hubiera que cambiarlas, se sigue el procedimiento de rotación
del apartado 3.4 del documento de diseño de seguridad.

Para pasar a la clave de reserva sin publicar una versión de la aplicación:

    PUERTO=8443 CERT=cert_reserva.pem CLAVE=clave_reserva.pem uv run servidor.py

Comprobación desde el ordenador, y la cadena completa que sirve el backend:

    curl --cacert ca.pem https://localhost:8443/api/salud
    openssl s_client -showcerts -connect localhost:8443 </dev/null

Sin `--cacert`, la conexión debe fallar: ninguna autoridad del sistema respalda
el certificado.

## Debilidades intencionadas

Este servidor es material docente y contiene fallos puestos a propósito. Se usan
en el Bloque III para razonar sobre defensas y en el Bloque IV como hallazgos a
localizar. **No debe usarse fuera del aula.**

| Debilidad | Dónde se trata |
|---|---|
| Todo el tráfico viaja en claro (modo por defecto, que la aplicación ya no acepta) | P3.2 · sesión 3.5 |
| Contraseñas almacenadas sin resumen ni sal | sesión 3.3 |
| Token predecible: `base64(usuario:tiempo)` | Bloque IV |
| El token no caduca ni se puede revocar | Bloque IV |
| Sin límite de intentos de autenticación | Bloque IV |
| El error distingue «usuario inexistente» de «contraseña incorrecta» | sesión 2.4 · Bloque IV |
| El certificado del modo TLS lo firma una autoridad de laboratorio | Aceptable en el laboratorio, nunca en producción |
| El registro confirma qué usuarios existen (409), no exige ninguna política de contraseñas y no limita el tamaño del selfie ni de las fotos | Bloque IV |
| El contexto del grupo y las fotos llegan al modelo de IA: se pueden usar para inyectarle instrucciones (texto escrito en la foto, contexto manipulado) | Bloque IV |
| En modo sin IA, cualquier foto se da por buena | Solo para desarrollo |
| Los códigos de invitación se generan con un generador no criptográfico, no caducan y se pueden probar sin límite: `/api/invitaciones/{codigo}` responde 404 a los que no existen | Bloque IV |
| `/api/misiones/{id}` no comprueba la propiedad de la misión | Bloque IV |

La última es una referencia directa a la idea de que la autorización debe
comprobarse donde la operación se ejecuta, no donde se ofrece.
