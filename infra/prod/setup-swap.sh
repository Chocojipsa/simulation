#!/bin/bash
set -e

echo "Starting SWAP space configuration..."

if [ -f /swapfile ]; then
    echo "Swapfile already exists. Skipping creation."
else
    echo "Creating 2GB swap file..."
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    
    # Append to /etc/fstab if not present
    if ! grep -q "/swapfile" /etc/fstab; then
        echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
    fi
    echo "Swapfile created and activated successfully."
fi

echo "Current memory status:"
free -h
