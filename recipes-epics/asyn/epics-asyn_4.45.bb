inherit epics-module

SUMMARY = "EPICS Asyn module recipe"
DESCRIPTION = "Recipe for building the Asyn module for the EPICS control system, with support for VXI-11, GPIB, and other industrial communication protocols."

LICENSE = "synApps"
LIC_FILES_CHKSUM = "file://LICENSE;md5=9f42f43716fb1d5e8498617125cb3c21"
LICENSE_PATH += "${S}"
NO_GENERIC_LICENSE[synApps] = "LICENSE"

SRCREV = "d55786e0508b1f8244cfae943ebc5fffccfb7590"
SRC_URI = "git://github.com/epics-modules/asyn;protocol=https;branch=master;rev=${SRCREV}"

# libtirpc is required by the vxi11 sub-module: glibc >= 2.32 no longer ships SunRPC
# headers, so we must pull in the standalone tirpc implementation.
# python3-native provides the `python3` → `python` symlink needed by test app scripts
# whose shebang lines reference bare `python`.
DEPENDS += "libtirpc python3-native"

S = "${WORKDIR}/git"

# Create a `python` → `python3` symlink in the build PATH so that test scripts
# using `#!/usr/bin/env python` don't fail with "python: No such file or directory".
do_compile:prepend() {
    mkdir -p "${B}/.bin"
    ln -sf "$(which python3)" "${B}/.bin/python"
    export PATH="${B}/.bin:${PATH}"
}

python do_configure() {
    # Generate RELEASE.local to resolve EPICS_BASE and other module dependencies
    epics.generate_release_local(d)

    # Configure Asyn to use the Yocto sysroot-provided tirpc headers and libraries.
    # TIRPC=YES enables vxi11 support; explicit INCLUDES/LIBS ensure cross-compilation
    # uses the target sysroot instead of host paths.
    epics.generate_config_site(d, {
        "TIRPC": "YES",
        "TIRPC_INCLUDES": "-I${STAGING_INCDIR}/tirpc",
        "TIRPC_LIBS": "-L${STAGING_LIBDIR} -ltirpc",
    })
}

# Fallback flags for sub-directories that do not fully inherit CONFIG_SITE TIRPC settings.
# TARGET_CFLAGS / TARGET_LDFLAGS end up in USR_CFLAGS / USR_LDFLAGS, which the
# EPICS build system places inside its -Wl,-Bstatic … -Wl,-Bdynamic wrapping.
# libtirpc only ships as a shared library in Yocto, so we must force dynamic
# linking for it with -Wl,-Bdynamic / -Wl,-Bstatic guards.
TARGET_CFLAGS:append = " -I${STAGING_INCDIR}/tirpc"
TARGET_LDFLAGS:append = " -L${STAGING_LIBDIR} -Wl,-Bdynamic -ltirpc -Wl,-Bstatic"

do_install() {
    # Install all production artifacts
    make -j${BB_NUMBER_THREADS} install

    # Remove test binaries that are not needed on the target (only if they exist)
    rm -rf ${D}/opt/epics/${MODNAME}/bin

    # Remove test static libraries to reduce image footprint (only if they exist)
    rm -f ${D}/opt/epics/${MODNAME}/lib/linux-${TARGET_ARCH}/libtest*.a
    rm -f ${D}/opt/epics/${MODNAME}/lib/linux-${TARGET_ARCH}/libdevTestGpib.a
}
