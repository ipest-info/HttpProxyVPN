package com.httpproxy.vpn.data;

import android.content.Context;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 从 SD/存储中的配置文件读取代理配置与默认代理包名。
 * 配置文件 httpproxy.json 可放在：
 * 1. Download/httpproxy.json
 * 2. Android/data/com.httpproxy.vpn/files/httpproxy.json
 * <p>
 * JSON 格式示例：
 * {
 *   "proxy": { "type": "http", "host": "127.0.0.1", "port": 1080, "username": "", "password": "" },
 *   "defaultPackages": ["com.android.chrome", "com.tencent.mm"]
 * }
 */
public class ConfigFileReader {

    private static final String FILE_NAME = "httpproxy.json";
    private static final String KEY_PROXY = "proxy";
    private static final String KEY_TYPE = "type";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_DEFAULT_PACKAGES = "defaultPackages";

    /** 代理配置（从文件读取的部分） */
    public static class ProxyConfig {
        public String type = ProxyPreferences.TYPE_HTTP;
        public String host = "";
        public int port = 1080;
        public String username = "";
        public String password = "";
    }

    /** 读取结果 */
    public static class ConfigResult {
        public ProxyConfig proxy;
        public Set<String> defaultPackages;
        public boolean fromFile;
    }

    /**
     * 尝试从存储读取配置，文件不存在或解析失败时返回 null。
     */
    public static ConfigResult read(Context context) {
        File file = findConfigFile(context);
        if (file == null || !file.exists() || !file.canRead()) return null;
        try {
            String json = readFile(file);
            if (json == null || json.isEmpty()) return null;
            return parse(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 获取配置文件的可能路径（用于提示用户） */
    public static String[] getConfigPaths(Context context) {
        List<String> paths = new ArrayList<>();
        File appFiles = context.getExternalFilesDir(null);
        if (appFiles != null) {
            paths.add(new File(appFiles, FILE_NAME).getAbsolutePath());
        }
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (download != null) {
                paths.add(new File(download, FILE_NAME).getAbsolutePath());
            }
        }
        return paths.toArray(new String[0]);
    }

    private static File findConfigFile(Context context) {
        File appFiles = context.getExternalFilesDir(null);
        if (appFiles != null) {
            File f = new File(appFiles, FILE_NAME);
            if (f.exists()) return f;
        }
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            try {
                File download = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (download != null) {
                    File f = new File(download, FILE_NAME);
                    if (f.exists() && f.canRead()) return f;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static String readFile(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static ConfigResult parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        ConfigResult result = new ConfigResult();
        result.fromFile = true;

        if (root.has(KEY_PROXY)) {
            JSONObject proxy = root.getJSONObject(KEY_PROXY);
            result.proxy = new ProxyConfig();
            if (proxy.has(KEY_TYPE)) result.proxy.type = proxy.getString(KEY_TYPE);
            if (proxy.has(KEY_HOST)) result.proxy.host = proxy.optString(KEY_HOST, "");
            if (proxy.has(KEY_PORT)) result.proxy.port = proxy.getInt(KEY_PORT);
            if (proxy.has(KEY_USERNAME)) result.proxy.username = proxy.optString(KEY_USERNAME, "");
            if (proxy.has(KEY_PASSWORD)) result.proxy.password = proxy.optString(KEY_PASSWORD, "");
        } else {
            result.proxy = null;
        }

        result.defaultPackages = new HashSet<>();
        if (root.has(KEY_DEFAULT_PACKAGES)) {
            JSONArray arr = root.getJSONArray(KEY_DEFAULT_PACKAGES);
            for (int i = 0; i < arr.length(); i++) {
                String pkg = arr.optString(i, "").trim();
                if (!pkg.isEmpty()) result.defaultPackages.add(pkg);
            }
        }
        return result;
    }
}
