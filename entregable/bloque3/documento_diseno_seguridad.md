# Documento de diseño de seguridad

**AI que te pillo** · Grupo L · Seguridad en Entornos Móviles, curso 2026/27 · Bloque III
Iniciado en P3.1 (10 de noviembre de 2026) · apartado 3 completado en P3.2 (18 de noviembre de 2026)

Este documento describe cómo protege la aplicación los datos que maneja y qué ha decidido no proteger. Parte del [documento de alcance del Bloque II](../v1/documento_alcance.md) y se completa a lo largo del Bloque III: el apartado 2 corresponde a P3.1, el 3 a P3.2 y el 4 a P3.3.

## 1. La aplicación y su modelo de amenazas

*AI que te pillo* es un juego para grupos de amigos. Cada jugador recibe misiones generadas por un modelo de IA, del tipo «pilla a Luis bebiendo agua», y las completa con una foto que el mismo modelo compara con el selfie que el objetivo se hizo al registrarse. La aplicación habla con un backend propio por HTTPS; el backend guarda usuarios, grupos y misiones y es el único que conoce la clave del proveedor de IA.

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
| Falla la apertura por otro motivo | Solo se descarta la sesión ante un fallo definitivo: clave invalidada o inexistente, paquete guardado con base64 no válido o etiqueta de GCM que no coincide tras la autenticación. Ante cualquier otro fallo no se descarta, porque descartarla borraría también las misiones y las fotos: se avisa y se permite reintentar. La pantalla de desbloqueo ofrece además «Cerrar sesión», que descarta la sesión guardada y los datos locales, para que un fallo que se repita, como un paquete con otra forma de malformación, no deje al usuario sin salida |
| Se cancela el diálogo al desbloquear | No se hace ninguna operación. La aplicación permanece en la pantalla de la puerta, sin mostrar datos, y permite reintentar |
| Se cancela el diálogo al proteger una sesión recién iniciada | La sesión no se escribe en disco: existe solo en memoria y la aplicación permanece en la pantalla «Proteger tu sesión», sin mostrar contenido, hasta que se confirma con la huella. Si se cierra la aplicación, la sesión se pierde y hay que volver a entrar. Se ha decidido no permitir el juego con una sesión que no ha quedado protegida |
| El cifrado falla pese a una autenticación correcta | Se borra la clave, la sesión sigue pendiente de guardar y se avisa. El siguiente intento crea una clave nueva |
| Hay una sesión en claro de una versión anterior | Se borra al arrancar y hay que volver a entrar. Las filas de la tabla `misiones` de ese usuario permanecen hasta su siguiente cierre de sesión |
| Una foto cifrada se ha modificado, se ha renombrado o su clave ya no existe | GCM rechaza la etiqueta y el fichero se borra; si faltaba la clave, el almacén ha creado otra con la que la etiqueta no coincide. Un selfie se vuelve a descargar; la foto de una misión hay que repetirla. Cualquier otro fallo de lectura, como un rechazo del almacén porque el terminal se ha bloqueado, deja el fichero intacto |
| Hay fotos en claro (`.jpg`) de una versión anterior | Se borran al arrancar |

### 2.5 Registros

La línea que registraba la respuesta de acceso completa, con el token, solo indica ahora que el acceso ha sido correcto. El interceptor de registro de OkHttp en nivel `BODY` se sustituye por uno propio que escribe el verbo HTTP, el nombre de la operación de la API y el código de respuesta, por ejemplo `GET grupos -> 200`. El nivel `BASIC` del interceptor estándar no es suficiente, porque imprime la URL completa y en la ruta viajan el código de invitación de un grupo (`api/invitaciones/{codigo}`) y los nombres de usuario de los miembros (`api/usuarios/{usuario}/selfie`).

Con ello dejan de aparecer en logcat la contraseña, el token, la cabecera `Authorization`, los selfies y fotos en base64, el contenido de misiones y grupos, los códigos de invitación y los nombres de usuario. Los fallos de las operaciones criptográficas, de la descarga de selfies, del guardado de fotos y, desde P3.2, de las llamadas de red registran el tipo y el mensaje de la excepción, que no contienen datos del usuario.

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
| Registros de red | Verbo, operación y código de cada llamada, o el tipo y el mensaje de la excepción si falla | Permiten saber qué hace el usuario y cuándo, pero no con qué datos. El registro no depende todavía del tipo de compilación (P3.3) |

