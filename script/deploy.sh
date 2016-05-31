#!/bin/bash

# Warning: No attempt is made to minimize downtime.

set -e

REPO_ROOT=$(dirname "${BASH_SOURCE[0]}")/..
NGINX_ROOT=/opt/nginx-clojure-0.4.4

echo "Stopping nginx..."
# Free the memory for the build process.
sudo systemctl stop nginx

cd $REPO_ROOT
echo "Building uberjar..."
boot build
echo "Replacing uberjar..."
sudo cp target/project.jar $NGINX_ROOT/libs/relembra.jar
echo "Replacing config files..."
NGINX_CONF=$NGINX_ROOT/conf/nginx.conf
sudo cp etc/nginx.conf $NGINX_CONF
sudo chown root:root $NGINX_CONF
sudo chmod 644 $NGINX_CONF
SVC_DST=/lib/systemd/system/nginx.service
sudo cp etc/nginx.service $SVC_DST
sudo chown root:root $SVC_DST
sudo chmod 644 $SVC_DST
sudo systemctl daemon-reload
echo "Starting nginx..."
sudo systemctl start nginx
cd -
echo "Done."
