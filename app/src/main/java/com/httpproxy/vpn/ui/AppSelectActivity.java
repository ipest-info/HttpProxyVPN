package com.httpproxy.vpn.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.httpproxy.vpn.R;
import com.httpproxy.vpn.data.ProxyPreferences;
import com.httpproxy.vpn.vpn.DefaultProxyPackages;
import com.httpproxy.vpn.vpn.ProxyVpnService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppSelectActivity extends AppCompatActivity {

    private ProxyPreferences prefs;
    private List<AppItem> appList = new ArrayList<>();
    private List<AppItem> filteredList = new ArrayList<>();
    private Set<String> selectedPackages = new HashSet<>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_select);
        setTitle(R.string.app_select_title);
        prefs = new ProxyPreferences(this);
        selectedPackages = new HashSet<>(prefs.getSelectedPackages());
        // 若从未保存过选择，用默认列表（指定应用 + 浏览器 + 用户应用）初始化，使 UI 与 VPN 默认行为一致
        if (selectedPackages.isEmpty()) {
            selectedPackages = DefaultProxyPackages.getDefaultPackages(this, getPackageName());
        }

        loadApps();
        applyFilter(null);
        ListView list = findViewById(R.id.list_apps);
        adapter = new AppAdapter(filteredList, selectedPackages);
        list.setAdapter(adapter);

        EditText etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                applyFilter(s == null ? null : s.toString());
            }
        });
    }

    private void applyFilter(String query) {
        filteredList.clear();
        if (TextUtils.isEmpty(query)) {
            filteredList.addAll(appList);
        } else {
            String q = query.toLowerCase().trim();
            for (AppItem item : appList) {
                if (item.label.toLowerCase().contains(q) || item.packageName.toLowerCase().contains(q)) {
                    filteredList.add(item);
                }
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        prefs.setSelectedPackages(selectedPackages);
        // 若 VPN 正在运行，重启以使新的应用选择生效
        if (MainActivity.ProxyVpnServiceRunningHolder.isRunning()) {
            Intent restart = new Intent(this, ProxyVpnService.class);
            restart.setAction(ProxyVpnService.ACTION_DISCONNECT);
            startService(restart);
            restart.setAction(ProxyVpnService.ACTION_CONNECT);
            startService(restart);
        }
        super.onPause();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> infos = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        appList.clear();
        for (ApplicationInfo info : infos) {
            CharSequence label = info.loadLabel(pm);
            if (label == null) label = info.packageName;
            boolean isSystem = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            appList.add(new AppItem(info.packageName, label.toString(), info, isSystem));
        }
        // 排序：已选中的应用 -> 用户应用 -> 系统应用；组内按名称
        appList.sort((a, b) -> {
            int orderA = selectedPackages.contains(a.packageName) ? 0 : (a.isSystem ? 2 : 1);
            int orderB = selectedPackages.contains(b.packageName) ? 0 : (b.isSystem ? 2 : 1);
            if (orderA != orderB) return orderA - orderB;
            return a.label.compareToIgnoreCase(b.label);
        });
    }

    private static class AppItem {
        String packageName;
        String label;
        ApplicationInfo info;
        boolean isSystem;

        AppItem(String packageName, String label, ApplicationInfo info, boolean isSystem) {
            this.packageName = packageName;
            this.label = label;
            this.info = info;
            this.isSystem = isSystem;
        }
    }

    private class AppAdapter extends BaseAdapter {
        private final List<AppItem> items;
        private final Set<String> selected;

        AppAdapter(List<AppItem> items, Set<String> selected) {
            this.items = items;
            this.selected = selected;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
            }
            AppItem item = items.get(position);
            ImageView icon = convertView.findViewById(R.id.icon);
            TextView label = convertView.findViewById(R.id.label);
            TextView tvSystemBadge = convertView.findViewById(R.id.tv_system_badge);
            CheckBox check = convertView.findViewById(R.id.check);
            PackageManager pm = getPackageManager();
            icon.setImageDrawable(item.info.loadIcon(pm));
            label.setText(item.label);
            tvSystemBadge.setVisibility(item.isSystem ? View.VISIBLE : View.GONE);
            check.setOnCheckedChangeListener(null);
            check.setChecked(selected.contains(item.packageName));
            check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selected.add(item.packageName);
                else selected.remove(item.packageName);
            });
            convertView.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            return convertView;
        }
    }
}
