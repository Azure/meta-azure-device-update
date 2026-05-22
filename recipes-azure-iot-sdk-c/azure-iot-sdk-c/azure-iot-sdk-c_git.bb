# Build and install the azure-iot-sdk-c with PnP support.

DESCRIPTION = "Microsoft Azure IoT SDKs and libraries for C"
AUTHOR = "Microsoft Corporation"
HOMEPAGE = "https://github.com/Azure/azure-iot-sdk-c"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=4283671594edec4c13aeb073c219237a"

# We pull from main branch in order to get PnP APIs
SRC_URI = "gitsm://github.com/Azure/azure-iot-sdk-c.git;protocol=https;branch=main"

SRCREV = "021212f84f3014709f11314a829e22bc537c11e2"
PV = "1.0+git${SRCPV}"

S = "${WORKDIR}/git"

# Local source support for development
# Enable: Set USE_LOCAL_AZIOT_SDK_C_SOURCE=1 and ensure it's passed through to BitBake:
#   BB_ENV_PASSTHROUGH_ADDITIONS="USE_LOCAL_AZIOT_SDK_C_SOURCE AZIOT_SDK_C_LOCAL_SOURCE_DIR"
# Override default path: export AZIOT_SDK_C_LOCAL_SOURCE_DIR=/custom/path
# See layer README.md for usage examples
AZIOT_SDK_C_LOCAL_SOURCE_DIR ?= "${TOPDIR}/../../../sources/azure-iot-sdk-c"

python __anonymous() {
    import os
    
    use_local = d.getVar('USE_LOCAL_AZIOT_SDK_C_SOURCE')
    local_src = d.getVar('AZIOT_SDK_C_LOCAL_SOURCE_DIR')
    
    if use_local == "1":
        if local_src and os.path.exists(local_src):
            bb.warn("=" * 60)
            bb.warn("Using LOCAL Azure IoT SDK C source from: %s" % local_src)
            bb.warn("GitHub fetch: DISABLED")
            bb.warn("Patches: NOT APPLIED (apply manually if needed)")
            bb.warn("=" * 60)
            
            # Set EXTERNALSRC to use local directory
            d.setVar('EXTERNALSRC', local_src)
            d.setVar('EXTERNALSRC_BUILD', local_src + '/build-yocto')
            
            # Disable fetch and unpack tasks (source already available)
            d.setVarFlag('do_fetch', 'noexec', '1')
            d.setVarFlag('do_unpack', 'noexec', '1')
            d.setVarFlag('do_patch', 'noexec', '1')
            
            # Mark as externally provided
            d.setVar('EXTERNALSRC_SYMLINKS', '')
        else:
            bb.fatal("USE_LOCAL_AZIOT_SDK_C_SOURCE=1 but directory not found: %s" % local_src)
}

# util-linux for uuid-dev
DEPENDS = "util-linux curl openssl boost cpprest libproxy msft-gsl"

inherit cmake

# Do not use amqp since it is deprecated.
# Do not build sample code to save build time.
# use_http: required uhttp for eis_utils
EXTRA_OECMAKE += "-Duse_amqp:BOOL=OFF -Duse_http:BOOL=ON -Duse_mqtt:BOOL=ON -Ddont_use_uploadtoblob:BOOL=ON -Dskip_samples:BOOL=ON -Dbuild_service_client:BOOL=OFF -Dbuild_provisioning_service_client:BOOL=OFF -Duse_prov_client:BOOL=OFF"

sysroot_stage_all:append () {
    sysroot_stage_dir ${D}${exec_prefix}/cmake ${SYSROOT_DESTDIR}${exec_prefix}/cmake
}

#Placeholder file so do_rootfs / libdnf do not complain when packages-split/${PN} is empty
do_install:append(){
	install -d ${D}
	echo "This package is linked against during compilation" > ${D}/azure-iot-sdk-c-placeholder-file
	echo "Therefore, there is nothing to install on the target" >> ${D}/azure-iot-sdk-c-placeholder-file
	
	# Fix hardcoded TMPDIR paths in CMake target files
	# Replace absolute paths to libraries with just the library names
	# This prevents buildpaths QA warnings and makes the package relocatable
	for cmakefile in $(find ${D} -name "*Targets*.cmake" -o -name "*Config.cmake"); do
		sed -i \
			-e 's#${TMPDIR}[^;)]*recipe-sysroot/usr/lib/\([^.]*\.so[^;)]*\)#\1#g' \
			-e 's#${STAGING_DIR_TARGET}/usr/lib/\([^.]*\.so[^;)]*\)#\1#g' \
			"$cmakefile"
	done
}

FILES:${PN} += " \
	/azure-iot-sdk-c-placeholder-file \
"

FILES:${PN}-dev += "${exec_prefix}/cmake"

BBCLASSEXTEND = "native nativesdk"
