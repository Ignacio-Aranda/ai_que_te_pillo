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

- Inicio de sesión, listado de misiones y pantalla de detalle con Jetpack Compose, navegación con argumentos de ruta y enlace profundo `aiquetepillo://mision/{id}`.
- Persistencia local con Room y sesión en `SharedPreferences`, con el cierre de sesión que borra también la copia local (P2.2, sección 5.6).
- Foto de la misión activa con CameraX dentro de la aplicación: el permiso de cámara se pide en tiempo de ejecución al pulsar «HACER FOTO» y, si se deniega, la aplicación sigue funcionando sin esa función. La foto se guarda en el directorio privado y se muestra en la misión; todavía no se sube al servidor.
- La ventana está marcada como segura (`FLAG_SECURE`).

El modelo de datos ya está adaptado al dominio: la aplicación y el backend trabajan con misiones (`Mision`: título, descripción, puntos según la dificultad y fecha). Faltan los grupos, los miembros, la generación de misiones con IA y la verificación de las fotos.

La aplicación es **deliberadamente insegura** en el Bloque II: guarda el token y los datos en claro, registra las peticiones completas en logcat y usa HTTP sin cifrar. Cada uno de esos puntos se corrige en el Bloque III. No debe usarse fuera del aula ni exponerse a Internet.

## Convenciones del equipo

