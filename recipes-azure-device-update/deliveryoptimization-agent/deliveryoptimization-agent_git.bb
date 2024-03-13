# Build and install Delivery Optimization Simple Client.

# Environment variables that can be used to configure the behaviour of this recipe.
# DO_GIT_BRANCH         The branch of the Delivery Optimization Simple Client repository to use.
#                       Note: This value is only used if DO_GIT_BRANCH is not set. 
# DO_SRC_URI            The URI of the Delivery Optimization Simple Client repository to use.
#                       Note: This vlaue is only used if DO_SRC_URI is not set
# DO_GIT_COMMIT         The commit of the Delivery Optimization Simple Client repository to use.
#                       Note: This value is only used if DO_GIT_COMMIT is not set.
# BUILD_TYPE            Changes the type of build produced by this recipe.
#                       Valid values are Debug, Release, RelWithDebInfo, and MinRelSize.
#                       These values are the same as the CMAKE_BUILD_TYPE variable.

LICENSE = "CLOSED"

DO_GIT_BRANCH ?= "develop"

DO_SRC_URI ?= "git://github.com/microsoft/do-client"
SRC_URI = "${DO_SRC_URI};protocol=https;branch=${DO_GIT_BRANCH}"

DO_GIT_COMMIT ?= "8362e36bb990914bb3f69a08f50f383612983002"
SRCREV = "${DO_GIT_COMMIT}"

PV = "1.0+git${SRCPV}"
S = "${WORKDIR}/git" 


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
