# Recipe for iocStats

inherit epics-module

EPICS_INSTALL_DIR = "/opt/epics/support/iocstats"

SUMMARY = "iocSats recipe"
DESCRIPTION = "Recipe for building EPICS iocStats for the EPICS control system."

LICENSE = "EPICS"
LIC_FILES_CHKSUM = "file://LICENSE;md5=76d18f9132055ed510b481f6f211e0d7"
LICENSE_PATH += "${S}"

SRCREV = "b0a51778f50b97cd9c50d0aa0e03a7b7ee2f3a84"
SRC_URI = "git://github.com/epics-modules/iocStats;protocol=https;branch=master;rev=${SRCREV}"

S = "${WORKDIR}/git"
