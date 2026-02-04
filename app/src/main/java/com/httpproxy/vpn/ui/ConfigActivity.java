package com.httpproxy.vpn.ui;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.httpproxy.vpn.R;
import com.httpproxy.vpn.data.ProxyPreferences;

public class ConfigActivity extends AppCompatActivity {

    private ProxyPreferences prefs;
    private TextInputEditText etHost, etPort, etUsername, etPassword;
    private RadioGroup rgType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        setTitle(R.string.config_title);
        prefs = new ProxyPreferences(this);

        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        rgType = findViewById(R.id.rg_type);

        etHost.setText(prefs.getHost());
        etPort.setText(String.valueOf(prefs.getPort()));
        etUsername.setText(prefs.getUsername());
        etPassword.setText(prefs.getPassword());
        if (ProxyPreferences.TYPE_SOCKS5.equals(prefs.getProxyType())) {
            rgType.check(R.id.rb_socks5);
        } else {
            rgType.check(R.id.rb_http);
        }

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
    }

    private void save() {
        String host = etHost.getText() != null ? etHost.getText().toString().trim() : "";
        String portStr = etPort.getText() != null ? etPort.getText().toString().trim() : "";
        int port = 1080;
        try {
            if (!portStr.isEmpty()) port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.config_port) + " 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (port <= 0 || port > 65535) {
            Toast.makeText(this, getString(R.string.config_port) + " 无效", Toast.LENGTH_SHORT).show();
            return;
        }
        if (host.isEmpty()) {
            Toast.makeText(this, getString(R.string.config_host) + " 不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        String type = rgType.getCheckedRadioButtonId() == R.id.rb_socks5
                ? ProxyPreferences.TYPE_SOCKS5 : ProxyPreferences.TYPE_HTTP;
        String username = etUsername.getText() != null ? etUsername.getText().toString() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        prefs.setHost(host);
        prefs.setPort(port);
        prefs.setProxyType(type);
        prefs.setUsername(username);
        prefs.setPassword(password);
        Toast.makeText(this, R.string.config_saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
