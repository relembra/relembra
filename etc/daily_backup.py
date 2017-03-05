#!/usr/bin/env python

from datetime import datetime
import os.path
import sys


def weekday_abbrev():
    return datetime.now().strftime('%a').lower()

def warn(txt):
    print >> sys.stderr, "WARNING: %s!" % txt
    os.system("""echo "At %s: %s" | mail -s "WARNING: %s" euccastro@gmail.com""" % (datetime.now(), txt, txt))

def backup():
    wda = weekday_abbrev()
    backup_root = "/opt/datomic/bak"
    backup_path = os.path.join(backup_root, wda)
    os.chdir(backup_root)
    retcode = os.system("/opt/datomic/current/bin/datomic -Xmx800m -Xms800m backup-db datomic:free://localhost:4334/relembra file:%s"
                        % backup_path)
    if retcode != 0:
        warn("Couldn't backup")
        return
    retcode = os.system("/usr/bin/rclone --checksum sync %s acd:relembra-backup-%s" % (backup_path, wda))
    if retcode != 0:
        warn("Couldn't upload backup")


if __name__ == '__main__':
    backup()
