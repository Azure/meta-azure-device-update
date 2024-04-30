DESCRIPTION = "Opentelemetry"
LICENSE = "Apache2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=86d3f3a95c324c9479bd8986968f4327"

FILESEXTRAPATHS:prepend := "${THISDIR}/${PN}:"

SRC_URI = "git://github.com/open-telemetry/opentelemetry-cpp.git;protocol=https;branch=main"

SRCREV = "52309ddc65e74e7309c7224cacf3cfb04730b2be"
PV = "1.0+git${SRCPV}"

S = "${WORKDIR}/git"

DEPENDS = "gtest googlebenchmark"

inherit cmake

