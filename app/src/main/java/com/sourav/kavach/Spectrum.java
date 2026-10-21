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

public class Spectrum extends AppCompatActivity {

    private static final String FIREBASE_URL = "https://kavach-rf-default-rtdb.firebaseio.com";
    private static final String NODE_PATH = "kavach/readings/esp32_nrf24";

    private RFSpectrumView rfSpectrumView;
    private TextView tvSpectrumPeakFreq, tvSpectrumPeakRssi, tvSpectrumBandwidth;
    private ImageButton btnBack;

    private DatabaseReference dbRef;
    private ValueEventListener valueListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_spectrum);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupFirebase();
    }

    private void initViews() {
        rfSpectrumView        = findViewById(R.id.rfSpectrumView);
        tvSpectrumPeakFreq    = findViewById(R.id.tvSpectrumPeakFreq);
        tvSpectrumPeakRssi    = findViewById(R.id.tvSpectrumPeakRssi);
        tvSpectrumBandwidth   = findViewById(R.id.tvSpectrumBandwidth);
        btnBack               = findViewById(R.id.btnBack);

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

                float freqVal = (freq != null) ? freq.floatValue() : 2424.0f;
                float rssiVal = (rssi != null) ? rssi.floatValue() : -62.0f;
                float bwVal   = (bw != null)   ? bw.floatValue()   : 2.0f;

                if (rfSpectrumView != null) {
                    rfSpectrumView.setDetectedSignal(freqVal, rssiVal, bwVal);
                }

                if (tvSpectrumPeakFreq != null) {
                    if (freqVal >= 1000.0f) {
                        tvSpectrumPeakFreq.setText(String.format(Locale.US, "%.3f GHz", freqVal / 1000.0f));
                    } else {
                        tvSpectrumPeakFreq.setText(String.format(Locale.US, "%.1f MHz", freqVal));
                    }
                }
                if (tvSpectrumPeakRssi != null) {
                    tvSpectrumPeakRssi.setText(String.format(Locale.US, "%.0f dBm", rssiVal));
                }
                if (tvSpectrumBandwidth != null) {
                    tvSpectrumBandwidth.setText(String.format(Locale.US, "%.0f MHz", bwVal));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Spectrum.this, "Firebase: " + error.getMessage(), Toast.LENGTH_SHORT).show();
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