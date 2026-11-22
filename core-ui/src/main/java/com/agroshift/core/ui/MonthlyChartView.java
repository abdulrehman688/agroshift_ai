package com.agroshift.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import java.text.NumberFormat;
import java.util.function.IntConsumer;

/** Twelve-month chart: rain bars with a water-demand line. Time runs with reading direction. */
public final class MonthlyChartView extends View {
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private double[] bars = new double[12];
    private double[] demand = new double[12];
    private final Paint selectedBar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private IntConsumer listener;
    private int selected = -1;

    public MonthlyChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        bar.setColor(ContextCompat.getColor(context, R.color.zameen_canal));
        line.setColor(ContextCompat.getColor(context, R.color.zameen_accent));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * density);
        line.setStrokeJoin(Paint.Join.ROUND);
        grid.setColor(ContextCompat.getColor(context, R.color.zameen_hairline));
        grid.setStrokeWidth(density);
        label.setColor(ContextCompat.getColor(context, R.color.zameen_text_secondary));
        label.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        label.setTextAlign(Paint.Align.CENTER);
        selectedBar.setColor(ContextCompat.getColor(context, R.color.zameen_text));
        setClickable(true);
    }

    public void setOnMonthSelected(IntConsumer next) {
        listener = next;
    }

    /** Highlights a month, where 0 is January. */
    public void selectMonth(int month) {
        selected = month;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float axis = 34 * density;
            boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
            float position = rtl ? event.getX() : event.getX() - axis;
            int slot = Math.max(0, Math.min(11, (int) (position / ((getWidth() - axis) / 12))));
            int month = rtl ? 11 - slot : slot;
            if (month != selected) {
                selected = month;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                invalidate();
                if (listener != null) {
                    listener.accept(month);
                }
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public void setSeries(double[] rain, double[] waterDemand) {
        bars = rain.clone();
        demand = waterDemand.clone();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec),
                resolveSize(Math.round(190 * density), heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float top = 16 * density;
        float bottom = getHeight() - 24 * density;
        float axis = 34 * density;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float start = rtl ? 0 : axis;
        float width = getWidth() - axis;
        float step = width / 12;
        double max = 1;
        for (int i = 0; i < 12; i++) {
            max = Math.max(max, Math.max(safe(bars[i]), safe(demand[i])));
        }
        max = Math.ceil(max / 50) * 50;
        NumberFormat format = NumberFormat.getIntegerInstance();
        label.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        float labelX = rtl ? getWidth() : 0;
        for (int i = 0; i <= 2; i++) {
            float y = bottom - (bottom - top) * i / 2f;
            canvas.drawLine(start, y, start + width, y, grid);
            canvas.drawText(format.format(max * i / 2), labelX, y + 4 * density, label);
        }
        label.setTextAlign(Paint.Align.CENTER);
        path.reset();
        for (int i = 0; i < 12; i++) {
            int slot = rtl ? 11 - i : i;
            float center = start + step * slot + step / 2;
            float barHeight = (float) (safe(bars[i]) / max * (bottom - top));
            rect.set(center - step * 0.28f, bottom - barHeight, center + step * 0.28f, bottom);
            canvas.drawRoundRect(rect, 3 * density, 3 * density, i == selected ? selectedBar : bar);
            if (i == selected) {
                canvas.drawLine(center, top, center, bottom, grid);
            }
            canvas.drawText(format.format(i + 1), center, getHeight() - 6 * density, label);
            float y = (float) (bottom - safe(demand[i]) / max * (bottom - top));
            if (i == 0) {
                path.moveTo(center, y);
            } else {
                path.lineTo(center, y);
            }
        }
        canvas.drawPath(path, line);
    }

    private static double safe(double value) {
        return Double.isNaN(value) ? 0 : value;
    }
}
