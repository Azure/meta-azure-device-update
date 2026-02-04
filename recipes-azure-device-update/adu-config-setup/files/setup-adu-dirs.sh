#!/bin/sh
# setup-adu-dirs.sh
# Purpose: Bootstrap, verify, and maintain ADU directory structure on /adu partition
# Phases: Bootstrap (first-time), Verify (every boot), Migrate (version changes), Repair (fix corruption)
# This script runs via systemd service after the /adu partition is mounted

# DO NOT use 'set -e' - we want explicit error handling with diagnostics
set -u  # Exit on undefined variables

# =========================================================================
# Configuration
# =========================================================================
SCRIPT_VERSION="1.0"
VERSION_FILE="/adu/.setup-version"
TEMPLATE_DIR="/usr/share/adu-config-templates"
LOG_PREFIX="[ADU-OOBE]"

# Fallback logging when /adu is not available (critical for debugging mount failures)
FALLBACK_LOG_DIR="/boot/adu-diags"
FALLBACK_LOG="${FALLBACK_LOG_DIR}/oobe-failure.log"
DIAG_SNAPSHOT="${FALLBACK_LOG_DIR}/oobe-snapshot.txt"

# Error codes
ERR_MOUNT_MISSING=1
ERR_NOT_WRITABLE=2
ERR_USER_MISSING=3
ERR_MKDIR_FAILED=4
ERR_CHOWN_FAILED=5
ERR_TEMPLATE_FAILED=6

# =========================================================================
# Diagnostic Logging Functions
# =========================================================================

# Initialize fallback logging
init_fallback_logging() {
    if [ ! -d "$FALLBACK_LOG_DIR" ]; then
        mkdir -p "$FALLBACK_LOG_DIR" 2>/dev/null || return 0
    fi
    # Start fresh log for this boot
    echo "=== ADU OOBE Log - $(date) ===" > "$FALLBACK_LOG" 2>/dev/null || return 0
}

# Log to both systemd journal and fallback location
log_both() {
    local level="$1"
    shift
    local msg="$*"
    
    # To systemd journal (via stdout/stderr)
    if [ "$level" = "ERROR" ] || [ "$level" = "WARNING" ]; then
        echo "$LOG_PREFIX $level: $msg" >&2
    else
        echo "$LOG_PREFIX $level: $msg"
    fi
    
    # To fallback log (for debugging when /adu fails)
    if [ -w "$FALLBACK_LOG_DIR" ]; then
        echo "$(date '+%Y-%m-%d %H:%M:%S') [$level] $msg" >> "$FALLBACK_LOG" 2>/dev/null || true
    fi
}

log_info() {
    log_both "INFO" "$@"
}

log_warn() {
    log_both "WARNING" "$@"
}

log_error() {
    log_both "ERROR" "$@"
}

# Create diagnostic snapshot for troubleshooting
create_diagnostic_snapshot() {
    local snapshot_file="${1:-$DIAG_SNAPSHOT}"
    
    if [ ! -w "$FALLBACK_LOG_DIR" ]; then
        return 0
    fi
    
    {
        echo "=== ADU OOBE Diagnostic Snapshot ==="
        echo "Timestamp: $(date)"
        echo "Script Version: $SCRIPT_VERSION"
        echo ""
        
        echo "--- Mount Status ---"
        mount | grep -E '(adu|mmcblk0p4)' || echo "No /adu mount found"
        echo ""
        
        echo "--- /adu Directory ---"
        if [ -d /adu ]; then
            ls -laR /adu 2>&1 || echo "Cannot list /adu"
            df -h /adu 2>&1 || echo "Cannot get /adu filesystem info"
        else
            echo "/adu directory does not exist"
        fi
        echo ""
        
        echo "--- Users/Groups ---"
        grep -E '^(adu|do):' /etc/passwd || echo "adu user not found"
        grep -E '^(adu|do):' /etc/group || echo "adu group not found"
        echo ""
        
        echo "--- Systemd Services ---"
        systemctl status adu.mount 2>&1 | head -20 || echo "adu.mount not found"
        systemctl status adu-oobe.service 2>&1 | head -20 || echo "adu-oobe.service not found"
        echo ""
        
        echo "--- Disk Space ---"
        df -h 2>&1
        echo ""
        
        echo "--- Recent Journal Entries ---"
        journalctl -u adu-oobe.service --no-pager -n 50 2>&1 || echo "Cannot read journal"
        
    } > "$snapshot_file" 2>&1
    
    log_info "Diagnostic snapshot saved to $snapshot_file"
}

