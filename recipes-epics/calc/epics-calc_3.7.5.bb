inherit epics-module

EPICS_INSTALL_DIR = "/opt/epics/support/calc"

SUMMARY = "Calc recipe"
DESCRIPTION = "Recipe for building Calc for the EPICS control system."

LICENSE = "synApps"
LIC_FILES_CHKSUM = "file://LICENSE;md5=a2c259c010f2152379d7769be894bf4a"
LICENSE_PATH += "${S}"
NO_GENERIC_LICENSE[synApps] = "LICENSE"

SRCREV = "2f5b175f260bc3fe35bc25a3f6c204e9d6f628c9"
SRC_URI = "git://github.com/epics-modules/calc;protocol=https;branch=master;rev=${SRCREV}"

DEPENDS += "${EPICS_DEPENDS}"

# We decide not to build with SSCAN here; this can be changed with a bbappend
disable_sscan () {
    echo "SSCAN=" >> "${S}/configure/RELEASE.local"
}

do_configure[postfuncs] += "disable_sscan"

S = "${WORKDIR}/git"
