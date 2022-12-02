# Build and install Delivery Optimization Simple Client.

# Environment variables that can be used to configure the behaviour of this recipe.
# BUILD_TYPE            Changes the type of build produced by this recipe.
#                       Valid values are Debug, Release, RelWithDebInfo, and MinRelSize.
#                       These values are the same as the CMAKE_BUILD_TYPE variable.

LICENSE = "CLOSED"

# Option 1 - Bind to a specific branch and commit
#
# e.g.
#
# SRC_URI = "gitsm://github.com/microsoft/do-client;branch=main"
# SRCREV = "ef70c5c8a59d46820c648f434249516957966e7f"
# PV = "1.0+git${SRCPV}"
# S = "${WORKDIR}/git" 

# Option 2 - Bind to branch and commit specified in a build env 'DO_SRC_URI'
#            This supports both git or a local file.
DO_SRC_URI ?= "gitsm://github.com/microsoft/do-client;branch=main"
SRC_URI = "${DO_SRC_URI}"
# This code handles setting variables for either git or for a local file.
# This is only while we are using private repos, once our repos are public,
# we will just use git.
python () {
    src_uri = d.getVar('DO_SRC_URI')
    if src_uri.startswith('git'):
        d.setVar('SRCREV', d.getVar('AUTOREV'))
        d.setVar('PV', '1.0+git' + d.getVar('SRCPV'))
        d.setVar('S', d.getVar('WORKDIR') + "/git")
    elif src_uri.startswith('file'):
        d.setVar('S',  d.getVar('WORKDIR') + "/do-client")
}

DEPENDS = "boost curl libproxy msft-gsl"

inherit cmake

BUILD_TYPE ?= "Debug"
EXTRA_OECMAKE += "-DCMAKE_BUILD_TYPE=${BUILD_TYPE}"
# Don't build DO tests.
EXTRA_OECMAKE += "-DDO_BUILD_TESTS=OFF"
# Specify build is for deliveryoptimization-agent
EXTRA_OECMAKE += "-DDO_INCLUDE_AGENT=ON"

# cpprest installs its config.cmake file in a non-standard location.
# Tell cmake where to find it.
EXTRA_OECMAKE += "-Dcpprestsdk_DIR=${WORKDIR}/recipe-sysroot/usr/lib/cmake"
BBCLASSEXTEND = "native nativesdk"
