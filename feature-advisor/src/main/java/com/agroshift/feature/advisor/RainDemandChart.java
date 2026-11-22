package com.agroshift.feature.advisor;

import android.content.Context;
import android.view.View;
import androidx.core.content.ContextCompat;
import com.agroshift.core.ui.ZameenUi;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/** MPAndroidChart view of monthly rain and water loss, with time following reading direction. */
final class RainDemandChart {
    private static final int MONTHS = 12;
    private static final int BAR_DATA_INDEX = 1;

    private RainDemandChart() {
    }

    static void style(CombinedChart chart, IntConsumer onMonth) {
        Context context = chart.getContext();
        int text = ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_text_secondary);
        int grid = ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_hairline);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText("");
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDragEnabled(false);
        chart.setHighlightPerDragEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawOrder(new CombinedChart.DrawOrder[] {CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.LINE});
        chart.getAxisRight().setEnabled(false);
        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setLabelCount(3, false);
        left.setTextColor(text);
        left.setTextSize(11f);
        left.setGridColor(grid);
        left.setDrawAxisLine(false);
        XAxis bottom = chart.getXAxis();
        bottom.setPosition(XAxis.XAxisPosition.BOTTOM);
        bottom.setDrawGridLines(false);
        bottom.setAxisLineColor(grid);
        bottom.setTextColor(text);
        bottom.setTextSize(11f);
        bottom.setGranularity(1f);
        bottom.setLabelCount(MONTHS);
        bottom.setAxisMinimum(-0.5f);
        bottom.setAxisMaximum(MONTHS - 0.5f);
        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry entry, Highlight highlight) {
                onMonth.accept(position(chart, Math.round(entry.getX())));
            }

            @Override
            public void onNothingSelected() {
                // Keep the last month's text visible.
            }
        });
    }

    static void show(CombinedChart chart, double[] rain, double[] demand, int selectedMonth) {
        Context context = chart.getContext();
        List<BarEntry> bars = new ArrayList<>(MONTHS);
        List<Entry> line = new ArrayList<>(MONTHS);
        String[] labels = new String[MONTHS];
        NumberFormat format = NumberFormat.getIntegerInstance();
        for (int slot = 0; slot < MONTHS; slot++) {
            int month = position(chart, slot);
            bars.add(new BarEntry(slot, safe(rain[month])));
            line.add(new Entry(slot, safe(demand[month])));
            labels[slot] = format.format(month + 1);
        }
        BarDataSet barSet = new BarDataSet(bars, "rain");
        barSet.setColor(ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_canal));
        barSet.setHighLightColor(ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_text));
        barSet.setHighLightAlpha(255);
        barSet.setDrawValues(false);
        BarData barData = new BarData(barSet);
        barData.setBarWidth(0.58f);
        LineDataSet lineSet = new LineDataSet(line, "water loss");
        int accent = ContextCompat.getColor(context, com.agroshift.core.ui.R.color.zameen_accent);
        lineSet.setColor(accent);
        lineSet.setLineWidth(2.4f);
        lineSet.setCircleColor(accent);
        lineSet.setCircleRadius(2.5f);
        lineSet.setDrawCircleHole(false);
        lineSet.setDrawValues(false);
        lineSet.setHighlightEnabled(false);
        lineSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        CombinedData data = new CombinedData();
        data.setData(new LineData(lineSet));
        data.setData(barData);
        chart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        chart.setData(data);
        Highlight highlight = new Highlight(position(chart, selectedMonth), Float.NaN, 0);
        highlight.setDataIndex(BAR_DATA_INDEX);
        chart.highlightValue(highlight, false);
        if (ZameenUi.motionEnabled(context)) {
            chart.animateY(520);
        } else {
            chart.invalidate();
        }
    }

    /** Maps a chart slot to a month, and a month to a slot, because mirroring is symmetric. */
    private static int position(CombinedChart chart, int value) {
        return chart.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? MONTHS - 1 - value : value;
    }

    private static float safe(double value) {
        return Double.isNaN(value) ? 0f : (float) value;
    }
}
