package com.httpproxy.vpn.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

import com.httpproxy.vpn.R;
import com.httpproxy.vpn.data.ProxyPreferences;
import com.httpproxy.vpn.proxy.HttpUpstreamClient;
import com.httpproxy.vpn.proxy.Socks5UpstreamClient;
import com.httpproxy.vpn.proxy.UpstreamProxyClient;
import com.httpproxy.vpn.ui.MainActivity;

import static com.httpproxy.vpn.ui.MainActivity.ProxyVpnServiceRunningHolder;

import java.util.Set;

/**
 * VPN 服务：建立按应用代理，setHttpProxy 指向本地 HTTP 代理，本地代理转发到上游。
 */
public class ProxyVpnService extends VpnService {

    public static final String ACTION_CONNECT = "com.httpproxy.vpn.CONNECT";
    public static final String ACTION_DISCONNECT = "com.httpproxy.vpn.DISCONNECT";
    private static final String CHANNEL_ID = "proxy_vpn_channel";
    private static final int NOTIFICATION_ID = 1;

    private ProxyPreferences prefs;
    private LocalProxyServer localProxy;
    private ParcelFileDescriptor vpnFd;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new ProxyPreferences(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_DISCONNECT.equals(action)) {
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_CONNECT.equals(action)) {
            if (!prefs.isConfigComplete()) {
                stopSelf();
                return START_NOT_STICKY;
            }
            startVpn();
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void startVpn() {
        if (vpnFd != null) return;

        UpstreamProxyClient upstream = createUpstreamClient();
        localProxy = new LocalProxyServer(upstream);
        try {
            localProxy.start();
        } catch (Exception e) {
            stopSelf();
            return;
        }

        Builder builder = new Builder()
                .setSession(getString(R.string.notification_title))
                .setMtu(1500)
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setHttpProxy(android.net.ProxyInfo.buildDirectProxy("127.0.0.1", localProxy.getPort()));

        Set<String> allowed = prefs.getSelectedPackages();
        if (allowed != null && !allowed.isEmpty()) {
            for (String pkg : allowed) {
                try {
                    builder.addAllowedApplication(pkg);
                } catch (Exception ignored) { }
            }
        } else {
            // 默认：指定应用 + 浏览器 + 用户应用走代理（不含本应用）
            Set<String> defaultPackages = DefaultProxyPackages.getDefaultPackages(this, getPackageName());
            for (String pkg : defaultPackages) {
                try {
                    builder.addAllowedApplication(pkg);
                } catch (Exception ignored) { }
            }
        }

        vpnFd = builder.establish();
        if (vpnFd == null) {
            localProxy.stop();
            stopSelf();
            return;
        }

        prefs.setVpnEnabled(true);
        ProxyVpnServiceRunningHolder.setRunning(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification());
        }
    }

    private void stopVpn() {
        ProxyVpnServiceRunningHolder.setRunning(false);
        prefs.setVpnEnabled(false);
        stopForeground(true);
        if (vpnFd != null) {
            try {
                vpnFd.close();
            } catch (Exception ignored) { }
            vpnFd = null;
        }
        if (localProxy != null) {
            localProxy.stop();
            localProxy = null;
        }
    }

    private UpstreamProxyClient createUpstreamClient() {
        String type = prefs.getProxyType();
        String host = prefs.getHost();
        int port = prefs.getPort();
        String user = prefs.getUsername();
        String pass = prefs.getPassword();
        if (ProxyPreferences.TYPE_SOCKS5.equals(type)) {
            return new Socks5UpstreamClient(host, port, user, pass);
        }
        return new HttpUpstreamClient(host, port, user, pass);
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }
}
