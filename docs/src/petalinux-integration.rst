.. _petalinux-integration:

###########################################################
PetaLinux 2024.2 User Layer Integration Guide (meta-epics)
###########################################################

Official PetaLinux 2024.2 workflow for integrating the meta-epics layer, with
tested procedures for Zynq-7000 / ZynqMP series boards.

.. contents:: Table of Contents
   :depth: 2
   :local:

******************************
1. Key Changes in 2024.2
******************************

PetaLinux 2024.2 overhauled User Layer management. **Manually editing
``project-spec/meta-user/conf/bblayers.conf`` to add custom layers is no longer
supported.** The only officially recognized method is the graphical menu.

+----------------------------+---------------------------------------------------+---------------------------------------------------+
| Aspect                     | Old (≤2023.1)                                     | New (≥2024.1, recommended)                        |
+============================+===================================================+===================================================+
| Layer injection            | Hand-edit ``meta-user/conf/bblayers.conf``,       | ``petalinux-config → Yocto Settings →             |
|                            | then ``petalinux-config`` auto-merges             | User Layers`` GUI injection;                      |
|                            |                                                   | tool writes ``build/conf/bblayers.conf`` directly |
+----------------------------+---------------------------------------------------+---------------------------------------------------+
| Rootfs menu                | Auto-scans recipes from all layers                | Packages must be declared manually to appear      |
+----------------------------+---------------------------------------------------+---------------------------------------------------+

***********************************
2. Standard Directory Layout
***********************************

Place your layer **alongside** ``meta-user`` under ``project-spec/`` — do **not**
nest it inside ``meta-user``:

.. code-block:: text

    <plnx-project-root>/  (e.g. /home/rf/impcas/plnx/blm7015/plnx)
    ├── project-spec/
    │   ├── meta-user/              ← Official PetaLinux user layer (auto-loaded)
    │   │   └── conf/
    │   │       ├── bblayers.conf   ← Managed by petalinux-config, do not edit
    │   │       ├── user-rootfsconfig ← ★ Where rootfs packages are declared
    │   │       └── layer.conf
    │   ├── meta-epics-impcas/      ← ★ Your EPICS layer (physical directory name)
    │   │   ├── conf/
    │   │   │   └── layer.conf     ← ★ Layer core configuration
    │   │   ├── classes/
    │   │   └── recipes-epics/
    │   └── hw-description/
    │       └── system.xsa
    └── build/

****************************************
3. Step-by-Step Workflow
****************************************

Step 1: Initialize Environment & Purge Stale Cache (Required)
=====================================================================

.. code-block:: bash

    cd <plnx-project-root>
    source /tools/Xilinx/Petalinux/2024.2/settings.sh
    # Remove stale cache from moving projects or bare bitbake runs
    rm -rf build/conf build/tmp

Step 2: Inject User Layer via GUI (Only Supported Method)
==================================================================

.. code-block:: bash

    petalinux-config

Navigate through the menu:

.. code-block:: text

    Yocto Settings  --->
      User Layers  --->
        # Select an empty slot, press Enter to add a new entry
        # Enter the path (must use ${PROOT}; do NOT use ${TOPDIR} or absolute paths)
        ${PROOT}/project-spec/meta-epics-impcas
        # Save and exit (Exit → Yes)

✅ Verify the injection:

.. code-block:: bash

    grep "meta-epics-impcas" build/conf/bblayers.conf
    # Expected output: ${PROOT}/project-spec/meta-epics-impcas \

Step 3: Declare Rootfs Packages (Required — without this the menu is empty)
===================================================================================

PetaLinux does not auto-scan recipes from new layers. Each package must be
manually declared in ``user-rootfsconfig``:

.. code-block:: bash

    vim project-spec/meta-user/conf/user-rootfsconfig

