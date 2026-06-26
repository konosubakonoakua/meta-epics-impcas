inherit epics-component

SUMMARY = "EPICS base recipe"
DESCRIPTION = "Recipe for building EPICS base, the core component of the EPICS control system."

LICENSE = "EPICS"
LIC_FILES_CHKSUM = "file://LICENSE;md5=2eeea17a15fc6ba8501fdcec09b854dc"
LICENSE_PATH += "${S}"

BBCLASSEXTEND = "native nativesdk"

# Force install fragment to "base" regardless of PN
MODNAME = "base"

SRCREV = "bf11a0c31c919ba85ba2e23b72bcf0b5f9f62e77"
SRC_URI = "gitsm://github.com/epics-base/epics-base;protocol=https;branch=7.0;rev=${SRCREV}"

SRC_URI += " \
            file://0001-host-build-option.patch \
           "

DEPENDS += " readline"

RDEPENDS:${PN} += " bash perl"
RDEPENDS:${PN}:class-target += " epics-env readline"

S = "${WORKDIR}/git"

# Some downstream packages, such as pyepics, need shared libs.
EPICS_ENABLE_SHARED_LIBS = "1"

do_configure() {
    install -d "${D}/opt/epics/${MODNAME}"

    #############################################################
    # configure/CONFIG_SITE.local
    #############################################################

    F="${S}/configure/CONFIG_SITE.local"

    # Enable/disable static and shared libs
    if [ "${EPICS_ENABLE_STATIC_LIBS}" = "1" ]; then
        echo "STATIC_BUILD=YES" > "${F}"
    else
        echo "STATIC_BUILD=NO" > "${F}"
    fi

    if [ "${EPICS_ENABLE_SHARED_LIBS}" = "1" ]; then
        echo "SHARED_LIBRARIES=YES" >> "${F}"
    else
        echo "SHARED_LIBRARIES=NO" >> "${F}"
    fi

    echo "CROSS_COMPILER_TARGET_ARCHS=linux-${TARGET_ARCH}" >> "${F}"
    # Use $ORIGIN for RPATH so we dont need to set LD_LIBRARY_PATH
    echo 'LINKER_USE_RPATH=ORIGIN' >> "${F}"

    # Point at /opt/epics; better to do this here to avoid bad file paths
    echo "FINAL_LOCATION=/opt/epics/${MODNAME}" >> "${F}"

    # Build only for target architecture(s), not for the build host
    echo "HOST_BUILD=NO" >> "${F}"

    #############################################################
    # configure/CONFIG_SITE.Common.linux-${BUILD_ARCH}
    #############################################################

    # Force C99; GCC 15 switches the default C standard to C23, which breaks a *lot* of things.
    echo 'OP_SYS_CFLAGS += -std=c99' >> "${S}/configure/CONFIG_SITE.Common.linux-${BUILD_ARCH}"

    if [ ! -f "${S}/configure/os/CONFIG_SITE.linux-${BUILD_ARCH}.linux-${TARGET_ARCH}" ]; then
        echo "Target architecture linux-${TARGET_ARCH} is unsupported by EPICS base. Cannot continue"
        exit 1
    fi

    #############################################################
    # configure/CONFIG_SITE.linux-${BUILD_ARCH}.linux-${BUILD_ARCH}
    #############################################################

    # This file can be safely overwritten
    F="${S}/configure/os/CONFIG_SITE.linux-${BUILD_ARCH}.linux-${TARGET_ARCH}"

    # Set compile tools (with a bit of an ugly workaround for flags Yocto injects into $CC/$CXX/etc.)
    echo "CC=$(echo ${CC} | cut -d ' ' -f1)" > "${F}"
    echo "CXX=$(echo ${CXX} | cut -d ' ' -f1)" >> "${F}"
    echo "CCC=$(echo ${CXX} | cut -d ' ' -f1)" >> "${F}"
    # -r is ordinarily appended by EPICS base, but not here because we overrode LD directly
    echo "LD=$(echo ${LD} | cut -d ' ' -f1) -r" >> "${F}"
    # ...same situation with -rc
    echo "AR=${AR} -rc" >> "${F}"
    echo "RANLIB=${RANLIB}" >> "${F}"

    # Ensure we use GNU hash style, because that's what Yocto expects...
    # Can't pull in the entire BUILD_LDFLAGS var here, that needs to be done on the command line
    echo "USR_LDFLAGS+=-Wl,--hash-style=gnu" >> "${F}"

    #############################################################
    # configure/CONFIG_SITE_ENV
    #############################################################

    # Tell EPICS base to call abort() on assertion failure rather than suspend the executing thread.
    sed -iE 's/EPICS_ABORT_ON_ASSERT=.*/EPICS_ABORT_ON_ASSERT=YES/g' "${S}/configure/CONFIG_SITE_ENV"
}

