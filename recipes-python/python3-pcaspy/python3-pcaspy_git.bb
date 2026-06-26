SUMMARY = "pcaspy: Python bindings for the EPICS Portable Channel Access Server"
DESCRIPTION = "Python bindings for the EPICS PCAS library, allowing EPICS Channel Access servers to be written in Python. Developed by the Paul Scherrer Institute (PSI)."

# LICENSE — verify md5 on first build; PSI projects typically use GPL-3.0 or BSD.
# The hash below is a placeholder. BitBake will report the correct md5 on first fetch.
LICENSE = "GPL-3.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=d32239bcb673463ab874e80d47fae504"
LICENSE_PATH += "${S}"

SRCREV = "59157d51f1913491f85d59b3141cf97116015822"
SRC_URI = "git://github.com/paulscherrerinstitute/pcaspy;protocol=https;branch=master;rev=${SRCREV}"

S = "${WORKDIR}/git"

inherit setuptools3

# Build dependencies: EPICS base + PCAS module + native Python tooling
DEPENDS += "\
    epics-base \
    epics-pcas \
    python3-setuptools-native \
    python3-wheel-native \
    python3-numpy-native \
"

# Runtime dependencies
RDEPENDS:${PN} += "\
    python3-numpy \
    python3-core \
    epics-pcas \
"

# Point pcaspy's build at the PCAS module in the recipe sysroot.
# pcaspy setup.py checks the PCAS env var; without it, it falls back to
# <EPICS_BASE>/modules/pcas (which won't exist in our standalone layout).
export PCAS = "${RECIPE_SYSROOT}/opt/epics/support/pcas"

# Also ensure EPICS base headers and libs are visible.
export EPICS_BASE = "${RECIPE_SYSROOT}/opt/epics/base"

do_compile:prepend() {
    # pcaspy's setup.py may shell out to EPICS build tools — expose the
    # native epics-base host-bin directory on PATH.
    export PATH="${RECIPE_SYSROOT_NATIVE}/opt/epics/base/bin/linux-${BUILD_ARCH}:${PATH}"
}

do_install:append() {
    # Emit a profile.d snippet so LD_LIBRARY_PATH includes the PCAS shared
    # library at runtime on the target.
    install -d "${D}${sysconfdir}/profile.d"
    cat > "${D}${sysconfdir}/profile.d/pcaspy.sh" <<EOF
export LD_LIBRARY_PATH=/opt/epics/support/pcas/lib/linux-\${EPICS_HOST_ARCH}:/opt/epics/base/lib/linux-\${EPICS_HOST_ARCH}:\${LD_LIBRARY_PATH}
EOF
}

FILES:${PN} += "${sysconfdir}/profile.d"

BBCLASSEXTEND = "native nativesdk"