### 2.7 Antes y después de P3.1

La tabla refleja el estado al terminar P3.1. Lo relativo al tránsito por HTTP se cierra en P3.2 (apartado 3).

| Dato | Antes de P3.1 | Después de P3.1 | Qué sigue sin proteger |
|---|---|---|---|
| Token de sesión | En claro en `sesion.xml` y completo en logcat | Cifrado con AES-256-GCM y una clave ligada a la huella; fuera de logcat | La memoria del proceso; se puede reconstruir a partir del usuario y la hora; viaja por HTTP |
| Usuario y nombre visible | En claro en `sesion.xml` | Cifrados junto al token | El usuario sigue en claro en la columna `propietario` de Room |
| Misiones | En claro en Room y su contenido en logcat | En claro en Room; fuera de logcat | Todo su contenido para quien obtenga los ficheros o la copia de seguridad |
| Selfies y fotos de las misiones | JPEG en claro en `cache/fotos` y en base64 en logcat | Cifrados con AES-256-GCM y una clave que exige el terminal desbloqueado; fuera de logcat | Quien ejecute código como la aplicación con el terminal desbloqueado; Android 8; el tránsito por HTTP |
| Contraseña | No se guarda; en logcat, en el cuerpo del acceso | Fuera de logcat | Viaja en claro por HTTP |
| Códigos de invitación y usuarios en las URL | En logcat | Fuera de logcat | Viajan en claro por HTTP |

## 3. Datos en tránsito

### 3.1 Política de red declarada

La política de red está declarada en `res/xml/network_security_config.xml` y referenciada desde el manifiesto con `android:networkSecurityConfig`. El atributo `usesCleartextTraffic` se ha retirado: cuando hay una configuración declarada, el sistema lo ignora, y mantenerlo dejaría un control aparente que no controla nada. La política la aplica la plataforma, antes de abrir la conexión, a todo el tráfico que pasa por su pila de red, también el de las bibliotecas que la usan, como OkHttp. Una biblioteca que abriera sus propios sockets o validara los certificados con su propio código quedaría fuera; la aplicación no incluye ninguna que lo haga.

| Elemento | Variante de publicación | Variante de depuración |
|---|---|---|
| Tráfico en claro | Prohibido para cualquier destino (`cleartextTrafficPermitted="false"`) | Igual |
| Anclas de confianza | Solo las autoridades del sistema | Las del sistema, la autoridad del laboratorio y el almacén del usuario, en el bloque `debug-overrides` |
| Anclaje del backend (`10.0.2.2`) | Resumen SHA-256 de la clave pública del certificado en uso y del de reserva, con caducidad el 30 de junio de 2027 | El mismo, que se comprueba también para las cadenas firmadas por la autoridad del laboratorio. Las que terminan en el almacén del usuario quedan exentas |

Las excepciones de desarrollo están separadas en dos niveles. El sistema solo aplica el bloque `debug-overrides` cuando la aplicación es depurable. Además, la configuración que contiene ese bloque y el certificado de la autoridad del laboratorio están en el conjunto de fuentes `src/debug`, que sustituye al fichero de `src/main` solo en las compilaciones de depuración: la variante de publicación no lleva ni el bloque ni el certificado. Se ha comprobado sobre los dos APK con `aapt2 dump`. El coste es que la parte común de la configuración está escrita dos veces y cada cambio debe llevarse a los dos ficheros, como indican sus comentarios.

Las dos excepciones de depuración se comportan de forma distinta frente al anclaje. Las anclas de `debug-overrides` desactivan por omisión el anclaje para las cadenas que terminan en ellas. Para la autoridad del laboratorio se anula ese efecto (`overridePins="false"`), de modo que un cambio de clave en el backend que no se haya llevado al anclaje se detecta durante el desarrollo y no después de publicar. El almacén del usuario conserva el comportamiento por omisión, para que quien desarrolla pueda inspeccionar su propio tráfico con un proxy de intercepción.