# =========================================================================
# Pre-Flight Checks
# =========================================================================

# Check if /adu is mounted and writable
check_adu_mount() {
    log_info "Pre-flight: Checking /adu mount status..."
    
    # Check if /adu exists
    if [ ! -d /adu ]; then
        log_error "/adu directory does not exist"
        create_diagnostic_snapshot
        return $ERR_MOUNT_MISSING
    fi
    
    # Check if /adu is actually mounted (not just an empty dir in rootfs)
    if ! mount | grep -q '/adu'; then
        log_error "/adu is not mounted - expected /dev/mmcblk0p4 or similar"
        log_error "Run: mount | grep adu"
        create_diagnostic_snapshot
        return $ERR_MOUNT_MISSING
    fi
    
    # Check if /adu is writable
    if ! touch /adu/.write-test 2>/dev/null; then
        log_error "/adu is mounted but not writable"
        log_error "Check: mount | grep adu (look for 'ro' flag)"
        create_diagnostic_snapshot
        return $ERR_NOT_WRITABLE
    fi
    rm -f /adu/.write-test
    
    log_info "Pre-flight: /adu is mounted and writable"
    return 0
}

# Check if required users/groups exist
check_users() {
    log_info "Pre-flight: Checking adu:adu user/group..."
    
    if ! grep -q "^adu:" /etc/passwd 2>/dev/null; then
        log_error "User 'adu' (UID 800) does not exist"
        log_error "Required: RDEPENDS on azure-device-update"
        create_diagnostic_snapshot
        return $ERR_USER_MISSING
    fi
    
    if ! grep -q "^adu:" /etc/group 2>/dev/null; then
        log_error "Group 'adu' (GID 800) does not exist"
        create_diagnostic_snapshot
        return $ERR_USER_MISSING
    fi
    
    log_info "Pre-flight: adu:adu user/group verified (UID:GID 800:800)"
    
    # Check and fix group memberships if 'do' user/group exists
    if grep -q "^do:" /etc/passwd 2>/dev/null && grep -q "^do:" /etc/group 2>/dev/null; then
        log_info "Pre-flight: Checking group memberships for adu and do users..."
        
        # Check if adu is member of do group
        if ! id -nG adu 2>/dev/null | grep -qw do; then
            log_warn "adu user is not member of 'do' group - fixing..."
            if usermod -a -G do adu 2>/dev/null; then
                log_info "✓ Added adu to 'do' group"
            else
                log_error "Failed to add adu to 'do' group"
                return $ERR_USER_MISSING
            fi
        else
            log_info "✓ adu is member of 'do' group"
        fi
        
        # Check if do is member of adu group
        if ! id -nG do 2>/dev/null | grep -qw adu; then
            log_warn "do user is not member of 'adu' group - fixing..."
            if usermod -a -G adu do 2>/dev/null; then
                log_info "✓ Added do to 'adu' group"
            else
                log_error "Failed to add do to 'adu' group"
                return $ERR_USER_MISSING
            fi
        else
            log_info "✓ do is member of 'adu' group"
        fi
    else
        log_info "Pre-flight: do user/group not present (delivery optimization not installed)"
    fi
    
    return 0
}

# Safe ownership change with verification
safe_chown() {
    local owner="$1"
    local path="$2"
    
    if ! chown "$owner" "$path" 2>/dev/null; then
        log_error "Failed to chown $owner $path"
        return $ERR_CHOWN_FAILED
    fi
    
    # Verify it worked - compare both name and numeric formats
    local actual_owner_name=$(stat -c '%U:%G' "$path" 2>/dev/null)
    local actual_owner_numeric=$(stat -c '%u:%g' "$path" 2>/dev/null)
    
    # Check if either format matches (supports both "adu:adu" and "800:800")
    if [ "$actual_owner_name" != "$owner" ] && [ "$actual_owner_numeric" != "$owner" ]; then
        log_warn "chown reported success but ownership mismatch: expected=$owner actual=$actual_owner_name($actual_owner_numeric) path=$path"
        return $ERR_CHOWN_FAILED
    fi
    
    return 0
}

