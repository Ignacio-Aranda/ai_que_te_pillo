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
| Aplicación maliciosa | Puede invocar los componentes y enlaces exportados. Los ficheros privados ya los protege el aislamiento entre aplicaciones del sistema, no el cifrado | Apartado 4 |
| Atacante de red | Las partidas se juegan en wifis compartidas | Apartado 3 |
| Usuario con privilegios totales | Un jugador tramposo puede modificar la aplicación o leer lo que guarda. En la variante depurable basta adb con `run-as` para ejecutar órdenes con la identidad de la aplicación | Apartados 2 y 4; la autorización, en el servidor |
| Analista con el artefacto | Basta el APK para buscar secretos y estudiar la API | Apartados 3 y 4 |
| Cadena de suministro | Las bibliotecas de AndroidX, OkHttp y el SDK del proveedor de IA | Apartado 5 |

Los escenarios de partida son los cuatro del documento de alcance: quien accede a un grupo sin invitación, quien tiene el móvil en la mano o comparte la wifi, quien busca las fotos para difundirlas y el jugador tramposo.

## 2. Datos en reposo

### 2.1 Qué se cifra

La sesión del usuario (token, nombre de usuario y nombre visible) se guarda cifrada en `shared_prefs/sesion.xml`, en un único campo `datos`. El valor contiene el nonce y el texto cifrado en base64, separados por dos puntos. Los tres valores van en un solo paquete porque cada operación con la clave exige una autenticación: tres campos exigirían tres autenticaciones.

Las fotos se guardan cifradas en `cache/fotos`: el selfie de registro hasta que se sube, los selfies descargados de los miembros de los grupos hasta el cierre de sesión y la foto de cada misión hasta que se consigue o se salta. Cada fichero (`selfie.cifrada`, `selfie_<usuario>.cifrada`, `mision_<id>.cifrada`) contiene el nonce de 12 bytes seguido del texto cifrado y su etiqueta. El nombre del fichero se incluye como dato asociado de GCM: no se cifra, pero queda cubierto por la etiqueta, de modo que una foto no puede sustituirse por otra renombrando ficheros.

### 2.2 Con qué clave y con qué condiciones

El cifrado es AES-256 en modo GCM, sin relleno y con etiqueta de autenticación de 128 bits. La clave la genera el Android Keystore con el alias `clave_datos` y los propósitos de cifrar y descifrar; la aplicación solo recibe una referencia y el material nunca sale del almacén. El nonce lo genera el almacén en cada cifrado, y se guarda junto al texto cifrado porque hace falta para descifrar y no es secreto.

La clave se crea con dos condiciones. `setUserAuthenticationRequired(true)`, sin ventana de validez, obliga a autenticar al usuario con biometría fuerte en cada operación. `setInvalidatedByBiometricEnrollment(true)` invalida la clave en cuanto se registra una huella nueva en el terminal. Las dos las aplica keystore2, no la aplicación: sin un token de autenticación asociado a la operación, el cifrado falla con `KEY_USER_NOT_AUTHENTICATED`.

Las fotos usan el mismo algoritmo con una segunda clave, `clave_fotos`, porque sus condiciones de uso son otras. Las fotos se leen continuamente mientras se juega (avatares, miniaturas y el envío al servidor), y pedir la huella en cada lectura haría la aplicación inutilizable. Una ventana de validez tras la huella tampoco sirve, ya que una partida dura horas. Esta clave no exige autenticación por uso, pero se crea con `setUnlockedDeviceRequired(true)` (Android 9 o superior): el almacén solo la usa con el terminal desbloqueado. En terminales sin bloqueo de pantalla la condición no restringe nada; en ellos la aplicación no pasa del acceso (apartado 2.4), de modo que la única foto que puede llegar a guardarse es el selfie de registro antes de subirlo. En Android 8 (API 26 y 27), que la aplicación admite, la condición no existe y la clave de las fotos se crea sin ninguna restricción de uso.

Ninguna de las dos claves exige StrongBox. Que residan en un entorno de ejecución seguro depende del terminal, y esta versión no lo comprueba.

### 2.3 Cómo se usa

Al iniciar sesión o registrarse, la sesión queda en memoria y la aplicación pide la huella para guardarla. Al abrir la aplicación con una sesión guardada, pide la huella para desbloquearla antes de mostrar nada. En los dos casos el cifrador se entrega a `BiometricPrompt` dentro de un `CryptoObject`, y solo se usa el que devuelve el diálogo tras una autenticación correcta. La pantalla de desbloqueo no es el control: si se modifica la aplicación para saltarla, no hay un cifrador habilitado con el que descifrar.

Las fotos no pasan nunca por el disco en claro. La cámara entrega la imagen en memoria; la aplicación la gira según la orientación del sensor, la reduce a 1280 píxeles de lado, la comprime como JPEG sin metadatos y la cifra antes de escribirla. Los selfies descargados se cifran al recibirlos. Para mostrar o subir una foto se descifra en memoria, fuera del hilo principal.

