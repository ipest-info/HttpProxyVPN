package com.httpproxy.vpn.vpn;

import android.content.Context;

import com.httpproxy.vpn.data.ConfigFileReader;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.HashSet;
import java.util.Set;

/**
 * 默认走代理的应用：指定常用应用 + 浏览器 + 用户应用。
 */
public class DefaultProxyPackages {

    /** 若存在则加入默认代理的包名（快手、抖音、小红书、微信、Via 等） */
    private static final String[] PREFERRED_PACKAGES = {
            "com.smile.gifmaker",      // 快手
            "com.ss.android.ugc.aweme",// 抖音
            "com.xingin.xhs",          // 小红书
            "com.tencent.mm",          // 微信
            "mark.via",                // Via 浏览器
    };

    private static final String[] BROWSER_PACKAGES = {
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
            "com.google.android.webview", "com.android.webview",
            "org.mozilla.firefox", "org.mozilla.fenix", "org.mozilla.firefox_beta", "org.mozilla.fdroid",
            "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
            "com.sec.android.app.sbrowser", "com.vivo.browser", "com.bbk.browser",
            "com.oppo.browser", "com.mi.globalbrowser", "com.miui.browser",
            "com.UCMobile.intl", "com.UCMobile", "com.quark.browser",
            "com.tencent.mtt", "com.aliyun.mobile.browser", "com.huawei.browser", "com.huawei.webview",
            "com.lenovo.browser", "mark.via.gp", "com.kiwibrowser.browser",
            "com.brave.browser", "com.duckduckgo.mobile.android",
    };

    /**
     * 获取默认走代理的包名集合：文件中的包名 + 指定应用 + 浏览器 + 用户应用。不含 excludePackage。
     */
    public static Set<String> getDefaultPackages(Context context, String excludePackage) {
        Set<String> result = new HashSet<>();
        PackageManager pm = context.getPackageManager();
        if (pm == null) return result;

        // 0. 从 SD 文件读取的包名（若存在且已安装）
        ConfigFileReader.ConfigResult fileConfig = ConfigFileReader.read(context);
        if (fileConfig != null && fileConfig.defaultPackages != null && !fileConfig.defaultPackages.isEmpty()) {
            for (String pkg : fileConfig.defaultPackages) {
                if (excludePackage != null && excludePackage.equals(pkg)) continue;
                try {
                    pm.getPackageInfo(pkg, 0);
                    result.add(pkg);
                } catch (PackageManager.NameNotFoundException ignored) { }
            }
        }

        // 1. 指定包名（若存在）
        for (String pkg : PREFERRED_PACKAGES) {
            if (excludePackage != null && excludePackage.equals(pkg)) continue;
            try {
                pm.getPackageInfo(pkg, 0);
                result.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }

        // 2. 已安装的浏览器
        for (String pkg : BROWSER_PACKAGES) {
            if (excludePackage != null && excludePackage.equals(pkg)) continue;
            try {
                pm.getPackageInfo(pkg, 0);
                result.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }

        // 3. 包名/标签含 browser/webview 的兜底
        for (ApplicationInfo info : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (excludePackage != null && excludePackage.equals(info.packageName)) continue;
            String pkg = info.packageName.toLowerCase();
            CharSequence label = info.loadLabel(pm);
            String labelStr = label != null ? label.toString().toLowerCase() : "";
            if (pkg.contains("browser") || pkg.contains("webview") || pkg.contains("web.view")
                    || labelStr.contains("浏览器") || labelStr.contains("browser") || labelStr.contains("webview")) {
                result.add(info.packageName);
            }
        }

        // 4. 用户应用（非系统应用）
        for (ApplicationInfo info : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (excludePackage != null && excludePackage.equals(info.packageName)) continue;
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                result.add(info.packageName);
            }
        }

        return result;
    }
}