do_compile() {
    # Bring in the env flags. These must be supplied on the command line *only* because they
    # may contain package specific settings (i.e. --sysroot=). Putting them in a CONFIG_SITE.Common file
    # will result in them being passed down to other EPICS packages.
    # Build base with the build host and target flags
    make -j${BB_NUMBER_THREADS} \
        USR_CFLAGS="${BUILD_CFLAGS}" \
        USR_CXXFLAGS="${BUILD_CXXFLAGS}" \
        USR_LDFLAGS="${BUILD_LDFLAGS}" \
        install.linux-${BUILD_ARCH}
}

do_compile:append:class-target() {
    # Discover the actual ELF dynamic linker provided by the target's glibc.
    # On ARM the toolchain may default to ld-linux.so.3 (soft-float) while the
    # rootfs only ships ld-linux-armhf.so.3 (hard-float).  Inject the real path
    # at link time so the resulting binaries can execute on the target.
    REAL_LD=$(basename $(ls ${STAGING_DIR_HOST}${base_libdir}/ld-linux* 2>/dev/null | head -1))
    LD_FLAG=""
    [ -n "$REAL_LD" ] && LD_FLAG="-Wl,--dynamic-linker=${base_libdir}/$REAL_LD"

    # Extract flags from $CC/$CXX/$LD and put them into the USR_ variables
    make -j${BB_NUMBER_THREADS} \
        USR_CFLAGS="$(echo "${CC}" | cut -d ' ' -f 2-) ${CFLAGS}" \
        USR_CXXFLAGS="$(echo "${CXX}" | cut -d ' ' -f 2-) ${CXXFLAGS}" \
        USR_LDFLAGS="$LD_FLAG $(echo "${LD}" | cut -d ' ' -f 2-) ${LDFLAGS}" \
        install.linux-${TARGET_ARCH}
}

