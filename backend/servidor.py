#!/usr/bin/env python3
"""
Backend común de la asignatura Seguridad en Entornos Móviles.

API REST mínima para las prácticas del Bloque II. Se sirve por HTTP sin cifrar
de forma deliberada: en la práctica P3.2 se comparará este tráfico con el de la
misma aplicación una vez aplicado TLS y anclaje de certificados.

No requiere ninguna dependencia externa: sólo la biblioteca estándar de Python.

    python3 servidor.py            # escucha en 0.0.0.0:8080
    PUERTO=9000 python3 servidor.py

ADVERTENCIA DOCENTE
Este servidor contiene debilidades intencionadas (contraseñas en claro, tokens
predecibles, sin caducidad, sin límite de intentos, mensajes de error que
distinguen usuario inexistente de contraseña incorrecta). Son material de
estudio para los Bloques III y IV. No debe usarse fuera del aula.
"""

import base64
import json
import os
import random
import re
import ssl
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PUERTO = int(os.environ.get("PUERTO", "8080"))
CERT = os.environ.get("CERT")          # ruta al certificado, opcional
CLAVE = os.environ.get("CLAVE")        # ruta a la clave privada, opcional

# --- Datos de ejemplo (en memoria) --------------------------------------
USUARIOS = {
    "ana":    {"clave": "Practica2024", "nombre": "Ana Ruiz",     "correo": "ana@uma.es",    "dni": "12345678Z", "rol": "usuario"},
    "luis":   {"clave": "Practica2024", "nombre": "Luis Moreno",  "correo": "luis@uma.es",   "dni": "87654321X", "rol": "usuario"},
    "admin":  {"clave": "Administrador", "nombre": "Administración", "correo": "admin@uma.es", "dni": "11111111H", "rol": "administrador"},
}

_lock = threading.Lock()
_siguiente_id = 4
# Misiones de ejemplo; los puntos dependen de la dificultad: 1 (fácil), 3 (media) o 5 (difícil)
MISIONES = {
    "ana": [
        {"id": 1, "titulo": "Pilla a Luis bebiendo agua", "descripcion": "Haz una foto a Luis con un vaso de agua en la mano.", "puntos": 1, "fecha": "2026-11-04"},
        {"id": 2, "titulo": "Pilla a Luis haciendo el pino", "descripcion": "Consigue una foto de Luis boca abajo, sin ayuda.", "puntos": 5, "fecha": "2026-11-05"},
    ],
    "luis": [
        {"id": 3, "titulo": "Pilla a Ana bailando", "descripcion": "Haz una foto a Ana bailando en medio de la habitación.", "puntos": 3, "fecha": "2026-11-05"},
    ],
    "admin": [],
}

# Grupos de juego. Los miembros se guardan por nombre de usuario.
GRUPOS = {
    1: {"id": 1, "nombre": "Casa rural", "contexto": "Fin de semana en una casa rural en la sierra.",
        "codigo": "K7P2QX", "creador": "ana", "miembros": ["ana", "luis"]},
}
_siguiente_grupo = 2

SESIONES = {}  # token -> usuario


def crear_token(usuario):
    """Token deliberadamente predecible: material de análisis para el Bloque IV."""
    crudo = "%s:%d" % (usuario, int(time.time()))
    return base64.b64encode(crudo.encode()).decode()


def crear_codigo():
    """Código de invitación de 6 caracteres. Generador no criptográfico, sin
    caducidad y sin límite de intentos al unirse: escenario del modelo de amenazas."""
    while True:
        codigo = "".join(random.choices("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", k=6))
        if all(g["codigo"] != codigo for g in GRUPOS.values()):
            return codigo


def grupo_publico(grupo):
    """El grupo tal como lo ve la aplicación: los miembros con su nombre."""
    return {
        "id": grupo["id"], "nombre": grupo["nombre"], "contexto": grupo["contexto"],
        "codigo": grupo["codigo"], "creador": grupo["creador"],
        "miembros": [{"usuario": u, "nombre": USUARIOS[u]["nombre"]} for u in grupo["miembros"]],
    }


def grupo_por_codigo(codigo):
    codigo = (codigo or "").strip().upper()
    return next((g for g in GRUPOS.values() if g["codigo"] == codigo), None)


