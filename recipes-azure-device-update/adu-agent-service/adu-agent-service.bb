# Installs the Device Update Agent Service that will auto-start the DU Agent
# and pass in the DU Agent configurations located at /adu/du-config.json

LICENSE="CLOSED"

SRC_URI = "\
    file://deviceupdate-agent.service \
"

SYSTEMD_SERVICE:${PN} = "deviceupdate-agent.service"

do_install:append() {
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/deviceupdate-agent.service ${D}${systemd_system_unitdir}
}

FILES:${PN} += "${systemd_system_unitdir}/deviceupdate-agent.service"

REQUIRED_DISTRO_FEATURES = "systemd"

DEPENDS = "azure-device-update deliveryoptimization-agent-service"

RDEPENDS:${PN} += "azure-device-update deliveryoptimization-agent-service"

inherit allarch systemd features_check
