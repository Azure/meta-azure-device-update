FILESEXTRAPATHS:prepend := "${THISDIR}:"

# Enable static library build
EXTRA_OECMAKE += "-DWITH_STATIC_LIBRARIES=ON"

# Install static libraries
do_install:append() {
    install -d ${D}${libdir}
    # Update paths to use the correct static library names
    install -m 0644 ${B}/lib/libmosquitto_static.a ${D}${libdir}/libmosquitto.a
    install -m 0644 ${B}/lib/cpp/libmosquittopp_static.a ${D}${libdir}/libmosquittopp.a
}

# Add static lib archives to the development package
FILES:${PN}-dev += " \
    ${libdir}/libmosquitto.a \
    ${libdir}/libmosquittopp.a \
"