Mientras la sesión está bloqueada no se compone ninguna pantalla de la aplicación, tampoco las que abren los enlaces `aiquetepillo://mision/{id}` y `aiquetepillo://unirse/{codigo}`: el enlace se atiende después de desbloquear.

### 2.4 Casos de error

| Situación | Comportamiento |
|---|---|
| Se registra una huella nueva | La clave queda invalidada. Al desbloquear, el diálogo no llega a aparecer: se borran la sesión cifrada y la clave y se vuelve a la pantalla de acceso. Se borran también la copia local de las misiones y todas las fotos, porque eran del usuario anterior y quien entre después puede ser otra persona. El siguiente acceso crea una clave nueva |
| Se retira el bloqueo de pantalla | El sistema destruye la clave y el resultado es el mismo. Mientras el terminal no tenga bloqueo de pantalla y una huella, no se puede crear una clave nueva: la aplicación se niega a continuar después del acceso y pide configurarlos. Se ha preferido este comportamiento a funcionar con la sesión sin cifrar |
| Falla la apertura por otro motivo | La sesión no se descarta, porque descartarla borraría también las misiones y las fotos: se avisa y se permite reintentar. Solo se descarta si la clave ha sido invalidada o ya no existe |
| Se cancela el diálogo al desbloquear | No se hace ninguna operación. La aplicación permanece en la pantalla de la puerta, sin mostrar datos, y permite reintentar |
| Se cancela el diálogo al proteger una sesión recién iniciada | La sesión no se escribe en disco: existe solo en memoria y la aplicación permanece en la pantalla «Proteger tu sesión», sin mostrar contenido, hasta que se confirma con la huella. Si se cierra la aplicación, la sesión se pierde y hay que volver a entrar. Se ha decidido no permitir el juego con una sesión que no ha quedado protegida |
| El cifrado falla pese a una autenticación correcta | Se borra la clave, la sesión sigue pendiente de guardar y se avisa. El siguiente intento crea una clave nueva |
| Hay una sesión en claro de una versión anterior | Se borra al arrancar y hay que volver a entrar. Las filas de la tabla `misiones` de ese usuario permanecen hasta su siguiente cierre de sesión |
| Una foto cifrada se ha modificado, se ha renombrado o su clave ya no existe | GCM rechaza la etiqueta y el fichero se borra; si faltaba la clave, el almacén ha creado otra con la que la etiqueta no coincide. Un selfie se vuelve a descargar; la foto de una misión hay que repetirla. Cualquier otro fallo de lectura, como un rechazo del almacén porque el terminal se ha bloqueado, deja el fichero intacto |
| Hay fotos en claro (`.jpg`) de una versión anterior | Se borran al arrancar |

### 2.5 Registros

La línea que registraba la respuesta de acceso completa, con el token, solo indica ahora que el acceso ha sido correcto. El interceptor de registro de OkHttp en nivel `BODY` se sustituye por uno propio que escribe el verbo HTTP, el nombre de la operación de la API y el código de respuesta, por ejemplo `GET grupos -> 200`. El nivel `BASIC` del interceptor estándar no es suficiente, porque imprime la URL completa y en la ruta viajan el código de invitación de un grupo (`api/invitaciones/{codigo}`) y los nombres de usuario de los miembros (`api/usuarios/{usuario}/selfie`).

Con ello dejan de aparecer en logcat la contraseña, el token, la cabecera `Authorization`, los selfies y fotos en base64, el contenido de misiones y grupos, los códigos de invitación y los nombres de usuario. Los fallos de las operaciones criptográficas, de la descarga de selfies y del guardado de fotos registran el tipo y el mensaje de la excepción, que no contienen datos del usuario.

### 2.6 Qué queda fuera

| Dato | Estado | Motivo |
|---|---|---|
| Token descifrado | En la memoria del proceso mientras la sesión está abierta, en la sesión y en el resultado del acceso que conserva el modelo de vista | Es necesario para hablar con el servidor. Quien tenga privilegios totales con la sesión abierta puede leerlo. El servidor no tiene una operación de cierre de sesión, así que el token sigue siendo válido después de salir |
| Token reconstruible | El token es el base64 de `usuario:segundo de emisión`. El usuario está en claro en la columna `propietario` de Room, y el momento de emisión se aproxima por la fecha de modificación de `sesion.xml` | Quien tenga los ficheros puede reconstruirlo sin la clave probando unos pocos valores. Ver apartado 5 |
| Misiones y usuario propietario en Room | `databases/aiquetepillo.db`, en claro | Ver apartado 5 |
| Fotos descifradas | En la memoria del proceso mientras se muestran o se suben | Igual que el token |
| Fotos cifradas con el terminal desbloqueado | Quien ejecute código como la aplicación puede pedir al almacén que las descifre. En la variante depurable, basta adb con `run-as` | Ver apartado 5 |
| Fotos en Android 8 | La clave de las fotos no tiene ninguna condición de uso | La condición de terminal desbloqueado no existe antes de Android 9 |
| Reversión de `sesion.xml` | Una copia anterior del fichero, del mismo terminal, se descifra sin error | GCM detecta la modificación, no la reversión. Solo devuelve una sesión que el usuario ya tuvo |
| Copia de seguridad | `allowBackup="true"` con reglas vacías: la copia incluye la base de datos en claro y `sesion.xml` cifrado. La caché, donde están las fotos, no se copia. La clave no sale del almacén, así que `sesion.xml` no se puede descifrar al restaurarlo en otro terminal | Se trata en P3.3 |
| Estructura de `sesion.xml` y de `cache/fotos` | Se ve que existe una sesión guardada, cuántas fotos hay, de qué misiones y de qué usuarios, y su tamaño aproximado | El cifrado protege el contenido, no los nombres ni la existencia de los ficheros |
| Registros de red | Verbo, operación y código de cada llamada | Permiten saber qué hace el usuario y cuándo, pero no con qué datos. El registro no depende todavía del tipo de compilación (P3.3) |

