#
# (C) Copyright IBM Corp. 2001, 2003
#
# $Id$

# @author Peter Sweeney
# @date 11/1/2001
# @modified Steven Augart
# @date 6/9/2003

## Auxiliary AWK program to help out rvm/bin/findDeviantFiles.  You should
## never run this directly; to discourage people from doing so, this is not an
## executable file.
#
# Files that don't have a CVS/RCS $Id tag.
#
/\/bin\/classpath\.stamp/ { next }

/\/_timestamp\/timestamp/ { next }

/\/doc\/userguide\// { next }

/\/regression\/tests\/javalex\/qb1\.lex\.ref/ {next}
/\/regression\/tests\/SPECjbb2000\/SPECjbb./ { next }
/\/regression\/tests\/pseudojbb\/pseudojbb/ {next}
/\/regression\/tests\/pseudojbb\/props/ {next}
/\/regression\/tests\/mauve\/mauve-jikesrvm/ {next}

/\/src\/tools\/preprocessor\/testFancy\.preprocessor/ { next }
/\/src\/tools\/preprocessor\/testSimple\.preprocessor/ { next }
/\/src\/tools\/eclipse\/plugin2\/src\/com\/ibm\/jikesrvm\/eclipse\/ui\/jalapeno.jpg/ { next }

/\/tools\/bootImageRunner\/com_ibm_JikesRVM_VM_0005fProcess.h/ { next }
/\/tools\/bootImageWriter\/rvm\.security/ { next }
/\/tools\/jburg\/COPYRIGHT/ { next }

/\/ReleaseNotes-/ { next }

/\/TimeLimit\.sanity/ { next }
/\/TimeLimit\.performance/ { next }

/\.properties$/ { next }
/\.properties\.sample$/ { next }
/README$/ { next }

/\/LICENSE/ { next }
/\/src\/tools\/install\/macros.txt/ { next }

#
# print everything else
#
/.*/
