#!/bin/sh
# Genera un certificado autofirmado para el backend de prácticas.
#
#   ./generar_certificado.sh
#
# Produce dos ficheros en el directorio actual:
#   cert.pem   certificado del servidor
#   clave.pem  clave privada
#
# El certificado incluye como nombres alternativos la dirección que usa el
# emulador para llegar al anfitrión (10.0.2.2), el nombre api.local y
# localhost. Si trabajas con un dispositivo físico, añade la IP de tu
# ordenador a la lista de subjectAltName.

set -e

DIAS=${DIAS:-365}
NOMBRE=${NOMBRE:-api.local}
EXTRA=${EXTRA:-}

SAN="DNS:${NOMBRE},DNS:localhost,IP:127.0.0.1,IP:10.0.2.2"
if [ -n "$EXTRA" ]; then
    SAN="${SAN},IP:${EXTRA}"
fi

openssl req -x509 -newkey rsa:2048 -nodes -days "$DIAS" \
    -keyout clave.pem -out cert.pem \
    -subj "/C=ES/O=Practicas SEM/CN=${NOMBRE}" \
    -addext "subjectAltName=${SAN}" \
    -addext "basicConstraints=critical,CA:FALSE" \
    -addext "keyUsage=critical,digitalSignature,keyEncipherment" \
    -addext "extendedKeyUsage=serverAuth" 2>/dev/null

echo "Certificado generado."
echo "  Nombres válidos: ${SAN}"
echo
echo "Resumen de la clave pública, para el anclaje de certificados:"
openssl x509 -in cert.pem -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
