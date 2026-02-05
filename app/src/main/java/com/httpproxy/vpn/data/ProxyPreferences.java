package com.httpproxy.vpn.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 代理配置与已选应用列表的持久化。
 */
public class ProxyPreferences {

    public static final String PREF_NAME = "proxy_prefs";
    public static final String KEY_PROXY_TYPE = "proxy_type";
    public static final String KEY_HOST = "host";
    public static final String KEY_PORT = "port";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_SELECTED_PACKAGES = "selected_packages";
    public static final String KEY_VPN_ENABLED = "vpn_enabled";

    public static final String TYPE_HTTP = "http";
    public static final String TYPE_SOCKS5 = "socks5";

    private static final int DEFAULT_PORT = 1080;
    private static final String DEFAULT_HOST = "";

    private final SharedPreferences prefs;
    private final Context appContext;

    public ProxyPreferences(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private ConfigFileReader.ProxyConfig getFileProxyConfig() {
        ConfigFileReader.ConfigResult r = ConfigFileReader.read(appContext);
        return r != null && r.proxy != null ? r.proxy : null;
    }

    public String getProxyType() {
        ConfigFileReader.ProxyConfig fc = getFileProxyConfig();
        return fc != null ? fc.type : prefs.getString(KEY_PROXY_TYPE, TYPE_HTTP);
    }

    public void setProxyType(String type) {
        prefs.edit().putString(KEY_PROXY_TYPE, type).apply();
    }

    public String getHost() {
        ConfigFileReader.ProxyConfig fc = getFileProxyConfig();
        return fc != null ? fc.host : prefs.getString(KEY_HOST, DEFAULT_HOST);
    }

    public void setHost(String host) {
        prefs.edit().putString(KEY_HOST, host == null ? DEFAULT_HOST : host).apply();
    }

    public int getPort() {
        ConfigFileReader.ProxyConfig fc = getFileProxyConfig();
        return fc != null ? fc.port : prefs.getInt(KEY_PORT, DEFAULT_PORT);
    }

    public void setPort(int port) {
        prefs.edit().putInt(KEY_PORT, port).apply();
    }

    public String getUsername() {
        ConfigFileReader.ProxyConfig fc = getFileProxyConfig();
        return fc != null ? fc.username : prefs.getString(KEY_USERNAME, "");
    }

    public void setUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username == null ? "" : username).apply();
    }

    public String getPassword() {
        ConfigFileReader.ProxyConfig fc = getFileProxyConfig();
        return fc != null ? fc.password : prefs.getString(KEY_PASSWORD, "");
    }

    public void setPassword(String password) {
        prefs.edit().putString(KEY_PASSWORD, password == null ? "" : password).apply();
    }

    public Set<String> getSelectedPackages() {
        return new HashSet<>(prefs.getStringSet(KEY_SELECTED_PACKAGES, new HashSet<>()));
    }

    public void setSelectedPackages(Set<String> packages) {
        prefs.edit().putStringSet(KEY_SELECTED_PACKAGES, packages == null ? new HashSet<>() : packages).apply();
    }

    public boolean isVpnEnabled() {
        return prefs.getBoolean(KEY_VPN_ENABLED, false);
    }

    public void setVpnEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VPN_ENABLED, enabled).apply();
    }

    /** 配置是否完整（可启动 VPN 的最小条件：类型、主机、端口）。 */
    public boolean isConfigComplete() {
        String host = getHost();
        return host != null && !host.trim().isEmpty() && getPort() > 0 && getPort() <= 65535;
    }
}
