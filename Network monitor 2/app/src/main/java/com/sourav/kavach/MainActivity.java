package com.sourav.kavach;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    // 🔴 Firebase Realtime Database URL & Node Paths
    public static final String FIREBASE_URL = "https://kavach-rf-default-rtdb.firebaseio.com";
    public static final String NODE_PATH = "kavach/readings/esp32_nrf24";
    public static final String CONTROL_NODE_PATH = "kavach/system_control";

    // 🔴 Cloud Master Switch state
    private boolean isDeviceOnline = false;
    private boolean isLiveStreaming = false;

    private TextView tvLastUpdated, tvActive, tvAvgSignal;
    private TextView tvFrequency1, tvStrength1, tvBandwidth, tvModulation1, tvActivePill1;
    private ImageView ivDot1;
    private LinearProgressIndicator progressQuality1;
    private ImageButton btnRefresh;

    private DatabaseReference refFM1;
    private DatabaseReference refControl;
    private ValueEventListener valueListener;
    private ValueEventListener controlListener;

    private BroadcastReceiver wifiReceiver;
    private ConnectivityManager.NetworkCallback networkCallback;

    private Handler simulatorHandler;
    private Runnable simulatorRunnable;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        FirebaseApp.initializeApp(this);
        FirebaseDatabase db = FirebaseDatabase.getInstance(FIREBASE_URL);
        refFM1 = db.getReference(NODE_PATH);
        refControl = db.getReference(CONTROL_NODE_PATH);

        setupNavigationChips();
        bindViews();
        attachListeners();
        setupRefreshButton();
        setupNetworkMonitoring();
        checkWifiAndPrompt();
    }

    private void setupNavigationChips() {
        Chip chipFrequencies = findViewById(R.id.chipFrequencies);
        Chip chipGraphs      = findViewById(R.id.chipGraphs);
        Chip chipSpectrum    = findViewById(R.id.chipSpectrum);

        if (chipFrequencies != null) {
            chipFrequencies.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, Frequencies.class);
                i.putExtra("nodePath1", NODE_PATH);
                startActivity(i);
            });
        }

        if (chipGraphs != null) {
            chipGraphs.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, Graphs.class);
                startActivity(i);
            });
        }

        if (chipSpectrum != null) {
            chipSpectrum.setOnClickListener(v -> {
                Intent i = new Intent(MainActivity.this, Spectrum.class);
                startActivity(i);
            });
        }
    }

    private void bindViews() {
        tvLastUpdated    = findViewById(R.id.tvLastUpdated);
        tvActive         = findViewById(R.id.tvActive);
        tvAvgSignal      = findViewById(R.id.tvAvgSignal);
        btnRefresh       = findViewById(R.id.btnRefresh);

        if (tvActive != null) tvActive.setText("0");

        tvFrequency1     = findViewById(R.id.tvFrequency1);
        tvStrength1      = findViewById(R.id.tvStrength1);
        tvBandwidth      = findViewById(R.id.tvBandwidth);
        tvModulation1    = findViewById(R.id.tvModulation1);
        tvActivePill1    = findViewById(R.id.tvActivePill1);
        ivDot1           = findViewById(R.id.ivDot1);
        progressQuality1 = findViewById(R.id.progressQuality1);

        if (progressQuality1 != null) progressQuality1.setMax(100);
        if (tvActivePill1 != null) tvActivePill1.setText("standby");
        if (ivDot1 != null) ivDot1.setBackgroundResource(R.drawable.shape_dot_gray);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemState();
    }

    private void setupRefreshButton() {
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> singleRefresh());
        }
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

        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        runOnUiThread(() -> updateSystemState());
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        runOnUiThread(() -> updateSystemState());
                    }

                    @Override
                    public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                        runOnUiThread(() -> updateSystemState());
                    }
                };
                cm.registerDefaultNetworkCallback(networkCallback);
            }
        } catch (Exception ignored) {}
    }

    private void checkWifiAndPrompt() {
        if (!isWifiConnected()) {
            if (isFinishing()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Wi-Fi Required")
                    .setMessage("Please turn ON Wi-Fi to connect ESP32.")
                    .setPositiveButton("Turn ON", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        }
    }

    // 🔒 Strict Wi-Fi Enforcement:
    // If the Wi-Fi toggle on the phone is turned OFF, this ALWAYS returns false!
    private boolean isWifiConnected() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) {
                return false; // Wi-Fi is physically switched OFF in Quick Settings
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
        boolean canStream = isWifiConnected() && isDeviceOnline;

        if (canStream) {
            if (!isLiveStreaming) {
                isLiveStreaming = true;
                startVirtualIoTDevice();
            }
            if (tvActivePill1 != null) {
                tvActivePill1.setText("active");
                tvActivePill1.setBackgroundResource(R.drawable.bg_pill_green);
            }
            if (ivDot1 != null) {
                ivDot1.setBackgroundResource(R.drawable.shape_dot_green);
            }
            if (tvActive != null) {
                tvActive.setText("1");
            }
        } else {
            if (isLiveStreaming) {
                isLiveStreaming = false;
                stopVirtualIoTDevice();
            }
            if (tvActivePill1 != null) {
                tvActivePill1.setText("standby");
                tvActivePill1.setBackgroundResource(R.drawable.bg_pill_neutral);
            }
            if (ivDot1 != null) {
                ivDot1.setBackgroundResource(R.drawable.shape_dot_gray);
            }
            if (tvActive != null) {
                tvActive.setText("0");
            }
        }
    }

    private void attachListeners() {
        // 1. Cloud Master Switch Listener (Controls whether hardware is Online or Standby across all devices)
        controlListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Boolean online = s.child("device_online").getValue(Boolean.class);
                isDeviceOnline = (online != null && online);
                updateSystemState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        if (refControl != null) {
            refControl.addValueEventListener(controlListener);
        }

        // 2. Telemetry Listener
        valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists()) return;

                Double freq = readDouble(s, "frequency_mhz");
                Double bw   = readDouble(s, "bandwidth_mhz");
                Integer rssi= readInt(s, "signal_strength_dbm");
                Integer qual= readInt(s, "signal_quality_percent");
                Long ts     = readLong(s, "timestamp");
                String mod  = (String) s.child("modulation").getValue();

                if (tvFrequency1  != null && freq != null) tvFrequency1.setText(formatFreq(freq));
                if (tvStrength1   != null && rssi != null) {
                    tvStrength1.setText(rssi + " dBm");
                    if (tvAvgSignal != null) tvAvgSignal.setText(rssi + " dBm");
                }
                if (tvBandwidth   != null && bw != null)   tvBandwidth.setText(formatBandwidthMHz(bw));
                if (tvModulation1 != null) tvModulation1.setText(mod != null ? mod : "GFSK");
                if (progressQuality1 != null && qual != null) progressQuality1.setProgress(coerce01(qual), true);

                if (tvLastUpdated != null) {
                    tvLastUpdated.setText("Last updated: " + formatTime(ts));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Firebase error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        refFM1.addValueEventListener(valueListener);
    }

    // ⚡ Virtual IoT Simulator: Pushes realistic telemetry to Firebase when enabled
    private void startVirtualIoTDevice() {
        stopVirtualIoTDevice(); // ensure single instance

        simulatorHandler = new Handler(Looper.getMainLooper());
        simulatorRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isLiveStreaming) return;

                double baseFreq = 2424.0;
                double offset = (random.nextInt(5) - 2) * 0.2;
                double freq = Math.round((baseFreq + offset) * 10.0) / 10.0;

                int rssi = -55 - random.nextInt(18); // -55 to -73 dBm
                int quality = 82 + random.nextInt(16); // 82% to 98%
                long currentTimestamp = System.currentTimeMillis() / 1000L;

                Map<String, Object> telemetry = new HashMap<>();
                telemetry.put("hardware", "ESP32 + NRF24L01+ PA Antenna");
                telemetry.put("channel", 24);
                telemetry.put("frequency_mhz", freq);
                telemetry.put("bandwidth_mhz", 2.0);
                telemetry.put("modulation", "GFSK");
                telemetry.put("signal_strength_dbm", rssi);
                telemetry.put("signal_quality_percent", quality);
                telemetry.put("timestamp", currentTimestamp);

                refFM1.setValue(telemetry);

                // Run next reading in 3.5 seconds
                simulatorHandler.postDelayed(this, 3500);
            }
        };

        simulatorHandler.postDelayed(simulatorRunnable, 1000);
    }

    private void stopVirtualIoTDevice() {
        if (simulatorHandler != null && simulatorRunnable != null) {
            simulatorHandler.removeCallbacks(simulatorRunnable);
        }
    }

    private void singleRefresh() {
        if (!isWifiConnected()) {
            Toast.makeText(this, "Wi-Fi is OFF. Enable Wi-Fi to refresh telemetry.", Toast.LENGTH_SHORT).show();
            checkWifiAndPrompt();
            return;
        }

        String msg = isDeviceOnline ? "Refreshed (Device Active)" : "Refreshed (Hardware is in Standby)";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

        if (tvLastUpdated != null) {
            tvLastUpdated.setText("Last updated: " + formatTime(System.currentTimeMillis() / 1000L));
        }

        refFM1.get().addOnSuccessListener(s -> {
            Double f = readDouble(s, "frequency_mhz");
            Double b = readDouble(s, "bandwidth_mhz");
            Integer r= readInt(s, "signal_strength_dbm");
            Integer q= readInt(s, "signal_quality_percent");
            Long ts  = readLong(s, "timestamp");

            if (tvFrequency1 != null && f != null) tvFrequency1.setText(formatFreq(f));
            if (tvStrength1  != null && r != null) {
                tvStrength1.setText(r + " dBm");
                if (tvAvgSignal != null) tvAvgSignal.setText(r + " dBm");
            }
            if (tvBandwidth  != null && b != null) tvBandwidth.setText(formatBandwidthMHz(b));
            if (progressQuality1 != null && q != null) progressQuality1.setProgress(coerce01(q), true);
            if (tvLastUpdated != null && ts != null) tvLastUpdated.setText("Last updated: " + formatTime(ts));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refFM1 != null && valueListener != null) {
            refFM1.removeEventListener(valueListener);
        }
        if (refControl != null && controlListener != null) {
            refControl.removeEventListener(controlListener);
        }
        if (wifiReceiver != null) {
            try {
                unregisterReceiver(wifiReceiver);
            } catch (Exception ignored) {}
        }
        if (networkCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
            } catch (Exception ignored) {}
        }
        stopVirtualIoTDevice();
    }

    private String formatFreq(double mhz) {
        if (mhz >= 1000.0) {
            return String.format(Locale.US, "%.3f GHz", mhz / 1000.0);
        }
        return String.format(Locale.US, "%.1f MHz", mhz);
    }

    private String formatBandwidthMHz(double mhz) {
        if (Math.abs(mhz - Math.round(mhz)) < 0.005) {
            return String.format(Locale.US, "%.0f MHz", mhz);
        }
        return String.format(Locale.US, "%.1f MHz", mhz);
    }

    private String formatTime(Long tsSeconds) {
        long ms = ((tsSeconds != null) ? tsSeconds : (System.currentTimeMillis() / 1000)) * 1000L;
        return new SimpleDateFormat("h:mm:ss a", Locale.getDefault()).format(new Date(ms));
    }

    private int coerce01(Integer v) {
        if (v == null) return 0;
        if (v < 0) return 0;
        if (v > 100) return 100;
        return v;
    }

    private Double readDouble(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) { try { return Double.parseDouble((String) v); } catch (Exception ignored) {} }
        return null;
    }

    private Integer readInt(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) { try { return Integer.parseInt((String) v); } catch (Exception ignored) {} }
        return null;
    }

    private Long readLong(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) { try { return Long.parseLong((String) v); } catch (Exception ignored) {} }
        return null;
    }
}