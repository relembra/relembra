#!/bin/bash

# Warning: No attempt is made to minimize downtime.

set -e

REPO_ROOT=$(dirname "${BASH_SOURCE[0]}")/..
NGINX_ROOT=/opt/nginx-clojure-0.4.4

echo "Stopping nginx..."
# Free the memory for the build process.
sudo service nginx stop

cd $REPO_ROOT
echo "Building uberjar..."
boot build
echo "Replacing uberjar..."
sudo cp target/project.jar $NGINX_ROOT/libs/relembra.jar
echo "Replacing config files..."
sudo cp etc/nginx.conf $NGINX_ROOT/conf/nginx.conf
sudo cp etc/nginx.service /lib/systemd/system/nginx.service
echo "Starting nginx..."
sudo service nginx start
cd -
echo "Done."
