package com.sourav.kavach;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Firebase Realtime Database Configuration
    public static final String FIREBASE_URL = "https://kavach-rf-default-rtdb.firebaseio.com";
    public static final String NODE_PATH = "kavach/readings/esp32_nrf24";

    // Dynamic Hardware Connectivity State (Industrial IoT Heartbeat Watchdog)
    private static final long PACKET_TIMEOUT_SECONDS = 15L;
    private Long lastHardwareTimestamp = null;

    private TextView tvLastUpdated, tvActive, tvAvgSignal;
    private TextView tvFrequency1, tvStrength1, tvBandwidth, tvModulation1, tvActivePill1;
    private ImageView ivDot1;
    private LinearProgressIndicator progressQuality1;
    private ImageButton btnRefresh;

    private DatabaseReference refFM1;
    private ValueEventListener valueListener;

    // Periodic watchdog to dynamically evaluate active / standby status
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            updateDeviceConnectivityStatus();
            watchdogHandler.postDelayed(this, 3000); // Check every 3 seconds
        }
    };

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

        setupNavigationChips();
        bindViews();
        attachFirebaseListener();
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

        tvFrequency1     = findViewById(R.id.tvFrequency1);
        tvStrength1      = findViewById(R.id.tvStrength1);
        tvBandwidth      = findViewById(R.id.tvBandwidth);
        tvModulation1    = findViewById(R.id.tvModulation1);
        tvActivePill1    = findViewById(R.id.tvActivePill1);
        ivDot1           = findViewById(R.id.ivDot1);
        progressQuality1 = findViewById(R.id.progressQuality1);

        if (progressQuality1 != null) progressQuality1.setMax(100);

        // Initial default state before hardware connects
        updateDeviceConnectivityStatus();

        // Clean single-click refresh button
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> singleRefresh());
        }
    }

    private void attachFirebaseListener() {
        valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists()) return;
                applyTelemetryData(s);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Firebase error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        refFM1.addValueEventListener(valueListener);
    }

    private void applyTelemetryData(DataSnapshot s) {
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

        if (ts != null) {
            lastHardwareTimestamp = ts;
            if (tvLastUpdated != null) {
                tvLastUpdated.setText("Last updated: " + formatTime(ts));
            }
        }

        updateDeviceConnectivityStatus();
    }

    /**
     * Genuine IoT Industrial Connectivity Status Watchdog:
     * If (currentTime - last_packet_timestamp <= 15s) -> Node is Active (Green)
     * Else -> Node is Standby / Offline (Gray)
     */
    private void updateDeviceConnectivityStatus() {
        long currentEpochSec = System.currentTimeMillis() / 1000L;
        boolean isActive = (lastHardwareTimestamp != null &&
                (currentEpochSec - lastHardwareTimestamp <= PACKET_TIMEOUT_SECONDS));

        if (isActive) {
            if (tvActivePill1 != null) {
                tvActivePill1.setText("active");
                tvActivePill1.setBackgroundResource(R.drawable.bg_pill_green);
                tvActivePill1.setTextColor(Color.WHITE);
            }
            if (tvActive != null) tvActive.setText("1");
            if (ivDot1 != null) ivDot1.setBackgroundResource(R.drawable.shape_dot_green);
        } else {
            if (tvActivePill1 != null) {
                tvActivePill1.setText("standby / offline");
                tvActivePill1.setBackgroundResource(R.drawable.bg_pill_neutral);
                tvActivePill1.setTextColor(Color.WHITE);
            }
            if (tvActive != null) tvActive.setText("0");
            if (ivDot1 != null) ivDot1.setBackgroundResource(R.drawable.shape_dot_gray);
        }
    }

    /**
     * Clean single-click refresh: Queries the latest live telemetry from Firebase
     */
    private void singleRefresh() {
        Toast.makeText(this, "Fetching live telemetry...", Toast.LENGTH_SHORT).show();
        refFM1.get().addOnSuccessListener(s -> {
            if (s.exists()) {
                applyTelemetryData(s);
                Toast.makeText(this, "Telemetry Up to Date", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No telemetry received from ESP32 yet", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Refresh error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        watchdogHandler.post(watchdogRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        watchdogHandler.removeCallbacks(watchdogRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refFM1 != null && valueListener != null) {
            refFM1.removeEventListener(valueListener);
        }
        watchdogHandler.removeCallbacks(watchdogRunnable);
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