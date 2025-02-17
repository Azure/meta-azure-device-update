# Generates and copies/installs the public key .pem file
# used to validate the signatures of images.
# Note: ADU reference images are signed with test keys.

LICENSE="CLOSED"

# Path in the image to place the generated public key file.
ADUC_KEY_DIR = "/adukey"

DEPENDS = "openssl-native"

# Generated RSA key with password using command:
# openssl genrsa -aes256 -passout file:priv.pass -out priv.pem

# These variables can be overriden via whitelisted environment variables:
# ADUC_PUBLIC_KEY is the build host path to the .pem public key file to use for verifying the image.
# ADUC_PRIVATE_KEY is the build host path to the .pem private key file to use to sign the image.
# ADUC_PRIVATE_KEY_PASSWORD is the build host path to the .pass password file for the private key.
# You can either specify the public key file using ADUC_PUBLIC_KEY
# or alternatively let the public key be extracted from the private key given using ADUC_PRIVATE_KEY*.

do_configure() {
    if [ -z "${ADUC_PUBLIC_KEY}" ]; then
        if [ -z "${ADUC_PRIVATE_KEY}" ]; then
            bbfatal "Neither 'ADUC_PUBLIC_KEY' nor 'ADUC_PRIVATE_KEY' environment variable is set or it is not included in BB_ENV_PASSTHROUGH_ADDITIONS."
        fi

        if [ -z "${ADUC_PRIVATE_KEY_PASSWORD}" ]; then
            bbfatal "The environment variable 'ADUC_PRIVATE_KEY_PASSWORD' is not set or not included in BB_ENV_PASSTHROUGH_ADDITIONS."
        fi
    fi
}

# Either take existing public key file or extract it from private key file using openssl, private key and password file.
do_compile() {
    if [ -z ${ADUC_PUBLIC_KEY} ]
    then
        openssl rsa -in ${ADUC_PRIVATE_KEY} -passin file:${ADUC_PRIVATE_KEY_PASSWORD} -out public.pem -outform PEM -pubout
    else
        cp ${ADUC_PUBLIC_KEY} public.pem
    fi
}

# Install the public key file to ADUC_KEY_DIR
do_install() {
    install -d ${D}${ADUC_KEY_DIR}
    install -m 0444 public.pem ${D}${ADUC_KEY_DIR}/public.pem
}

FILES:${PN} += "${ADUC_KEY_DIR}/public.pem"

inherit allarch