Add your EPICS packages (the suffix is the recipe's **PN**, not the layer name):

.. code-block:: text

    # EPICS Core
    CONFIG_epics-base
    CONFIG_epics-asyn
    CONFIG_epics-streamdevice
    CONFIG_epics-autosave
    # Demo IOC
    CONFIG_epics-demo-ioc

.. tip::

    **Syntax**: ``CONFIG_`` is the PetaLinux Kconfig prefix. The suffix is the
    recipe filename prefix (PN). For example, ``epics-asyn_4.45.bb`` maps to
    ``CONFIG_epics-asyn`` — it has nothing to do with the directory name
    ``meta-epics-impcas``.

Step 4: Refresh Rootfs Menu
=====================================

.. code-block:: bash

    petalinux-config            # Top-level config to parse user-rootfsconfig
    petalinux-config -c rootfs

Search for ``/epics`` in the menu. All declared packages will appear; press ``Y``
to set them to ``<*>`` (Built-in).

✅ Selections are written to ``project-spec/configs/rootfs_config`` as
``CONFIG_epics-base=y``, etc.

Step 5: Build & Verify
============================

.. code-block:: bash

    # Build epics-base first to verify the layer is not masked
    petalinux-build -c epics-base
    # Then build the full image
    petalinux-build

**********************************************
4. Layer Configuration Rules
**********************************************

The ``meta-epics-impcas/conf/layer.conf`` must follow this exact pattern.
**Three names must never be mixed up:**

.. code-block:: bitbake

    BBPATH .= ":${LAYERDIR}"
    BBFILES += "${LAYERDIR}/recipes-*/*/*.bb ${LAYERDIR}/recipes-*/*/*.bbappend"

    # ▼ 1. Layer internal ID (BitBake identifier; independent of directory name)
    BBFILE_COLLECTIONS += "meta-epics"
    BBFILE_PATTERN_meta-epics = "^${LAYERDIR}/"
    BBFILE_PRIORITY_meta-epics = "6"

    # ▼ 2. Upstream layer dependencies (EPICS Python packages need meta-python)
    LAYERDEPENDS_meta-epics = "core openembedded-layer networking-layer meta-python"

    # ▼ 3. Compatibility series [MUST match BBFILE_COLLECTIONS, not the directory name]
    # PetaLinux 2024.2 ≈ Yocto Scarthgap, so scarthgap must be included
    LAYERSERIES_COMPAT_meta-epics = "kirkstone langdale mickledore nanbield scarthgap"

Name mapping:

+--------------------------------------+------------------------------+---------------------------------------------------+
| Name                                 | Your Value                   | Purpose                                           |
+======================================+==============================+===================================================+
| Physical directory name              | ``meta-epics-impcas``        | Only used for path reference in ``bblayers.conf`` |
+--------------------------------------+------------------------------+---------------------------------------------------+
| ``BBFILE_COLLECTIONS``               | ``meta-epics``               | BitBake internal layer ID                         |
+--------------------------------------+------------------------------+---------------------------------------------------+
| ``LAYERSERIES_COMPAT_`` suffix       | ``meta-epics``               | Yocto release compatibility check                 |
+--------------------------------------+------------------------------+---------------------------------------------------+

.. warning::

    Using the physical directory name ``meta-epics-impcas`` as the
    ``LAYERSERIES_COMPAT_`` suffix will cause the entire layer to be masked
    by Yocto, resulting in ``Nothing PROVIDES 'epics-base'``.

****************************************************
5. Verification Chain (Build → Board)
****************************************************

5.1 Build-Time Verification
===========================

.. code-block:: bash

    # Confirm packages are included in rootfs
    grep epics build/tmp/deploy/images/blm7015/zynq-base/rootfs.manifest
    # Expected: epics-base, epics-asyn, etc.

5.2 Board Boot Verification
===========================

.. code-block:: bash

    # Confirm target architecture
    file /usr/bin/caRepeater
    # Expected: ELF 64-bit LSB executable, ARM aarch64

    # Confirm IOC service is running (if systemd unit is configured)
    systemctl status epics-demo-ioc
    caget <your-pv-name>            # Verify PV connectivity from a remote host

******************************
6. Pitfall Checklist
******************************

+-----------------------------------------------------------+---------------------------------------------------+----------------------------------------------------------------------------+
| Pitfall                                                   | Symptom                                           | Correct Approach                                                           |
+===========================================================+===================================================+============================================================================+
| ``LAYERSERIES_COMPAT`` suffix set to the directory name   | 27 masked,                                        | Suffix must match ``BBFILE_COLLECTIONS`` value (``meta-epics``)            |
|                                                           | ``Nothing PROVIDES 'epics-base'``                 |                                                                            |
+-----------------------------------------------------------+---------------------------------------------------+----------------------------------------------------------------------------+
| Hand-editing ``meta-user/conf/bblayers.conf``             | Changes overwritten by ``petalinux-config``       | In 2024.2, only use ``Yocto Settings → User Layers`` GUI                   |
+-----------------------------------------------------------+---------------------------------------------------+----------------------------------------------------------------------------+
| Using ``${TOPDIR}`` or absolute paths in User Layers      | Injection has no effect                           | Must use ``${PROOT}`` (PetaLinux-managed project root variable)            |
+-----------------------------------------------------------+---------------------------------------------------+----------------------------------------------------------------------------+
| Missing ``user-rootfsconfig`` declarations                | Rootfs menu does not show epics-*                 | Every PN to be selected must have a ``CONFIG_pn`` entry                    |
+-----------------------------------------------------------+---------------------------------------------------+----------------------------------------------------------------------------+
| Running bare ``bitbake-layers`` under ``build/``          | ``${PROOT}/build/tmp/hosttools`` expansion error  | PetaLinux projects must use ``petalinux-build``; never invoke bitbake directly |
+-----------------------------------------------------------+---------------------------------------------------+----------------------------------------------------------------------------+
| Running ``-c rootfs`` immediately after moving the project| ``gen-machineconf: command not found``             | Run ``petalinux-config`` first to regenerate machine conf, then clear      |
|                                                           |                                                   | ``build/conf`` and ``build/tmp``                                           |
+-----------------------------------------------------------+---------------------------------------------------+----------------------------------------------------------------------------+

******************************
7. Next Steps
******************************

1. Enhance ``epics-ioc-systemd.bbclass`` to integrate with PetaLinux 2024.2's
   systemd preset mechanism for automatic IOC startup on boot.
2. Add ``SECTION ??= "libs/epics"`` in ``epics-component.bbclass`` for cleaner
   categorization in the Rootfs menu.
3. Configure CA networking on the board:

   .. code-block:: bash

       export EPICS_CA_AUTO_ADDR_LIST=NO
       export EPICS_CA_ADDR_LIST=<PC_IP>

4. For on-board IOC development, add ``build-essential epics-base-dev`` to
   ``IMAGE_INSTALL``.
