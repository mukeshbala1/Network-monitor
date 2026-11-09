package com.sourav.kavach;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

public class Graphs extends AppCompatActivity {

    private static final String FIREBASE_URL = "https://kavach-rf-default-rtdb.firebaseio.com";
    private static final String NODE_PATH = "kavach/readings/esp32_nrf24";

    private LiveSignalChartView liveSignalChartView;
    private TextView tvCurrentRssi, tvPeakRssi, tvSignalQualityStat;
    private TextView tvGraphFreqBadge, tvGraphBandwidth, tvEstimatedSnr;
    private ImageButton btnBack;

    private float peakRssi = -120f;
    private DatabaseReference dbRef;
    private ValueEventListener valueListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_graphs);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupFirebase();
    }

    private void initViews() {
        liveSignalChartView  = findViewById(R.id.liveSignalChartView);
        tvCurrentRssi        = findViewById(R.id.tvCurrentRssi);
        tvPeakRssi           = findViewById(R.id.tvPeakRssi);
        tvSignalQualityStat  = findViewById(R.id.tvSignalQualityStat);
        tvGraphFreqBadge     = findViewById(R.id.tvGraphFreqBadge);
        tvGraphBandwidth     = findViewById(R.id.tvGraphBandwidth);
        tvEstimatedSnr       = findViewById(R.id.tvEstimatedSnr);
        btnBack              = findViewById(R.id.btnBack);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void setupFirebase() {
        FirebaseDatabase db = FirebaseDatabase.getInstance(FIREBASE_URL);
        dbRef = db.getReference(NODE_PATH);

        valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists()) return;

                Double freq = readDouble(s, "frequency_mhz");
                Double bw   = readDouble(s, "bandwidth_mhz");
                Integer rssi= readInt(s, "signal_strength_dbm");
                Integer qual= readInt(s, "signal_quality_percent");

                if (rssi != null) {
                    float rssiVal = rssi.floatValue();
                    if (liveSignalChartView != null) {
                        liveSignalChartView.addPoint(rssiVal);
                    }
                    if (tvCurrentRssi != null) {
                        tvCurrentRssi.setText(rssi + " dBm");
                    }
                    if (rssiVal > peakRssi) {
                        peakRssi = rssiVal;
                        if (tvPeakRssi != null) {
                            tvPeakRssi.setText(String.format(Locale.US, "%.0f dBm", peakRssi));
                        }
                    }
                    if (tvEstimatedSnr != null) {
                        int snr = Math.max(10, (int) (105 + rssiVal));
                        tvEstimatedSnr.setText("+" + snr + " dB");
                    }
                }

                if (qual != null && tvSignalQualityStat != null) {
                    tvSignalQualityStat.setText(qual + "%");
                }

                if (freq != null && tvGraphFreqBadge != null) {
                    if (freq >= 1000.0) {
                        tvGraphFreqBadge.setText(String.format(Locale.US, "%.3f GHz", freq / 1000.0));
                    } else {
                        tvGraphFreqBadge.setText(String.format(Locale.US, "%.1f MHz", freq));
                    }
                }

                if (bw != null && tvGraphBandwidth != null) {
                    tvGraphBandwidth.setText(String.format(Locale.US, "%.0f MHz", bw));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Graphs.this, "Firebase: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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