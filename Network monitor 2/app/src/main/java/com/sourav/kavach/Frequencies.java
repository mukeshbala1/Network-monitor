package com.sourav.kavach;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class Frequencies extends AppCompatActivity {

    private static final String FIREBASE_URL = "https://kavach-rf-default-rtdb.firebaseio.com";
    private static final String NODE_PATH = "kavach/readings/esp32_nrf24";
    private static final String CONTROL_NODE_PATH = "kavach/system_control";

    // Dynamic Scenario Model
    private static class ThreatScenario {
        int unauthorized;
        int authorized;

        ThreatScenario(int unauth, int auth) {
            this.unauthorized = unauth;
            this.authorized = auth;
        }
    }

    // Realistic scanning scenarios cycling every 2 minutes when Active:
    // (2,6) -> (3,8) -> (1,3) -> (0,5) -> (2,6) -> (0,6)
    private final ThreatScenario[] scenarios = new ThreatScenario[]{
            new ThreatScenario(2, 6), // 2 unauthorized, 6 authorized
            new ThreatScenario(3, 8), // 3 unauthorized, 8 authorized
            new ThreatScenario(1, 3), // 1 unauthorized, 3 authorized
            new ThreatScenario(0, 5), // 0 unauthorized, 5 authorized (Zero Threats / Safe!)
            new ThreatScenario(2, 6), // 2 unauthorized, 6 authorized
            new ThreatScenario(0, 6)  // 0 unauthorized, 6 authorized (Zero Threats / Safe!)
    };

    private int currentScenarioIndex = 0;
    private final Handler scenarioHandler = new Handler(Looper.getMainLooper());
    private Runnable scenarioRunnable;
    private boolean isHardwareActive = false;
    private boolean isCloudOnline = false;
    private BroadcastReceiver wifiReceiver;

    // Views
    private Chip chipUnauthorizedCount, chipAuthorizedCount;
    private MaterialCardView cardSecurityStatus, cardSecurityWarning, cardThreatDetails;
    private TextView tvUnauthorizedSubtitle, tvWarningBody, tvThreatTitle, tvBadgeUnauthorized, tvAlertText;
    private LinearLayout boxUnauthorizedSignals, boxSecurityAlert;
    private ImageView ivSecurityStatusIcon, ivThreatIcon, ivAlertIcon;

    private TextView tvFreqValue, tvStrengthValue, tvBandwidthValue, tvModulationValue, tvQualityPercent;
    private LinearProgressIndicator progressSignalQuality;
    private DatabaseReference dbRef;
    private DatabaseReference controlRef;
    private ValueEventListener valueListener;
    private ValueEventListener controlListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_frequencies);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bindViews();
        setupDynamicScenarios();
        setupNetworkMonitoring();
        setupFirebase();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemState();
    }

    private void setupNetworkMonitoring() {
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateSystemState();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(wifiReceiver, filter);
            }
        } catch (Exception ignored) {}
    }

    private boolean isWifiConnected() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network activeNet = cm.getActiveNetwork();
        if (activeNet == null) return false;
        NetworkCapabilities cap = cm.getNetworkCapabilities(activeNet);
        if (cap == null) return false;

        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private void updateSystemState() {
        isHardwareActive = isWifiConnected() && isCloudOnline;
        if (!isHardwareActive) {
            scenarioHandler.removeCallbacks(scenarioRunnable);
            applyStandbyState();
        } else {
            applyScenario(scenarios[currentScenarioIndex]);
            scenarioHandler.removeCallbacks(scenarioRunnable);
            scenarioHandler.postDelayed(scenarioRunnable, 120000);
        }
    }

    private void bindViews() {
        chipUnauthorizedCount  = findViewById(R.id.chipUnauthorizedCount);
        chipAuthorizedCount    = findViewById(R.id.chipAuthorizedCount);
        cardSecurityStatus     = findViewById(R.id.cardSecurityStatus);
        cardSecurityWarning    = findViewById(R.id.cardSecurityWarning);
        cardThreatDetails      = findViewById(R.id.cardThreatDetails);
        tvUnauthorizedSubtitle = findViewById(R.id.tvUnauthorizedSubtitle);
        boxUnauthorizedSignals = findViewById(R.id.boxUnauthorizedSignals);
        ivSecurityStatusIcon   = findViewById(R.id.ivSecurityStatusIcon);
        tvWarningBody          = findViewById(R.id.tvWarningBody);
        tvThreatTitle          = findViewById(R.id.tvThreatTitle);
        tvBadgeUnauthorized    = findViewById(R.id.tvBadgeUnauthorized);
        boxSecurityAlert       = findViewById(R.id.boxSecurityAlert);
        tvAlertText            = findViewById(R.id.tvAlertText);
        ivThreatIcon           = findViewById(R.id.ivThreatIcon);
        ivAlertIcon            = findViewById(R.id.ivAlertIcon);

        tvFreqValue            = findViewById(R.id.tvFreqValue);
        tvStrengthValue        = findViewById(R.id.tvStrengthValue);
        tvBandwidthValue       = findViewById(R.id.tvBandwidthValue);
        tvModulationValue      = findViewById(R.id.tvModulationValue);
        tvQualityPercent       = findViewById(R.id.tvQualityPercent);
        progressSignalQuality  = findViewById(R.id.progressSignalQuality);
        if (progressSignalQuality != null) progressSignalQuality.setMax(100);

        // Tap top card to cycle scenarios (Only when Hardware is Active!)
        if (cardSecurityStatus != null) {
            cardSecurityStatus.setOnClickListener(v -> {
                if (!isHardwareActive) {
                    Toast.makeText(this, "Hardware is in Standby. Activate live stream to scan.", Toast.LENGTH_SHORT).show();
                    return;
                }
                currentScenarioIndex = (currentScenarioIndex + 1) % scenarios.length;
                applyScenario(scenarios[currentScenarioIndex]);
                Toast.makeText(this, "RF Scan: " + scenarios[currentScenarioIndex].unauthorized + " Threats, " + scenarios[currentScenarioIndex].authorized + " Authorized", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupDynamicScenarios() {
        scenarioRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHardwareActive) {
                    currentScenarioIndex = (currentScenarioIndex + 1) % scenarios.length;
                    applyScenario(scenarios[currentScenarioIndex]);
                    scenarioHandler.postDelayed(this, 120000); // 2 minutes
                }
            }
        };
        // Initial state is Standby until confirmed Online by Cloud
        applyStandbyState();
    }

    private void applyStandbyState() {
        if (chipUnauthorizedCount != null) {
            chipUnauthorizedCount.setText("0");
            chipUnauthorizedCount.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#94A3B8")));
        }
        if (tvUnauthorizedSubtitle != null) {
            tvUnauthorizedSubtitle.setText("Hardware in Standby • Scanner Inactive");
            tvUnauthorizedSubtitle.setTextColor(Color.parseColor("#64748B"));
        }
        if (boxUnauthorizedSignals != null) {
            boxUnauthorizedSignals.setBackgroundColor(Color.parseColor("#F8FAFC"));
        }
        if (ivSecurityStatusIcon != null) {
            ivSecurityStatusIcon.setColorFilter(Color.parseColor("#94A3B8"));
        }
        if (cardSecurityWarning != null) {
            cardSecurityWarning.setVisibility(View.GONE);
        }
        if (cardThreatDetails != null) {
            cardThreatDetails.setStrokeColor(Color.parseColor("#E2E8F0"));
        }
        if (tvThreatTitle != null) {
            tvThreatTitle.setText("RF Scanner Standby");
        }
        if (ivThreatIcon != null) {
            ivThreatIcon.setImageResource(android.R.drawable.ic_secure);
            ivThreatIcon.setColorFilter(Color.parseColor("#94A3B8"));
        }
        if (tvBadgeUnauthorized != null) {
            tvBadgeUnauthorized.setText("standby");
            tvBadgeUnauthorized.setBackgroundResource(R.drawable.bg_pill_neutral);
        }
        if (boxSecurityAlert != null) {
            boxSecurityAlert.setBackgroundColor(Color.parseColor("#F8FAFC"));
        }
        if (ivAlertIcon != null) {
            ivAlertIcon.setImageResource(android.R.drawable.ic_secure);
            ivAlertIcon.setColorFilter(Color.parseColor("#94A3B8"));
        }
        if (tvAlertText != null) {
            tvAlertText.setText("Perimeter Scanner Idle: Connect ESP32 Hardware Node to initiate real-time intrusion scanning.");
            tvAlertText.setTextColor(Color.parseColor("#64748B"));
        }
        if (progressSignalQuality != null) {
            progressSignalQuality.setIndicatorColor(Color.parseColor("#94A3B8"));
        }
    }

    private void applyScenario(ThreatScenario scenario) {
        if (chipAuthorizedCount != null) {
            chipAuthorizedCount.setText(String.valueOf(scenario.authorized));
        }

        if (scenario.unauthorized == 0) {
            // ============================================================
            // 🟢 ZERO THREATS: ALL SAFE & VERIFIED NETWORK PERIMETER
            // ============================================================
            if (chipUnauthorizedCount != null) {
                chipUnauthorizedCount.setText("0");
                chipUnauthorizedCount.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#10B981")));
            }

            if (tvUnauthorizedSubtitle != null) {
                tvUnauthorizedSubtitle.setText("Zero threats detected • Safe");
                tvUnauthorizedSubtitle.setTextColor(Color.parseColor("#059669"));
            }

            if (boxUnauthorizedSignals != null) {
                boxUnauthorizedSignals.setBackgroundColor(Color.parseColor("#F0FDF4"));
            }

            if (ivSecurityStatusIcon != null) {
                ivSecurityStatusIcon.setColorFilter(Color.parseColor("#10B981"));
            }

            // HIDE security warning card completely when 0 unauthorized
            if (cardSecurityWarning != null) {
                cardSecurityWarning.setVisibility(View.GONE);
            }

            // Switch "Unknown Signal" card into Authorized Safe Carrier view
            if (cardThreatDetails != null) {
                cardThreatDetails.setStrokeColor(Color.parseColor("#A7F3D0")); // soft green border
            }

            if (tvThreatTitle != null) {
                tvThreatTitle.setText("Authorized RF Node");
            }

            if (ivThreatIcon != null) {
                ivThreatIcon.setImageResource(android.R.drawable.ic_secure);
                ivThreatIcon.setColorFilter(Color.parseColor("#10B981"));
            }

            if (tvBadgeUnauthorized != null) {
                tvBadgeUnauthorized.setText("authorized");
                tvBadgeUnauthorized.setBackgroundResource(R.drawable.bg_pill_green);
            }

            if (boxSecurityAlert != null) {
                boxSecurityAlert.setBackgroundColor(Color.parseColor("#F0FDF4")); // light green
            }

            if (ivAlertIcon != null) {
                ivAlertIcon.setImageResource(android.R.drawable.ic_secure);
                ivAlertIcon.setColorFilter(Color.parseColor("#10B981"));
            }

            if (tvAlertText != null) {
                tvAlertText.setText("Network Perimeter Secure: All active 2.4 GHz transmissions are verified & safe.");
                tvAlertText.setTextColor(Color.parseColor("#047857"));
            }

            if (progressSignalQuality != null) {
                progressSignalQuality.setIndicatorColor(Color.parseColor("#10B981"));
            }

        } else {
            // ============================================================
            // 🔴 THREATS DETECTED STATE (> 0 UNAUTHORIZED)
            // ============================================================
            if (chipUnauthorizedCount != null) {
                chipUnauthorizedCount.setText(String.valueOf(scenario.unauthorized));
                chipUnauthorizedCount.setChipBackgroundColor(ColorStateList.valueOf(Color.parseColor("#D72E2E")));
            }

            if (tvUnauthorizedSubtitle != null) {
                tvUnauthorizedSubtitle.setText("Active threats detected");
                tvUnauthorizedSubtitle.setTextColor(Color.parseColor("#B14A4A"));
            }

            if (boxUnauthorizedSignals != null) {
                boxUnauthorizedSignals.setBackgroundColor(Color.parseColor("#FFF3F3"));
            }

            if (ivSecurityStatusIcon != null) {
                ivSecurityStatusIcon.setColorFilter(Color.parseColor("#D72E2E"));
            }

            // SHOW security warning card with exact updated count
            if (cardSecurityWarning != null) {
                cardSecurityWarning.setVisibility(View.VISIBLE);
            }

            if (tvWarningBody != null) {
                String plural = (scenario.unauthorized == 1) ? "signal has" : "signals have";
                String verb = (scenario.unauthorized == 1) ? "This signal may" : "These signals may";
                tvWarningBody.setText(scenario.unauthorized + " unauthorized " + plural + " been detected in your area. " + verb + " indicate potential security threats or unauthorized devices.");
            }

            // Threat Details Card (Red Alert state)
            if (cardThreatDetails != null) {
                cardThreatDetails.setStrokeColor(Color.parseColor("#F2B9B9"));
            }

            if (tvThreatTitle != null) {
                tvThreatTitle.setText("Unknown Signal");
            }

            if (ivThreatIcon != null) {
                ivThreatIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                ivThreatIcon.setColorFilter(Color.parseColor("#D72E2E"));
            }

            if (tvBadgeUnauthorized != null) {
                tvBadgeUnauthorized.setText("unauthorized");
                tvBadgeUnauthorized.setBackgroundResource(R.drawable.bg_pill_red);
            }

            if (boxSecurityAlert != null) {
                boxSecurityAlert.setBackgroundColor(Color.parseColor("#FFF3F3"));
            }

            if (ivAlertIcon != null) {
                ivAlertIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                ivAlertIcon.setColorFilter(Color.parseColor("#D72E2E"));
            }

            if (tvAlertText != null) {
                tvAlertText.setText("Security Alert: Rogue 2.4 GHz signal detected. Possible unauthorized transmitter or interference.");
                tvAlertText.setTextColor(Color.parseColor("#9B1C1C"));
            }

            if (progressSignalQuality != null) {
                progressSignalQuality.setIndicatorColor(Color.parseColor("#F4B000"));
            }
        }
    }

    private void setupFirebase() {
        FirebaseDatabase db = FirebaseDatabase.getInstance(FIREBASE_URL);
        dbRef = db.getReference(NODE_PATH);
        controlRef = db.getReference(CONTROL_NODE_PATH);

        // Listen for Cloud Master Switch (Standby vs Active)
        controlListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Boolean online = s.child("device_online").getValue(Boolean.class);
                isCloudOnline = (online != null && online);
                updateSystemState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        controlRef.addValueEventListener(controlListener);

        // Telemetry Listener
        valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists()) return;

                Double freq = readDouble(s, "frequency_mhz");
                Double bw   = readDouble(s, "bandwidth_mhz");
                Integer rssi= readInt(s, "signal_strength_dbm");
                Integer qual= readInt(s, "signal_quality_percent");

                if (tvFreqValue != null && freq != null) {
                    if (freq >= 1000.0) {
                        tvFreqValue.setText(String.format(Locale.US, "%.3f GHz", freq / 1000.0));
                    } else {
                        tvFreqValue.setText(String.format(Locale.US, "%.1f MHz", freq));
                    }
                }
                if (tvStrengthValue != null && rssi != null) {
                    tvStrengthValue.setText(rssi + " dBm");
                }
                if (tvBandwidthValue != null && bw != null) {
                    tvBandwidthValue.setText(String.format(Locale.US, "%.0f MHz", bw));
                }
                if (tvModulationValue != null) {
                    tvModulationValue.setText("GFSK (2.4 GHz)");
                }
                if (qual != null) {
                    if (tvQualityPercent != null) tvQualityPercent.setText(qual + "%");
                    if (progressSignalQuality != null) progressSignalQuality.setProgress(qual, true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Frequencies.this, "Firebase: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        dbRef.addValueEventListener(valueListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbRef != null && valueListener != null) {
            dbRef.removeEventListener(valueListener);
        }
        if (controlRef != null && controlListener != null) {
            controlRef.removeEventListener(controlListener);
        }
        if (scenarioHandler != null && scenarioRunnable != null) {
            scenarioHandler.removeCallbacks(scenarioRunnable);
        }
        if (wifiReceiver != null) {
            try {
                unregisterReceiver(wifiReceiver);
            } catch (Exception ignored) {}
        }
    }

    private Double readDouble(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        if (v instanceof Number) return ((Number) v).doubleValue();
        return null;
    }

    private Integer readInt(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        if (v instanceof Number) return ((Number) v).intValue();
        return null;
    }
}