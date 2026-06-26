#----------------------------------------------------------------------------------#
# Recipe: epics-env
#----------------------------------------------------------------------------------#
# Description: This recipe emits environment variables to /etc/profile.d so they're
#  set for all users. Some basic defaults are provided, but for the most part this
#  relies on the defaults set by EPICS internally.
#
#  If you wish to change one of the variables (i.e. EPICS_CA_ADDR_LIST), provide
#  either a .bbappend for this recipe, or set them in your local.conf.
#----------------------------------------------------------------------------------#

# Random permissive license.
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Override these as you need!
EPICS_CA_ADDR_LIST               ?= ""
EPICS_CA_AUTO_ADDR_LIST          ?= "YES"
EPICS_CA_NAME_SERVERS            ?= ""
EPICS_CA_CONN_TMO                ?= ""
EPICS_CA_BEACON_PERIOD           ?= ""
EPICS_CA_REPEATER_PORT           ?= "5065"
EPICS_CA_SERVER_PORT             ?= "5064"
EPICS_CA_MAX_ARRAY_BYTES         ?= ""
EPICS_CA_AUTO_ARRAY_BYTES        ?= ""
EPICS_CA_MAX_SEARCH_PERIOD       ?= ""
EPICS_CA_MCAST_TTL               ?= ""
EPICS_TS_MIN_WEST                ?= ""

EPICS_CAS_SERVER_PORT            ?= ""
EPICS_CAS_AUTO_BEACON_ADDR_LIST  ?= ""
EPICS_CAS_BEACON_ADDR_LIST       ?= ""
EPICS_CAS_BEACON_PORT            ?= ""
EPICS_CAS_INTF_ADDR_LIST         ?= ""
EPICS_CAS_IGNORE_ADDR_LIST       ?= ""

EPICS_PVA_ADDR_LIST              ?= ""
EPICS_PVA_AUTO_ADDR_LIST         ?= "YES"
EPICS_PVA_NAME_SERVERS           ?= ""
EPICS_PVA_BROADCAST_PORT         ?= "5076"
EPICS_PVA_CONN_TMO               ?= ""

EPICS_PVAS_INTF_ADDR_LIST        ?= ""
EPICS_PVAS_BEACON_ADDR_LIST      ?= ""
EPICS_PVAS_AUTO_BEACON_ADDR_LIST ?= ""
EPICS_PVAS_SERVER_PORT           ?= ""
EPICS_PVAS_BROADCAST_PORT        ?= ""
EPICS_PVAS_IGNORE_ADDR_LIST      ?= ""

# Unfortunately need a python recipe otherwise we can't access the EPICS_ vars set in the recipe :(
python do_install() {
    import os
    
    D = d.getVar("D")
    SC = d.getVar("sysconfdir")

    # Ensure dir exists
    os.makedirs(f"{D}{SC}/profile.d", exist_ok=True)

    vars = [
        "EPICS_CA_ADDR_LIST",
        "EPICS_CA_AUTO_ADDR_LIST",
        "EPICS_CA_NAME_SERVERS",
        "EPICS_CA_CONN_TMO",
        "EPICS_CA_BEACON_PERIOD",
        "EPICS_CA_REPEATER_PORT",
        "EPICS_CA_SERVER_PORT",
        "EPICS_CA_MAX_ARRAY_BYTES",
        "EPICS_CA_AUTO_ARRAY_BYTES",
        "EPICS_CA_MAX_SEARCH_PERIOD",
        "EPICS_CA_MCAST_TTL",
        "EPICS_TS_MIN_WEST",
        "EPICS_CAS_SERVER_PORT",
        "EPICS_CAS_AUTO_BEACON_ADDR_LIST",
        "EPICS_CAS_BEACON_ADDR_LIST",
        "EPICS_CAS_BEACON_PORT",
        "EPICS_CAS_INTF_ADDR_LIST",
        "EPICS_CAS_IGNORE_ADDR_LIST",
        "EPICS_PVA_ADDR_LIST",
        "EPICS_PVA_AUTO_ADDR_LIST",
        "EPICS_PVA_NAME_SERVERS",
        "EPICS_PVA_BROADCAST_PORT",
        "EPICS_PVA_CONN_TMO",
        "EPICS_PVAS_INTF_ADDR_LIST",
        "EPICS_PVAS_BEACON_ADDR_LIST",
        "EPICS_PVAS_AUTO_BEACON_ADDR_LIST",
        "EPICS_PVAS_SERVER_PORT",
        "EPICS_PVAS_BROADCAST_PORT",
        "EPICS_PVAS_IGNORE_ADDR_LIST"
    ]
    
    with open(f"{D}{SC}/profile.d/epics-env.sh", "w") as fp:
        # These cannot be overridden
        fp.write(f"export EPICS_HOST_ARCH=linux-{d.getVar('TARGET_ARCH')}\n")
        fp.write(f"export EPICS_BASE=/opt/epics/base\n")
        fp.write(f"export EPICS_MODULES=/opt/epics/support\n")
        fp.write(f"export EPICS_SITE_TOP=/opt/epics\n")

        for v in vars:
            val = d.getVar(v)
            # Avoid emitting empty vars; we want to rely on the EPICS base defaults
            if val is None or val == '':
                continue
            
            fp.write(f"export {v}={val}\n")
}

FILES:${PN} += " /etc/profile.d/epics-env.sh"