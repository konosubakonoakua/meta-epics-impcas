#
# Defines common methods and rules for EPICS module recipes
#

# Default module name is the package name
MODNAME ?= "${PN}"

# Absolute install directory on target.  Every recipe that needs a non-default
# location must override this variable explicitly.
#   epics-base               → /opt/epics/base
#   epics-<stem> modules     → /opt/epics/support/<stem>  (default: /opt/epics/support/${PN})
EPICS_INSTALL_DIR ?= "/opt/epics/support/${PN}"

# Add your EPICS dependencies to this variable
EPICS_DEPENDS = ""

# Enable static libs
EPICS_ENABLE_STATIC_LIBS ?= "1"

# Disable shared libs (by default)
EPICS_ENABLE_SHARED_LIBS ?= "0"

# Disable host packages by default
ENABLE_HOST_PACKAGE ?= "0"

# Disable other checks that are incompatible with the EPICS build style
# - staticdev is incompatible with packages that include static libs in the base package.
# - file-rdeps is incompatible with combined sdk/target packages, where we have a mix of host tools and target bins.
# - arch is incompatible with -native packages, since they contain binaries for the build host OS
# - buildpaths is to suppress complaints about the build host (class-native)
#   package containing references to TMPDIR, which is fine because these are
#   not installed on the target, only used in compilation of the target package
# - dev-so prevents the build erroring out from symlinks like libca.so -> libco.so.1.2.3. you're "supposed" to put these in a dev package.
INSANE_SKIP:class-target = "file-rdeps staticdev dev-so"
INSANE_SKIP:class-native = "file-rdeps staticdev arch buildpaths"

# Sucks, but we have to compile the package for x86_64 hosts too in case some tools run during the build process
COMPATIBLE_HOST = "(x86_64|aarch64|arm).*linux.*"

python do_configure() {
    print(f'host arch={epics.host_arch(d)}')
    print(f'epics target={epics.target_arch(d)}')

    print(f'RECIPE_SYSROOT={d.getVar("RECIPE_SYSROOT")}')

    # Generate a RELEASE.local handling all dependencies
    epics.generate_release_local(d)

    # Retarget build products too
    epics.generate_config_site(d)
}

do_compile() {
    oe_runmake build
}

# We can only package files in a single package; this exposes perl tools that
# are necessary for both host and target builds, which are packaged with the
# target, to be available to the host build as well.
do_compile:prepend() {
    export PERL5LIB="${RECIPE_SYSROOT}/opt/epics/base/lib/perl"
}

do_install() {
    oe_runmake install

    inst="${D}${EPICS_INSTALL_DIR}"

    # Copy top level Makefile to build on target if necessary
    install -m 0644 ${S}/Makefile "${D}/opt/epics/${MODNAME}"

    # Copy iocBoot and cpuBoot directories
    for d in iocBoot cpuBoot; do
        if [ -d $d ]; then
            cp -rfv $d "${inst}/$d"
        fi
    done

    # Get streamDevice protocol files from typical directories
    if [ -d protocol ]; then
        cp -rfv protocol "${inst}/protocol"
    fi

    if [ -d app/srcProtocol ]; then
        install -d "${inst}/app"
        cp -rfv app/srcProtocol "${inst}/app/srcProtocol"
    fi

    # Sanitize TOOLCHAIN files. These contain absolute paths in comments
    for d in $(find "${inst}" -name "TOOLCHAIN*"); do
        sed -i "/^#/d" "${d}"
    done
}

# See comment above; need to do this before tasks with compilation
do_install:prepend() {
    export PERL5LIB="${RECIPE_SYSROOT}/opt/epics/base/lib/perl"
}

# Common directories to install for both native and target pkgs
ALL_FILES += "${EPICS_INSTALL_DIR}/Makefile"
ALL_FILES += "${EPICS_INSTALL_DIR}/db"
ALL_FILES += "${EPICS_INSTALL_DIR}/dbd"
ALL_FILES += "${EPICS_INSTALL_DIR}/include"
ALL_FILES += "${EPICS_INSTALL_DIR}/configure"
ALL_FILES += "${EPICS_INSTALL_DIR}/cfg"
ALL_FILES += "${EPICS_INSTALL_DIR}/templates"
ALL_FILES += "${EPICS_INSTALL_DIR}/doc"
ALL_FILES += "${EPICS_INSTALL_DIR}/html"
ALL_FILES += "${EPICS_INSTALL_DIR}/iocBoot"
ALL_FILES += "${EPICS_INSTALL_DIR}/cpuBoot"
ALL_FILES += "${EPICS_INSTALL_DIR}/autosave"
ALL_FILES += "${EPICS_INSTALL_DIR}/display"
ALL_FILES += "${EPICS_INSTALL_DIR}/screens"
ALL_FILES += "${EPICS_INSTALL_DIR}/archive"
ALL_FILES += "${EPICS_INSTALL_DIR}/iocsh"
ALL_FILES += "${EPICS_INSTALL_DIR}/src/tools"
ALL_FILES += "${EPICS_INSTALL_DIR}/lib/perl"
ALL_FILES += "${EPICS_INSTALL_DIR}/edl"
ALL_FILES += "${EPICS_INSTALL_DIR}/protocol"
ALL_FILES += "${EPICS_INSTALL_DIR}/app/srcProtocol"


# Build a package for the build host
PACKAGES += "${PN}-native"

# Build a package for the target system
FILES:${PN}:append:class-target = " ${ALL_FILES}"
FILES:${PN}:append:class-target = " ${EPICS_INSTALL_DIR}/bin/linux-${TARGET_ARCH}"
FILES:${PN}:append:class-target = " ${EPICS_INSTALL_DIR}/lib/linux-${TARGET_ARCH}"

FILES:${PN}:append:class-native = " ${EPICS_INSTALL_DIR}/bin/linux-${BUILD_ARCH}"
FILES:${PN}:append:class-native = " ${EPICS_INSTALL_DIR}/lib/linux-${BUILD_ARCH}"

# Expose this package in the sysroot
SYSROOT_DIRS:append:class-target = " ${EPICS_INSTALL_DIR}/bin/linux-${TARGET_ARCH}"
SYSROOT_DIRS:append:class-target = " ${EPICS_INSTALL_DIR}/lib/linux-${TARGET_ARCH}"
SYSROOT_DIRS:append:class-target = " ${ALL_FILES}"

SYSROOT_DIRS_NATIVE:append = " ${STAGING_DIR_NATIVE}${EPICS_INSTALL_DIR}/bin/linux-${BUILD_ARCH}"
SYSROOT_DIRS_NATIVE:append = " ${STAGING_DIR_NATIVE}${EPICS_INSTALL_DIR}/lib/linux-${BUILD_ARCH}"
