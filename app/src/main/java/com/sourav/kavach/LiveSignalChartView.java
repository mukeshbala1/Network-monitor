package com.sourav.kavach;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LiveSignalChartView extends View {

    private final List<Float> dataPoints = new ArrayList<>();
    private final int MAX_POINTS = 20;

    private Paint gridPaint;
    private Paint textPaint;
    private Paint linePaint;
    private Paint fillPaint;
    private Paint pointPaint;
    private Paint glowPaint;
    private Paint statusMsgPaint;

    private float minDb = -100f;
    private float maxDb = -40f;

    public LiveSignalChartView(Context context) {
        super(context);
        init();
    }

    public LiveSignalChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiveSignalChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#E0E4EC"));
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{8, 8}, 0));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#8A94A6"));
        textPaint.setTextSize(26f);

        statusMsgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        statusMsgPaint.setColor(Color.parseColor("#94A3B8"));
        statusMsgPaint.setTextSize(26f);
        statusMsgPaint.setTextAlign(Paint.Align.CENTER);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#4A90E2"));
        linePaint.setStrokeWidth(6f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setColor(Color.parseColor("#6A5CF0"));
        pointPaint.setStyle(Paint.Style.FILL);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setColor(Color.parseColor("#446A5CF0"));
        glowPaint.setStyle(Paint.Style.FILL);
    }

    public void addPoint(float rssiDbm) {
        if (rssiDbm > 0) rssiDbm = -rssiDbm; // ensure negative dBm
        if (rssiDbm > maxDb) rssiDbm = maxDb;
        if (rssiDbm < minDb) rssiDbm = minDb;

        dataPoints.add(rssiDbm);
        if (dataPoints.size() > MAX_POINTS) {
            dataPoints.remove(0);
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float paddingLeft = 90f;
        float paddingRight = 30f;
        float paddingTop = 30f;
        float paddingBottom = 60f;

        float plotWidth = width - paddingLeft - paddingRight;
        float plotHeight = height - paddingTop - paddingBottom;

        // Draw horizontal grid lines and labels (-40, -60, -80, -100 dBm)
        int steps = 4;
        for (int i = 0; i <= steps; i++) {
            float ratio = (float) i / steps;
            float y = paddingTop + ratio * plotHeight;
            float dbVal = maxDb - ratio * (maxDb - minDb);

            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint);
            canvas.drawText(String.format("%.0f", dbVal) + " dB", 10f, y + 8f, textPaint);
        }

        if (dataPoints.size() < 2) {
            if (dataPoints.isEmpty()) {
                canvas.drawText("Awaiting live RF telemetry...", paddingLeft + plotWidth / 2f, paddingTop + plotHeight / 2f, statusMsgPaint);
            }
            return;
        }

        // Calculate (x, y) coordinates for each point
        float stepX = plotWidth / (MAX_POINTS - 1);
        int offset = MAX_POINTS - dataPoints.size();

        Path linePath = new Path();
        Path fillPath = new Path();

        float startX = paddingLeft + offset * stepX;
        float firstY = getPixelY(dataPoints.get(0), plotHeight, paddingTop);

        linePath.moveTo(startX, firstY);
        fillPath.moveTo(startX, paddingTop + plotHeight);
        fillPath.lineTo(startX, firstY);

        for (int i = 1; i < dataPoints.size(); i++) {
            float x = paddingLeft + (offset + i) * stepX;
            float y = getPixelY(dataPoints.get(i), plotHeight, paddingTop);
            float prevX = paddingLeft + (offset + i - 1) * stepX;
            float prevY = getPixelY(dataPoints.get(i - 1), plotHeight, paddingTop);

            // Smooth cubic bezier curve
            float cx = (prevX + x) / 2f;
            linePath.cubicTo(cx, prevY, cx, y, x, y);
            fillPath.cubicTo(cx, prevY, cx, y, x, y);
        }

        float lastX = paddingLeft + (offset + dataPoints.size() - 1) * stepX;
        fillPath.lineTo(lastX, paddingTop + plotHeight);
        fillPath.close();

        // Gradient shader for fill
        fillPaint.setShader(new LinearGradient(
                0, paddingTop, 0, paddingTop + plotHeight,
                Color.parseColor("#554A90E2"),
                Color.parseColor("#056A5CF0"),
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // Draw circles on points
        for (int i = 0; i < dataPoints.size(); i++) {
            float x = paddingLeft + (offset + i) * stepX;
            float y = getPixelY(dataPoints.get(i), plotHeight, paddingTop);

            if (i == dataPoints.size() - 1) {
                // Latest point gets glowing highlight circle
                canvas.drawCircle(x, y, 16f, glowPaint);
                pointPaint.setColor(Color.parseColor("#4A90E2"));
                canvas.drawCircle(x, y, 8f, pointPaint);
            } else {
                pointPaint.setColor(Color.parseColor("#6A5CF0"));
                canvas.drawCircle(x, y, 5f, pointPaint);
            }
        }
    }

    private float getPixelY(float dbm, float plotHeight, float paddingTop) {
        float ratio = (dbm - minDb) / (maxDb - minDb);
        return paddingTop + plotHeight - (ratio * plotHeight);
    }
}
