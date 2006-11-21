#
# This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
# The Jikes RVM project is distributed under the Common Public License (CPL).
# A copy of the license is included in the distribution, and is also
# available at http://www.opensource.org/licenses/cpl1.0.php
#
# (C) Copyright IBM Corp. 2001
#
# $Id: summarize.awk 10860 2006-10-03 19:25:15Z dgrove-oss $
#
# @author Julian Dolby

BEGIN {
    no = "no";
    yes = "yes";
    in_details = no;
}

/Valid run, Score is/ { print "Bottom Line: " $0 }

/\* Details of Runs/ { in_details = yes }

/[0-9]*[:whitespace:]*[0-9]*[:whitespace:]*[0-9.]*[:whitespace:]*[0-9.]*[:whitespace:]*[0-9.<]*[:whitespace:]*[0-9.<]*[:whitespace:]*new_order/ {
    if ($1 == 1)
      print "score for", $1, "warehouse is", $2;
    else
      print "score for", $1, "warehouses is", $2;
}

    
