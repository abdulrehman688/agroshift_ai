package com.agroshift.feature.advisor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.domain.engine.PlanStudio;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shows which crop is in the field each month, with crop names inside the blocks. */
public final class PlanYearView extends View {
    private static final int[] AXIS_MONTHS = {1, 4, 7, 10};

    private final Paint block = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint blockText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint axisText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Map<String, String> names = new HashMap<>();
    private final List<String> axis = new ArrayList<>();
    private final float density;
    private final int kharif;
    private final int rabi;
    private final int yearRound;
    private List<PlanStudio.Block> blocks = List.of();
    private int years = 1;

    public PlanYearView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        kharif = ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_accent);
        rabi = ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_canal);
        yearRound = ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_text_secondary);
        track.setColor(ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_hairline));
        blockText.setColor(ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_on_accent));
        blockText.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
        blockText.setTextAlign(Paint.Align.CENTER);
        axisText.setColor(ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_text_secondary));
        axisText.setTextSize(13 * getResources().getDisplayMetrics().scaledDensity);
        for (int month : AXIS_MONTHS) {
            axis.add(EngineText.month(context, month));
        }
    }

    public void setPlan(RotationPlan plan) {
        blocks = PlanStudio.yearBlocks(plan);
        years = plan.cycleYears();
        names.clear();
        for (String slug : plan.uniqueCropSlugs()) {
            names.put(slug, EngineText.cropName(getContext(), slug));
        }
        setContentDescription(EngineText.cropSequence(getContext(), plan));
        requestLayout();
        invalidate();
    }

    private float labelHeight() {
        return years > 1 ? 24 * density : 0;
    }

    private float rowHeight() {
        return labelHeight() + 40 * density + 6 * density;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = Math.round(rowHeight() * years + 24 * density) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(height, heightSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingStart() - getPaddingEnd();
        float cell = width / 12f;
        float origin = getPaddingLeft();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float radius = 8 * density;
        float barHeight = 40 * density;
        float top = getPaddingTop();
        for (int year = 1; year <= years; year++) {
            float rowTop = top + (year - 1) * rowHeight();
            if (years > 1) {
                axisText.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
                canvas.drawText(getContext().getString(string.studio_year_n, EngineText.number(year, 0)),
                        rtl ? origin + width : origin, rowTop + 17 * density, axisText);
            }
            float barTop = rowTop + labelHeight();
            rect.set(origin, barTop, origin + width, barTop + barHeight);
            canvas.drawRoundRect(rect, radius, radius, track);
            for (PlanStudio.Block item : blocks) {
                if (item.year() != year) {
                    continue;
                }
                float start = (item.startMonth() - 1) * cell;
                float length = item.length() * cell;
                float left = rtl ? origin + width - start - length : origin + start;
                rect.set(left + density, barTop, left + length - density, barTop + barHeight);
                block.setColor(colorFor(item.cropSlug()));
                canvas.drawRoundRect(rect, radius, radius, block);
                CharSequence label = TextUtils.ellipsize(names.getOrDefault(item.cropSlug(), item.cropSlug()),
                        blockText, Math.max(0, rect.width() - 6 * density), TextUtils.TruncateAt.END);
                float baseline = rect.centerY() - (blockText.descent() + blockText.ascent()) / 2;
                canvas.drawText(label, 0, label.length(), rect.centerX(), baseline, blockText);
            }
        }
        float axisBase = top + rowHeight() * years + 14 * density;
        axisText.setTextAlign(rtl ? Paint.Align.RIGHT : Paint.Align.LEFT);
        for (int i = 0; i < AXIS_MONTHS.length; i++) {
            float offset = (AXIS_MONTHS[i] - 1) * cell;
            canvas.drawText(axis.get(i), rtl ? origin + width - offset : origin + offset, axisBase, axisText);
        }
    }

    private int colorFor(String slug) {
        if (Crop.KHARIF.contains(slug)) {
            return kharif;
        }
        return Crop.RABI.contains(slug) ? rabi : yearRound;
    }
}