El backend sirve por TLS 1.2 o superior en el puerto 8443. El guion `backend/generar_certificado.sh` crea una autoridad de laboratorio y dos certificados de servidor firmados por ella, el que se sirve y el de reserva, válidos para `10.0.2.2`, `127.0.0.1`, `localhost` y `api.local`. Las claves privadas no están en el repositorio y se distribuyen entre los miembros del grupo por fuera de él, igual que la clave del proveedor de IA. Para la auditoría del Bloque IV se entregan con el artefacto, junto al backend, porque sin ellas no se puede arrancar un servidor que la aplicación acepte. El guion conserva las claves existentes y solo vuelve a emitir los certificados, de modo que puede ejecutarse de nuevo (por ejemplo, para añadir la IP de un dispositivo físico) sin cambiar los resúmenes anclados. Una clave nueva solo se crea retirando antes su fichero, como parte del procedimiento de rotación.

Los fallos de conexión se registran en logcat con el tipo y el mensaje de la excepción, por ejemplo `POST login -> SSLHandshakeException: Pin verification failed`. La pantalla muestra en todos los casos el mismo mensaje genérico, de modo que el registro es la única forma de distinguir un rechazo del anclaje de un fallo de red.

### 3.2 Comprobación

Se ha comprobado en el emulador, con mitmproxy como proxy de intercepción y su autoridad instalada en el almacén del usuario. El proyecto no define una firma para la variante de publicación; para instalarla en la prueba se firmó con la clave de depuración del SDK mediante `apksigner`.

| Variante y situación | Resultado |
|---|---|
| Depuración, sin proxy | Acceso, grupos y misiones funcionan por HTTPS |
| Depuración, con proxy | El proxy lee el tráfico: es la excepción de depuración del almacén del usuario |
| Depuración, backend con el certificado de reserva | Funciona: la clave de reserva está anclada |
| Depuración, backend con otro certificado firmado por la autoridad del laboratorio | `Pin verification failed`: el anclaje se sigue aplicando a la autoridad del laboratorio |
| Publicación, con proxy | `Trust anchor for certification path not found`: la aplicación rechaza el certificado del proxy y este no recibe ninguna petición |
| Publicación, sin proxy | El mismo error, porque la variante de publicación no confía en la autoridad del laboratorio. Solo puede conectar con un backend cuyo certificado emita una autoridad del sistema y cuya clave esté anclada |

En la variante de publicación, lo que detiene al proxy es que su autoridad no está entre las anclas. El anclaje es la segunda barrera, frente a un certificado que sí emita una autoridad de confianza, y su efecto es el de la cuarta fila.

### 3.3 Decisión sobre el anclaje en producción

El grupo aplicaría anclaje de certificados en la versión de producción. Por el canal viajan contraseñas, el token de sesión y, sobre todo, fotografías del rostro de los jugadores, que el servidor usa para identificarlos en las misiones, y fotos que pueden mostrar a alguien en una situación comprometida. El certificado del servidor lo administraría el responsable del backend y procedería de Let's Encrypt, cuyos certificados tienen una vida corta y se renuevan automáticamente. Anclar el certificado o una autoridad intermedia obligaría a publicar una versión en cada renovación o cada vez que la autoridad cambie de intermedia, así que se anclaría la clave pública del servidor, configurando la renovación para que la conserve (Let's Encrypt genera una clave nueva en cada renovación si no se indica lo contrario), junto con una clave de reserva generada y guardada fuera del servidor. Publicar una versión de emergencia supone días de revisión en la tienda, y una parte de los jugadores abre la aplicación solo cuando hay partida, a veces con semanas de diferencia. Por eso el anclaje lleva una caducidad de entre tres y seis meses desde la publicación de cada versión, que la siguiente amplía: si la rotación falla, quien no haya actualizado recupera el servicio, sin anclaje, como mucho seis meses después de instalar su versión. La fecha del laboratorio, el 30 de junio de 2027, es más lejana porque debe cubrir el curso y la auditoría.

### 3.4 Procedimiento de rotación

