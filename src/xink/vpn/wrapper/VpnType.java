package xink.vpn.wrapper;

import xink.vpn.R;

public enum VpnType {
    PPTP("PPTP", R.string.vpn_pptp, R.string.vpn_pptp_info, PptpProfile.class),
    L2TP("L2TP", R.string.vpn_l2tp, R.string.vpn_l2tp_info, L2tpProfile.class),
    L2TP_IPSEC_PSK("L2TP/IPSec PSK", R.string.vpn_l2tp_psk, R.string.vpn_l2tp_psk_info, L2tpIpsecPskProfile.class),
    // L2TP_IPSEC("L2TP/IPSec CRT", null)
    ;

    private String name;
    private Class<? extends VpnProfile> clazz;
    private boolean active;
    private int descRid;
    private int nameRid;

    VpnType(final String name, final int nameRid, final int descRid, final Class<? extends VpnProfile> clazz) {
        this.name = name;
        this.nameRid = nameRid;
        this.descRid = descRid;
        this.clazz = clazz;
    }

    public String getName() {
        return name;
    }

    public Class<? extends VpnProfile> getProfileClass() {
        return clazz;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean a) {
        this.active = a;
    }

    public int getNameRid() {
        return nameRid;
    }

    public int getDescRid() {
        return descRid;
    }
}