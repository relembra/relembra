#!/bin/bash

# Warning: No attempt is made to minimize downtime.

set -e

REPO_ROOT=$(dirname "${BASH_SOURCE[0]}")/..
DATOMIC_ROOT=/opt/datomic-free-0.9.5372

echo "Stopping datomic..."
#sudo systemctl stop datomic

echo "Replacing config files..."
DATOMIC_CONF=$DATOMIC_ROOT/config/datomic-free-transactor.properties
sudo cp etc/datomic-free-transactor.properties $DATOMIC_CONF
sudo chown root:root $DATOMIC_CONF
sudo chmod 644 $DATOMIC_CONF


SVC_DST=/lib/systemd/system/datomic.service
sudo cp etc/datomic.service $SVC_DST
sudo chown root:root $SVC_DST
sudo chmod 644 $SVC_DST


sudo systemctl daemon-reload

echo "Starting datomic..."
sudo systemctl start datomic

cd - > /dev/null
echo "Done."
