#!/usr/bin/env python

from datetime import datetime
import os.path


def weekday_abbrev():
    return datetime.utcnow().strftime('%a').lower()

def backup():
    os.system("/opt/datomic-free-0.9.5372/bin/datomic -Xmx800m -Xms800m backup-db datomic:free://localhost:4334/relembra file:/opt/datomic-free-0.9.5372/bak/%s" % weekday_abbrev())


if __name__ == '__main__':
    backup()