### 2.7 Antes y después de P3.1

| Dato | Antes de P3.1 | Después de P3.1 | Qué sigue sin proteger |
|---|---|---|---|
| Token de sesión | En claro en `sesion.xml` y completo en logcat | Cifrado con AES-256-GCM y una clave ligada a la huella; fuera de logcat | La memoria del proceso; se puede reconstruir a partir del usuario y la hora; viaja por HTTP |
| Usuario y nombre visible | En claro en `sesion.xml` | Cifrados junto al token | El usuario sigue en claro en la columna `propietario` de Room |
| Misiones | En claro en Room y su contenido en logcat | En claro en Room; fuera de logcat | Todo su contenido para quien obtenga los ficheros o la copia de seguridad |
| Selfies y fotos de las misiones | JPEG en claro en `cache/fotos` y en base64 en logcat | Cifrados con AES-256-GCM y una clave que exige el terminal desbloqueado; fuera de logcat | Quien ejecute código como la aplicación con el terminal desbloqueado; Android 8; el tránsito por HTTP |
| Contraseña | No se guarda; en logcat, en el cuerpo del acceso | Fuera de logcat | Viaja en claro por HTTP |
| Códigos de invitación y usuarios en las URL | En logcat | Fuera de logcat | Viajan en claro por HTTP |

## 3. Datos en tránsito

Se completa en P3.2. Hoy la aplicación habla con el backend por HTTP en claro (`usesCleartextTraffic="true"`), de modo que el token, la contraseña, los selfies y las fotos viajan legibles. El apartado recogerá la configuración de seguridad de red, el certificado del backend, el anclaje de certificados y cómo se renueva.

## 4. Superficie y endurecimiento

Se completa en P3.3. Partirá del inventario de superficie del documento de alcance: la actividad principal exportada, los enlaces `aiquetepillo://mision/{id}` y `aiquetepillo://unirse/{codigo}`, las actividades que exportan las bibliotecas de Compose en la variante de depuración, la copia de seguridad (`allowBackup="true"` con reglas vacías), el permiso de cámara y el nivel de registro, que todavía no depende del tipo de compilación. La ventana ya está protegida con `FLAG_SECURE`.

## 5. Riesgos asumidos

**Base de datos de misiones sin cifrar.** La tabla `misiones` guarda las misiones que el servidor devuelve al usuario, con su título, descripción, puntos, fecha y propietario. Las filas se reemplazan al recargar y no se eliminan hasta el cierre de sesión o el descarte de una sesión irrecuperable. Es una copia de datos que están en el servidor: su pérdida no tiene coste, y cifrarla exigiría una biblioteca de cifrado transparente y una clave con ventana de validez para no pedir la huella en cada consulta. Se asume en esta versión, con el coste de que quien tenga los ficheros, o la copia de seguridad, ve a quién tiene que fotografiar el jugador y en qué situación.

**Clave de las fotos sin autenticación por uso.** Ligar las fotos a la huella, como la sesión, exigiría una autenticación en cada lectura. Se ha optado por una clave que solo funciona con el terminal desbloqueado. Protege frente a quien copia los ficheros (con `run-as` o con un volcado del almacenamiento; la caché no entra en la copia de seguridad) y frente a quien tiene el terminal bloqueado. No protege frente a quien, con el terminal desbloqueado, ejecuta código con la identidad de la aplicación: quien tiene privilegios totales y, en la variante depurable, cualquiera con adb y `run-as`. En Android 8 tampoco protege frente al terminal bloqueado.

**Memoria del proceso.** El token descifrado es necesario mientras se juega. Se asume que quien tiene privilegios totales sobre un terminal con la sesión abierta puede leerlo.

**Token predecible.** El servidor emite como token el base64 de `usuario:segundo de emisión`. Cifrar `sesion.xml` no impide que quien tenga los ficheros lo reconstruya, porque el usuario está en claro en Room y el momento de emisión se acota con la fecha de modificación del fichero. La corrección corresponde al servidor, que debe emitir tokens aleatorios y permitir revocarlos, y se aborda con la autorización en el Bloque IV.

**Copia de seguridad.** Hasta que P3.3 restrinja la copia de seguridad, la base de datos en claro forma parte de ella.