| | |
|---|---|
| Dominio anclado | `10.0.2.2` en el laboratorio; en producción, el dominio del servidor |
| Caducidad del anclaje | 30 de junio de 2027, para cubrir el curso y la auditoría del Bloque IV |
| Responsable del certificado | El responsable del backend (José Ignacio Aranda, según el reparto de trabajo) |
| Responsable de la publicación | Quien publique cada versión de la aplicación |

1. **Antelación.** La versión publicada ancla la clave en uso (A) y la de reserva (B). Pasar de A a B no exige publicar nada: en el laboratorio basta con arrancar el backend con `CERT=cert_reserva.pem CLAVE=clave_reserva.pem`. Por eso B permite reaccionar en minutos si A se ve comprometida, y en ese caso de emergencia el cambio se hace sin esperar. En una rotación planificada, el cambio deja a las versiones instaladas sin reserva, así que se prepara antes una nueva: el día T−45 se emite un nuevo par C, firmado por la misma autoridad sin tocar A ni B (basta con ejecutar el guion en una carpeta fuera del repositorio que solo contenga `ca.pem` y `ca-clave.pem`, y tomar uno de los dos certificados que emite), y se publica una versión que ancla A, B y C y amplía la caducidad. Se cuentan 7 días para la revisión de la tienda, 30 para que la nueva versión alcance al 95 % de los usuarios activos y 8 de margen. El servidor pasa a B en T, cuando se alcanza ese umbral: el umbral no protege el paso a B, que todas las versiones aceptan, sino la reserva que queda después.
2. **Quién avisa a quién.** En T−45 el responsable del backend abre una incidencia en el repositorio del grupo con el resumen de la clave C, y el responsable de la publicación la cierra indicando el número de versión que la incluye. En T−1 el responsable del backend anuncia el cambio en la misma incidencia, y el de la publicación confirma en ella que se ha alcanzado el umbral. Sin esa confirmación no se cambia la clave.
3. **Usuarios que no actualizan.** En el paso a B no pierden el servicio, porque su versión ya ancla B. Lo que pierden es la reserva: si B tuviera que cambiar antes de que actualicen, el siguiente cambio les haría fallar con `Pin verification failed`, y solo verían el mensaje genérico de conexión. Serían alrededor del 5 % de los usuarios activos en T, según la estimación anterior. Recuperan el servicio al actualizar o cuando caduque su anclaje.
4. **Detección.** El backend tendría que registrar por hora los accesos correctos y los saludos TLS que el cliente aborta; hoy no registra lo segundo. Si en la hora siguiente a T los accesos caen más de un 20 % respecto a la misma hora de la semana anterior, o los saludos abortados superan el 1 % de las conexiones, el despliegue se da por erróneo (por ejemplo, un certificado emitido con una clave no anclada) y se vuelve a la clave anterior, que se conserva durante 7 días, en menos de 15 minutos.
5. **Caducidad sin intervención.** Una vez alcanzada la fecha declarada, el sistema deja de aplicar el anclaje y la conexión se valida solo contra las anclas de confianza: las del sistema en la variante de publicación, y además la autoridad del laboratorio y el almacén del usuario en la de depuración. La aplicación no deja de funcionar, pero pierde el anclaje sin que nadie lo advierta.

### 3.5 Lo que el canal no protege

| Qué | Por qué |
|---|---|
| Frente a quien controla el dispositivo | Puede descompilar la aplicación, retirar el anclaje, añadir su propia autoridad y volver a firmarla, o instrumentar el proceso para que acepte cualquier certificado. También puede leer los datos en memoria antes de cifrarlos. El anclaje solo encarece ese análisis |
| La variante de depuración | Confía en el almacén del usuario, de modo que quien instale una autoridad en el terminal lee todo el tráfico. Es la variante que se instala con `adb` y la que se entrega para la auditoría del Bloque IV (apartado 5) |
| La autorización del servidor | El canal cifrado llega a un servidor que acepta un token predecible (base64 de `usuario:segundo de emisión`), sin caducidad ni revocación; que no limita los intentos de acceso ni los de los códigos de invitación; y que entrega la misión de otro usuario a quien pida su id. Nada de eso cambia con TLS: se aborda en el Bloque IV |
| Los datos en el servidor | Contraseñas, selfies y grupos están en claro en la memoria del backend. TLS protege el trayecto, no el destino |
| El tramo hacia el proveedor de IA | El backend reenvía las fotos y los selfies a OpenRouter por su propia conexión TLS, fuera de esta configuración. Es un tercero que recibe datos biométricos |
| Los metadatos | Quien observa la red ve la dirección y el puerto del backend, la versión de TLS, los instantes y el tamaño de cada conexión. El envío de una foto, de cientos de kilobytes, se distingue del resto de llamadas. Con un nombre de dominio se añadirían la consulta DNS y el nombre del servidor (SNI) |
| El certificado del laboratorio | La autoridad que firma el certificado del backend es del laboratorio, de modo que la variante de publicación no puede conectar con él. En producción harían falta un dominio, un certificado de una autoridad pública y el anclaje de sus claves |

