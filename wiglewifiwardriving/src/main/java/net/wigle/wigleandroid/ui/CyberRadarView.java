package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.classifier.AdvancedDeviceClassifier;
import net.wigle.wigleandroid.model.Network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Cyberpunk GhostH High-Precision Tactical Signal Radar.
 * Plots nearby Wi-Fi, BLE, Flock/ALPR cameras, and IoT signals in real time
 * with rotating radar beam, range rings (-45, -65, -80, -95 dBm), threat classification,
 * and HUD targeting reticles.
 */
public class CyberRadarView extends View {

    public interface OnTargetSelectedListener {
        void onTargetSelected(@Nullable RadarTarget target);
    }

    public static class RadarTarget {
        public final Network network;
        public final float angleDeg;
        public final float distanceRatio; // 0.1 (closest) to 0.95 (edge)
        public final AdvancedDeviceClassifier.DeviceCategory category;
        public final int color;
        public long lastSweepFlashTime;

        public RadarTarget(@NonNull Network network, float angleDeg, float distanceRatio,
                           @NonNull AdvancedDeviceClassifier.DeviceCategory category) {
            this.network = network;
            this.angleDeg = angleDeg;
            this.distanceRatio = distanceRatio;
            this.category = category;
            this.color = category.getColor();
            this.lastSweepFlashTime = 0;
        }
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sweepBeamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blipGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF bounds = new RectF();
    private final List<RadarTarget> targets = new ArrayList<>();
    private RadarTarget selectedTarget = null;
    private OnTargetSelectedListener targetSelectedListener = null;

    private float sweepAngle = 0f;
    private long lastAnimTime = 0;
    private float centerX = 0f;
    private float centerY = 0f;
    private float maxRadius = 0f;

    public CyberRadarView(Context context) {
        super(context);
        init();
    }

    public CyberRadarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CyberRadarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.5f);
        ringPaint.setColor(Color.parseColor("#1A3A40")); // Deep cyber cyan ring

        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(1f);
        crosshairPaint.setColor(Color.parseColor("#142E33"));

        textPaint.setColor(Color.parseColor("#00FFF2"));
        textPaint.setTextSize(26f);
        textPaint.setTypeface(Typeface.MONOSPACE);

        sweepBeamPaint.setStyle(Paint.Style.STROKE);
        sweepBeamPaint.setStrokeWidth(2.5f);
        sweepBeamPaint.setColor(Color.parseColor("#00FFF2"));

        blipPaint.setStyle(Paint.Style.FILL);

        blipGlowPaint.setStyle(Paint.Style.STROKE);
        blipGlowPaint.setStrokeWidth(2f);

