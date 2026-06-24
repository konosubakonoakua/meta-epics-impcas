inherit epics-module

SUMMARY = "EPICS Portable Channel Access Server (pcas) recipe"
DESCRIPTION = "Recipe for building the standalone Portable Channel Access Server module for the EPICS control system. Provides a C++ library for creating EPICS Channel Access servers."

# NOTE: EPICS base >= 7.x bundles pcas internally (modules/pcas). This standalone
# recipe from epics-modules/pcas may conflict with the bundled version. If your
# IOC only needs the pcas library, the version shipped with epics-base is typically
# sufficient. Use this recipe only if you need features from the standalone repo.
LICENSE = "EPICS"
LIC_FILES_CHKSUM = "file://LICENSE;md5=2eeea17a15fc6ba8501fdcec09b854dc"
LICENSE_PATH += "${S}"
NO_GENERIC_LICENSE[EPICS] = "LICENSE"

SRCREV = "e075fd450ab9a66bbc044eaa4c2035d3d26d9651"
SRC_URI = "git://github.com/epics-modules/pcas;protocol=https;branch=master;rev=${SRCREV}"

S = "${WORKDIR}/git"