## 4. Superficie y endurecimiento

Se completa en P3.3. Partirá del inventario de superficie del documento de alcance: la actividad principal exportada, los enlaces `aiquetepillo://mision/{id}` y `aiquetepillo://unirse/{codigo}`, las actividades que exportan las bibliotecas de Compose en la variante de depuración, la copia de seguridad (`allowBackup="true"` con reglas vacías), el permiso de cámara y el nivel de registro, que todavía no depende del tipo de compilación. La ventana ya está protegida con `FLAG_SECURE`.

## 5. Riesgos asumidos

**Base de datos de misiones sin cifrar.** La tabla `misiones` guarda las misiones que el servidor devuelve al usuario, con su título, descripción, puntos, fecha y propietario. Las filas se reemplazan al recargar y no se eliminan hasta el cierre de sesión o el descarte de una sesión irrecuperable. Es una copia de datos que están en el servidor: su pérdida no tiene coste, y cifrarla exigiría una biblioteca de cifrado transparente y una clave con ventana de validez para no pedir la huella en cada consulta. Se asume en esta versión, con el coste de que quien tenga los ficheros, o la copia de seguridad, ve a quién tiene que fotografiar el jugador y en qué situación.

**Clave de las fotos sin autenticación por uso.** Ligar las fotos a la huella, como la sesión, exigiría una autenticación en cada lectura. Se ha optado por una clave que solo funciona con el terminal desbloqueado. Protege frente a quien copia los ficheros (con `run-as` o con un volcado del almacenamiento; la caché no entra en la copia de seguridad) y frente a quien tiene el terminal bloqueado. No protege frente a quien, con el terminal desbloqueado, ejecuta código con la identidad de la aplicación: quien tiene privilegios totales y, en la variante depurable, cualquiera con adb y `run-as`. En Android 8 tampoco protege frente al terminal bloqueado.

**Memoria del proceso.** El token descifrado es necesario mientras se juega. Se asume que quien tiene privilegios totales sobre un terminal con la sesión abierta puede leerlo.

**Token predecible.** El servidor emite como token el base64 de `usuario:segundo de emisión`. Cifrar `sesion.xml` no impide que quien tenga los ficheros lo reconstruya, porque el usuario está en claro en Room y el momento de emisión se acota con la fecha de modificación del fichero. La corrección corresponde al servidor, que debe emitir tokens aleatorios y permitir revocarlos, y se aborda con la autorización en el Bloque IV.

**Copia de seguridad.** Hasta que P3.3 restrinja la copia de seguridad, la base de datos en claro forma parte de ella.

**Variante de depuración interceptable.** La variante de depuración confía en el almacén del usuario para poder inspeccionar el tráfico durante el desarrollo, y es la que se instala con `adb` y la que se audita en el Bloque IV. Quien la tenga y controle el terminal puede leer su tráfico instalando una autoridad propia, sin modificar la aplicación. Se asume porque la variante de publicación no incluye esa excepción. Con el backend del laboratorio, sobre la variante de publicación solo puede observarse el rechazo del proxy, porque esa variante tampoco reconoce la autoridad del laboratorio (apartado 3.2).

**Certificado del laboratorio.** El backend presenta un certificado de una autoridad de laboratorio que solo la variante de depuración reconoce, y el anclaje se refiere a claves de ese laboratorio. La variante de publicación es correcta para un servidor real, pero no conecta con el del laboratorio. Se asume mientras el backend no tenga dominio ni certificado de una autoridad pública.
