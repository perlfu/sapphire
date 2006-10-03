#! /bin/bash
# -*- coding: iso-8859-1 ; mode: shell-script ;-*-
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright � IBM Corp. 2004
#
# $Id$
#
# Expand macros for installing Jikes RVM in /usr
#
# @author Steven Augart
# @date 24 April 2004

[ $# = 2 ] || { echo >&2 "Usage: $0 SRC DEST" ; exit 2 ; }
src="$1"
dest="$2"

#	-e 's,,,'			\
#

sed	-e 's,@USER_MANUAL@,@DOC_DIR@,g'			\
        -e 's,@VERSION@,2.3.3+CVS,g'				\
	-e 's,@DOC_DIR@,/usr/share/doc/jikesrvm,g'		\
	-e 's,@MAN_DIR@,/usr/share/man,g'			\
	-e 's,@BIN_DIR@,/usr/bin,g'				\
	-e 's,@RVM_ROOT@,/usr/share/jikesrvm,'			\
	-e 's,@RVM_BUILD@,/usr/lib/jikesrvm,'			$src > $dest
	
