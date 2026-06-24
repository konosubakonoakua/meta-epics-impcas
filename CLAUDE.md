# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`meta-epics` is a Yocto/OpenEmbedded meta-layer providing recipes and classes for building EPICS control system components. Maintained by SLAC National Accelerator Laboratory (PCDS).

## BitBake class hierarchy

Four classes in `classes/` form an inheritance chain:

- **`epics-component.bbclass`** — Base class for any EPICS-built package. Handles `do_configure` (generates `RELEASE.local` and `CONFIG_SITE.*` via the Python library), `do_compile` (`oe_runmake build`), and `do_install` (`oe_runmake install`). Creates both `${PN}` (target) and `${PN}-native` (build host) packages. EPICS artefacts install to `/opt/epics/${MODNAME}/`.
- **`epics-functions.bbclass`** — Utility shell functions (`set_pcre`, `set_tirpc`, `set_areadetector`, `unset_busy`, `unset_seq`, `unset_ipac`) and the Python `update_env_paths()` postfunc used by IOCs.
- **`epics-module.bbclass`** — Inherits `epics-component` + `epics-functions`. Adds automatic `DEPENDS` on `epics-base` and `epics-base-native`. Sanitizes `*.local` and `envPaths` files in `do_install:append` (strips `${RECIPE_SYSROOT}`). **This is the right inherit for any EPICS support module recipe.**
- **`epics-ioc-systemd.bbclass`** — Inherits `epics-module`. Adds a systemd unit running the IOC via `procServ` (telnet on `${PS_PORT}`). IOCs set `IOC_PATH`, `IOC_APP_NAME`, `IOC_ENV`, and `IOC_ST_CMD` to control the generated unit and `ioc-start.sh` wrapper.

## Python helper library

`python/epics/__init__.py` is used by the classes at build time (via `addpylib` in `layer.conf`). Key functions:

- `target_arch(d)` / `host_arch(d)` — EPICS-format arch strings (e.g. `linux-aarch64`).
- `generate_release_local(d, extra={})` — Writes `configure/RELEASE.local` with `EPICS_BASE` and all entries from `EPICS_DEPENDS` (auto-converts `epics-foo-bar` → `FOO_BAR`).
- `generate_config_site(d, extra={})` — Writes `CONFIG_SITE.local`, `CONFIG_SITE.<host>.Common`, `CONFIG_SITE.Common.<target>`, and `CONFIG_SITE.Common.<host>` with install paths, cross-compiler flags, and static/shared lib settings.

## Recipe conventions

### EPICS module recipe pattern

Inherit `epics-module`, pin a git `SRCREV`, and set `EPICS_DEPENDS` for EPICS-level deps + standard `DEPENDS` for Yocto-level deps:

```bitbake
inherit epics-module
SRCREV = "<commit>"
SRC_URI = "git://github.com/...;protocol=https;branch=master;rev=${SRCREV}"
S = "${WORKDIR}/git"
EPICS_DEPENDS += "epics-asyn epics-calc"
DEPENDS += "${EPICS_DEPENDS} libpcre"
```

Override `do_configure` (as a Python function calling `epics.generate_release_local(d)` and `epics.generate_config_site(d, {...})`) only when the module needs extra `CONFIG_SITE` variables beyond the defaults.

### IOC recipe pattern

Inherit `epics-ioc-systemd` and set the IOC-specific variables:

```bitbake
inherit epics-ioc-systemd
IOC_APP_NAME = "myIocApp"
IOC_PATH = "iocBoot/sioc-my-ioc"
IOC_ENV += "PV_PREFIX"
PV_PREFIX_ENV ?= "MY:PV:PREFIX"
```

### Shared .inc files

When multiple recipes share the same source (e.g. `epics-streamdevice` and `epics-streamdevice-i2c`), put the common recipe logic in a `.inc` file and `require` it from each `.bb`. See `recipes-epics/streamdevice/`.

## Layer configuration (`conf/layer.conf`)

- Compatible Yocto series: honister through scarthgap (`LAYERSERIES_COMPAT_meta-epics`).
- Priority 6 (`BBFILE_PRIORITY_meta-epics`).
- Depends on `core` layer only (`LAYERDEPENDS_meta-epics`).

## Key recipe variables

| Variable | Default | Purpose |
|---|---|---|
| `MODNAME` | `${PN}` | Module name — determines install path `/opt/epics/<MODNAME>` |
| `EPICS_DEPENDS` | `""` | EPICS module dependencies (auto-placed in `RELEASE.local`) |
| `EPICS_ENABLE_STATIC_LIBS` | `"1"` | Build static libraries |
| `EPICS_ENABLE_SHARED_LIBS` | `"0"` | Build shared libraries (needed for pyepics) |
| `ENABLE_HOST_PACKAGE` | `"0"` | Also build for host arch (for modules with host-side tools) |
| `PS_PORT` | `"30000"` | procServ telnet port (IOC class only) |
| `IOC_APP_NAME` | `""` | IOC binary name (IOC class only) |
| `IOC_PATH` | `""` | Path to st.cmd relative to module root (IOC class only) |

## Important cross-compilation details

- Compiler flags from Yocto's `$CC`/`$CXX`/`$LD` are stripped into `USR_CFLAGS`/`USR_CXXFLAGS`/`USR_LDFLAGS` and injected via `CONFIG_SITE.Common.<arch>` — they must NOT go in `CONFIG_SITE.local` or they leak to dependent packages.
- `CHECK_RELEASE=NO` is set universally because Yocto uses different sysroots per package, making the standard EPICS `RELEASE` consistency check incompatible.
- `LINKER_USE_RPATH=ORIGIN` avoids stale build paths in shared objects at runtime.
- Package arch whitelist: `x86_64`, `aarch64`, `arm` Linux only (`COMPATIBLE_HOST`).

## epics-env recipe

`recipes-epics/epics-env/` emits `/etc/profile.d/epics-env.sh` with runtime EPICS environment variables (`EPICS_CA_ADDR_LIST`, `EPICS_PVA_ADDR_LIST`, etc.) plus `EPICS_HOST_ARCH`, `EPICS_BASE=/opt/epics/epics-base`, and `EPICS_MODULES=/opt/epics`. Override the `?=` variables in a `.bbappend` or in `local.conf`. The `epics-base` recipe has `RDEPENDS:${PN}:class-target += " epics-env"`, so it's pulled in automatically.
