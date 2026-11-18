#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Genera el material criptografico del backend de practicas.
#
#   ca.pem             autoridad de laboratorio. Es el ANCLA DE CONFIANZA:
#                      este es el fichero que va a la aplicacion, en
#                      app/src/debug/res/raw/cert_laboratorio.pem
#   cert.pem           certificado del servidor (hoja) seguido de la CA.
#                      Es el que sirve el backend.
#   clave.pem          clave privada del servidor.
#   cert_reserva.pem   segundo certificado, firmado por la MISMA CA.
#   clave_reserva.pem  su clave privada.
#
# La CA solo se crea la primera vez. Si ya existe, se reutiliza: asi el ancla
# de confianza no cambia aunque se vuelvan a emitir los certificados, que es
# exactamente como funciona en produccion.
#
# Las claves de los dos certificados tambien se reutilizan si ya existen: el
# guion vuelve a emitir los certificados (por ejemplo, con otra IP en EXTRA)
# sin cambiar las claves, y por tanto sin cambiar los resumenes que ancla la
# aplicacion. Para emitir una clave NUEVA se retira antes su fichero
# (clave.pem o clave_reserva.pem); es un paso del procedimiento de rotacion.
#
# Uso:   EXTRA=192.168.1.50 ./generar_certificado.sh
#        EXTRA es la IP de vuestro ordenador en la red local.
# ---------------------------------------------------------------------------
set -e

DIAS="${DIAS:-825}"
SAN="DNS:api.local,DNS:localhost,IP:127.0.0.1,IP:10.0.2.2"
if [ -n "${EXTRA:-}" ]; then
    SAN="$SAN,IP:$EXTRA"
fi

huella() {
    openssl x509 -in "$1" -pubkey -noout \
      | openssl pkey -pubin -outform der \
      | openssl dgst -sha256 -binary \
      | openssl enc -base64
}

# --- 1. La autoridad de laboratorio, solo si no existe ---------------------
if [ ! -f ca.pem ] || [ ! -f ca-clave.pem ]; then
    cat > .ca.cnf <<EOF
[req]
distinguished_name = dn
prompt = no
x509_extensions = v3_ca
[dn]
CN = Autoridad de laboratorio SEM
O  = Universidad de Malaga
[v3_ca]
basicConstraints = critical,CA:TRUE,pathlen:0
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash
EOF
    openssl req -x509 -newkey rsa:2048 -nodes \
        -keyout ca-clave.pem -out ca.pem -days 3650 -config .ca.cnf 2>/dev/null
    rm -f .ca.cnf
    CA_NUEVA=1
else
    CA_NUEVA=0
fi

# --- 2. Emitir una hoja firmada por la CA ----------------------------------
emitir_hoja() {   # $1 = fichero certificado  $2 = fichero clave  $3 = CN
    cat > .hoja.cnf <<EOF
[req]
distinguished_name = dn
prompt = no
[dn]
CN = $3
[v3_hoja]
basicConstraints = CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = $SAN
EOF
    if [ -f "$2" ]; then
        # Clave existente: se conserva, y con ella el resumen anclado
        openssl req -new -key "$2" -out .hoja.csr -config .hoja.cnf 2>/dev/null
    else
        openssl req -newkey rsa:2048 -nodes -keyout "$2" -out .hoja.csr -config .hoja.cnf 2>/dev/null
    fi
    openssl x509 -req -in .hoja.csr -CA ca.pem -CAkey ca-clave.pem -CAcreateserial \
        -out .hoja.pem -days "$DIAS" -extfile .hoja.cnf -extensions v3_hoja 2>/dev/null
    cat .hoja.pem ca.pem > "$1"
    rm -f .hoja.csr .hoja.cnf .hoja.pem
}

emitir_hoja cert.pem         clave.pem         "backend de practicas SEM"
emitir_hoja cert_reserva.pem clave_reserva.pem "backend de practicas SEM (recambio)"

# --- 3. Resumen por pantalla ----------------------------------------------
echo
if [ "$CA_NUEVA" = "1" ]; then
    echo "Autoridad de laboratorio CREADA: ca.pem"
else
    echo "Autoridad de laboratorio reutilizada: ca.pem (no se toca)"
fi
echo "Certificados emitidos y firmados por ella."
echo
echo "  Nombres validos: $SAN"
echo
echo "  Ancla de confianza, va a la aplicacion:"
echo "      ca.pem  ->  app/src/debug/res/raw/cert_laboratorio.pem"
echo
echo "  Lo que sirve el backend:"
echo "      cert.pem (hoja + CA)   clave.pem"
echo
echo "Resumenes SHA-256 de la clave publica, para el anclaje:"
echo
echo "  ACTUAL  (hoja en uso)      $(huella cert.pem)"
echo "  RESERVA (hoja de recambio) $(huella cert_reserva.pem)"
echo
