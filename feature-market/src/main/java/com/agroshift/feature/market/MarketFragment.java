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
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.ZameenUi;
import com.agroshift.feature.farm.AddRecordSheet;
import com.agroshift.feature.farm.RecordListBinder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;

/** Opens official prices and tracks what the farmer sold. */
public final class MarketFragment extends Fragment {
    private static final String AMIS_URL = "http://www.amis.pk/daily%20market%20changes.aspx";

    private final List<FarmField> fields = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_market, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppServices services = (AppServices) requireContext().getApplicationContext();
        View prices = view.findViewById(R.id.market_prices);
        ZameenUi.applySurface(prices, com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        prices.setOnClickListener(v -> openPrices());
        TextView earned = view.findViewById(R.id.market_earned);
        View empty = view.findViewById(R.id.market_empty);
        RecyclerView list = view.findViewById(R.id.market_sales);
        RecordListBinder binder = new RecordListBinder(list, view, services.recordRepository());
        services.recordRepository().observeAll().observe(getViewLifecycleOwner(), records -> {
            List<FarmRecord> sales = new ArrayList<>();
            for (FarmRecord record : records) {
                if (record.type() == FarmRecord.Type.SALE) {
                    sales.add(record);
                }
            }
            binder.submit(sales);
            empty.setVisibility(sales.isEmpty() ? View.VISIBLE : View.GONE);
            earned.setText(EngineText.rupees(requireContext(), FarmRecord.totals(sales).earned()));
        });
        services.fieldRepository().observeFields().observe(getViewLifecycleOwner(), items -> {
            fields.clear();
            fields.addAll(items);
        });
        MaterialButton add = view.findViewById(R.id.market_add);
        ZameenUi.polishPrimary(add);
        add.setOnClickListener(v -> addSale(view));
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
