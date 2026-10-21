package com.sourav.kavach;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
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

    public static final String FIREBASE_URL = "https://kavach-rf-default-rtdb.firebaseio.com";
    public static final String NODE_TELEMETRY = "kavach/readings/esp32_nrf24";
    public static final String NODE_SECURITY  = "kavach/security_status";

    // UI Views
    private Chip chipUnauthorizedCount, chipAuthorizedCount;
    private MaterialCardView cardSecurityStatus, cardSecurityWarning, cardThreatDetails;
    private TextView tvUnauthorizedSubtitle, tvWarningBody, tvThreatTitle, tvBadgeUnauthorized, tvAlertText;
    private LinearLayout boxUnauthorizedSignals, boxSecurityAlert;
    private ImageView ivSecurityStatusIcon, ivThreatIcon, ivAlertIcon;

    private TextView tvFreqValue, tvStrengthValue, tvBandwidthValue, tvModulationValue, tvQualityPercent;
    private LinearProgressIndicator progressSignalQuality;

    // Firebase References & Listeners
    private DatabaseReference refTelemetry;
    private DatabaseReference refSecurity;
    private ValueEventListener telemetryListener;
    private ValueEventListener securityListener;

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
        setupFirebase();
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

        // Initial default state: Safe perimeter until hardware updates
        updateSecurityUI(0, 1, "Network Perimeter Secure: All active 2.4 GHz transmissions verified & safe.");
    }

    private void setupFirebase() {
        FirebaseDatabase db = FirebaseDatabase.getInstance(FIREBASE_URL);
        refTelemetry = db.getReference(NODE_TELEMETRY);
        refSecurity  = db.getReference(NODE_SECURITY);

        // 1. Live Hardware Telemetry Listener (Frequency, RSSI, Bandwidth, Modulation, Quality)
        telemetryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists()) return;

                Double freq = readDouble(s, "frequency_mhz");
                Double bw   = readDouble(s, "bandwidth_mhz");
                Integer rssi= readInt(s, "signal_strength_dbm");
                Integer qual= readInt(s, "signal_quality_percent");
                String mod  = (String) s.child("modulation").getValue();

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
                    tvModulationValue.setText(mod != null ? mod : "GFSK (2.4 GHz)");
                }
                if (qual != null) {
                    if (tvQualityPercent != null) tvQualityPercent.setText(qual + "%");
                    if (progressSignalQuality != null) progressSignalQuality.setProgress(qual, true);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Frequencies.this, "Telemetry error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        refTelemetry.addValueEventListener(telemetryListener);

        // 2. Live Security Node Listener (unauthorized_count, authorized_count, threat_description)
        securityListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                if (!s.exists()) return;

                Integer unauth = readInt(s, "unauthorized_count");
                Integer auth   = readInt(s, "authorized_count");
                String threatDesc = (String) s.child("threat_description").getValue();

                int unauthCount = (unauth != null) ? unauth : 0;
                int authCount   = (auth != null) ? auth : 1;

                updateSecurityUI(unauthCount, authCount, threatDesc);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Frequencies.this, "Security status error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        refSecurity.addValueEventListener(securityListener);
    }

    /**
     * Pure Dynamic Security Logic driven 100% by ESP32 Cloud Node:
     * - When unauthorized_count == 0: Safe state, hide warning card, green theme.
     * - When unauthorized_count > 0: Threat state, show warning card with real count and description.
     */
    private void updateSecurityUI(int unauthorizedCount, int authorizedCount, String threatDesc) {
        if (chipAuthorizedCount != null) {
            chipAuthorizedCount.setText(String.valueOf(authorizedCount));
        }

        if (unauthorizedCount == 0) {
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
                ivSecurityStatusIcon.setImageResource(android.R.drawable.ic_secure);
                ivSecurityStatusIcon.setColorFilter(Color.parseColor("#10B981"));
            }

            // Hide security warning card completely
            if (cardSecurityWarning != null) {
                cardSecurityWarning.setVisibility(View.GONE);
            }

            // Switch details card into Authorized Safe Carrier view
            if (cardThreatDetails != null) {
                cardThreatDetails.setStrokeColor(Color.parseColor("#A7F3D0"));
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
                boxSecurityAlert.setBackgroundColor(Color.parseColor("#F0FDF4"));
            }

            if (ivAlertIcon != null) {
                ivAlertIcon.setImageResource(android.R.drawable.ic_secure);
                ivAlertIcon.setColorFilter(Color.parseColor("#10B981"));
            }

            if (tvAlertText != null) {
                String safeMsg = (threatDesc != null && !threatDesc.trim().isEmpty() && !threatDesc.contains("Rogue"))
                        ? threatDesc
                        : "Network Perimeter Secure: All active 2.4 GHz transmissions verified & safe.";
                tvAlertText.setText(safeMsg);
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
                chipUnauthorizedCount.setText(String.valueOf(unauthorizedCount));
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
                ivSecurityStatusIcon.setImageResource(android.R.drawable.ic_dialog_alert);
                ivSecurityStatusIcon.setColorFilter(Color.parseColor("#D72E2E"));
            }

            // Show security warning card with exact updated count
            if (cardSecurityWarning != null) {
                cardSecurityWarning.setVisibility(View.VISIBLE);
            }

            if (tvWarningBody != null) {
                String plural = (unauthorizedCount == 1) ? "signal has" : "signals have";
                String verb = (unauthorizedCount == 1) ? "This signal may" : "These signals may";
                tvWarningBody.setText(unauthorizedCount + " unauthorized " + plural + " been detected in your area. " + verb + " indicate potential security threats or unauthorized devices.");
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
                String desc = (threatDesc != null && !threatDesc.trim().isEmpty())
                        ? threatDesc
                        : "Security Alert: Rogue 2.4 GHz signal detected. Possible unauthorized transmitter or interference.";
                tvAlertText.setText(desc);
                tvAlertText.setTextColor(Color.parseColor("#9B1C1C"));
            }

            if (progressSignalQuality != null) {
                progressSignalQuality.setIndicatorColor(Color.parseColor("#F4B000"));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refTelemetry != null && telemetryListener != null) {
            refTelemetry.removeEventListener(telemetryListener);
        }
        if (refSecurity != null && securityListener != null) {
            refSecurity.removeEventListener(securityListener);
        }
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
}