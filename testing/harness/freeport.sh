#!/bin/sh
#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id$
#
# @author Julian Dolby

if [ $# != 0 ]; then
    SKIP=$1
else
    SKIP=0
fi

if [ `uname` = Linux ]; then
    USED_PORTS=`netstat -n --protocol=inet | awk '{ print $4 }' | awk -F: '{ print $2 }' | sort -n | uniq`
else
    USED_PORTS=`netstat -an -f inet | awk '{ print $4 }' | awk -F. '/\*\./ { print $2} /^[0-9]/ { print $5 }' | sort -n | uniq`
fi

PORT=5000

while [ $SKIP != -1 ]; do
  PORT=`expr $PORT + 1`
  SKIP=`expr $SKIP - 1`
  while ( echo $USED_PORTS | fgrep -q $PORT ); do
    PORT=`expr $PORT + 1`
  done
done

echo $PORT
