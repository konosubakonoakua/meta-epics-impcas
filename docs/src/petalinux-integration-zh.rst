.. _petalinux-integration-zh:

####################################################
PetaLinux 2024.2 官方标准 User Layer 使用手册
####################################################

完全对齐 PetaLinux 2024.2 官方文档，覆盖全程踩坑经验，适配 Zynq-7000 / ZynqMP 系列板卡。

.. contents:: 目录
   :depth: 2
   :local:

************************
一、2024.2 核心变化
************************

PetaLinux 2024.2 彻底调整了 User Layer 的管理逻辑，**不再支持手改 ``project-spec/meta-user/conf/bblayers.conf`` 追加自定义 layer**，官方唯一认可的注入方式是图形化菜单。

+----------------------------+---------------------------------------------------+---------------------------------------------------+
| 版本                       | 旧逻辑（≤2023.1）                                 | 新逻辑（≥2024.1，官方推荐）                       |
+============================+===================================================+===================================================+
| Layer 注入                 | 手改 ``meta-user/conf/bblayers.conf``             | ``petalinux-config → Yocto Settings →             |
|                            | → ``petalinux-config`` 自动合并                   | User Layers`` 图形注入，                          |
|                            |                                                   | 工具直接写 ``build/conf/bblayers.conf``           |
+----------------------------+---------------------------------------------------+---------------------------------------------------+
| Rootfs 菜单                | 自动扫描所有 layer 的 recipe                      | 必须手动申报包名才会显示                          |
+----------------------------+---------------------------------------------------+---------------------------------------------------+

****************************************************
二、官方标准目录摆放
****************************************************

**你的 layer 和 ``meta-user`` 并列放在 ``project-spec/`` 目录下**，不要塞进 ``meta-user`` 内部：

.. code-block:: text

    <plnx工程根>/  （例：/home/rf/impcas/plnx/blm7015/plnx）
    ├── project-spec/
    │   ├── meta-user/              ← PetaLinux 官方用户层，自动加载
    │   │   └── conf/
    │   │       ├── bblayers.conf   ← 官方维护，禁止手改
    │   │       ├── user-rootfsconfig ← ★ 申报 rootfs 包的地方
    │   │       └── layer.conf
    │   ├── meta-epics-impcas/      ← ★ 你的 EPICS layer（物理文件夹名）
    │   │   ├── conf/
    │   │   │   └── layer.conf     ← ★ Layer 核心配置
    │   │   ├── classes/
    │   │   └── recipes-epics/
    │   └── hw-description/
    │       └── system.xsa
    └── build/

****************************************************
三、官方标准操作流程
****************************************************

Step 1：环境初始化 + 清旧缓存（必做）
====================================================

.. code-block:: bash

    cd <plnx工程根>
    source /tools/Xilinx/Petalinux/2024.2/settings.sh
    # 清掉之前挪工程、裸跑 bitbake 留下的污染缓存
    rm -rf build/conf build/tmp

Step 2：图形化注入 User Layer（官方唯一正确方式）
============================================================

.. code-block:: bash

    petalinux-config

按菜单路径操作：

.. code-block:: text

    Yocto Settings  --->
      User Layers  --->
        # 选中空白处，按 Enter 新增条目
        # 填入路径（必须用 ${PROOT}，不能用 ${TOPDIR}/绝对路径）
        ${PROOT}/project-spec/meta-epics-impcas
        # 保存退出（Exit → Yes）

✅ 验证注入结果：

.. code-block:: bash

    grep "meta-epics-impcas" build/conf/bblayers.conf
    # 预期输出：${PROOT}/project-spec/meta-epics-impcas \

Step 3：申报 Rootfs 包（官方隐藏必做步骤，否则菜单看不到）
====================================================================

PetaLinux 不会自动扫描新 layer 的 recipe，必须在 ``user-rootfsconfig`` 中手动申报包名。

.. code-block:: bash

    vim project-spec/meta-user/conf/user-rootfsconfig

添加你的 EPICS 包（后缀是 recipe 的 PN 名，不是 layer 名）：

.. code-block:: text

    # EPICS Core
    CONFIG_epics-base
    CONFIG_epics-asyn
    CONFIG_epics-streamdevice
    CONFIG_epics-autosave
    # Demo IOC
    CONFIG_epics-demo-ioc

.. tip::

    **语法说明**： ``CONFIG_`` 是 PetaLinux Kconfig 前缀，后缀是你的 recipe 文件名前缀（PN），比如 ``epics-asyn_4.45.bb`` 对应 ``CONFIG_epics-asyn``，和文件夹名 ``meta-epics-impcas`` 无关。

Step 4：刷新 Rootfs 菜单
=====================================

.. code-block:: bash

    petalinux-config            # 顶层配置，让系统读取 user-rootfsconfig 生成菜单
    petalinux-config -c rootfs

此时在菜单中搜索 ``/epics``，就能看到你申报的所有包，按 ``Y`` 勾选为 ``<*>``（Built-in）。

✅ 勾选结果会写入：``project-spec/configs/rootfs_config``，内容为 ``CONFIG_epics-base=y`` 等。

Step 5：编译验证
============================

.. code-block:: bash

    # 先单编 epics-base 验证 layer 未被 mask
    petalinux-build -c epics-base
    # 再编完整镜像
    petalinux-build

******************************************************
四、Layer 核心配置铁律（27 masked 的根因）
******************************************************

``meta-epics-impcas/conf/layer.conf`` 必须严格按以下写法，**三个名字绝对不能混**：