class Manejador(BaseHTTPRequestHandler):
    server_version = "SEM-Practicas/1.0"

    # ---------- utilidades ----------
    def _responder(self, codigo, cuerpo):
        datos = json.dumps(cuerpo, ensure_ascii=False).encode("utf-8")
        self.send_response(codigo)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(datos)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(datos)

    def _cuerpo(self):
        largo = int(self.headers.get("Content-Length") or 0)
        if largo == 0:
            return {}
        try:
            return json.loads(self.rfile.read(largo).decode("utf-8"))
        except Exception:
            return {}

    def _usuario_autenticado(self):
        cabecera = self.headers.get("Authorization", "")
        if not cabecera.startswith("Bearer "):
            return None
        return SESIONES.get(cabecera[7:])

    def log_message(self, formato, *args):
        print("[%s] %s" % (self.log_date_time_string(), formato % args))

    # ---------- rutas ----------
    def do_GET(self):
        ruta = self.path.split("?")[0]

        if ruta == "/api/salud":
            return self._responder(200, {"estado": "ok", "servicio": "backend de prácticas"})

        if ruta == "/api/misiones":
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            return self._responder(200, {"misiones": MISIONES.get(usuario, [])})

        if ruta == "/api/perfil":
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            u = USUARIOS[usuario]
            return self._responder(200, {
                "usuario": usuario, "nombre": u["nombre"],
                "correo": u["correo"], "dni": u["dni"], "rol": u["rol"]
            })

        # Ruta administrativa sin comprobación de rol: hallazgo previsto para el Bloque IV
        if ruta == "/api/admin/usuarios":
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            return self._responder(200, {"usuarios": [
                {"usuario": k, "nombre": v["nombre"], "correo": v["correo"], "rol": v["rol"]}
                for k, v in USUARIOS.items()
            ]})

        if ruta == "/api/grupos":
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            return self._responder(200, {"grupos": [
                grupo_publico(g) for g in GRUPOS.values() if usuario in g["miembros"]
            ]})

        # Vista previa de una invitación, para la pantalla de confirmación.
        # Responde 404 a los códigos que no existen: sirve de oráculo para
        # probar códigos al azar (Bloque IV).
        m = re.match(r"^/api/invitaciones/([A-Za-z0-9]+)$", ruta)
        if m:
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            grupo = grupo_por_codigo(m.group(1))
            if grupo is None:
                return self._responder(404, {"error": "Código no válido"})
            return self._responder(200, grupo_publico(grupo))

        m = re.match(r"^/api/misiones/(\d+)$", ruta)
        if m:
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            idn = int(m.group(1))
            # No comprueba que la misión pertenezca al usuario: intencionado
            for lista in MISIONES.values():
                for e in lista:
                    if e["id"] == idn:
                        return self._responder(200, e)
            return self._responder(404, {"error": "No encontrado"})

        return self._responder(404, {"error": "Ruta no encontrada", "ruta": ruta})

    def do_POST(self):
        ruta = self.path.split("?")[0]
        cuerpo = self._cuerpo()

        if ruta == "/api/login":
            usuario = (cuerpo.get("usuario") or "").strip().lower()
            clave = cuerpo.get("clave") or ""
            if usuario not in USUARIOS:
                # Mensaje que confirma qué cuentas existen: intencionado
                return self._responder(404, {"error": "El usuario no existe"})
            if USUARIOS[usuario]["clave"] != clave:
                return self._responder(401, {"error": "Contraseña incorrecta"})
            token = crear_token(usuario)
            SESIONES[token] = usuario
            return self._responder(200, {
                "token": token,
                "usuario": usuario,
                "nombre": USUARIOS[usuario]["nombre"],
                "rol": USUARIOS[usuario]["rol"]
            })

        if ruta == "/api/misiones":
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            global _siguiente_id
            with _lock:
                nuevo = {
                    "id": _siguiente_id,
                    "titulo": cuerpo.get("titulo", "(sin título)"),
                    "descripcion": cuerpo.get("descripcion", ""),
                    "puntos": int(cuerpo.get("puntos", 1)),
                    "fecha": time.strftime("%Y-%m-%d")
                }
                _siguiente_id += 1
            MISIONES.setdefault(usuario, []).append(nuevo)
            return self._responder(201, nuevo)

        if ruta == "/api/grupos":
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            nombre = (cuerpo.get("nombre") or "").strip()
            if not nombre:
                return self._responder(400, {"error": "Falta el nombre del grupo"})
            global _siguiente_grupo
            with _lock:
                grupo = {
                    "id": _siguiente_grupo, "nombre": nombre,
                    "contexto": (cuerpo.get("contexto") or "").strip(),
                    "codigo": crear_codigo(), "creador": usuario, "miembros": [usuario],
                }
                GRUPOS[grupo["id"]] = grupo
                _siguiente_grupo += 1
            return self._responder(201, grupo_publico(grupo))

        if ruta == "/api/grupos/unirse":
            usuario = self._usuario_autenticado()
            if usuario is None:
                return self._responder(401, {"error": "Token ausente o no válido"})
            grupo = grupo_por_codigo(cuerpo.get("codigo"))
            if grupo is None:
                return self._responder(404, {"error": "Código no válido"})
            with _lock:
                if usuario not in grupo["miembros"]:
                    grupo["miembros"].append(usuario)
            return self._responder(200, grupo_publico(grupo))

        return self._responder(404, {"error": "Ruta no encontrada", "ruta": ruta})


if __name__ == "__main__":
    servidor = ThreadingHTTPServer(("0.0.0.0", PUERTO), Manejador)

    if CERT and CLAVE:
        contexto = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        contexto.load_cert_chain(certfile=CERT, keyfile=CLAVE)
        contexto.minimum_version = ssl.TLSVersion.TLSv1_2
        servidor.socket = contexto.wrap_socket(servidor.socket, server_side=True)
        esquema = "https"
    else:
        esquema = "http"

    print("Backend de prácticas escuchando en %s://0.0.0.0:%d" % (esquema, PUERTO))
    if esquema == "http":
        print("MODO SIN CIFRAR: es el punto de partida de la práctica P3.2.")
    else:
        print("MODO TLS: certificado %s" % CERT)
    print("Desde el emulador de Android, la dirección del anfitrión es 10.0.2.2")
    print("Usuarios de prueba: ana / luis / admin  ·  contraseña: Practica2024 (admin: Administrador)")
    servidor.serve_forever()
