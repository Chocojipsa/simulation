#!/bin/bash
set -e

# Enforce root execution
if [ "$EUID" -ne 0 ]; then
    echo "Error: This script must be run as root. Please run with sudo." >&2
    exit 1
fi

echo "Starting SWAP space configuration..."

# Check if swap is active in /proc/swaps
if grep -q "/swapfile" /proc/swaps; then
    echo "Swapfile is already active."
else
    echo "Creating and initializing 2GB swap file..."
    # Touch and set permission first to close the security umask read window
    touch /swapfile
    chmod 600 /swapfile

    # Attempt fallocate, fallback to dd
    if ! fallocate -l 2G /swapfile; then
        echo "fallocate failed, falling back to dd..."
        dd if=/dev/zero of=/swapfile bs=1M count=2048
    fi

    mkswap /swapfile
    echo "Activating swapfile..."
    swapon /swapfile
fi

# Append to /etc/fstab if not present
if ! grep -q "/swapfile" /etc/fstab; then
    echo "Backing up /etc/fstab..."
    cp /etc/fstab "/etc/fstab.bak.$(date +%Y%m%d%H%M%S)"
    
    echo "Adding swapfile to /etc/fstab..."
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "Swapfile created and activated successfully."
echo "Current memory status:"
free -h
