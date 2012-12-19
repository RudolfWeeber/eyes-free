# Copyright 2012 Google Inc.
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

# This is a replacement for brltty's braille.mk, defining some make variables
# that the driver Makefiles depend on.

# In brltty, this is the object file extension.  We define this to be a unique
# placeholder per driver so that the make rules in the individual makefiles
# won't interfere with the android build system or each other.
O := NOTUSED$(DRIVER_CODE)
