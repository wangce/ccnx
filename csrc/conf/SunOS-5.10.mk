# conf/SunOS-5.10.mk
# 
# Part of the CCNx distribution.
#
# Copyright (C) 2009 Palo Alto Research Center, Inc.
#
# This work is free software; you can redistribute it and/or modify it under
# the terms of the GNU General Public License version 2 as published by the
# Free Software Foundation.
# This work is distributed in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
# FOR A PARTICULAR PURPOSE.
#
MORE_LDLIBS = -L/usr/local/ssl/lib -R/usr/local/ssl/lib -L/usr/sfw/lib -R/usr/local/ssl/lib -lmtmalloc -lnsl -lsocket
CPREFLAGS = -I../include -I/usr/local/ssl/include -I/usr/sfw/include
