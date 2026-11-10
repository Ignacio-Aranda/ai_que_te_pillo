# Documento de diseño de seguridad

**AI que te pillo** · Grupo L · Seguridad en Entornos Móviles, curso 2026/27 · Bloque III
Borrador iniciado en P3.1 · 10 de noviembre de 2026

Este documento describe cómo protege la aplicación los datos que maneja y qué ha decidido no proteger. Parte del [documento de alcance del Bloque II](../v1/documento_alcance.md) y se completa a lo largo del Bloque III: el apartado 2 corresponde a P3.1, el 3 a P3.2 y el 4 a P3.3.

## 1. La aplicación y su modelo de amenazas

*AI que te pillo* es un juego para grupos de amigos. Cada jugador recibe misiones generadas por un modelo de IA, del tipo «pilla a Luis bebiendo agua», y las completa con una foto que el mismo modelo compara con el selfie que el objetivo se hizo al registrarse. La aplicación habla con un backend propio por HTTP; el backend guarda usuarios, grupos y misiones y es el único que conoce la clave del proveedor de IA.

Los activos, por orden de gravedad, son las fotos de personas (el selfie de registro es un dato biométrico, art. 9 del RGPD, y las fotos de las misiones pueden mostrar a alguien en situaciones comprometidas), la sesión del usuario, que permite jugar y ver los grupos en su nombre, la contraseña, y el contenido de las misiones, que nombra a personas reales.

De los seis adversarios de la sesión 3.1, se consideran los siguientes:

| Adversario | Por qué aplica | Dónde se trata |
|---|---|---|
| Ladrón oportunista | El juego se usa en casas rurales y fiestas, con el teléfono a mano de otros | Apartado 2 |
| Aplicación maliciosa | Puede invocar los componentes y enlaces exportados | Apartado 4 |
| Atacante de red | Las partidas se juegan en wifis compartidas | Apartado 3 |
| Usuario con privilegios totales | Un jugador tramposo puede modificar la aplicación o leer lo que guarda | Apartados 2 y 4; la autorización, en el servidor |
| Analista con el artefacto | Basta el APK para buscar secretos y estudiar la API | Apartados 3 y 4 |
| Cadena de suministro | Las bibliotecas de AndroidX, OkHttp y el SDK del proveedor de IA | Apartado 5 |

Los escenarios de partida son los cuatro del documento de alcance: quien se cuela en un grupo, quien tiene el móvil en la mano o comparte la wifi, quien busca las fotos para difundirlas y el jugador tramposo.

## 2. Datos en reposo

### 2.1 Qué se cifra

La sesión del usuario (token, nombre de usuario y nombre visible) se guarda cifrada en `shared_prefs/sesion.xml`, en un único campo `datos`. El valor contiene el nonce y el texto cifrado en base64, separados por dos puntos. Los tres valores van en un solo paquete porque cada operación con la clave exige una autenticación: tres campos serían tres huellas.

Las fotos se guardan cifradas en `cache/fotos`: el selfie de registro hasta que se sube, los selfies descargados de los miembros de los grupos hasta el cierre de sesión y la foto de cada misión hasta que se consigue o se salta. Cada fichero (`selfie.cifrada`, `selfie_<usuario>.cifrada`, `mision_<id>.cifrada`) contiene el nonce de 12 bytes seguido del texto cifrado y su etiqueta.

### 2.2 Con qué clave y con qué condiciones

El cifrado es AES-256 en modo GCM, sin relleno y con etiqueta de autenticación de 128 bits. La clave la genera el Android Keystore con el alias `clave_datos` y los propósitos de cifrar y descifrar; la aplicación solo recibe una referencia y el material nunca sale del almacén. El nonce lo genera el almacén en cada cifrado, y se guarda junto al texto cifrado porque hace falta para descifrar y no es secreto.

La clave se crea con dos condiciones. `setUserAuthenticationRequired(true)`, sin ventana de validez, obliga a autenticar al usuario con biometría fuerte en cada operación. `setInvalidatedByBiometricEnrollment(true)` invalida la clave en cuanto se registra una huella nueva en el terminal. Las dos las aplica keystore2, no la aplicación: sin un token de autenticación asociado a la operación, el cifrado falla con `KEY_USER_NOT_AUTHENTICATED`.

Las fotos usan el mismo algoritmo con una segunda clave, `clave_fotos`, porque sus condiciones de uso son otras. Las fotos se leen continuamente mientras se juega (avatares, miniaturas y el envío al servidor), y pedir la huella en cada lectura haría la aplicación inutilizable. Una ventana de validez tras la huella tampoco sirve, ya que una partida dura horas. Esta clave no exige autenticación por uso, pero se crea con `setUnlockedDeviceRequired(true)` (Android 9 o superior): el almacén solo la usa con el terminal desbloqueado. En terminales sin bloqueo de pantalla la condición no restringe nada; en ellos la aplicación no pasa del acceso (apartado 2.4), de modo que la única foto que puede llegar a guardarse es el selfie de registro antes de subirlo.

### 2.3 Cómo se usa

Al iniciar sesión o registrarse, la sesión queda en memoria y la aplicación pide la huella para guardarla. Al abrir la aplicación con una sesión guardada, pide la huella para desbloquearla antes de mostrar nada. En los dos casos el cifrador se entrega a `BiometricPrompt` dentro de un `CryptoObject`, y solo se usa el que devuelve el diálogo tras una autenticación correcta. La pantalla de desbloqueo no es el control: si se modifica la aplicación para saltarla, no hay un cifrador habilitado con el que descifrar.

