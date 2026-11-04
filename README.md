# AI que te pillo

Proyecto de grupo de la asignatura Seguridad en Entornos Móviles (Universidad de Málaga, curso 2026/27).

## Equipo

**Grupo L**

- José Ignacio Aranda Gómez-Guillamón
- Jorge Varea Durán
- Alejandro Galán Rita
- Gonzalo Ramos Tarifa

**Dominio asignado (P2.1, sección 5.1):** Enlace, mensajería local entre dispositivos.

## La aplicación

Juego de fiesta para grupos de amigos. Cada jugador recibe misiones generadas por IA del tipo «pilla a Ignacio bebiendo agua», adaptadas al contexto del grupo, y las completa con una foto que una IA valida comparándola con el selfie de registro del objetivo. Los puntos de cada misión van a una clasificación del grupo.

El dato más sensible son las fotos de personas: el selfie de registro es un dato biométrico, y las fotos de las misiones se suben al servidor y se envían a un proveedor de IA externo. El detalle funcional y el plan de trabajo están en [TODO.md](TODO.md).

## Requisitos técnicos mínimos

| Requisito | Cómo se cumple |
|---|---|
| Autenticación de usuarios | Inicio de sesión contra el backend, que devuelve un token de sesión |
| Almacenamiento local de datos sensibles | Token y usuario en `SharedPreferences`; copia local de los datos en una base de datos Room |
| Llamada a una API remota | Retrofit contra el backend propio, que en la versión final llamará también al proveedor de IA |
| Permiso peligroso justificado | `CAMERA`, solicitado en tiempo de ejecución, para el selfie de registro y las fotos de las misiones |

## Estado actual

El código parte del proyecto base de las prácticas P2.2 y P2.3, con sus cambios aplicados:

- Inicio de sesión, listado y pantalla de detalle con Jetpack Compose, navegación con argumentos de ruta y enlace profundo `aiquetepillo://detalle/{id}`.
- Persistencia local con Room y sesión en `SharedPreferences`, con el cierre de sesión que borra también la copia local (P2.2, sección 5.6).
- Permiso de cámara solicitado en tiempo de ejecución; si se deniega, la aplicación sigue funcionando sin esa función.
- La ventana está marcada como segura (`FLAG_SECURE`).

El modelo de datos todavía es el del proyecto base (`Elemento`). El siguiente paso es adaptarlo al dominio del juego (`Mision`, `Grupo`, `Miembro`) y ampliar el backend.

La aplicación es **deliberadamente insegura** en el Bloque II: guarda el token y los datos en claro, registra las peticiones completas en logcat y usa HTTP sin cifrar. Cada uno de esos puntos se corrige en el Bloque III. No debe usarse fuera del aula ni exponerse a Internet.

## Convenciones del equipo

| Convención | Valor | Justificación |
|---|---|---|
| Nombre de paquete | `es.uma.aiquetepillo` | Android exige al menos un punto en el `applicationId` |
| `minSdk` | 26 (Android 8.0) | El del proyecto base de la asignatura; cubre la práctica totalidad de los dispositivos en uso |
| `targetSdk` | 36 (Android 16) | La versión más reciente con la que la aplicación funciona contra el backend: con 37, la conexión a `10.0.2.2` agota el tiempo de espera, previsiblemente por la protección de red local de Android 17. Se subirá cuando se resuelva ese acceso |
| `compileSdk` | 37 | Lo exigen las versiones actuales de las bibliotecas de AndroidX |
| Ramas | `main` + una rama por funcionalidad, integrada mediante *pull request* | `main` siempre compila y se ejecuta |
| Responsables | Uno por cada requisito técnico mínimo | Por asignar |
| Secretos | Ninguna clave, almacén de claves ni contraseña en el repositorio | La clave del proveedor de IA vive solo en el backend, como variable de entorno |

## Compilar y ejecutar

Requisitos: Android Studio con el SDK de la plataforma 37 y un emulador, y Python 3 para el backend.

1. Arrancar el backend en el ordenador:

   ```bash
   python3 backend/servidor.py
   ```

   Escucha en el puerto 8080. Los usuarios de prueba están en [backend/LEEME.md](backend/LEEME.md).

2. Abrir el proyecto en Android Studio y ejecutarlo en el emulador, o desde la línea de comandos:

   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   Desde la línea de comandos, Gradle necesita saber dónde está el SDK: la variable `ANDROID_HOME` o un fichero `local.properties` con `sdk.dir=...` (Android Studio lo crea solo y está excluido del repositorio).

   Desde el emulador, el backend está en `http://10.0.2.2:8080/`. En un dispositivo físico hay que cambiar `ClienteRed.BASE_URL` en `ApiServicio.kt` por la IP del ordenador, o usar `adb reverse tcp:8080 tcp:8080` con `http://localhost:8080/`.

## Reconocimiento de una aplicación real (P2.1, sección 5.4)

Pendiente de completar.

| Qué mirar | Con qué | Observación |
|---|---|---|
| Permisos declarados | `aapt2 dump permissions` | |
| Componentes exportados | `apktool` + lectura del manifiesto | |
| Esquemas de firma | `apksigner verify --print-certs` | |
| `minSdk` y `targetSdk` | `aapt2 dump badging` | |
| Número de piezas instaladas | `adb shell pm path` | |
