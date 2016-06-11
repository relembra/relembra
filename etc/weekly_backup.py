#!/usr/bin/env python

from datetime import datetime
import os.path
import sys


def day_abbrev():
    return datetime.now().strftime('%Y%m%d').lower()

def warn(txt):
    print >> sys.stderr, "WARNING: %s!" % txt
    os.system("""echo "At %s: %s" | mail -s "WARNING: %s" euccastro@gmail.com""" % (datetime.now(), txt, txt))

def backup():
    backup_root = "/opt/datomic-free-0.9.5372/bak"
    tar_name = "%s.tar.xz" % day_abbrev()
    os.chdir(backup_root)
    retcode = os.system("tar -cJf %s mon" % tar_name)
    if retcode != 0:
        warn("Couldn't tar")
        return
    retcode = os.system("/usr/bin/rclone --checksum copy %s acd:relembra-weekly-backups" % tar_name)
    if retcode != 0:
        warn("Couldn't upload backup")
    os.unlink(tar_name)


if __name__ == '__main__':
    backup()
