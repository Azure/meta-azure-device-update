# Build and install the azure-blob-storage-file-upload-utility

DESCRIPTION = "Microsoft Azure SD for CPP"
AUTHOR = "Microsoft Corporation"
HOMEPAGE = "https://github.com/Azure/azure-sdk-for-cpp"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=e74f78882cab57fd1cc4c5482b9a214a"

FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI = "git://github.com/Azure/azure-sdk-for-cpp.git;protocol=https;branch=main"

SRCREV = "f757bb06e71adb829edcaf2867abc4e87c5aa23f"
SRC_URI += "file://0001-Fixup-compiler-warning.patch"
PV = "1.0+git${SRCPV}"

S = "${WORKDIR}/git"

# util-linux for uuid-dev
# libxml2 for libxml2-dev
DEPENDS = "util-linux curl openssl libxml2 opentelemetry-cpp"

inherit cmake

sysroot_stage_all:append () {
    sysroot_stage_dir ${D}${exec_prefix}/cmake ${SYSROOT_DESTDIR}${exec_prefix}/cmake
}

FILES:${PN}-dev += "${exec_prefix}/cmake"

FILES:${PN} = "/usr/share/azure-storage-blobs-cpp \
                /usr/share/azure-storage-queues-cpp \
                /usr/share/azure-storage-common-cpp \
                /usr/share/azure-storage-files-shares-cpp \
                /usr/share/azure-security-keyvault-secrets-cpp \
                /usr/share/azure-security-keyvault-certificates-cpp \
                /usr/share/azure-security-keyvault-keys-cpp \
		        /usr/share/azure-security-attestation-cpp \
                /usr/share/azure-identity-cpp \
                /usr/share/azure-template-cpp \
                /usr/share/azure-core-cpp \
                /usr/share/azure-storage-files-datalake-cpp \
                /usr/share/azure-core-tracing-opentelemetry-cpp \
                /usr/share/azure-security-keyvault-administration-cpp \
                /usr/share/azure-core-tracing-opentelemetry-cpp/azure-core-tracing-opentelemetry-cppConfigVersion.cmake \
                /usr/share/azure-core-tracing-opentelemetry-cpp/azure-core-tracing-opentelemetry-cppTargets-noconfig.cmake \
                /usr/share/azure-core-tracing-opentelemetry-cpp/azure-core-tracing-opentelemetry-cppConfig.cmake \
                /usr/share/azure-core-tracing-opentelemetry-cpp/azure-core-tracing-opentelemetry-cppTargets.cmake \
                /usr/share/azure-core-tracing-opentelemetry-cpp/copyright \
                /usr/share/azure-security-keyvault-administration-cpp/azure-security-keyvault-administration-cppConfigVersion.cmake \
                /usr/share/azure-security-keyvault-administration-cpp/azure-security-keyvault-administration-cppTargets-noconfig.cmake \
                /usr/share/azure-security-keyvault-administration-cpp/azure-security-keyvault-administration-cppConfig.cmake \
                /usr/share/azure-security-keyvault-administration-cpp/azure-security-keyvault-administration-cppTargets.cmake \
                /usr/share/azure-security-keyvault-administration-cpp/copyright \
                "

BBCLASSEXTEND = "native nativesdk"
