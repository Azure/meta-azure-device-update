# Recipe: adu-persistence-symlinks
# Purpose: Simple symlink-based persistence strategy for ADU directories
# Strategy: Create symlinks /var/lib/adu → /adu/data, /var/log/adu → /adu/logs, /etc/adu → /adu/conf
# Use Case: Simple A/B updates without overlayfs complexity

SUMMARY = "ADU Persistence Strategy - Symlinks"
DESCRIPTION = "Implements symlink-based persistence for ADU directories. \
               Symlinks are created from /var/lib/adu, /var/log/adu, /etc/adu to /adu partition. \
               This is the simpler persistence strategy, suitable for basic A/B updates."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://setup-adu-symlinks.sh \
    file://adu-persistence-symlinks.service \
"

S = "${WORKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "adu-persistence-symlinks.service"
SYSTEMD_AUTO_ENABLE = "enable"

# Dependencies
RDEPENDS:${PN} = "adu-filesystem-layout"

# Conflicts with overlayfs strategy
RCONFLICTS:${PN} = "adu-persistent-overlay"

do_install() {
    # Install setup script
    install -d ${D}${sbindir}
    install -m 0755 ${WORKDIR}/setup-adu-symlinks.sh ${D}${sbindir}/
    
    # Install systemd service
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/adu-persistence-symlinks.service ${D}${systemd_system_unitdir}/
}

FILES:${PN} = " \
    ${sbindir}/setup-adu-symlinks.sh \
    ${systemd_system_unitdir}/adu-persistence-symlinks.service \
"

# This recipe provides ADU persistence strategy
PROVIDES = "adu-persistence-strategy"
RPROVIDES:${PN} = "adu-persistence-strategy"
