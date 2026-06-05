#!/bin/bash
set -e

echo "Starting SWAP space configuration..."

# 1. Backup /etc/fstab
echo "Backing up /etc/fstab..."
sudo cp /etc/fstab "/etc/fstab.bak.$(date +%Y%m%d%H%M%S)"

# 2. Check if swapfile exists
if [ ! -f /swapfile ]; then
    echo "Creating 2GB swap file..."
    # Attempt fallocate, fallback to dd if it fails
    if ! sudo fallocate -l 2G /swapfile; then
        echo "fallocate failed, falling back to dd..."
        sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
    fi
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
fi

# 3. Check if swap is active
if ! swapon --show | grep -q "/swapfile"; then
    echo "Activating swapfile..."
    sudo swapon /swapfile
else
    echo "Swapfile is already active."
fi

# 4. Append to /etc/fstab if not present
if ! grep -q "/swapfile" /etc/fstab; then
    echo "Adding swapfile to /etc/fstab..."
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "Swapfile created and activated successfully."
echo "Current memory status:"
free -h