# Safe directory creation with verification
safe_mkdir() {
    local dir="$1"
    
    if ! mkdir -p "$dir" 2>/dev/null; then
        log_error "Failed to create directory: $dir"
        return $ERR_MKDIR_FAILED
    fi
    
    if [ ! -d "$dir" ]; then
        log_error "mkdir reported success but directory doesn't exist: $dir"
        return $ERR_MKDIR_FAILED
    fi
    
    return 0
}

# =========================================================================
# Helper Functions
# =========================================================================

# Check if this is first boot (bootstrap phase)
is_first_boot() {
    [ ! -f "$VERSION_FILE" ]
}

# Get current setup version from partition
get_current_version() {
    if [ -f "$VERSION_FILE" ]; then
        cat "$VERSION_FILE"
    else
        echo "0.0"
    fi
}

# Update version file
update_version() {
    if echo "$SCRIPT_VERSION" > "$VERSION_FILE" 2>/dev/null; then
        chmod 0644 "$VERSION_FILE" 2>/dev/null || log_warn "Could not chmod version file"
        log_info "✓ Updated version marker to $SCRIPT_VERSION"
        return 0
    else
        log_error "Failed to write version file: $VERSION_FILE"
        return 1
    fi
}

# =========================================================================
# Phase 1: Bootstrap (First Boot Only)
# =========================================================================
bootstrap_directories() {
    log_info "PHASE 1: Bootstrapping ADU directory structure (first boot)"
    
    # Set ownership on /adu mount point (generic requirement for all ADU devices)
    log_info "Setting /adu mount point ownership..."
    if ! safe_chown "800:800" /adu; then
        log_error "Failed to set /adu ownership to 800:800"
        return $ERR_CHOWN_FAILED
    fi
    
    if ! chmod 770 /adu 2>/dev/null; then
        log_error "Failed to chmod 770 /adu"
        return $ERR_CHOWN_FAILED
    fi
    log_info "✓ /adu mount point: adu:adu (800:800) with mode 770"
    
    # Create directory structure on the mounted /adu partition
    log_info "Creating directory structure..."
    for dir in /adu/conf /adu/logs /adu/data/downloads /adu/data/extensions /adu/data/states /adu/data/sdc /adu/data/api /adu/tools; do
        if ! safe_mkdir "$dir"; then
            log_error "Failed to create $dir"
            return $ERR_MKDIR_FAILED
        fi
    done
    log_info "✓ Created directories: conf, logs, data/{downloads,extensions,states,sdc,api}, tools"
    
    # Set ownership and permissions with verification
    log_info "Setting directory ownership and permissions..."
    
    # /adu/conf - Configuration files, readable by adu group
    if ! safe_chown "adu:adu" /adu/conf || ! chmod 0750 /adu/conf; then
        log_error "Failed to set ownership on /adu/conf"
        return $ERR_CHOWN_FAILED
    fi
    log_info "✓ /adu/conf: adu:adu (750)"
    
    # /adu/logs - Log files, writable by adu user
    if ! safe_chown "adu:adu" /adu/logs || ! chmod 0774 /adu/logs; then
        log_error "Failed to set ownership on /adu/logs"
        return $ERR_CHOWN_FAILED
    fi
    log_info "✓ /adu/logs: adu:adu (774)"
    
    # Create symlink /var/log/adu -> /adu/logs for compatibility
    # Remove old directory or broken symlink if it exists
    if [ -e /var/log/adu ] && [ ! -L /var/log/adu ]; then
        log_warn "Found /var/log/adu as directory - removing to create symlink"
        rm -rf /var/log/adu 2>/dev/null || log_error "Failed to remove /var/log/adu directory"
    fi
    if [ ! -e /var/log/adu ]; then
        if ln -sf /adu/logs /var/log/adu 2>/dev/null; then
            log_info "✓ Created symlink: /var/log/adu -> /adu/logs"
        else
            log_error "Failed to create symlink /var/log/adu -> /adu/logs"
            return $ERR_MKDIR_FAILED
        fi
    else
        log_info "  Symlink already exists: /var/log/adu -> /adu/logs"
    fi
    
    # Create symlink /etc/adu -> /adu/conf for compatibility
    # Remove old directory or broken symlink if it exists
    if [ -e /etc/adu ] && [ ! -L /etc/adu ]; then
        log_warn "Found /etc/adu as directory - removing to create symlink"
        rm -rf /etc/adu 2>/dev/null || log_error "Failed to remove /etc/adu directory"
    fi
    if [ ! -e /etc/adu ]; then
        if ln -sf /adu/conf /etc/adu 2>/dev/null; then
            log_info "✓ Created symlink: /etc/adu -> /adu/conf"
        else
            log_error "Failed to create symlink /etc/adu -> /adu/conf"
            return $ERR_MKDIR_FAILED
        fi
    else
        log_info "  Symlink already exists: /etc/adu -> /adu/conf"
    fi
    
    # /adu/data - Parent directory for persistent data
    if ! safe_chown "adu:adu" /adu/data || ! chmod 0770 /adu/data; then
        log_error "Failed to set ownership on /adu/data"
        return $ERR_CHOWN_FAILED
    fi
    log_info "✓ /adu/data: adu:adu (770)"
    
    # Set ownership and permissions for /adu/data subdirectories
    local data_subdirs=(
        "downloads:Download cache (bind mount target for /var/lib/adu/downloads)"
        "extensions:Extensions cache (bind mount target for /var/lib/adu/extensions)"
        "states:State persistence (bind mount target for /var/lib/adu/states)"
        "sdc:Delta source cache (bind mount target for /var/lib/adu/sdc) - used by microsoft-delta-download-handler"
        "api:API FIFOs (bind mount target for /var/lib/adu/api) - for apireq.fifo"
    )
    
    for subdir_info in "${data_subdirs[@]}"; do
        local subdir="${subdir_info%%:*}"
        local description="${subdir_info#*:}"
        local full_path="/adu/data/${subdir}"
        
        if ! safe_chown "adu:adu" "$full_path" || ! chmod 0770 "$full_path"; then
            log_error "Failed to set ownership on $full_path"
            return $ERR_CHOWN_FAILED
        fi
        log_info "✓ /adu/data/${subdir}: adu:adu (770) - ${description}"
    done
    
    # /adu/tools - Utility scripts, readable by all
    if ! safe_chown "root:root" /adu/tools || ! chmod 0755 /adu/tools; then
        log_error "Failed to set ownership on /adu/tools"
        return $ERR_CHOWN_FAILED
    fi
    log_info "✓ /adu/tools: root:root (755)"
    log_info "  Note: User tools are in /usr/sbin/ (adu-wifi-setup.sh, adu-wifi-diagnostics.sh)"
    log_info "  Documentation is in /usr/share/doc/adu/"
    
    # Copy configuration templates (only if they don't exist)
    log_info "Installing configuration templates..."
    local template_count=0
    if [ -d "$TEMPLATE_DIR" ]; then
        for template in "$TEMPLATE_DIR"/*; do
            if [ -f "$template" ]; then
                filename=$(basename "$template")
                target="/adu/conf/$filename"
                
                if [ ! -f "$target" ]; then
                    if cp "$template" "$target" 2>/dev/null; then
                        safe_chown "adu:adu" "$target" || log_warn "Could not set ownership on $target"
                        chmod 0664 "$target" 2>/dev/null || log_warn "Could not chmod $target"
                        log_info "✓ Installed template: $filename"
                        template_count=$((template_count + 1))
                    else
                        log_error "Failed to copy template: $filename"
                        return $ERR_TEMPLATE_FAILED
                    fi
                else
                    log_info "  Template already exists: $filename"
                fi
            fi
        done
        log_info "✓ Installed $template_count configuration template(s)"
    else
        log_warn "Template directory not found: $TEMPLATE_DIR (continuing anyway)"
    fi
    
    # Write version marker
    if ! update_version; then
        log_error "Failed to write version marker"
        return 1
    fi
    
    log_info "✓ Bootstrap complete - ADU partition ready for first use"
    return 0
}

# =========================================================================
# Phase 2: Verify (Every Boot)
# =========================================================================
verify_structure() {
    log_info "PHASE 2: Verifying ADU directory structure"
    
    # Check if critical directories exist
    local needs_repair=0
    
    for dir in /adu/conf /adu/logs /adu/data/downloads /adu/data/extensions /adu/data/states /adu/data/sdc /adu/data/api /adu/tools; do
        if [ ! -d "$dir" ]; then
            log_warn "Missing directory: $dir"
            needs_repair=1
        fi
    done
    
    # Check ownership of critical directories (after A/B updates they might be wrong)
    # /adu/conf must be adu:adu (uid 800, gid 800)
    if [ -d /adu/conf ]; then
        local conf_owner=$(stat -c '%u:%g' /adu/conf 2>/dev/null)
        if [ "$conf_owner" != "800:800" ]; then
            log_warn "/adu/conf has wrong ownership: $conf_owner (should be 800:800)"
            needs_repair=1
        fi
    fi
    
    # Check if symlink exists and points to correct location
    if [ ! -L /var/log/adu ]; then
        log_warn "Missing or incorrect symlink: /var/log/adu"
        needs_repair=1
    elif [ "$(readlink /var/log/adu)" != "/adu/logs" ]; then
        log_warn "Symlink /var/log/adu points to wrong location: $(readlink /var/log/adu)"
        needs_repair=1
    fi
    
    if [ $needs_repair -eq 1 ]; then
        log_warn "Directory structure incomplete - triggering repair"
        return 1
    fi
    
    log_info "Directory structure verified"
    return 0
}

# =========================================================================
# Phase 3: Migrate (Version Changes)
# =========================================================================
migrate_configuration() {
    local current_version=$(get_current_version)
    
    if [ "$current_version" = "$SCRIPT_VERSION" ]; then
        log_info "PHASE 3: No migration needed (version $SCRIPT_VERSION)"
        return 0
    fi
    
    log_info "PHASE 3: Migrating from version $current_version to $SCRIPT_VERSION"
    
    # Future migrations can be added here based on version comparisons
    # Example:
    # if [ "$current_version" = "1.0" ] && [ "$SCRIPT_VERSION" = "1.1" ]; then
    #     # Add new directory for version 1.1
    #     mkdir -p /adu/new-feature
    #     chown adu:adu /adu/new-feature
    # fi
    
    # Update templates if new versions available (but preserve user changes)
    if [ -d "$TEMPLATE_DIR" ]; then
        for template in "$TEMPLATE_DIR"/*; do
            if [ -f "$template" ]; then
                filename=$(basename "$template")
                target="/adu/conf/$filename"
                
                # Only copy if target doesn't exist (preserve user modifications)
                if [ ! -f "$target" ]; then
                    cp "$template" "$target"
                    chown adu:adu "$target"
                    chmod 0664 "$target"
                    log_info "Installed new template: $filename"
                fi
            fi
        done
    fi
    
    update_version
    log_info "Migration complete"
    return 0
}

# =========================================================================
# Phase 4: Repair (Fix Corruption)
# =========================================================================
repair_structure() {
    log_info "PHASE 4: Repairing ADU directory structure"
    
    local repair_failed=0
    
    # Fix mount point ownership first (idempotent)
    if ! safe_chown "adu:adu" /adu || ! chmod 770 /adu 2>/dev/null; then
        log_error "Failed to fix /adu ownership"
        repair_failed=1
    else
        log_info "✓ Fixed /adu mount point ownership"
    fi
    
    # Recreate missing directories
    for dir in /adu/conf /adu/logs /adu/data/downloads /adu/data/extensions /adu/data/states /adu/data/sdc /adu/data/api /adu/tools; do
        if ! safe_mkdir "$dir"; then
            log_error "Failed to recreate $dir"
            repair_failed=1
        fi
    done
    
    # Fix ownership and permissions (idempotent)
    safe_chown "adu:adu" /adu/conf && chmod 0750 /adu/conf 2>/dev/null || repair_failed=1
    safe_chown "adu:adu" /adu/logs && chmod 0774 /adu/logs 2>/dev/null || repair_failed=1
    safe_chown "adu:adu" /adu/data 2>/dev/null || repair_failed=1
    safe_chown "adu:adu" /adu/data/downloads && chmod 0770 /adu/data/downloads 2>/dev/null || repair_failed=1
    safe_chown "adu:adu" /adu/data/extensions && chmod 0770 /adu/data/extensions 2>/dev/null || repair_failed=1
    safe_chown "adu:adu" /adu/data/states && chmod 0770 /adu/data/states 2>/dev/null || repair_failed=1
    safe_chown "adu:adu" /adu/data/sdc && chmod 0770 /adu/data/sdc 2>/dev/null || repair_failed=1
    safe_chown "adu:adu" /adu/data/api && chmod 0770 /adu/data/api 2>/dev/null || repair_failed=1
    safe_chown "root:root" /adu/tools && chmod 0755 /adu/tools 2>/dev/null || repair_failed=1
    
    # Fix symlink /var/log/adu -> /adu/logs
    if [ -e /var/log/adu ] && [ ! -L /var/log/adu ]; then
        log_warn "Removing /var/log/adu directory to create symlink"
        rm -rf /var/log/adu 2>/dev/null || {
            log_error "Failed to remove /var/log/adu directory"
            repair_failed=1
        }
    fi
    if [ ! -e /var/log/adu ]; then
        if ln -sf /adu/logs /var/log/adu 2>/dev/null; then
            log_info "✓ Created symlink: /var/log/adu -> /adu/logs"
        else
            log_error "Failed to create symlink /var/log/adu"
            repair_failed=1
        fi
    fi
    
    if [ $repair_failed -eq 0 ]; then
        log_info "✓ Repair complete - all structures restored"
        return 0
    else
        log_error "Repair completed with errors - see logs above"
        return 1
    fi
}

# =========================================================================
# Main Execution
# =========================================================================
main() {
    local exit_code=0
    
    # Initialize diagnostic logging first
    init_fallback_logging
    
    log_info "========================================"
    log_info "ADU Out-of-Box Experience v${SCRIPT_VERSION}"
    log_info "========================================"
    
    # Pre-flight checks (critical - exit if these fail)
    log_info "Running pre-flight checks..."
    
    if ! check_adu_mount; then
        log_error "FATAL: /adu partition check failed"
        log_error "This is a critical error - ADU cannot function without /adu partition"
        log_error "Debug: Check systemd service 'adu.mount' and /etc/fstab"
        create_diagnostic_snapshot
        exit $ERR_MOUNT_MISSING
    fi
    
    if ! check_users; then
        log_error "FATAL: Required users/groups missing"
        log_error "This usually means azure-device-update package hasn't run postinst yet"
        log_error "Debug: Check if azure-device-update is installed: opkg list-installed | grep azure-device-update"
        create_diagnostic_snapshot
        exit $ERR_USER_MISSING
    fi
    
    log_info "✓ Pre-flight checks passed"
    log_info ""
    
    # Phase 1: Bootstrap (first boot only)
    if is_first_boot; then
        log_info "Detected FIRST BOOT - running bootstrap"
        if bootstrap_directories; then
            log_info ""
            log_info "========================================" 
            log_info "✓ SUCCESS: First-boot setup complete"
            log_info "Device is ready for ADU operations"
            log_info "========================================"
            create_diagnostic_snapshot "$FALLBACK_LOG_DIR/oobe-success.txt"
            exit 0
        else
            exit_code=$?
            log_error ""
            log_error "========================================"
            log_error "✗ FAILED: Bootstrap phase failed"
            log_error "========================================"
            create_diagnostic_snapshot
            exit $exit_code
        fi
    fi
    
    # Phase 2: Verify structure
    log_info "Running verification phase..."
    if ! verify_structure; then
        log_warn "Verification failed - initiating repair"
        
        # Phase 4: Repair if verification failed
        if ! repair_structure; then
            log_error "Repair phase failed"
            create_diagnostic_snapshot
            exit_code=1
        else
            log_info "✓ Repair successful"
        fi
    else
        log_info "✓ Verification passed"
    fi
    
    # Phase 3: Migrate if version changed
    if ! migrate_configuration; then
        log_error "Migration phase failed"
        create_diagnostic_snapshot
        exit_code=1
    fi
    
    if [ $exit_code -eq 0 ]; then
        log_info ""
        log_info "========================================"
        log_info "✓ SUCCESS: ADU setup complete"
        log_info "Device verified and ready"
        log_info "========================================"
    else
        log_error ""
        log_error "========================================"
        log_error "✗ FAILED: ADU setup completed with errors"
        log_error "Check logs: journalctl -u adu-oobe.service"
        log_error "Check diagnostics: cat $FALLBACK_LOG"
        log_error "========================================"
        create_diagnostic_snapshot
    fi
    
    exit $exit_code
}

# Run main function
main
