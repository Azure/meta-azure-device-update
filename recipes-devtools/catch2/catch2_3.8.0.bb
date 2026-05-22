SUMMARY = "Catch2 - A modern, C++-native, header-only test framework for unit-tests, TDD and BDD"
DESCRIPTION = "Catch2 is a multi-paradigm test framework for C++. which also supports \
Objective-C (and maybe C). It is primarily distributed as a single header file, \
although certain extensions may require additional headers."
HOMEPAGE = "https://github.com/catchorg/Catch2"
LICENSE = "BSL-1.0"
LIC_FILES_CHKSUM = "file://LICENSE.txt;md5=e4224ccaecb14d942c71d31bef20d78c"

SRC_URI = "git://github.com/catchorg/Catch2.git;protocol=https;branch=devel"
SRCREV = "2b60af89e23d28eefc081bc930831ee9d45ea58b"

S = "${WORKDIR}/git"

inherit cmake

# Catch2 requires C++14 or later
CXXFLAGS:append = " -std=c++14"

# Configure CMake options
EXTRA_OECMAKE = " \
    -DBUILD_TESTING=OFF \
    -DCATCH_INSTALL_DOCS=OFF \
    -DCATCH_INSTALL_EXTRAS=ON \
"

# Disable automatic test discovery for cross-compilation
EXTRA_OECMAKE:append = " -DCATCH_BUILD_TESTING=OFF"

# For development/testing builds, you might want to enable examples
# EXTRA_OECMAKE += "-DCATCH_BUILD_EXAMPLES=ON"

FILES:${PN}-dev += "${libdir}/cmake/Catch2/*"
FILES:${PN}-dev += "${datadir}/Catch2/*"

BBCLASSEXTEND = "native nativesdk"

# This is a header-only library for the most part, but also provides
# some CMake integration files that need to be packaged