.. code-block:: bitbake

    BBPATH .= ":${LAYERDIR}"
    BBFILES += "${LAYERDIR}/recipes-*/*/*.bb ${LAYERDIR}/recipes-*/*/*.bbappend"

    # ▼ 1. Layer 内部 ID（BitBake 识别用，随便取，和文件夹名无关）
    BBFILE_COLLECTIONS += "meta-epics"
    BBFILE_PATTERN_meta-epics = "^${LAYERDIR}/"
    BBFILE_PRIORITY_meta-epics = "6"

    # ▼ 2. 依赖的上游 layer（EPICS Python 包需要 meta-python）
    LAYERDEPENDS_meta-epics = "core openembedded-layer networking-layer meta-python"

    # ▼ 3. 兼容性后缀【必须和 BBFILE_COLLECTIONS 完全一致，不能用文件夹名 meta-epics-impcas】
    # PetaLinux 2024.2 ≈ Yocto Scarthgap，必须包含 scarthgap
    LAYERSERIES_COMPAT_meta-epics = "kirkstone langdale mickledore nanbield scarthgap"

三个名字对照表：

+--------------------------------------+------------------------------+---------------------------------------------------+
| 名称                                 | 你的值                       | 作用                                              |
+======================================+==============================+===================================================+
| 物理文件夹名                         | ``meta-epics-impcas``        | 仅用于 ``bblayers.conf`` 路径引用                 |
+--------------------------------------+------------------------------+---------------------------------------------------+
| ``BBFILE_COLLECTIONS``               | ``meta-epics``               | BitBake 内部 layer ID                             |
+--------------------------------------+------------------------------+---------------------------------------------------+
| ``LAYERSERIES_COMPAT_`` 后缀         | ``meta-epics``               | Yocto 版本兼容性校验                              |
+--------------------------------------+------------------------------+---------------------------------------------------+

.. warning::

    ``LAYERSERIES_COMPAT_`` 后缀写成文件夹名 ``meta-epics-impcas`` 会导致整个 layer 被 Yocto mask，出现 ``Nothing PROVIDES 'epics-base'`` 错误。

********************************************
五、完整验证链路（从编译到板子运行）
********************************************

1. 编译期验证
=======================

.. code-block:: bash

    # 确认包被打进 rootfs
    grep epics build/tmp/deploy/images/blm7015/zynq-base/rootfs.manifest
    # 预期输出：epics-base、epics-asyn 等

2. 板子启动后验证
===============================

.. code-block:: bash

    # 确认架构正确
    file /usr/bin/caRepeater
    # 预期：ELF 64-bit LSB executable, ARM aarch64

    # 确认 CA 服务正常运行（如果配了 systemd 服务）
    systemctl status epics-demo-ioc
    caget <your-pv-name>            # PC 侧验证 PV 连通性

**********************
六、避坑清单
**********************

+-----------------------------------------------------------+---------------------------------------------------+-----------------------------------------------------------------------+
| 坑点                                                      | 现象                                              | 正确做法                                                              |
+===========================================================+===================================================+=======================================================================+
| ``LAYERSERIES_COMPAT`` 后缀写成文件夹名                    | 27 masked、                                       | 后缀必须等于 ``BBFILE_COLLECTIONS`` 的值 ``meta-epics``               |
|                                                           | ``Nothing PROVIDES 'epics-base'``                 |                                                                       |
+-----------------------------------------------------------+---------------------------------------------------+-----------------------------------------------------------------------+
| 手改 ``meta-user/conf/bblayers.conf``                     | 修改被 ``petalinux-config`` 覆盖                  | 2024.2 只能用 ``Yocto Settings → User Layers`` 图形注入               |
+-----------------------------------------------------------+---------------------------------------------------+-----------------------------------------------------------------------+
| User Layers 路径用 ``${TOPDIR}`` / 绝对路径               | 注入后不生效                                      | 必须用 ``${PROOT}``（PetaLinux 官方维护的工程根变量）                 |
+-----------------------------------------------------------+---------------------------------------------------+-----------------------------------------------------------------------+
| ``user-rootfsconfig`` 未申报包                            | Rootfs 菜单搜不到 epics-*                         | 每个要勾选的 PN 都必须加 ``CONFIG_pn`` 申报                           |
+-----------------------------------------------------------+---------------------------------------------------+-----------------------------------------------------------------------+
| 在 ``build/`` 下裸跑 ``bitbake-layers``                   | ``${PROOT}/build/tmp/hosttools`` 未展开报错       | PetaLinux 工程禁止裸跑 bitbake，走 ``petalinux-build`` 命令           |
+-----------------------------------------------------------+---------------------------------------------------+-----------------------------------------------------------------------+
| 挪工程后直接跑 ``petalinux-config -c rootfs``             | ``gen-machineconf: command not found``             | 先 ``petalinux-config`` 顶层刷新 machine conf，再清 ``build/conf build/tmp`` |
+-----------------------------------------------------------+---------------------------------------------------+-----------------------------------------------------------------------+

**********************************
七、下一步建议
**********************************

1. 完善 ``epics-ioc-systemd.bbclass``，对接 PetaLinux 2024.2 的 systemd preset 机制，实现 IOC 开机自启
2. 统一在 ``epics-component.bbclass`` 中加 ``SECTION ??= "libs/epics"``，让 Rootfs 菜单归类更清晰
3. 配置板子 CA 网络：

   .. code-block:: bash

       export EPICS_CA_AUTO_ADDR_LIST=NO
       export EPICS_CA_ADDR_LIST=<PC_IP>

4. 如需板子本地开发 IOC，在 ``IMAGE_INSTALL`` 中追加 ``build-essential epics-base-dev``
