package com.httpproxy.vpn.ui;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import com.google.android.material.switchmaterial.SwitchMaterial;

import androidx.appcompat.app.AppCompatActivity;

import com.httpproxy.vpn.R;
import com.httpproxy.vpn.data.ProxyPreferences;
import com.httpproxy.vpn.vpn.ProxyVpnService;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_VPN = 1;

    private ProxyPreferences prefs;
    private SwitchMaterial switchVpn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new ProxyPreferences(this);

        switchVpn = findViewById(R.id.switch_vpn);
        Button btnConfig = findViewById(R.id.btn_config);
        Button btnApps = findViewById(R.id.btn_apps);

        switchVpn.setChecked(prefs.isVpnEnabled());
        updateSwitchFromService();

        switchVpn.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!prefs.isConfigComplete()) {
                    Toast.makeText(this, R.string.config_incomplete, Toast.LENGTH_SHORT).show();
                    switchVpn.setChecked(false);
                    return;
                }
                Intent intent = VpnService.prepare(this);
                if (intent != null) {
                    startActivityForResult(intent, REQUEST_VPN);
                    switchVpn.setChecked(false);
                } else {
                    startProxyVpn();
                }
            } else {
                stopProxyVpn();
            }
        });

        btnConfig.setOnClickListener(v -> startActivity(new Intent(this, ConfigActivity.class)));
        btnApps.setOnClickListener(v -> startActivity(new Intent(this, AppSelectActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSwitchFromService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN && resultCode == RESULT_OK) {
            startProxyVpn();
            switchVpn.setChecked(true);
        }
    }

    private void updateSwitchFromService() {
        boolean running = ProxyVpnServiceRunningHolder.isRunning();
        switchVpn.setChecked(running);
        prefs.setVpnEnabled(running);
    }

    private void startProxyVpn() {
        Intent intent = new Intent(this, ProxyVpnService.class);
        intent.setAction(ProxyVpnService.ACTION_CONNECT);
        startService(intent);
        switchVpn.setChecked(true);
        prefs.setVpnEnabled(true);
    }

    private void stopProxyVpn() {
        Intent intent = new Intent(this, ProxyVpnService.class);
        intent.setAction(ProxyVpnService.ACTION_DISCONNECT);
        startService(intent);
        switchVpn.setChecked(false);
        prefs.setVpnEnabled(false);
    }

    /** 用于在无 Binder 的情况下让 MainActivity 知道 Service 是否在运行（通过 Service 内 set 或 ContentProvider 等）。此处用静态变量，Service 在 onDestroy 时清除。 */
    public static class ProxyVpnServiceRunningHolder {
        private static boolean running;

        public static void setRunning(boolean r) {
            running = r;
        }

        public static boolean isRunning() {
            return running;
        }
    }
}
