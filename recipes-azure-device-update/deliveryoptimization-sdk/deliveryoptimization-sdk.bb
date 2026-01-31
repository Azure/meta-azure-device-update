# Build and install the DO Client CPP SDK.

# Environment variables that can be used to configure the behaviour of this recipe.
# BUILD_TYPE            Changes the type of build produced by this recipe.
#                       Valid values are Debug, Release, RelWithDebInfo, and MinRelSize.
#                       These values are the same as the CMAKE_BUILD_TYPE variable.

LICENSE = "CLOSED"

DO_GIT_BRANCH ?= "main"

DO_SRC_URI ?= "git://github.com/microsoft/do-client"
SRC_URI = "${DO_SRC_URI};protocol=https;branch=${DO_GIT_BRANCH}"
DO_GIT_COMMIT ?= "b61de2d347c8032562056b18f90ec710e531baf8"
SRCREV = "${DO_GIT_COMMIT}"

PV = "1.0+git${SRCPV}"
S = "${WORKDIR}/git"

# Local source support for development
# Enable: Set USE_LOCAL_DO_SOURCE=1 and ensure it's passed through to BitBake:
#   BB_ENV_PASSTHROUGH_ADDITIONS="USE_LOCAL_DO_SOURCE DO_LOCAL_SOURCE_DIR"
# Override default path: export DO_LOCAL_SOURCE_DIR=/custom/path
# See layer README.md for usage examples
DO_LOCAL_SOURCE_DIR ?= "${TOPDIR}/../../../sources/do-client"

python __anonymous() {
    import os
    
    use_local = d.getVar('USE_LOCAL_DO_SOURCE')
    local_src = d.getVar('DO_LOCAL_SOURCE_DIR')
    
    if use_local == "1":
        if local_src and os.path.exists(local_src):
            bb.warn("=" * 60)
            bb.warn("Using LOCAL DO source from: %s" % local_src)
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
            bb.fatal("USE_LOCAL_DO_SOURCE=1 but directory not found: %s" % local_src)
} 



SRC_URI += "file://do_fstream_patch_for_static_function.patch"

DEPENDS = "boost curl libproxy msft-gsl"

inherit cmake

BUILD_TYPE ?= "Debug"
EXTRA_OECMAKE += "-DCMAKE_BUILD_TYPE=${BUILD_TYPE}"
# Specify build is for the sdk lib
EXTRA_OECMAKE += "-DDO_INCLUDE_SDK=ON"
# Don't build DO tests.
EXTRA_OECMAKE += "-DDO_BUILD_TESTS=OFF"

# cpprest installs its config.cmake file in a non-standard location.
# Tell cmake where to find it.
EXTRA_OECMAKE += "-Dcpprestsdk_DIR=${WORKDIR}/recipe-sysroot/usr/lib/cmake"

BBCLASSEXTEND = "native nativesdk"
