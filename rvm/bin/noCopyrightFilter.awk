#
# (C) Copyright IBM Corp. 2001, 2003
#
# $Id$

# @author Peter Sweeney
# @date 11/1/2001
# @modified Steven Augart
# @date June, 2003

## Auxiliary AWK program to help out rvm/bin/findDeviantFiles.  You should
## never run this directly; to discourage people from doing so, this is not an
## executable file.
#
# Find files that don't have a copyright notice.
#
/^\.\/aux\/gnu-classpath-on-mac-osx\.patch$/ { next }

/\/bin\/classpath.stamp/ { next }

/\/_timestamp\/timestamp/ { next }

/\/doc\/userguide\// { next }

/\/regression\/tests\/SPECjbb2000\/SPECjbb./ { next }
/\/regression\/tests\/pseudojbb\/pseudojbb/ {next}
/\/regression\/tests\/pseudojbb\/props/ {next}
/\/regression\/tests\/mauve\/mauve-jikesrvm/ {next}

/\/tests\/javalex\/qb1.lex.ref/ { next }

/\/tools\/bootImageRunner\/VM_0005fInetAddress.h/ { next }
/\/tools\/bootImageWriter\/rvm.security/ { next }
/\/tools\/jburg\/COPYRIGHT/ { next }
/\/src\/tools\/eclipse\/plugin2\/src\/com\/ibm\/jikesrvm\/eclipse\/ui\/jalapeno.jpg/ { next }

/\/ReleaseNotes-/ { next }

/\/TimeLimit.sanity/ { next }
/\/TimeLimit.performance/ { next }

/\.properties$/ { next }
/\.properties\.sample$/ { next }
/README$/ { next }

#
# print everything else
#
/.*/
