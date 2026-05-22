# Recipe: adu-filesystem-layout
# Purpose: Minimal /adu partition structure setup (generic, platform-independent)
# Scope: ONLY manages /adu/* directories - does NOT touch /var/lib/adu, /var/log/adu, /etc/adu
#        Persistence strategy (symlinks vs overlayfs) handled by separate recipes

SUMMARY = "ADU Filesystem Layout - /adu partition structure"
DESCRIPTION = "Creates and manages directory structure on /adu partition. \
               Does not implement persistence strategy - that's handled by \
               adu-persistence-symlinks or adu-persistent-overlay recipes."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://setup-adu-filesystem.sh \
    file://adu-filesystem-layout.service \
"

S = "${WORKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "adu-filesystem-layout.service"
SYSTEMD_AUTO_ENABLE = "enable"

# Runtime dependencies
RDEPENDS:${PN} = "azure-device-update bash"

do_install() {
    # Install setup script
    install -d ${D}${sbindir}
    install -m 0755 ${WORKDIR}/setup-adu-filesystem.sh ${D}${sbindir}/
    
    # Install systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/adu-filesystem-layout.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${sbindir}/setup-adu-filesystem.sh \
    ${systemd_system_unitdir}/adu-filesystem-layout.service \
"

# This recipe provides the base filesystem layout
PROVIDES = "adu-filesystem-base"
