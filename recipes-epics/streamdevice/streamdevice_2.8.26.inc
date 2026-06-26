inherit epics-module

EPICS_INSTALL_DIR = "/opt/epics/support/streamdevice"

SUMMARY = "StreamDevice recipe"
DESCRIPTION = "Recipe for building StreamDevice for the EPICS control system."

LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=1ebbd3e34237af26da5dc08a4e440464"
LICENSE_PATH += "${S}"

SRCREV = "668d1d525509604ab4ccd7382022dfb469c99841"
SRC_URI = "git://github.com/paulscherrerinstitute/StreamDevice;protocol=https;branch=master;rev=${SRCREV}"

S = "${WORKDIR}/git"

EPICS_DEPENDS += "epics-asyn epics-calc epics-sscan"
DEPENDS += "${EPICS_DEPENDS} libpcre"

do_configure[postfuncs] += "set_pcre"
do_configure[postfuncs] += "set_tirpc"
