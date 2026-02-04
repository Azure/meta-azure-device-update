# Recipe: adu-config-setup
# Description: Setup persistent ADU configuration and data directories with symlinks
# Purpose: Centralize ADU config in /adu/ and redirect standard Linux paths via symlinks

SUMMARY = "Azure Device Update configuration setup and directory structure"
DESCRIPTION = "Creates /adu/ directory structure for persistent ADU configuration and data, \
               sets up symlinks from standard Linux locations to /adu/ paths, and configures \
               systemd-tmpfiles for automatic directory maintenance."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = " \
    file://du-config.json \
    file://du-config.json.template \
    file://du-diagnostics-config.json \
    file://adu-logs.conf \
    file://adu-oobe.service \
    file://setup-adu-dirs.sh \
"

S = "${WORKDIR}"

inherit systemd

SYSTEMD_SERVICE:${PN} = "adu-oobe.service"
SYSTEMD_AUTO_ENABLE = "enable"

# Runtime dependency: azure-device-update creates adu and do users/groups
# We need those users to exist before setting directory ownership
RDEPENDS:${PN} = "azure-device-update"

do_install() {
    # =========================================================================
    # Install setup script and systemd service
    # =========================================================================
    # The script creates directories on the mounted /adu partition (mmcblk0p4)
    # This runs after adu.mount via systemd dependency
    
    install -d ${D}${sbindir}
    install -m 0755 ${WORKDIR}/setup-adu-dirs.sh ${D}${sbindir}/
    
    install -d ${D}${systemd_system_unitdir}
    install -m 0644 ${WORKDIR}/adu-oobe.service ${D}${systemd_system_unitdir}/
    
    # =========================================================================
    # Install configuration templates to /usr/share/adu-config-templates
    # =========================================================================
    # These will be copied to /adu/conf by the setup script
    # du-config.json is installed as a working default (with placeholder connection string)
    # du-config.json.template is kept as reference for users to customize
    
    install -d ${D}${datadir}/adu-config-templates
    install -m 0644 ${WORKDIR}/du-config.json ${D}${datadir}/adu-config-templates/
    install -m 0644 ${WORKDIR}/du-config.json.template ${D}${datadir}/adu-config-templates/
    install -m 0644 ${WORKDIR}/du-diagnostics-config.json ${D}${datadir}/adu-config-templates/
    
    # =========================================================================
    # Install systemd-tmpfiles configuration
    # =========================================================================
    # This ensures /adu/logs directory is cleaned periodically (on /adu partition)
    
    install -d ${D}${sysconfdir}/tmpfiles.d
    install -m 0644 ${WORKDIR}/adu-logs.conf ${D}${sysconfdir}/tmpfiles.d/
    
    # =========================================================================
    # Create diagnostic log directory on boot partition
    # =========================================================================
    # This provides fallback logging when /adu mount fails
    # /boot is always available (FAT32 boot partition)
    
    install -d ${D}/boot/adu-diagnostics
}

pkg_postinst_ontarget:${PN}() {
    #!/bin/sh
    set -e
    
    # Function to safely create symlink
    create_symlink() {
        local target="$1"
        local link="$2"
        
        # If link already exists as a symlink pointing to target, nothing to do
        if [ -L "$link" ] && [ "$(readlink "$link")" = "$target" ]; then
            echo "Symlink $link -> $target already exists"
            return 0
        fi
        
        # If link exists but is not a symlink or points elsewhere
        if [ -e "$link" ] || [ -L "$link" ]; then
            echo "WARNING: $link exists but is not a symlink to $target"
            echo "  Backing up to ${link}.backup and creating symlink"
            mv "$link" "${link}.backup"
        fi
        
        # Create parent directory if needed
        mkdir -p "$(dirname "$link")"
        
        # Create the symlink
        ln -sf "$target" "$link"
        
        # Set ownership of the symlink itself (not the target)
        chown -h adu:adu "$link" 2>/dev/null || echo "WARNING: Could not set ownership on symlink $link"
        
        echo "Created symlink: $link -> $target (owner: adu:adu)"
    }

    # Create symlinks
    create_symlink "/adu/conf" "/etc/adu"
    # Ensure /adu/conf (target of /etc/adu symlink) has correct ownership and permissions
    # Note: chmod/chown on a symlink changes the target, not the symlink itself
    # Symlinks always show as lrwxrwxrwx, but target permissions control access
    chown adu:adu /adu/conf 2>/dev/null || echo "WARNING: Could not set ownership on /adu/conf"
    chmod 0750 /adu/conf 2>/dev/null || echo "WARNING: Could not set permissions on /adu/conf"
    
    create_symlink "/adu/logs" "/var/log/adu"

    # # For /var/lib/adu/downloads, we need to ensure parent directory exists
    # # but azure-device-update recipe creates /var/lib/adu, so this should exist
    # if [ -d "/var/lib/adu" ]; then
    #     create_symlink "/adu/data/downloads" "/var/lib/adu/downloads"
    # else
    #     echo "WARNING: /var/lib/adu does not exist yet, symlinking downloads may fail"
    #     echo "  Creating /var/lib/adu and then symlinking..."
    #     mkdir -p /var/lib/adu
    #     chown adu:adu /var/lib/adu
    #     chmod 0770 /var/lib/adu
    #     create_symlink "/adu/data/downloads" "/var/lib/adu/downloads"
    # fi

exit 0
}

# Package files
FILES:${PN} = " \
    ${sbindir}/setup-adu-dirs.sh \
    ${systemd_system_unitdir}/adu-oobe.service \
    ${datadir}/adu-config-templates/* \
    ${sysconfdir}/tmpfiles.d/adu-logs.conf \
    /boot/adu-diagnostics \
"

# Allow empty directories to be packaged
ALLOW_EMPTY:${PN} = "1"

# Replaces the old adu-log-dir recipe functionality
RPROVIDES:${PN} = "adu-log-dir"
RREPLACES:${PN} = "adu-log-dir"
RCONFLICTS:${PN} = "adu-log-dir"