Las fotos no pasan nunca por el disco en claro. La cámara entrega la imagen en memoria; la aplicación la gira según la orientación del sensor, la reduce a 1280 píxeles de lado, la comprime como JPEG sin metadatos y la cifra antes de escribirla. Los selfies descargados se cifran al recibirlos. Para mostrar o subir una foto se descifra en memoria.

Mientras la sesión está bloqueada no se compone ninguna pantalla de la aplicación, tampoco las que abren los enlaces `aiquetepillo://mision/{id}` y `aiquetepillo://unirse/{codigo}`: el enlace se atiende después de desbloquear.

### 2.4 Casos de error

| Situación | Comportamiento |
|---|---|
| Se registra una huella nueva | La clave queda invalidada. Al desbloquear, el diálogo no llega a aparecer: se borran la sesión cifrada y la clave y se vuelve a la pantalla de acceso. El siguiente acceso crea una clave nueva |
| Se retira el bloqueo de pantalla | El sistema destruye la clave y el resultado es el mismo. Mientras el terminal no tenga bloqueo de pantalla y una huella, no se puede crear una clave nueva: la aplicación se niega a continuar después del acceso y pide configurarlos. Se ha preferido a funcionar con la sesión sin cifrar |
| Se cancela el diálogo | No se hace ninguna operación. La aplicación permanece en la pantalla de la puerta, sin mostrar datos, y permite reintentar |
| Hay una sesión en claro de una versión anterior | Se borra al arrancar y hay que volver a entrar |
| Una foto cifrada se ha modificado o su clave ya no existe | GCM rechaza la etiqueta y el fichero se borra. Un selfie se vuelve a descargar; la foto de una misión hay que repetirla |
| Hay fotos en claro (`.jpg`) de una versión anterior | Se borran al arrancar |

### 2.5 Registros

La línea que registraba la respuesta de acceso completa, con el token, solo indica ahora que el acceso ha sido correcto. El interceptor de OkHttp pasa del nivel `BODY` a `BASIC`: registra el método, la URL, el código de respuesta y el tamaño del cuerpo, sin cabeceras ni cuerpos. Con ello dejan de aparecer en logcat la contraseña, el token, la cabecera `Authorization`, los selfies y fotos en base64 y el contenido de misiones y grupos.

### 2.6 Qué queda fuera

| Dato | Estado | Motivo |
|---|---|---|
| Token descifrado | En la memoria del proceso mientras la sesión está abierta | Es necesario para hablar con el servidor. Quien tenga privilegios totales con la sesión abierta puede leerlo |
| Misiones y usuario propietario en Room | `databases/aiquetepillo.db`, en claro | Ver apartado 5 |
| Fotos descifradas | En la memoria del proceso mientras se muestran o se suben | Igual que el token |
| Fotos cifradas con el terminal desbloqueado | Quien ejecute código como la aplicación puede pedir al almacén que las descifre | Ver apartado 5 |
| Estructura de `sesion.xml` y de `cache/fotos` | Se ve que existe una sesión guardada, cuántas fotos hay, de qué misiones y de qué usuarios, y su tamaño aproximado | El cifrado protege el contenido, no los nombres ni la existencia de los ficheros |
| Tamaño de las respuestas en logcat | Visible con el nivel `BASIC` | Permite distinguir unas llamadas de otras, pero no su contenido |

## 3. Datos en tránsito

Se completa en P3.2. Hoy la aplicación habla con el backend por HTTP en claro (`usesCleartextTraffic="true"`), de modo que el token, la contraseña, los selfies y las fotos viajan legibles. El apartado recogerá la configuración de seguridad de red, el certificado del backend, el anclaje de certificados y cómo se renueva.

## 4. Superficie y endurecimiento

Se completa en P3.3. Partirá del inventario de superficie del documento de alcance: la actividad principal exportada, los enlaces `aiquetepillo://mision/{id}` y `aiquetepillo://unirse/{codigo}`, las actividades que exportan las bibliotecas de Compose en la variante de depuración, la copia de seguridad (`allowBackup="true"` con reglas vacías), el permiso de cámara y el nivel de registro, que todavía no depende del tipo de compilación. La ventana ya está protegida con `FLAG_SECURE`.

## 5. Riesgos asumidos

**Base de datos de misiones sin cifrar.** La tabla `misiones` guarda el título, la descripción y el propietario de la misión activa de cada grupo. Es una copia de datos que están en el servidor: perderla no cuesta nada, y cifrarla exigiría una biblioteca de cifrado transparente y una clave con ventana de validez para no pedir la huella en cada consulta. Se asume en esta versión, con el coste de que quien tenga los ficheros ve a quién tiene que fotografiar el jugador y en qué situación.

**Clave de las fotos sin autenticación por uso.** Ligar las fotos a la huella, como la sesión, exigiría una autenticación en cada lectura. Se ha optado por una clave que solo funciona con el terminal desbloqueado. Protege frente a quien copia los ficheros (con `run-as`, una copia de seguridad o un volcado del almacenamiento) y frente a quien tiene el terminal bloqueado. No protege frente a quien, con el terminal desbloqueado, ejecuta código con la identidad de la aplicación, como quien tiene privilegios totales.

**Memoria del proceso.** El token descifrado es necesario mientras se juega. Se asume que quien tiene privilegios totales sobre un terminal con la sesión abierta puede leerlo.
