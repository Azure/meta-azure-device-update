# Build and install the azure-iot-sdk-c with PnP support.

DESCRIPTION = "Microsoft Azure IoT SDKs and libraries for C"
AUTHOR = "Microsoft Corporation"
HOMEPAGE = "https://github.com/Azure/azure-iot-sdk-c"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=4283671594edec4c13aeb073c219237a"

# We pull from main branch in order to get PnP APIs
SRC_URI = "gitsm://github.com/Azure/azure-iot-sdk-c.git;protocol=https;branch=lts_07_2021"

SRCREV = "LTS_07_2021_Ref01"
PV = "1.0+git${SRCPV}"

S = "${WORKDIR}/git"

# util-linux for uuid-dev
DEPENDS = "util-linux curl openssl boost cpprest libproxy msft-gsl"

# depend on openssl 1.1.1 custom recipe
DEPENDS += "adu-iothub-c-sdk-openssl"

inherit cmake

# Do not use amqp since it is deprecated.
# Do not build sample code to save build time.
# use_http: required uhttp for eis_utils
EXTRA_OECMAKE += "-Duse_amqp:BOOL=OFF -Duse_http:BOOL=ON -Duse_mqtt:BOOL=ON -Duse_wsio:BOOL=ON -Ddont_use_uploadtoblob:BOOL=ON -Dskip_samples:BOOL=ON -Dbuild_service_client:BOOL=OFF -Dbuild_provisioning_service_client:BOOL=OFF -DOPENSSL_ROOT_DIR=${WORKDIR}/adu-iothub-c-sdk-openssl-1.1.1u"

sysroot_stage_all:append () {
    sysroot_stage_dir ${D}${exec_prefix}/cmake ${SYSROOT_DESTDIR}${exec_prefix}/cmake
}

#Placeholder file so do_rootfs / libdnf do not complain when packages-split/${PN} is empty
do_install:append(){
	install -d ${D}
	echo "This package is linked against during compilation" > ${D}/azure-iot-sdk-c-placeholder-file
	echo "Therefore, there is nothing to install on the target" >> ${D}/azure-iot-sdk-c-placeholder-file
}

FILES:${PN} += " \
	/azure-iot-sdk-c-placeholder-file \
"

FILES:${PN}-dev += "${exec_prefix}/cmake"

BBCLASSEXTEND = "native nativesdk"
