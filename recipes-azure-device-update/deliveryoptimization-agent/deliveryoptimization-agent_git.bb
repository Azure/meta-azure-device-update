# Build and install Delivery Optimization Simple Client.

# Environment variables that can be used to configure the behaviour of this recipe.
# BUILD_TYPE            Changes the type of build produced by this recipe.
#                       Valid values are Debug, Release, RelWithDebInfo, and MinRelSize.
#                       These values are the same as the CMAKE_BUILD_TYPE variable.

LICENSE = "CLOSED"

DO_GIT_BRANCH ?= "develop"

DO_SRC_URI ?= "git://github.com/microsoft/do-client"
SRC_URI = "${DO_SRC_URI};protocol=https;branch=${DO_GIT_BRANCH}"
DO_GIT_COMMIT ?= "b61de2d347c8032562056b18f90ec710e531baf8"
SRCREV = "${DO_GIT_COMMIT}"

PV = "1.0+git${SRCPV}"
S = "${WORKDIR}/git" 

SRC_URI += "file://Findlibproxy.cmake.patch"
SRC_URI += "file://Findglib-2.0.cmake.patch"
SRC_URI += "file://0001-Fix-incomplete-type-std-array-in-do_date_time.h.patch"
SRC_URI += "file://0001-add-std-array-include-in-download-cpp.patch"
SRC_URI += "file://0001-fix-array-incl-in-http_agent-cpp.patch"

DEPENDS = "boost curl libproxy msft-gsl glib-2.0"

inherit cmake

BUILD_TYPE ?= "Debug"
EXTRA_OECMAKE += "--trace-expand"
EXTRA_OECMAKE += "-DCMAKE_BUILD_TYPE=${BUILD_TYPE}"
# Don't build DO tests.
EXTRA_OECMAKE += "-DDO_BUILD_TESTS=OFF"
# Specify build is for deliveryoptimization-agent
EXTRA_OECMAKE += "-DDO_INCLUDE_AGENT=ON"

EXTRA_OECMAKE += "-DCMAKE_PREFIX_PATH=${WORKDIR}/recipe-sysroot/usr/"
# EXTRA_OECMAKE += "-DCMAKE_INCLUDE_PATH=${WORKDIR}/recipe-sysroot/usr/include/"
# EXTRA_OECMAKE += "-DCMAKE_LIBRARY_PATH=${WORKDIR}/recipe-sysroot/usr/lib"
# EXTRA_OECMAKE += "-DCMAKE_LIBRARY_PATH=${WORKDIR}/recipe-sysroot/usr/lib"
# cpprest installs its config.cmake file in a non-standard location.
# Tell cmake where to find it.
EXTRA_OECMAKE += "-Dcpprestsdk_DIR=${WORKDIR}/recipe-sysroot/usr/lib/cmake"
BBCLASSEXTEND = "native nativesdk"
