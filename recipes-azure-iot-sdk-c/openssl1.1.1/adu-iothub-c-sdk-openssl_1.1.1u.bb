SUMMARY = "Secure Socket Layer"
DESCRIPTION = "Secure Socket Layer (SSL) binary and related cryptographic tools."
HOMEPAGE = "http://www.openssl.org/"
BUGTRACKER = "http://www.openssl.org/news/vulnerabilities.html"
SECTION = "libs/network"

# "openssl | SSLeay" dual license
LICENSE = "openssl"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d343e62fc9c833710bbbed25f27364c8"

#DEPENDS = "hostperl-runtime-native"
#DEPENDS_append_class-target = " openssl-native"

SRC_URI = "http://www.openssl.org/source/openssl-${PV}.tar.gz;name=srctarball"

SRC_URI[srctarball.md5sum] = "72f7ba7395f0f0652783ba1089aa0dcc"
SRC_URI[srctarball.sha256sum] = "e2f8d84b523eecd06c7be7626830370300fbcc15386bf5142d72758f6963ebc6"

S = "${WORKDIR}/openssl-${PV}"

inherit autotools

EXTRA_OECONF += "no-ssl3 shared zlib --prefix=${DEPLOY_DIR}/adu-iothub-c-sdk-openssl-1.1.1u --openssldir=${DEPLOY_DIR}/adu-iothub-c-sdk-openssl-1.1.1u"

