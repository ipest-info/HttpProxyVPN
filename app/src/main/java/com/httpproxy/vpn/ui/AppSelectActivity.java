package com.httpproxy.vpn.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.httpproxy.vpn.R;
import com.httpproxy.vpn.data.ProxyPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppSelectActivity extends AppCompatActivity {

    private ProxyPreferences prefs;
    private List<AppItem> appList = new ArrayList<>();
    private Set<String> selectedPackages = new HashSet<>();
    private AppAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_select);
        setTitle(R.string.app_select_title);
        prefs = new ProxyPreferences(this);
        selectedPackages = new HashSet<>(prefs.getSelectedPackages());

        loadApps();
        ListView list = findViewById(R.id.list_apps);
        adapter = new AppAdapter(appList, selectedPackages);
        list.setAdapter(adapter);
    }

    @Override
    protected void onPause() {
        prefs.setSelectedPackages(selectedPackages);
        super.onPause();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> infos = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        appList.clear();
        for (ApplicationInfo info : infos) {
            CharSequence label = info.loadLabel(pm);
            if (label == null) label = info.packageName;
            appList.add(new AppItem(info.packageName, label.toString(), info));
        }
        appList.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
    }

    private static class AppItem {
        String packageName;
        String label;
        ApplicationInfo info;

        AppItem(String packageName, String label, ApplicationInfo info) {
            this.packageName = packageName;
            this.label = label;
            this.info = info;
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
            CheckBox check = convertView.findViewById(R.id.check);
            PackageManager pm = getPackageManager();
            icon.setImageDrawable(item.info.loadIcon(pm));
            label.setText(item.label);
            check.setChecked(selected.contains(item.packageName));
            check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selected.add(item.packageName);
                else selected.remove(item.packageName);
            });
            convertView.setOnClickListener(v -> {
                check.setChecked(!check.isChecked());
                if (check.isChecked()) selected.add(item.packageName);
                else selected.remove(item.packageName);
            });
            return convertView;
        }
    }
}
