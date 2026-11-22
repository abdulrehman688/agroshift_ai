package com.agroshift.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/** Decorative aerial placeholder until a field's real satellite imagery is connected. */
public final class FieldHeroView extends View {
    private static final int[] PLOTS = {
            0xFF3E5A2A, 0xFF6D7F38, 0xFF2F4724, 0xFF8C8A4B, 0xFF4F7A2E, 0xFF5F6D33,
            0xFF344C27, 0xFF73833D, 0xFF3A5629, 0xFF667738, 0xFF465F2C, 0xFF7D7F43
    };
    private static final int COLUMNS = 3;
    private static final int ROWS = 4;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int accent;
    private final int canal;
    private final float density;

    public FieldHeroView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        accent = ContextCompat.getColor(context, R.color.zameen_accent);
        canal = ContextCompat.getColor(context, R.color.zameen_canal);
        line.setStyle(Paint.Style.STROKE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        canvas.drawColor(0xFF26351D);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                fill.setColor(PLOTS[(row * COLUMNS + col) % PLOTS.length]);
                drawPlot(canvas, row, col, w, h, fill);
            }
        }
        line.setColor(canal);
        line.setStrokeWidth(5 * density);
        canvas.drawLine(-10, y(1, 0, h), w + 10, y(1, COLUMNS, h), line);

        fill.setColor(Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent)));
        drawPlot(canvas, 1, 1, w, h, fill);
        line.setColor(accent);
        line.setStrokeWidth(2.5f * density);
        drawPlot(canvas, 1, 1, w, h, line);
    }

    private void drawPlot(Canvas canvas, int row, int col, float w, float h, Paint paint) {
        float inset = paint == line ? 0 : density;
        path.reset();
        path.moveTo(x(row, col, w) + inset, y(row, col, h) + inset);
        path.lineTo(x(row, col + 1, w) - inset, y(row, col + 1, h) + inset);
        path.lineTo(x(row + 1, col + 1, w) - inset, y(row + 1, col + 1, h) - inset);
        path.lineTo(x(row + 1, col, w) + inset, y(row + 1, col, h) - inset);
        path.close();
        canvas.drawPath(path, paint);
    }

    private static float x(int row, int col, float w) {
        return w * (col / (float) COLUMNS) - w * 0.055f * row + w * 0.08f;
    }

    private static float y(int row, int col, float h) {
        return h * (row / (float) ROWS) + h * 0.04f - h * 0.035f * col;
    }
}
