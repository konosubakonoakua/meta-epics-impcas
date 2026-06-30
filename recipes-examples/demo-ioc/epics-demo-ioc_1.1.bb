inherit epics-ioc-systemd

EPICS_INSTALL_DIR = "/opt/epics/ioc/demo-ioc"

SUMMARY = "EPICS Demo IOC recipe"
DESCRIPTION = "Recipe for building a simple demo IOC for EPICS"

LICENSE = "BSD-3-Clause"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d531561ed074c1f7474bc9a1593bf906"

SRCREV = "56353a6a41e8dd3094e76c8f18671a7d5f8e1184"
SRC_URI = "git://github.com/JJL772/epics-yocto-demo-ioc;protocol=https;branch=main;rev=${SRCREV}"

S = "${WORKDIR}/git"

EPICS_DEPENDS += "epics-linstat"
DEPENDS += "${EPICS_DEPENDS}"

# Name of the IOC application (the file in bin/<tarch>/)
IOC_APP_NAME = "systemMonitor"

# Path to the iocBoot directory, where st.cmd lives
IOC_PATH = "iocBoot/sioc-yocto-demo"

# Additional vars to append to the envPaths.
# EPICS_IOC_PV will be set to the value of ${EPICS_IOC_PV_ENV}
IOC_ENV += "EPICS_IOC_PV"

# PV prefix for this IOC. This will be emitted into envPaths.
EPICS_IOC_PV_ENV ?= "SYS:DEMO"
