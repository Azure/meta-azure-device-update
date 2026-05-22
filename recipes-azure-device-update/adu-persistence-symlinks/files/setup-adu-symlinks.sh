#!/bin/bash
# ADU Persistence Strategy - Symlinks
# Purpose: Create symlinks from /var/lib/adu, /var/log/adu, /etc/adu to /adu partition
# Version: 1.0

set -e

VERSION_FILE="/adu/.symlinks-persistence-version"
SCRIPT_VERSION="1.0"
LOG_PREFIX="[ADU-SYMLINKS]"

log_info() {
    echo "$LOG_PREFIX INFO: $*"
    logger -t adu-persistence-symlinks -p user.info "$*"
}

log_warn() {
    echo "$LOG_PREFIX WARNING: $*" >&2
    logger -t adu-persistence-symlinks -p user.warning "$*"
}

log_error() {
    echo "$LOG_PREFIX ERROR: $*" >&2
    logger -t adu-persistence-symlinks -p user.err "$*"
}

# Create or verify symlink
create_symlink() {
    local target="$1"
    local link="$2"
    local description="$3"
    
    # If link already exists as correct symlink, nothing to do
    if [ -L "$link" ] && [ "$(readlink "$link")" = "$target" ]; then
        log_info "✓ $description: $link → $target (already exists)"
        return 0
    fi
    
    # If link exists but is wrong (directory or wrong symlink), migrate
    if [ -e "$link" ] || [ -L "$link" ]; then
        if [ -d "$link" ] && [ ! -L "$link" ]; then
            log_warn "$link exists as directory - migrating content to $target"
            # Copy content to target location
            cp -a "$link/." "$target/" 2>/dev/null || true
            # Remove original directory
            rm -rf "$link"
        elif [ -L "$link" ]; then
            log_warn "$link is symlink to wrong target - updating"
            rm -f "$link"
        else
            log_warn "$link exists as file - backing up and replacing"
            mv "$link" "${link}.backup"
        fi
    fi
    
    # Create parent directory if needed
    mkdir -p "$(dirname "$link")"
    
    # Create the symlink
    ln -sf "$target" "$link"
    
    # Set ownership (symlinks are lrwxrwxrwx, but we set for reference)
    chown -h adu:adu "$link" 2>/dev/null || true
    
    log_info "✓ $description: Created $link → $target"
    return 0
}

# Setup all symlinks
setup_symlinks() {
    log_info "Creating ADU persistence symlinks..."
    
    # /var/lib/adu → /adu/data (ADU data: downloads, extensions, states)
    create_symlink "/adu/data" "/var/lib/adu" "ADU data directory"
    
    # /var/log/adu → /adu/logs (ADU logs)
    create_symlink "/adu/logs" "/var/log/adu" "ADU log directory"
    
    # /etc/adu → /adu/conf (ADU configuration)
    create_symlink "/adu/conf" "/etc/adu" "ADU config directory"
    
    log_info "✓ All symlinks created successfully"
}

# Verify symlinks
verify_symlinks() {
    log_info "Verifying symlinks..."
    
    local failed=0
    
    # Check /var/lib/adu → /adu/data
    if [ ! -L "/var/lib/adu" ] || [ "$(readlink /var/lib/adu)" != "/adu/data" ]; then
        log_error "/var/lib/adu symlink incorrect"
        failed=1
    fi
    
    # Check /var/log/adu → /adu/logs
    if [ ! -L "/var/log/adu" ] || [ "$(readlink /var/log/adu)" != "/adu/logs" ]; then
        log_error "/var/log/adu symlink incorrect"
        failed=1
    fi
    
    # Check /etc/adu → /adu/conf
    if [ ! -L "/etc/adu" ] || [ "$(readlink /etc/adu)" != "/adu/conf" ]; then
        log_error "/etc/adu symlink incorrect"
        failed=1
    fi
    
    if [ $failed -eq 1 ]; then
        log_error "Symlink verification failed"
        return 1
    fi
    
    log_info "✓ All symlinks verified"
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
    log_info "=== ADU Symlinks Persistence Setup v${SCRIPT_VERSION} ==="
    
    # Verify /adu structure exists (created by adu-filesystem-layout)
    if [ ! -d /adu/conf ] || [ ! -d /adu/logs ] || [ ! -d /adu/data ]; then
        log_error "/adu structure incomplete - adu-filesystem-layout must run first"
        exit 1
    fi
    
    current_version=$(get_current_version)
    log_info "Current version: $current_version, Script version: $SCRIPT_VERSION"
    
    if [ "$current_version" = "0.0" ]; then
        # First boot - create symlinks
        log_info "First boot detected - creating symlinks"
        setup_symlinks
        verify_symlinks || exit 1
        update_version
        log_info "✓ First boot setup completed successfully"
        
    elif [ "$current_version" != "$SCRIPT_VERSION" ]; then
        # Version upgrade - verify and repair
        log_info "Version upgrade detected ($current_version → $SCRIPT_VERSION)"
        setup_symlinks  # Idempotent
        verify_symlinks || exit 1
        update_version
        log_info "✓ Upgrade completed successfully"
        
    else
        # Already set up - quick verification
        log_info "Already set up (v${SCRIPT_VERSION}) - performing quick verification"
        if ! verify_symlinks; then
            log_info "Verification failed - attempting repair"
            setup_symlinks
            verify_symlinks || exit 1
            log_info "✓ Repair completed successfully"
        else
            log_info "✓ Verification passed - no action needed"
        fi
    fi
    
    log_info "=== ADU Symlinks Persistence Setup Complete ==="
    exit 0
}

main "$@"
