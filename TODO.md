# Práctica 2.1

## Requisitos:
> Plazo del Bloque II completo (código + memoria): **viernes 6 de noviembre de 2026, 00:00** en el Campus.

### Proyecto Fiesta:
Juego de fiesta: cada jugador recibe misiones generadas por IA del tipo «pilla a Ignacio bebiendo agua», adaptadas al contexto del grupo (casa rural, clase…). Se completan con una foto que una IA valida comparándola con el selfie de registro del objetivo. Los puntos van a una clasificación.

Dato más sensible: las fotos de personas (selfie de registro = dato biométrico, y fotos de amigos en situaciones comprometidas), que se suben al servidor y se envían a un proveedor de IA externo.

**Cuenta**
- Registro (usuario + contraseña) e inicio de sesión; la sesión se mantiene entre aperturas
- En el registro, selfie hecho en el momento con la cámara de la app (no desde la galería)

**Grupos**
- Crear grupo con nombre y contexto en texto libre («nos hemos ido de casa rural»); el creador se une automáticamente
- Cada grupo tiene un código de invitación
- Unirse escribiendo el código o abriendo un enlace profundo `aiquetepillo://unirse/{codigo}` (compartido con el selector de Android). El enlace lleva a una pantalla de confirmación, nunca une sin aceptar
- Ver miembros con su selfie
- Botón «Empezar partida» (solo el creador)

**Misiones**
- Al empezar, el backend llama a OpenRouter con contexto + miembros y genera misiones (objetivo = otro miembro, nunca uno mismo)
- Dificultad asignada por la IA: fácil = 1 punto, media = 3, difícil = 5
- 1 misión activa por jugador; al completarla o saltarla, se genera otra
- Saltar una misión resta 1 punto
- Pantallas de listado y detalle de misión (cubre el «listado + detalle» de P2.3)

**Verificación**
- Foto de la misión con la cámara de la app
- El backend envía al modelo de visión: descripción de la misión + selfie del objetivo + foto nueva → `{ valida, motivo }`
- Si es válida, suma puntos y genera otra misión; si no, muestra el motivo

**Clasificación**
- Ranking del grupo por puntos, se refresca al abrir o con un botón

**Opcional**
- Proximidad: comprobar que cazador y objetivo están cerca antes de verificar (encaja con el permiso «proximidad» del dominio).
- Historial de misiones completadas con sus fotos

**Pantallas:** login/registro (con selfie) · mis grupos (crear / unirse con código) · confirmación de unión (destino del enlace) · detalle del grupo · mis misiones · detalle de misión · cámara · clasificación

**Decisiones técnicas**
- Partir del proyecto base de P2.2/P2.3 (Compose, Retrofit, Room, sesión local) y adaptar el modelo: `Elemento` → `Mision`, más `Grupo` y `Miembro`
- Backend: ampliar `servidor.py` de la asignatura con grupos, misiones, fotos y clasificación (varios móviles tienen que compartir estado). Todos los dispositivos apuntan al mismo portátil (`10.0.2.2:8080` en emulador, IP del portátil en móvil físico)
- La clave de OpenRouter solo en el backend (variable de entorno), nunca en el APK ni en el repo: con jadx saldría directamente y sería un hallazgo del Bloque IV
- Cámara con CameraX dentro de la app, no con el intent `ACTION_IMAGE_CAPTURE`: con el intent la foto la hace la app de cámara del sistema y no se necesita el permiso `CAMERA`, así que nos quedaríamos sin permiso peligroso. CameraX además garantiza foto en directo
- Sin modelo propio de reconocimiento facial: la comparación la hace el modelo de visión
- Un solo modelo con visión en OpenRouter para generar y verificar
- Código de invitación de 6 caracteres alfanuméricos. Buen escenario para el modelo de amenazas

### Obligatorios:
- Sistema de Autenticación de Usuarios → registro/login contra el backend, token de sesión
- Almacenamiento Local de Datos Sensibles → token (SharedPreferences), caché de grupo y misiones (Room), selfie
- Llamada a API Remota → app ↔ backend propio (grupos, misiones, verificación); el backend llama a OpenRouter
- Permiso Peligroso Justificado → `CAMERA`, para el selfie de registro y las fotos de misión hechas en directo


## Convención del Equipo:
- Nombre de Paquete: aiquetepillo (Android exige al menos un punto en el applicationId: propuesta `es.uma.aiquetepillo`)
- minSdk y por qué: propuesta API 26, la del proyecto base
- targetSdk y por qué: propuesta el último estable, con una frase que lo justifique (aplica las restricciones de seguridad más recientes)
- Ramas y versión: propuesta `main` protegida + una rama por funcionalidad con PR
- Responsables: uno por cada requisito obligatorio

## Reconocimiento de una Aplicación Real (Que sea sencilla)
- Mirar Permisos declarados [aapt2 dump permissions]
- Componentes exportados [apktool + lectura del manifiesto]
- Esquemas de firma [apksigner verify --print-certs]
- minSdk y targetSdk [aapt2 dump badging]
- Número de piezas instaladas [adb shell pm path]

- Pide la aplicación algún permiso que no sabrías justificar a partir de lo que hace?
- Cuántos componentes tiene exportados?

## Objetivos de la 2.1
### Contenido del Repo:
- Proyecto Android que compila y se ejecuta, acordado como base común del equipo
- Un README:
    - Nombre del Equipo
    - Miembros
    - Dominio Asignado
    - Descripción de la aplicación en dos o tres frases
    - Línea por cada uno de los cuatro requisitos técnicos mínimos
- Convención del Equipo
- Tabla de Reconocimiento de una Aplicación Real
- Un .gitignore correcto y ningún fichero sensible en el histórico (ni keystores, ni contraseñas, ni la clave de OpenRouter)

## Después (P2.2 y P2.3, misma entrega del viernes)
- App completa: acceso, listado, detalle, persistencia, red y permiso peligroso justificado
- Repositorio etiquetado (tag) en el estado entregado
- Memoria / documento de alcance (1–2 páginas):
    1. La aplicación: dominio, para qué sirve, quién la usaría
    2. Inventario de datos sensibles (dato · dónde se guarda · quién puede leerlo · cómo se protegerá): token, contraseña, selfie, fotos de misiones, contexto del grupo, clave de OpenRouter (servidor)
    3. Superficie expuesta: activity exportada, enlace profundo `aiquetepillo://unirse/{codigo}`, API, almacenamiento local, logcat, recientes, cámara
    4. Modelo de amenazas preliminar (3–4 escenarios): unirse a un grupo adivinando el código, filtración de las fotos, foto trucada para engañar a la IA, robo de la clave de la API
    5. Reparto de los cuatro requisitos obligatorios

## Orden de trabajo
1. Convenciones, README y tabla de reconocimiento (P2.1)
2. Backend: usuarios + grupos + código de invitación
3. App: login/registro + selfie con CameraX + grupos + unirse por código y por enlace
4. Backend: generación de misiones y verificación con OpenRouter
5. App: misiones (listado + detalle), foto, saltar, clasificación
6. Memoria, tag y entrega
