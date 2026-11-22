package com.agroshift.feature.market;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.farm.RecordStats;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.SalesBarsView;
import com.agroshift.core.ui.ZameenUi;
import com.agroshift.feature.farm.AddRecordSheet;
import com.agroshift.feature.farm.RecordListBinder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Opens official prices and shows the farmer's own sales history. */
public final class MarketFragment extends Fragment {
    private static final String AMIS_URL = "http://www.amis.pk/daily%20market%20changes.aspx";

    private static final int CHART_MONTHS = 6;

    private final List<FarmField> fields = new ArrayList<>();
    private double[] monthTotals = new double[CHART_MONTHS];
    private String[] monthNames = new String[CHART_MONTHS];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_market, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        ZameenUi.applySafeArea(view, false);
        AppServices services = (AppServices) requireContext().getApplicationContext();
        RecyclerView list = view.findViewById(R.id.market_sales);
        View header = getLayoutInflater().inflate(R.layout.header_market, list, false);
        View prices = header.findViewById(R.id.market_prices);
        ZameenUi.applySurface(prices, com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.attachPressSpring(prices);
        prices.setOnClickListener(v -> openPrices());
        for (int id : new int[] {R.id.market_card_count, R.id.market_card_top, R.id.market_chart_card}) {
            ZameenUi.applySurface(header.findViewById(id), com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
        }
        TextView earned = header.findViewById(R.id.market_earned);
        TextView count = header.findViewById(R.id.market_count);
        TextView top = header.findViewById(R.id.market_top);
        TextView chartValue = header.findViewById(R.id.market_chart_value);
        SalesBarsView chart = header.findViewById(R.id.market_chart);
        View empty = header.findViewById(R.id.market_empty);
        RecordListBinder binder = new RecordListBinder(list, header, view, services.recordRepository());
        chart.setOnBarSelected(index -> showMonth(chartValue, index));
        services.recordRepository().observeAll().observe(getViewLifecycleOwner(), records -> {
            List<FarmRecord> sales = RecordStats.ofType(records, FarmRecord.Type.SALE);
            binder.submit(sales);
            empty.setVisibility(sales.isEmpty() ? View.VISIBLE : View.GONE);
            RecordStats.Summary summary = RecordStats.total(sales);
            earned.setText(EngineText.rupees(requireContext(), summary.earned()));
            count.setText(EngineText.number(summary.sales(), 0));
            String best = RecordStats.topSale(sales);
            top.setText(best == null ? getString(string.market_top_none) : best);
            YearMonth now = YearMonth.now();
            monthTotals = RecordStats.monthlySales(sales, now, CHART_MONTHS, ZoneId.systemDefault());
            String[] labels = new String[CHART_MONTHS];
            monthNames = new String[CHART_MONTHS];
            for (int i = 0; i < CHART_MONTHS; i++) {
                YearMonth month = now.minusMonths(CHART_MONTHS - 1L - i);
                labels[i] = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault());
                monthNames[i] = month.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
            }
            chart.setData(monthTotals, labels);
            showMonth(chartValue, CHART_MONTHS - 1);
        });
        services.fieldRepository().observeFields().observe(getViewLifecycleOwner(), items -> {
            fields.clear();
            fields.addAll(items);
        });
        MaterialButton add = view.findViewById(R.id.market_add);
        ZameenUi.polishPrimary(add);
        add.setOnClickListener(v -> addSale(view));
    }

    private void showMonth(TextView target, int index) {
        target.setText(getString(string.market_month_value, monthNames[index],
                EngineText.rupees(requireContext(), monthTotals[index])));
    }

    private void addSale(View view) {
        if (fields.isEmpty()) {
            Snackbar.make(view, string.market_need_field, Snackbar.LENGTH_LONG).show();
        } else if (fields.size() == 1) {
            showSheet(fields.get(0));
        } else {
            String[] names = new String[fields.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = fields.get(i).name();
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(string.market_add)
                    .setItems(names, (dialog, which) -> showSheet(fields.get(which)))
                    .show();
        }
    }

    private void showSheet(FarmField field) {
        AddRecordSheet.create(field.id(), FarmRecord.Type.SALE).show(getChildFragmentManager(), "add_sale");
    }

    private void openPrices() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AMIS_URL)));
        } catch (ActivityNotFoundException noBrowser) {
            Snackbar.make(requireView(), string.assistant_error, Snackbar.LENGTH_LONG).show();
        }
    }
}
