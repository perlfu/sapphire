#! /usr/bin/env bash
# -*- coding: iso-8859-1 ; mode: shell-script ;-*-
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright � IBM Corp. 2003, 2005
#
# $Id$
#
# Purpose: Autodetect whether:
msg="Do we have a C++ strtold() function?"
#
# @author Steven Augart
# @date 22 October 2003
set -e
echo $msg >> ${LOG}
echo -n "$msg..." >&2

. ${JAL_BUILD}/environment.target

echo "\
/* HAVE_CXX_STRTOLD: Do we have a strtold() function reachable from C++?  
   AIX 5.1 does not declare strtold() in <stdlib.h> unless 
   sizeof (long double) > sizeof (double).
   Note that the C '99 standard requires that function to be present. */"

RUN_ME="${CPLUS} -o ${SCRATCH}/have_cxx_strtold.o -c have_cxx_strtold.C" 
echo $RUN_ME >> ${LOG}
if $RUN_ME >> ${LOG} 2>&1
then
    echo >&2 Yes
    echo "$msg...Yes." >> ${LOG}
    echo "#define HAVE_CXX_STRTOLD 1"
else
    echo "$msg...No" >> ${LOG}
    echo >&2 No
    echo "#undef HAVE_CXX_STRTOLD"
fi
echo ""
echo ""
