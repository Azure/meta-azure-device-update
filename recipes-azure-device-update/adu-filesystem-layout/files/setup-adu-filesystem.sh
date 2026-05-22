#!/bin/bash
# ADU Filesystem Layout Setup
# Purpose: Create and maintain /adu partition directory structure
# Scope: ONLY manages /adu/* - does NOT create symlinks to /var/* or /etc/*
# Version: 1.0

set -e

VERSION_FILE="/adu/.filesystem-layout-version"
SCRIPT_VERSION="1.0"
LOG_PREFIX="[ADU-FS-LAYOUT]"

log_info() {
    echo "$LOG_PREFIX INFO: $*"
    logger -t adu-filesystem-layout -p user.info "$*"
}

log_error() {
    echo "$LOG_PREFIX ERROR: $*" >&2
    logger -t adu-filesystem-layout -p user.err "$*"
}

# Check if /adu is mounted and writable
check_adu_mount() {
    if [ ! -d /adu ]; then
        log_error "/adu directory does not exist"
        return 1
    fi
    
    if ! mount | grep -q '/adu'; then
        log_error "/adu is not mounted"
        return 1
    fi
    
    if ! touch /adu/.write-test 2>/dev/null; then
        log_error "/adu is not writable"
        return 1
    fi
    rm -f /adu/.write-test
    
    log_info "/adu mount verified"
    return 0
}

# Create /adu partition directory structure
setup_directories() {
    log_info "Creating /adu directory structure..."
    
    # Core directories
    mkdir -p /adu/conf
    mkdir -p /adu/logs
    mkdir -p /adu/data/downloads
    mkdir -p /adu/data/extensions/sources
    mkdir -p /adu/data/extensions/component_enumerator
    mkdir -p /adu/data/extensions/content_downloader
    mkdir -p /adu/data/extensions/update_content_handlers
    mkdir -p /adu/data/extensions/download_handlers
    mkdir -p /adu/data/states
    mkdir -p /adu/tools
    
    # Optional directories for persistence strategies
    mkdir -p /adu/overlay
    mkdir -p /adu/work
    mkdir -p /adu/system
    mkdir -p /adu/.backups
    
    log_info "✓ Created directory structure"
}

# Set ownership and permissions
set_permissions() {
    log_info "Setting ownership and permissions..."
    
    # /adu mount point
    chown adu:adu /adu 2>/dev/null || log_error "Failed to chown /adu (adu user may not exist yet)"
    chmod 0770 /adu
    
    # /adu/conf - Configuration files (readable by adu group)
    chown -R adu:adu /adu/conf 2>/dev/null || true
    chmod 0750 /adu/conf
    
    # /adu/logs - Log files (writable by adu user)
    chown -R adu:adu /adu/logs 2>/dev/null || true
    chmod 0774 /adu/logs
    
    # /adu/data - ADU data (downloads, extensions, states)
    chown -R adu:adu /adu/data 2>/dev/null || true
    chmod 0770 /adu/data
    find /adu/data -type d -exec chmod 0770 {} \; 2>/dev/null || true
    
    # /adu/tools - Root-owned tools
    chown root:root /adu/tools
    chmod 0755 /adu/tools
    
    # Persistence strategy directories (owned by adu for flexibility)
    chown adu:adu /adu/overlay /adu/work /adu/system /adu/.backups 2>/dev/null || true
    chmod 0770 /adu/overlay /adu/work /adu/system /adu/.backups 2>/dev/null || true
    
    log_info "✓ Set ownership and permissions"
}

# Verify directory structure
verify_structure() {
    log_info "Verifying directory structure..."
    
    local failed=0
    local required_dirs=(
        "/adu/conf"
        "/adu/logs"
        "/adu/data/downloads"
        "/adu/data/extensions"
        "/adu/data/states"
        "/adu/tools"
    )
    
    for dir in "${required_dirs[@]}"; do
        if [ ! -d "$dir" ]; then
            log_error "Required directory missing: $dir"
            failed=1
        fi
    done
    
    if [ $failed -eq 1 ]; then
        log_error "Directory structure verification failed"
        return 1
    fi
    
    log_info "✓ Directory structure verified"
    return 0
}

# Get current version
get_current_version() {
    if [ -f "$VERSION_FILE" ]; then
        cat "$VERSION_FILE"
    else
        echo "0.0"
    fi
}

# Update version file
update_version() {
    echo "$SCRIPT_VERSION" > "$VERSION_FILE"
    chmod 0644 "$VERSION_FILE"
    log_info "✓ Updated version marker to $SCRIPT_VERSION"
}

# Main execution
main() {
    log_info "=== ADU Filesystem Layout Setup v${SCRIPT_VERSION} ==="
    
    # Pre-flight checks
    if ! check_adu_mount; then
        log_error "Pre-flight check failed - /adu not properly mounted"
        exit 1
    fi
    
    current_version=$(get_current_version)
    log_info "Current version: $current_version, Script version: $SCRIPT_VERSION"
    
    if [ "$current_version" = "0.0" ]; then
        # First boot - full setup
        log_info "First boot detected - performing full setup"
        setup_directories
        set_permissions
        verify_structure || exit 1
        update_version
        log_info "✓ First boot setup completed successfully"
        
    elif [ "$current_version" != "$SCRIPT_VERSION" ]; then
        # Version upgrade - verify and repair
        log_info "Version upgrade detected ($current_version → $SCRIPT_VERSION)"
        setup_directories  # Idempotent - creates missing dirs
        set_permissions    # Idempotent - fixes permissions
        verify_structure || exit 1
        update_version
        log_info "✓ Upgrade completed successfully"
        
    else
        # Already set up - quick verification
        log_info "Already set up (v${SCRIPT_VERSION}) - performing quick verification"
        if ! verify_structure; then
            log_info "Verification failed - attempting repair"
            setup_directories
            set_permissions
            verify_structure || exit 1
            log_info "✓ Repair completed successfully"
        else
            log_info "✓ Verification passed - no action needed"
        fi
    fi
    
    log_info "=== ADU Filesystem Layout Setup Complete ==="
    exit 0
}

main "$@"