do_install() {
    install_dir="${D}/opt/epics/${MODNAME}"

    # Install built or otherwise useful EPICS files
    # Arch specific files are handled in do_install:append functions below
    for subdir in configure cfg db dbd include templates; do
        install -d ${install_dir}/$subdir
        cp -RP --preserve=mode,links -v ${S}/$subdir/* ${install_dir}/$subdir
    done

    # Install EPICS Perl tools
    install -d ${install_dir}/src/tools
    cp -RP --preserve=mode,links -v ${S}/src/tools/* ${install_dir}/src/tools

    # Install more EPICS Perl tools
    # Have to be more specific here rather than copying _everything_ because
    # of libCap5.so that gets generated for the build host under this directory
    for subdir in DBD EPICS Pod URI; do
        install -d ${install_dir}/lib/perl/${subdir}
        cp -RP --preserve=mode,links -v ${S}/lib/perl/${subdir}/* ${install_dir}/lib/perl/${subdir}
    done

    for fname in CA.pm DBD.pm EpicsHostArch.pl; do
        install -m 0755 ${S}/lib/perl/$fname ${install_dir}/lib/perl/$fname
    done

    # Regardless of target or native build, the TARGET_ARCH is correct
    install_bin="${D}/opt/epics/${MODNAME}/bin/linux-${TARGET_ARCH}"
    install -d ${install_bin}
    cp -RP --preserve=mode,links -v ${S}/bin/linux-${TARGET_ARCH}/* ${install_bin}

    install_lib="${D}/opt/epics/${MODNAME}/lib/linux-${TARGET_ARCH}"
    install -d ${install_lib}
    cp -RP --preserve=mode,links -v ${S}/lib/linux-${TARGET_ARCH}/* ${install_lib}

    # EPICS builds arch-independent scripts (Perl *.pl, Python *.py) under the
    # BUILD_ARCH bin directory.  Copy them into the TARGET_ARCH bin so on-target
    # IOC development works out of the box.
    for ext in pl py; do
        for src in ${S}/bin/linux-${BUILD_ARCH}/*.$ext; do
            [ -f "$src" ] || continue
            install -m 0755 "$src" "${install_bin}/"
        done
    done

    # Add the EPICS libraries to the LD_LIBRARY_PATH. Certain downstream packages need this (i.e. pyepics)
    install -d "${D}${sysconfdir}/profile.d"
    echo "export LD_LIBRARY_PATH=/opt/epics/base/lib/linux-${TARGET_ARCH}:\${LD_LIBRARY_PATH}" > "${D}${sysconfdir}/profile.d/epics.sh"
    echo "export PERL5LIB=/opt/epics/base/lib/perl" >> "${D}${sysconfdir}/profile.d/epics.sh"

    # Fix shebangs: EPICS Perl scripts may use "#!/bin/env perl" which breaks
    # RPM packaging on usrmerge systems (/bin is a symlink to /usr/bin, so
    # /bin/env is not a path any package directly provides as a dependency).
    find "${D}" -type f -exec grep -l '^#!/bin/env' {} \; 2>/dev/null | while read f; do
        sed -i 's|^#!/bin/env |#!/usr/bin/env |' "$f"
    done

    # Erase cross-compilation markers from the installed CONFIG_SITE.local so
    # the target board can do self-hosted IOC compilation.  Without this,
    # CROSS_COMPILER_TARGET_ARCHS=linux-arm tells EPICS that linux-arm is
    # always a cross target, and HOST_BUILD=NO prevents native tool detection.
    sed -i 's/^CROSS_COMPILER_TARGET_ARCHS=.*/CROSS_COMPILER_TARGET_ARCHS=/' \
        "${install_dir}/configure/CONFIG_SITE.local"
    sed -i '/^HOST_BUILD=NO/d' \
        "${install_dir}/configure/CONFIG_SITE.local"

    # libCom links against GNU readline (iocsh, epicsReadline).  On the
    # target the shared libreadline is available but EPICS' static linking
    # wrapper ignores it.  Inject a global USR_LDFLAGS guard so every
    # self-hosted IOC automatically picks up -lreadline at link time.
    echo 'USR_LDFLAGS += -Wl,-Bdynamic -lreadline -Wl,-Bstatic' \
        >> "${install_dir}/configure/CONFIG_SITE.local"

    # Remove all tempoary directories that came over as we copied
    find "${install_dir}" -type d -name "O.*" -exec rm -rf {} +
}

do_install:append:class-native() {
    native_bin="${D}${STAGING_DIR_NATIVE}/opt/epics/${MODNAME}/bin/linux-${BUILD_ARCH}"
    install -d ${native_bin}
    cp -RP --preserve=mode,links -v ${S}/bin/linux-${BUILD_ARCH}/* ${native_bin}

    native_lib="${D}${STAGING_DIR_NATIVE}/opt/epics/${MODNAME}/lib/linux-${BUILD_ARCH}"
    install -d ${native_lib}
    cp -RP --preserve=mode,links -v ${S}/lib/linux-${BUILD_ARCH}/* ${native_lib}
}

do_install:append:class-target() {
    # Symlink commonly used EPICS CLI tools
    mkdir -p "${D}/usr/local/bin"
    for prog in caput caget cainfo camonitor catime caRepeater pvcall pvget pvinfo pvlist pvmonitor pvput
    do
        ln -s /opt/epics/${MODNAME}/bin/linux-${TARGET_ARCH}/$prog "${D}/usr/local/bin/$prog"
    done

    # Sanitize TOOLCHAIN files. These contain absolute paths in comments
    for d in $(find ${D}/opt/epics/${MODNAME} -name "TOOLCHAIN*"); do
        sed -i "/^#/d" "${d}"
    done

    # Install the generated caRepeater.service
    mkdir -p "${D}/etc/systemd/system/multi-user.target.wants"
    cp "${D}/opt/epics/${MODNAME}/bin/linux-${TARGET_ARCH}/caRepeater.service" "${D}/etc/systemd/system/caRepeater.service"
    chmod 644 "${D}/etc/systemd/system/caRepeater.service"
    ln -s "/etc/systemd/system/caRepeater.service" "${D}/etc/systemd/system/multi-user.target.wants/caRepeater.service"
}

FILES:${PN}:append:class-target = " /usr/local/bin"
FILES:${PN}:append:class-target = " /etc/systemd/system"
FILES:${PN}:append:class-target = " ${sysconfdir}"