| Convención | Valor | Justificación |
|---|---|---|
| Nombre de paquete | `es.uma.aiquetepillo` | Android exige al menos un punto en el `applicationId` |
| `minSdk` | 26 (Android 8.0) | El del proyecto base de la asignatura; cubre la práctica totalidad de los dispositivos en uso |
| `targetSdk` | 36 (Android 16) | La versión más reciente con la que la aplicación funciona contra el backend: con 37, la conexión a `10.0.2.2` agota el tiempo de espera, previsiblemente por la protección de red local de Android 17. Se subirá cuando se resuelva ese acceso |
| `compileSdk` | 37 | Lo exigen las versiones actuales de las bibliotecas de AndroidX |
| Ramas | `main` + una rama por funcionalidad, integrada mediante *pull request* | `main` siempre compila y se ejecuta |
| Responsables | Uno por cada requisito técnico mínimo | Ver [Reparto de trabajo](#reparto-de-trabajo) |
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

App analizada: **Frunk** (`com.frunk_ignacioaranda`, versionName "Version 3", versionCode 3), una app de minijuegos para grupos (ruleta, dado, moneda, trivial…). Instalada desde Google Play (`installerPackageName=com.android.vending`) en un Pixel 7a. Los APK se sacaron con `adb pull` de las rutas que devuelve `pm path`.

| Qué mirar | Con qué | Observación |
|---|---|---|
| Permisos declarados | `aapt2 dump permissions` | 12 `uses-permission`. Peligrosos: `CAMERA` y `WRITE_EXTERNAL_STORAGE` (este último sin `maxSdkVersion`, pero con `targetSdk` 34 no da nada a partir de Android 11). Normales: `INTERNET`, `ACCESS_NETWORK_STATE`, `VIBRATE`, `WAKE_LOCK`, `FOREGROUND_SERVICE`. Publicidad: `AD_ID` y `ACCESS_ADSERVICES_AD_ID`/`_ATTRIBUTION`/`_TOPICS`, que añade el SDK de Google Mobile Ads al fusionar manifiestos. El último es `com.frunk_ignacioaranda.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, que la app declara y usa ella misma (lo genera AndroidX para los receptores dinámicos). |
| Componentes exportados | `apktool` + lectura del manifiesto | 43 componentes (24 actividades, 8 servicios, 8 receptores, 3 proveedores); solo 3 exportados: `ActividadHome` (la del lanzador, `MAIN`/`LAUNCHER`), `SystemJobService` de WorkManager (protegido con `BIND_JOB_SERVICE`) y `DiagnosticsReceiver` de WorkManager (protegido con `DUMP`). Ninguno queda abierto a otras apps salvo la actividad de inicio. Los 3 proveedores (Firebase, Mobile Ads, AndroidX Startup) son `exported="false"`. Otros datos del manifiesto: `allowBackup="true"` con reglas de copia vacías (se copia todo), no es `debuggable` y no declara `networkSecurityConfig`. |
| Esquemas de firma | `apksigner verify --print-certs` | Firma v2 y v3; v1 (JAR), v3.1 y v4 no. Un firmante, RSA 4096, certificado `CN=Android, OU=Android, O=Google Inc.`: es la clave de Play App Signing, así que Google firma la app, no el desarrollador. SHA-256 del certificado: `8141a9d0…e6c64397`. Lleva también *Source Stamp* de Google Play. |
| `minSdk` y `targetSdk` | `aapt2 dump badging` | `minSdk` 26 (Android 8.0), `targetSdk` 34 (Android 14), `compileSdk` 34. |
| Número de piezas instaladas | `adb shell pm path` | 5 piezas, porque se instaló como App Bundle desde Play: `base.apk` (25 MB, con 3 `classes*.dex`) y 4 *splits* de configuración: `split_config.arm64_v8a.apk` (ABI), `split_config.xxhdpi.apk` (densidad), `split_config.en.apk` y `split_config.es.apk` (idiomas). |

**¿Pide algún permiso que no sabríamos justificar a partir de lo que hace?** Sí. Frunk es un juego de minijuegos para grupos con una pantalla de cámara (`ActividadCamara`) y música de fondo (`MusicService`), así que `CAMERA`, `INTERNET`, `VIBRATE` y `WAKE_LOCK` se entienden. No se justifican por la función:

- `WRITE_EXTERNAL_STORAGE`: con `targetSdk` 34 no concede nada desde Android 11, y en Android 8 a 10 daría escritura sobre todo el almacenamiento compartido. Al no llevar `maxSdkVersion` parece un resto de una versión antigua.
- `AD_ID` y los tres `ACCESS_ADSERVICES_*`: sirven para publicidad y atribución, no para jugar. No los escribió el desarrollador: los trae el SDK de Google Mobile Ads al fusionar manifiestos.
- `FOREGROUND_SERVICE`: el único servicio propio (`MusicService`) no declara `foregroundServiceType`, que es obligatorio con `targetSdk` 34 para ejecutarse en primer plano, así que el permiso no parece usarse.

**¿Cuántos componentes tiene exportados?** 3 de 43: `ActividadHome`, que es la del lanzador, y dos de WorkManager (`SystemJobService` y `DiagnosticsReceiver`) protegidos con permisos que solo tiene el sistema (`BIND_JOB_SERVICE` y `DUMP`). En la práctica, otra aplicación solo puede abrir la pantalla de inicio.

**Lo que nos llevamos para la nuestra:** revisar el manifiesto fusionado y no solo el que escribimos, porque las bibliotecas añaden permisos y componentes; declarar solo los permisos que se usan, y no dejar `allowBackup="true"` con reglas vacías, que es lo que tiene Frunk y lo que tenemos nosotros ahora mismo.

## Inventario de datos sensibles (P2.2, sección 6.2)

Estado del Bloque II. Las filas marcadas *(previsto)* son datos que la aplicación aún no maneja pero que forman parte del diseño ([TODO.md](TODO.md)); se describe dónde quedarían si se implementasen tal como está el código hoy.

| Dato | Dónde se guarda hoy | Quién puede leerlo hoy | Cómo se protegerá |
|---|---|---|---|
| Token de sesión | `shared_prefs/sesion.xml`, en claro. Además se escribe en logcat dos veces: el `Log.d` de `RespuestaLogin` en `Repositorio.iniciarSesion` y el interceptor de OkHttp con nivel `BODY`. Viaja por HTTP en la cabecera `Authorization` de cada petición | Cualquiera con adb y la depuración activada (`run-as`, `logcat`); cualquiera en la misma red; la copia de seguridad en la nube (`allowBackup="true"` sin exclusiones). Es además predecible: Base64 de `usuario:hora` | A completar en el Bloque III |
| Contraseña | No se guarda en el dispositivo. Viaja en claro en el cuerpo de `POST /api/login` y queda en logcat por el interceptor. En el backend está en claro, en memoria | Cualquiera con `adb logcat`; cualquiera en la misma red; quien tenga acceso al servidor | A completar en el Bloque III |
| Nombre de usuario | `shared_prefs/sesion.xml` y columna `propietario` de la tabla `misiones`, en claro | Cualquiera con adb y la depuración activada; la copia de seguridad en la nube | A completar en el Bloque III |
| Misiones (título, descripción, puntos, fecha) | `databases/aiquetepillo.db` y sus ficheros `-wal` y `-shm`, sin cifrar; los cuerpos de las respuestas, en logcat. Las descripciones nombran a personas reales («Pilla a Luis…») | Cualquiera con adb y la depuración activada; la copia de seguridad en la nube; cualquiera en la misma red. El servidor entrega la misión de otro usuario a quien pida su id | A completar en el Bloque III |
| Datos personales del perfil (nombre, correo, DNI, rol) | No se guardan en el dispositivo: la interfaz aún no llama a `GET /api/perfil`, aunque el cliente ya la tiene. En el backend, en claro | Cualquier usuario autenticado puede listar los de todos con `GET /api/admin/usuarios`, que no comprueba el rol; cualquiera en la misma red cuando se pidan | A completar en el Bloque III |
| Selfie de registro *(previsto)* | Todavía no existe. Se haría con la cámara de la app y se subiría al backend. Es un dato biométrico, categoría especial del art. 9 del RGPD | Nadie todavía. Con el código actual viajaría en claro por HTTP y quedaría sin cifrar en el servidor y en la caché local | A completar en el Bloque III |
| Fotos de las misiones | `cache/fotos/mision_<id>.jpg` en el directorio privado, sin cifrar; se borran al cerrar sesión. Todavía no se suben: en el diseño irían al backend y este las reenviaría al proveedor de IA (OpenRouter) para validarlas | Cualquiera con adb y `run-as` sobre la variante depurable. Cuando se suban: la red, el servidor y un tercero, el proveedor de IA | A completar en el Bloque III |
| Contexto y código de invitación del grupo *(previsto)* | Todavía no existen. Se guardarían en el backend y en la caché de Room; el código viajaría también en el enlace `aiquetepillo://unirse/{codigo}` | Nadie todavía. Con el código actual: adb, la red y cualquiera que reciba o adivine el enlace (6 caracteres) | A completar en el Bloque III |
| Clave del proveedor de IA *(previsto)* | Solo en el backend, como variable de entorno. Nunca en el APK ni en el repositorio | Quien tenga acceso al servidor. Si llegase al APK, cualquiera que lo decompile con jadx | A completar en el Bloque III |

## Inventario de la superficie expuesta (P2.3, sección 5)

En nuestro manifiesto solo declaramos exportada `MainActivity`, pero el manifiesto fusionado de la variante de depuración exporta cuatro componentes: `MainActivity`, dos actividades que añaden las bibliotecas de Compose y un receptor de AndroidX. Las dos actividades vienen de dependencias `debugImplementation` y desaparecen en la variante de publicación, pero el APK de depuración es el que se instala con `adb` y el que se audita en el Bloque IV. Se comprueba con `aapt2 dump xmltree --file AndroidManifest.xml app-debug.apk` o con `adb shell dumpsys package es.uma.aiquetepillo`.

| Vía de entrada | Cómo se invoca hoy | Qué comprueba hoy | Se trata en |
|---|---|---|---|
| `MainActivity` exportada | `adb shell am start -n es.uma.aiquetepillo/.MainActivity`, o cualquier aplicación con un `Intent` explícito | Nada | Sesión 3.4 |
| `androidx.compose.ui.tooling.PreviewActivity` exportada (solo en depuración) | `adb shell am start -n es.uma.aiquetepillo/androidx.compose.ui.tooling.PreviewActivity`, o cualquier aplicación con un `Intent` explícito | Nada: está exportada sin permiso. La añade `ui-tooling` y dibuja el *composable* cuyo nombre recibe en el `Intent`, sin pasar por la navegación de la app | Sesión 3.4 |
| `androidx.activity.ComponentActivity` exportada (solo en depuración) | `adb shell am start -n es.uma.aiquetepillo/androidx.activity.ComponentActivity`, o cualquier aplicación con un `Intent` explícito | Nada: está exportada sin permiso. La añade `ui-test-manifest` para las pruebas de Compose y abre una actividad vacía | Sesión 3.4 |
| `androidx.profileinstaller.ProfileInstallReceiver` exportado (depuración y publicación) | Un *broadcast* dirigido al receptor | Exige `android.permission.DUMP`, que solo tienen el sistema y `adb shell`; ninguna otra aplicación puede invocarlo | Sesión 3.4 |
| Enlace profundo `aiquetepillo://mision/{id}` | `adb shell am start -a android.intent.action.VIEW -d "aiquetepillo://mision/3" es.uma.aiquetepillo`, o un enlace en un mensaje, una web u otra app | Nada en la interfaz: abre el detalle sin pasar por la pantalla de acceso, y solo exige que el id sea un entero. Si queda un token guardado, carga la misión de ese id aunque sea de otro usuario | Sesión 3.4 |
| Llamadas a la API | `curl` con un token válido, por ejemplo `curl -H "Authorization: Bearer <token>" http://localhost:8080/api/misiones/3` | Solo la presencia del token, que además es predecible. No comprueba de quién es la misión (`/api/misiones/{id}`) ni el rol (`/api/admin/usuarios`), y el inicio de sesión dice si el usuario existe | Sesión 3.5 · P3.2 (el canal) · Bloque IV (la autorización) |
| Canal de red | Cualquiera en la misma red o con un proxy entre el móvil y el backend | Nada: HTTP en claro, permitido por `usesCleartextTraffic="true"` | Sesión 3.5 · P3.2 |
| Almacenamiento local | `adb shell run-as es.uma.aiquetepillo`, y desde ahí `shared_prefs/sesion.xml` y `databases/aiquetepillo.db` (con `-wal` y `-shm`) | Nada: está en claro | Sesión 3.3 · P3.1 |
| Copia de seguridad | Copia automática en la nube de Google, o `adb backup` sobre la variante de depuración | Nada: `allowBackup="true"` con reglas vacías, así que se copian el token y la base de datos | Sesión 3.3 · P3.1 |
| Registros | `adb logcat \| grep -i -E "Repositorio\|okhttp"` | Nada: salen el usuario, la contraseña, el token y los cuerpos de todas las respuestas | Sesión 3.6 |
| Vista de recientes y capturas | Botón de recientes del sistema, o una captura de pantalla | Protegida: `FLAG_SECURE` en `MainActivity` deja la miniatura en negro y bloquea las capturas | Sesión 3.6 |
| Permiso de cámara | Botón «HACER FOTO» de la misión activa | El sistema pide el permiso en tiempo de ejecución y la app sigue funcionando si se deniega. Se usa para hacer la foto de la misión con CameraX dentro de la app; la pantalla de la cámara vuelve a comprobar el permiso y no tiene enlace profundo | Bloque III · Bloque IV |
| Enlace `aiquetepillo://unirse/{codigo}` *(previsto)* | Un enlace compartido con el selector de Android | Debe llevar a una pantalla de confirmación y no unir sin aceptar. Con 6 caracteres el código se puede adivinar por fuerza bruta contra la API | Sesión 3.4 · Bloque IV |
| Subida de fotos y envío al proveedor de IA *(previsto)* | Desde la app, o con `curl` contra el endpoint de subida | Pendiente de diseñar: tamaño, tipo de fichero y que la foto sea de la misión propia. Una foto trucada podría engañar al modelo de visión | Sesión 3.5 · Bloque IV |

## Reparto de trabajo

Cada miembro es responsable de un requisito técnico mínimo de principio a fin: lo construye en el Bloque II, lo protege en el Bloque III y responde de él en la auditoría del Bloque IV. Como la API remota es el bloque más grande (el backend entero, más la integración con el proveedor de IA), su responsable no lleva nada más; los otros tres suman una tarea de tamaño parecido, elegida para que caiga en la misma práctica de protección que su requisito.

| Miembro | Requisito técnico mínimo | Además | Lo protege en |
|---|---|---|---|
| José Ignacio Aranda Gómez-Guillamón | Llamada a una API remota: backend de grupos, misiones y clasificación; generación y verificación con el proveedor de IA | — | P3.2 (TLS y anclaje de certificados) |
| Jorge Varea Durán | Autenticación de usuarios: registro e inicio de sesión en app y backend, gestión del token y cierre de sesión | Coordinar el documento de alcance | Sesión 3.5 · Bloque IV (tokens y autorización) |
| Alejandro Galán Rita | Almacenamiento local de datos sensibles: sesión, caché de Room de grupos y misiones, borrado al cerrar sesión | Pantalla de clasificación | P3.1 (cifrado en reposo) |
| Gonzalo Ramos Tarifa | Permiso peligroso justificado: cámara con CameraX para el selfie de registro y las fotos de las misiones | Enlace profundo `aiquetepillo://unirse/{codigo}` y su pantalla de confirmación | P3.3 (componentes, permisos y fugas) |