        lockPaint.setStyle(Paint.Style.STROKE);
        lockPaint.setStrokeWidth(2.5f);
        lockPaint.setColor(Color.parseColor("#FF1F8F")); // Neon Pink reticle
    }

    public void setOnTargetSelectedListener(OnTargetSelectedListener listener) {
        this.targetSelectedListener = listener;
    }

    public void updateNetworks(@Nullable Collection<Network> networkList) {
        targets.clear();
        if (networkList == null || networkList.isEmpty()) {
            postInvalidateOnAnimation();
            return;
        }

        for (Network net : networkList) {
            if (net == null) continue;
            final String bssid = net.getBssid() != null ? net.getBssid() : "";
            // Compute a stable pseudo-random azimuth bearing based on BSSID hash
            int hash = Math.abs(bssid.hashCode());
            float angle = (float) (hash % 360);

            // Distance ratio mapped from RSSI dBm:
            // -40 dBm -> 0.15 (very close)
            // -95 dBm -> 0.92 (outer ring)
            int level = net.getLevel();
            int clampedLevel = Math.max(-100, Math.min(-35, level));
            float ratio = 0.15f + ((float) (-35 - clampedLevel) / 65f) * 0.77f;
            ratio = Math.max(0.12f, Math.min(0.95f, ratio));

            AdvancedDeviceClassifier.DeviceCategory cat = AdvancedDeviceClassifier.classify(net);
            targets.add(new RadarTarget(net, angle, ratio, cat));
        }

        // Keep selected target reference fresh if still present
        if (selectedTarget != null) {
            RadarTarget match = null;
            for (RadarTarget t : targets) {
                if (t.network.getBssid().equals(selectedTarget.network.getBssid())) {
                    match = t;
                    break;
                }
            }
            selectedTarget = match;
        }

        postInvalidateOnAnimation();
    }

    public void selectTarget(@Nullable RadarTarget target) {
        this.selectedTarget = target;
        postInvalidateOnAnimation();
    }

    @Nullable
    public RadarTarget getSelectedTarget() {
        return selectedTarget;
    }

    public List<RadarTarget> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        maxRadius = Math.min(centerX, centerY) * 0.90f;
        bounds.set(centerX - maxRadius, centerY - maxRadius, centerX + maxRadius, centerY + maxRadius);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (maxRadius <= 0) return;

        long now = SystemClock.uptimeMillis();
        if (lastAnimTime == 0) lastAnimTime = now;
        long dt = now - lastAnimTime;
        lastAnimTime = now;

        // Advance sweep beam: 360 deg every ~3.5 seconds (~100 deg/sec)
        sweepAngle = (sweepAngle + (dt * 0.10f)) % 360f;

        // 1. Draw radar background grid & concentric range rings
        drawRadarGrid(canvas);

        // 2. Draw rotating sweep beam & radar sector glow
        drawSweepBeam(canvas);

        // 3. Draw detected signal blips
        drawBlips(canvas, now);

        // 4. Draw HUD targeting reticle for selected target
        drawTargetLock(canvas);

        // Request next frame for smooth 60fps rotation
        postInvalidateOnAnimation();
    }

    private void drawRadarGrid(Canvas canvas) {
        // Range rings: 25%, 50%, 75%, 100% (-45 dBm, -65 dBm, -80 dBm, -95 dBm)
        float[] ringRatios = {0.25f, 0.50f, 0.75f, 1.0f};
        String[] ringLabels = {"-45 dBm", "-65 dBm", "-80 dBm", "-95 dBm"};

        for (int i = 0; i < ringRatios.length; i++) {
            float r = maxRadius * ringRatios[i];
            if (i == ringRatios.length - 1) {
                ringPaint.setColor(Color.parseColor("#00FFF2"));
                ringPaint.setStrokeWidth(2f);
            } else {
                ringPaint.setColor(Color.parseColor("#15383E"));
                ringPaint.setStrokeWidth(1.2f);
            }
            canvas.drawCircle(centerX, centerY, r, ringPaint);

            // Draw range label along the vertical axis
            canvas.drawText(ringLabels[i], centerX + 8f, centerY - r + 24f, textPaint);
        }

        // Crosshairs: North-South, East-West
        canvas.drawLine(centerX - maxRadius, centerY, centerX + maxRadius, centerY, crosshairPaint);
        canvas.drawLine(centerX, centerY - maxRadius, centerX, centerY + maxRadius, crosshairPaint);

        // Compass tick marks: N, E, S, W
        textPaint.setColor(Color.parseColor("#00FFF2"));
        canvas.drawText("000°", centerX - 26f, centerY - maxRadius - 10f, textPaint);
        canvas.drawText("090°", centerX + maxRadius + 10f, centerY + 8f, textPaint);
        canvas.drawText("180°", centerX - 26f, centerY + maxRadius + 28f, textPaint);
        canvas.drawText("270°", centerX - maxRadius - 70f, centerY + 8f, textPaint);
    }

    private void drawSweepBeam(Canvas canvas) {
        canvas.save();
        canvas.rotate(sweepAngle, centerX, centerY);

        // Radial sweep beam line
        sweepBeamPaint.setColor(Color.parseColor("#00FFF2"));
        canvas.drawLine(centerX, centerY, centerX + maxRadius, centerY, sweepBeamPaint);

        // Draw small trailing sweep gradient arc
        Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trailPaint.setStyle(Paint.Style.FILL);
        int[] colors = {Color.TRANSPARENT, Color.parseColor("#2000FFF2"), Color.parseColor("#5000FFF2")};
        float[] positions = {0.0f, 0.90f, 1.0f};
        SweepGradient gradient = new SweepGradient(centerX, centerY, colors, positions);
        trailPaint.setShader(gradient);
        canvas.drawArc(bounds, -45f, 45f, true, trailPaint);

        canvas.restore();
    }

    private void drawBlips(Canvas canvas, long now) {
        for (RadarTarget target : targets) {
            float rad = (float) Math.toRadians(target.angleDeg);
            float dist = maxRadius * target.distanceRatio;
            float bx = centerX + (float) (dist * Math.cos(rad));
            float by = centerY + (float) (dist * Math.sin(rad));

            // Check angular distance between sweep beam and this target
            float angleDiff = Math.abs(sweepAngle - target.angleDeg);
            if (angleDiff > 180f) angleDiff = 360f - angleDiff;

            if (angleDiff < 8f) {
                target.lastSweepFlashTime = now;
            }

            long timeSinceFlash = now - target.lastSweepFlashTime;
            boolean isFlashing = timeSinceFlash < 800;
            float flashAlpha = isFlashing ? Math.max(0.2f, 1f - (timeSinceFlash / 800f)) : 0.4f;

            int baseColor = target.color;
            int r = Color.red(baseColor);
            int g = Color.green(baseColor);
            int b = Color.blue(baseColor);
            int alphaInt = (int) (flashAlpha * 255);

            blipPaint.setColor(Color.argb(Math.max(120, alphaInt), r, g, b));
            blipGlowPaint.setColor(Color.argb(alphaInt, r, g, b));

            float blipRadius = target.category.isSpecial() ? 9f : 6f;
            canvas.drawCircle(bx, by, blipRadius, blipPaint);

            if (target.category.isSpecial() || isFlashing) {
                float glowRadius = blipRadius + (isFlashing ? (12f * (1f - flashAlpha)) : 5f);
                canvas.drawCircle(bx, by, glowRadius, blipGlowPaint);
            }
        }
    }

    private void drawTargetLock(Canvas canvas) {
        if (selectedTarget == null) return;

        float rad = (float) Math.toRadians(selectedTarget.angleDeg);
        float dist = maxRadius * selectedTarget.distanceRatio;
        float bx = centerX + (float) (dist * Math.cos(rad));
        float by = centerY + (float) (dist * Math.sin(rad));

        float size = 20f;
        // Cyber brackets [ ]
        // Left bracket
        canvas.drawLine(bx - size, by - size, bx - size, by + size, lockPaint);
        canvas.drawLine(bx - size, by - size, bx - (size / 2f), by - size, lockPaint);
        canvas.drawLine(bx - size, by + size, bx - (size / 2f), by + size, lockPaint);

        // Right bracket
        canvas.drawLine(bx + size, by - size, bx + size, by + size, lockPaint);
        canvas.drawLine(bx + size, by - size, bx + (size / 2f), by - size, lockPaint);
        canvas.drawLine(bx + size, by + size, bx + (size / 2f), by + size, lockPaint);

        // Center reticle dot
        lockPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(bx, by, 3f, lockPaint);
        lockPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            RadarTarget closest = null;
            float minDistanceSq = 48f * 48f; // touch target radius tolerance (48px)

            for (RadarTarget target : targets) {
                float rad = (float) Math.toRadians(target.angleDeg);
                float dist = maxRadius * target.distanceRatio;
                float bx = centerX + (float) (dist * Math.cos(rad));
                float by = centerY + (float) (dist * Math.sin(rad));

                float dx = x - bx;
                float dy = y - by;
                float dSq = (dx * dx) + (dy * dy);
                if (dSq < minDistanceSq) {
                    minDistanceSq = dSq;
                    closest = target;
                }
            }

            selectedTarget = closest;
            if (targetSelectedListener != null) {
                targetSelectedListener.onTargetSelected(closest);
            }
            invalidate();
            return true;
        }
        return super.onTouchEvent(event);
    }
}
