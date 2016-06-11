#!/bin/bash

# Warning: No attempt is made to minimize downtime.

set -e

REPO_ROOT=$(dirname "${BASH_SOURCE[0]}")/..
NGINX_ROOT=/opt/nginx-clojure-0.4.4
DATOMIC_ROOT=/opt/datomic-free-0.9.5372

echo "Stopping nginx..."
# Free the memory for the build process.
sudo systemctl stop nginx

echo "Stopping datomic..."
sudo systemctl stop datomic

cd $REPO_ROOT > /dev/null
echo "Building uberjar..."
boot build
echo "Replacing uberjar..."
sudo cp target/project.jar $NGINX_ROOT/libs/relembra.jar

echo "Replacing config files..."
DATOMIC_CONF=$DATOMIC_ROOT/config/datomic-free-transactor.properties
sudo cp etc/datomic-free-transactor.properties $DATOMIC_CONF
sudo chown root:root $DATOMIC_CONF
sudo chmod 644 $DATOMIC_CONF

NGINX_CONF=$NGINX_ROOT/conf/nginx.conf
sudo cp etc/nginx.conf $NGINX_CONF
sudo chown root:root $NGINX_CONF
sudo chmod 644 $NGINX_CONF

SVC_DST=/lib/systemd/system/datomic.service
sudo cp etc/datomic.service $SVC_DST
sudo chown root:root $SVC_DST
sudo chmod 644 $SVC_DST

SVC_DST=/lib/systemd/system/nginx.service
sudo cp etc/nginx.service $SVC_DST
sudo chown root:root $SVC_DST
sudo chmod 644 $SVC_DST

sudo cp etc/backup_daily.py /usr/bin
sudo chown root:root /usr/bin/backup_daily.py
sudo chmod 755 /usr/bin/backup_daily.py

sudo systemctl daemon-reload

echo "Starting datomic..."
sudo systemctl start datomic

echo "Starting nginx..."
sudo systemctl start nginx

cd - > /dev/null
echo "Done."
