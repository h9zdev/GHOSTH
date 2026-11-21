package net.wigle.wigleandroid;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.wigle.wigleandroid.analytics.WirelessAnalytics;
import net.wigle.wigleandroid.classifier.AdvancedDeviceClassifier;
import net.wigle.wigleandroid.classifier.DeviceTypeClassifier;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.ui.CyberRadarView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Cyberpunk GhostH Tactical Signal & Threat Radar Fragment.
 * Displays real-time rotating sweep radar, target lock telemetry,
 * and categorization of nearby FLOCK cameras, ALPRs, IoT, and BLE trackers.
 */
public class RadarFragment extends Fragment {

    private CyberRadarView radarView;
    private TextView totalCountView;
    private TextView threatCountView;
    private TextView iotCountView;
    private TextView bleCountView;
    private TextView securityScoreView;
    private TextView bestChannelView;

    private View targetLockCard;
    private TextView lockCategoryView;
    private TextView lockSsidView;
    private TextView lockRssiView;
    private TextView lockBssidView;
    private TextView lockFreqView;

    private ListView targetListView;
    private TargetListAdapter listAdapter;
    private final List<CyberRadarView.RadarTarget> targetList = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRunning = false;

    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            refreshRadarData();
            handler.postDelayed(this, 1200);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.radar, container, false);

        radarView = view.findViewById(R.id.cyber_radar_view);
        totalCountView = view.findViewById(R.id.radar_total_count);
        threatCountView = view.findViewById(R.id.radar_threat_count);
        iotCountView = view.findViewById(R.id.radar_iot_count);
        bleCountView = view.findViewById(R.id.radar_ble_count);
        securityScoreView = view.findViewById(R.id.radar_security_score);
        bestChannelView = view.findViewById(R.id.radar_best_channel);

        targetLockCard = view.findViewById(R.id.target_lock_card);
        lockCategoryView = view.findViewById(R.id.lock_target_category);
        lockSsidView = view.findViewById(R.id.lock_target_ssid);
        lockRssiView = view.findViewById(R.id.lock_target_rssi);
        lockBssidView = view.findViewById(R.id.lock_target_bssid);
        lockFreqView = view.findViewById(R.id.lock_target_freq);

        targetListView = view.findViewById(R.id.radar_target_list);
        listAdapter = new TargetListAdapter();
        targetListView.setAdapter(listAdapter);

        radarView.setOnTargetSelectedListener(target -> {
            displaySelectedTarget(target);
            listAdapter.notifyDataSetChanged();
        });

        targetListView.setOnItemClickListener((parent, itemView, position, id) -> {
            if (position >= 0 && position < targetList.size()) {
                CyberRadarView.RadarTarget target = targetList.get(position);
                radarView.selectTarget(target);
                displaySelectedTarget(target);
                listAdapter.notifyDataSetChanged();
            }
        });

        // Initialize empty HUD state
        displaySelectedTarget(null);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        isRunning = true;
        handler.post(updateTask);
    }

    @Override
    public void onPause() {
        super.onPause();
        isRunning = false;
        handler.removeCallbacks(updateTask);
    }

    private void refreshRadarData() {
        if (radarView == null) return;

        Collection<Network> networks = null;
        if (MainActivity.getNetworkCache() != null) {
            networks = MainActivity.getNetworkCache().values();
        }

        radarView.updateNetworks(networks);

        // Calculate threat and category stats
        int total = 0;
        int threats = 0;
        int iot = 0;
        int ble = 0;

        targetList.clear();
        targetList.addAll(radarView.getTargets());

        for (CyberRadarView.RadarTarget t : targetList) {
            total++;
            switch (t.category) {
                case FLOCK_ALPR:
                case ALPR_SYSTEM:
                    threats++;
                    break;
                case IOT_DEVICE:
                    iot++;
                    break;
                case LOW_RANGE_BLE:
                    ble++;
                    break;
                default:
                    break;
            }
        }

        totalCountView.setText(String.format(Locale.US, "TARGETS: %d", total));
        threatCountView.setText(String.format(Locale.US, "FLOCK/ALPR: %d", threats));
        iotCountView.setText(String.format(Locale.US, "IoT: %d", iot));
        bleCountView.setText(String.format(Locale.US, "BLE: %d", ble));

        if (securityScoreView != null && bestChannelView != null && networks != null) {
            WirelessAnalytics.SecurityPosture posture = WirelessAnalytics.calculateSecurityPosture(networks);
            WirelessAnalytics.ChannelReport report = WirelessAnalytics.analyzeChannels(networks);
            securityScoreView.setText(String.format(Locale.US, "SEC SCORE: %d%% [OPEN: %.0f%% | WPA2/3: %.0f%%]",
                    posture.getPostureScore(), posture.getOpenPercent(), posture.getWpa2Percent() + posture.getWpa3Percent()));
            bestChannelView.setText(String.format(Locale.US, "BEST CH: 2.4G [%d] | 5G [%d]",
                    report.bestChannel24, report.bestChannel5));
        }

        // Sort targets: special threats first, then by signal strength descending
        Collections.sort(targetList, new Comparator<CyberRadarView.RadarTarget>() {
            @Override
            public int compare(CyberRadarView.RadarTarget o1, CyberRadarView.RadarTarget o2) {
                int p1 = getPriority(o1.category);
                int p2 = getPriority(o2.category);
                if (p1 != p2) return Integer.compare(p2, p1);
                return Integer.compare(o2.network.getLevel(), o1.network.getLevel());
            }

            private int getPriority(AdvancedDeviceClassifier.DeviceCategory c) {
                if (c == AdvancedDeviceClassifier.DeviceCategory.FLOCK_ALPR) return 4;
                if (c == AdvancedDeviceClassifier.DeviceCategory.ALPR_SYSTEM) return 3;
                if (c == AdvancedDeviceClassifier.DeviceCategory.LOW_RANGE_BLE) return 2;
                if (c == AdvancedDeviceClassifier.DeviceCategory.IOT_DEVICE) return 1;
                return 0;
            }
        });

        listAdapter.notifyDataSetChanged();

        // Update selected target telemetry if active
        displaySelectedTarget(radarView.getSelectedTarget());
    }

    private void displaySelectedTarget(@Nullable CyberRadarView.RadarTarget target) {
        if (target == null || target.network == null) {
            lockCategoryView.setText("[STANDBY]");
            lockCategoryView.setTextColor(getResources().getColor(R.color.cyber_dim_cyan));
            lockSsidView.setText("NO TARGET LOCKED - TAP RADAR OR LIST");
            lockSsidView.setTextColor(getResources().getColor(R.color.cyber_cyan));
            lockRssiView.setText("-- dBm");
            lockBssidView.setText("MAC: --:--:--:--:--:--");
            lockFreqView.setText("SWEEP ACTIVE");
            return;
        }

        final Network net = target.network;
        if (target.category.isSpecial()) {
            lockCategoryView.setText("[" + target.category.getLabel() + "]");
            lockCategoryView.setTextColor(target.color);
        } else {
            lockCategoryView.setText("[" + net.getType().name() + "]");
            lockCategoryView.setTextColor(target.color);
        }

        final String ssid = net.getSsid() != null && !net.getSsid().isEmpty() ? net.getSsid() : "<HIDDEN>";
        lockSsidView.setText(ssid);
        lockSsidView.setTextColor(target.color);

        lockRssiView.setText(net.getLevel() + " dBm");
        lockBssidView.setText("MAC: " + net.getBssid());

        if (net.getFrequency() > 0) {
            lockFreqView.setText(net.getFrequency() + "MHz");
        } else {
            lockFreqView.setText(net.getType().name());
        }
    }

    private class TargetListAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return targetList.size();
        }

        @Override
        public Object getItem(int position) {
            return targetList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.radar_target_row, parent, false);
            }

            final CyberRadarView.RadarTarget target = targetList.get(position);
            final Network net = target.network;

            final ImageView icon = convertView.findViewById(R.id.row_target_icon);
            final TextView badge = convertView.findViewById(R.id.row_target_badge);
            final TextView ssid = convertView.findViewById(R.id.row_target_ssid);
            final TextView level = convertView.findViewById(R.id.row_target_level);
            final TextView bssid = convertView.findViewById(R.id.row_target_bssid);
            final TextView detail = convertView.findViewById(R.id.row_target_detail);

            DeviceTypeClassifier.DeviceType dt = DeviceTypeClassifier.classify(net);
            if (icon != null) {
                icon.setImageResource(dt.getIconRes());
            }

            if (target.category.isSpecial()) {
                badge.setText("[" + target.category.getLabel() + "]");
                badge.setTextColor(target.color);
                badge.setVisibility(View.VISIBLE);
                ssid.setTextColor(target.color);
            } else if (net.getType() == NetworkType.BLE || net.getType() == NetworkType.BT) {
                badge.setText("[" + dt.getLabel() + "]");
                badge.setTextColor(target.color);
                badge.setVisibility(View.VISIBLE);
                ssid.setTextColor(target.color);
            } else if (net.getType() == NetworkType.WIFI) {
                badge.setText("[WIFI]");
                badge.setTextColor(target.color);
                badge.setVisibility(View.VISIBLE);
                ssid.setTextColor(target.color);
            } else {
                badge.setText("[" + net.getType().name() + "]");
                badge.setTextColor(target.color);
                badge.setVisibility(View.VISIBLE);
                ssid.setTextColor(target.color);
            }

            final String ssidStr = net.getSsid() != null && !net.getSsid().isEmpty()
                    ? net.getSsid() : "<HIDDEN SSID>";
            ssid.setText(ssidStr);

            level.setText(net.getLevel() + " dBm");
            bssid.setText(net.getBssid());

            String det = net.getDetail() != null ? net.getDetail() : "";
            if (net.getFrequency() > 0) {
                det = net.getFrequency() + "MHz " + det;
            }
            detail.setText(det);

            // Highlight if this is the currently locked target
            CyberRadarView.RadarTarget selected = radarView.getSelectedTarget();
            if (selected != null && selected.network.getBssid().equals(net.getBssid())) {
                convertView.setBackgroundResource(R.drawable.upload_button);
            } else {
                convertView.setBackgroundResource(R.drawable.cyber_card_bg);
            }

            return convertView;
        }
    }
}

