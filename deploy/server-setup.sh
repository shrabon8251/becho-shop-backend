#!/usr/bin/env bash
# First-time setup for a fresh Ubuntu 24.04 VPS. Run as root:
#     curl the file to the server, then:  sudo bash server-setup.sh
set -euo pipefail

echo "==> System update..."
apt-get update && apt-get upgrade -y

echo "==> Installing Docker..."
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

echo "==> Firewall: allow only SSH (22), HTTP (80), HTTPS (443)..."
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "==> fail2ban (blocks brute-force SSH attempts)..."
apt-get install -y fail2ban
systemctl enable --now fail2ban

echo "==> Deploy directory..."
mkdir -p /opt/becho

echo "==> AWS CLI (used by backup.sh to upload DB dumps to R2)..."
apt-get install -y awscli

echo "DONE. Next: copy the deploy/ folder to /opt/becho and create /opt/becho/.env (see DEPLOY.md)."
