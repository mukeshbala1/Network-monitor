package com.sourav.kavach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.Locale;

public class RFSpectrumView extends View {

    private Paint gridPaint;
    private Paint axisTextPaint;
    private Paint barPaint;
    private Paint peakLinePaint;
    private Paint markerPaint;
    private Paint markerTextPaint;
    private Paint peakGlowPaint;

    // 2.4 GHz ISM Band (2.400 - 2.485 GHz)
    private float startFreq = 2400.0f;
    private float endFreq   = 2485.0f;

    // Real detected hardware signal parameters
    private float detectedFreq = 2424.0f;
    private float detectedRssi = -62.0f;
    private float detectedBw   = 2.0f;
    private boolean hasSignal  = false;

    private final int BARS_COUNT = 45;

    public RFSpectrumView(Context context) {
        super(context);
        init();
    }

    public RFSpectrumView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RFSpectrumView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#E0E4EC"));
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{6, 6}, 0));

        axisTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisTextPaint.setColor(Color.parseColor("#8A94A6"));
        axisTextPaint.setTextSize(20f);

        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setStyle(Paint.Style.FILL);

        peakLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        peakLinePaint.setColor(Color.parseColor("#6A5CF0"));
        peakLinePaint.setStrokeWidth(3f);
        peakLinePaint.setStyle(Paint.Style.STROKE);
        peakLinePaint.setPathEffect(new DashPathEffect(new float[]{8, 6}, 0));

        markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerPaint.setColor(Color.parseColor("#1E293B"));
        markerPaint.setStyle(Paint.Style.FILL);

        markerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markerTextPaint.setColor(Color.WHITE);
        markerTextPaint.setTextSize(22f);
        markerTextPaint.setTextAlign(Paint.Align.CENTER);
        markerTextPaint.setFakeBoldText(true);

        peakGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        peakGlowPaint.setColor(Color.parseColor("#336A5CF0"));
        peakGlowPaint.setStyle(Paint.Style.FILL);
    }

    public void setDetectedSignal(float freqMhz, float rssiDbm, float bwMhz) {
        this.detectedFreq = freqMhz;
        this.detectedRssi = (rssiDbm > 0) ? -rssiDbm : rssiDbm;
        this.detectedBw   = (bwMhz > 0) ? bwMhz : 2.0f;
        this.hasSignal    = true;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float paddingLeft = 35f;
        float paddingRight = 35f;
        float paddingTop = 85f; // space for top floating badge
        float paddingBottom = 55f;

        float plotWidth = width - paddingLeft - paddingRight;
        float plotHeight = height - paddingTop - paddingBottom;

        // Draw frequency axis grid and labels with Channel Numbers
        float[] freqLabels = {2400.0f, 2424.0f, 2450.0f, 2480.0f};
        String[] channelNames = {"CH 0", "CH 24", "CH 50", "CH 80"};

        for (int idx = 0; idx < freqLabels.length; idx++) {
            float freq = freqLabels[idx];
            float ratio = (freq - startFreq) / (endFreq - startFreq);
            float x = paddingLeft + ratio * plotWidth;

            canvas.drawLine(x, paddingTop, x, paddingTop + plotHeight, gridPaint);

            // Channel tag (highlight active CH 24)
            if (freq == 2424.0f) {
                axisTextPaint.setColor(Color.parseColor("#6A5CF0"));
                axisTextPaint.setFakeBoldText(true);
            } else {
                axisTextPaint.setColor(Color.parseColor("#8A94A6"));
                axisTextPaint.setFakeBoldText(false);
            }
            canvas.drawText(channelNames[idx], x - 25f, height - 28f, axisTextPaint);
            canvas.drawText(String.format(Locale.US, "%.3fG", freq / 1000.0f), x - 28f, height - 10f, axisTextPaint);
        }

        // Draw spectrum bars
        float barWidth = (plotWidth / BARS_COUNT) * 0.72f;
        float gap = (plotWidth / BARS_COUNT) * 0.28f;

        float peakX = 0f;
        float peakY = 0f;
        float maxBarHeight = 0f;

        for (int i = 0; i < BARS_COUNT; i++) {
            float binFreq = startFreq + ((float) i / (BARS_COUNT - 1)) * (endFreq - startFreq);
            float x = paddingLeft + i * (barWidth + gap);

            // Distance to detected hardware frequency peak
            float distance = Math.abs(binFreq - detectedFreq);

            float normalizedStrength = (Math.abs(detectedRssi) > 20f) ? (105f + detectedRssi) / 65f : 0.7f;
            if (normalizedStrength < 0.20f) normalizedStrength = 0.20f;
            if (normalizedStrength > 0.95f) normalizedStrength = 0.95f;

            float barHeight;
            if (hasSignal && distance < 6.0f) {
                // Peak region around detected hardware channel (Gaussian RF envelope)
                float bellCurve = (float) Math.exp(-(distance * distance) / 8.0f);
                barHeight = (plotHeight * normalizedStrength * bellCurve);
            } else {
                // Calibrated deterministic noise floor across 2.4 GHz band
                barHeight = 16f + 4f * (float) Math.sin((binFreq - startFreq) * 0.18f);
            }

            if (barHeight > plotHeight) barHeight = plotHeight;
            float topY = paddingTop + plotHeight - barHeight;

            if (barHeight > maxBarHeight) {
                maxBarHeight = barHeight;
                peakX = x + barWidth / 2f;
                peakY = topY;
            }

            // Spectrum color styling: active channel is electric purple/blue, noise floor is clean slate
            if (hasSignal && distance < 4.5f) {
                barPaint.setShader(new LinearGradient(
                        x, topY, x, paddingTop + plotHeight,
                        Color.parseColor("#4A90E2"),
                        Color.parseColor("#6A5CF0"),
                        Shader.TileMode.CLAMP
                ));
            } else {
                barPaint.setShader(new LinearGradient(
                        x, topY, x, paddingTop + plotHeight,
                        Color.parseColor("#CBD5E1"),
                        Color.parseColor("#E2E8F0"),
                        Shader.TileMode.CLAMP
                ));
            }

            RectF barRect = new RectF(x, topY, x + barWidth, paddingTop + plotHeight);
            canvas.drawRoundRect(barRect, 6f, 6f, barPaint);
        }

        // Draw Peak Glow & Banner for Channel 24
        if (peakX > 0 && hasSignal) {
            // Glow around the peak
            canvas.drawCircle(peakX, peakY, 24f, peakGlowPaint);
            canvas.drawLine(peakX, peakY, peakX, paddingTop, peakLinePaint);

            // Channel 24 marker banner
            String markerText = String.format(Locale.US, "CH 24: %.3f GHz (%.0f dBm)", detectedFreq / 1000.0f, detectedRssi);

            float textWidth = markerTextPaint.measureText(markerText);
            float boxWidth = textWidth + 36f;
            float boxHeight = 46f;

            float boxLeft = peakX - boxWidth / 2f;
            if (boxLeft < paddingLeft) boxLeft = paddingLeft;
            if (boxLeft + boxWidth > width - paddingRight) boxLeft = width - paddingRight - boxWidth;

            float boxTop = 22f;
            RectF markerRect = new RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight);
            canvas.drawRoundRect(markerRect, 12f, 12f, markerPaint);

            canvas.drawText(markerText, boxLeft + boxWidth / 2f, boxTop + 30f, markerTextPaint);

            // Indicator triangle
            Path triPath = new Path();
            triPath.moveTo(peakX - 8f, boxTop + boxHeight);
            triPath.lineTo(peakX + 8f, boxTop + boxHeight);
            triPath.lineTo(peakX, boxTop + boxHeight + 9f);
            triPath.close();
            canvas.drawPath(triPath, markerPaint);
        }
    }
}
