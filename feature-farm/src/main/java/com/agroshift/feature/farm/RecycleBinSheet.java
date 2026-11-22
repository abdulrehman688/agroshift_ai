package com.agroshift.feature.farm;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.WaterRepository;
import com.agroshift.core.domain.farm.BinItem;
import com.agroshift.core.domain.farm.RecycleBin;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.plurals;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.sync.CanalAlarms;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/** Deleted farms, tasks, costs, sales and notes, each with a one-tap restore. */
public final class RecycleBinSheet extends BottomSheetDialogFragment {
    private static final float SHEET_HEIGHT = 0.88f;

    private final BinAdapter adapter = new BinAdapter(this::restore);
    private List<BinItem> items = List.of();
    private BinItem.Kind filter;
    private View empty;
    private RecyclerView list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_recycle_bin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        empty = view.findViewById(R.id.bin_empty);
        list = view.findViewById(R.id.bin_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        ChipGroup filters = view.findViewById(R.id.bin_filters);
        addFilter(filters, string.bin_filter_all, null, true);
        addFilter(filters, string.bin_filter_farms, BinItem.Kind.FARM, false);
        addFilter(filters, string.record_task, BinItem.Kind.TASK, false);
        addFilter(filters, string.record_cost, BinItem.Kind.EXPENSE, false);
        addFilter(filters, string.record_sale, BinItem.Kind.SALE, false);
        addFilter(filters, string.record_note, BinItem.Kind.NOTE, false);
        filters.setOnCheckedStateChangeListener((group, ids) -> {
            filter = ids.isEmpty() ? null : (BinItem.Kind) group.findViewById(ids.get(0)).getTag();
            render();
        });
        services().recordRepository().observeBin().observe(getViewLifecycleOwner(), next -> {
            items = next;
            render();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog dialog) {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                ViewGroup.LayoutParams params = sheet.getLayoutParams();
                params.height = Math.round(getResources().getDisplayMetrics().heightPixels * SHEET_HEIGHT);
                sheet.setLayoutParams(params);
            }
            dialog.getBehavior().setSkipCollapsed(true);
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private AppServices services() {
        return (AppServices) requireContext().getApplicationContext();
    }

    private void addFilter(ChipGroup group, @StringRes int label, @Nullable BinItem.Kind kind, boolean checked) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_choice_chip, group, false);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setTag(kind);
        chip.setChecked(checked);
        group.addView(chip);
    }

    private void render() {
        List<BinItem> shown = RecycleBin.filter(items, filter);
        adapter.submitList(shown);
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(shown.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void restore(BinItem item) {
        AppServices services = services();
        boolean withFarm = RecycleBin.restoresFarm(item);
        if (item.kind() == BinItem.Kind.FARM) {
            services.fieldRepository().restore(item.id());
            restoreCanalAlarm(item.id());
        } else {
            if (withFarm) {
                services.fieldRepository().restore(item.fieldId());
                restoreCanalAlarm(item.fieldId());
            }
            services.recordRepository().restore(item.id());
        }
        Snackbar.make(requireView(), withFarm ? string.bin_restored_with_farm : string.bin_restored,
                Snackbar.LENGTH_SHORT).show();
    }

    /** A canal alarm that fired while the farm was deleted must be scheduled again. */
    private void restoreCanalAlarm(String fieldId) {
        Context app = requireContext().getApplicationContext();
        WaterRepository water = services().waterRepository();
        Thread worker = new Thread(() -> water.findById(fieldId).ifPresent(saved -> CanalAlarms.sync(app, saved)),
                "restore-canal-alarm");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void onDestroyView() {
        list.setAdapter(null);
        list = null;
        empty = null;
        super.onDestroyView();
    }

    private interface RestoreAction {
        void restore(BinItem item);
    }

    private static final class BinAdapter extends ListAdapter<BinItem, BinAdapter.Holder> {
        private static final DiffUtil.ItemCallback<BinItem> DIFF = new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull BinItem before, @NonNull BinItem after) {
                return before.kind() == after.kind() && before.id().equals(after.id());
            }

            @Override
            public boolean areContentsTheSame(@NonNull BinItem before, @NonNull BinItem after) {
                return before.equals(after);
            }
        };

        private final RestoreAction action;

        BinAdapter(RestoreAction action) {
            super(DIFF);
            this.action = action;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bin, parent, false);
            ZameenUi.applySurface(view, com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            BinItem item = getItem(position);
            Context context = holder.itemView.getContext();
            holder.icon.setImageResource(icon(item.kind()));
            holder.title.setText(item.title());
            String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .format(Instant.ofEpochMilli(item.deletedAtMillis()).atZone(ZoneId.systemDefault()));
            String deleted = context.getString(string.bin_deleted_on, date);
            holder.meta.setText(context.getString(string.record_meta, detail(context, item), deleted));
            holder.note.setVisibility(RecycleBin.restoresFarm(item) ? View.VISIBLE : View.GONE);
            holder.restore.setContentDescription(context.getString(string.bin_restore_item, item.title()));
            holder.restore.setOnClickListener(v -> action.restore(item));
        }

        private static String detail(Context context, BinItem item) {
            return switch (item.kind()) {
                case FARM -> context.getResources().getQuantityString(plurals.bin_farm_records, item.recordCount(),
                        item.recordCount());
                case EXPENSE, SALE -> context.getString(string.record_meta, item.farmName(),
                        EngineText.rupees(context, item.amountRupees()));
                default -> context.getString(string.record_meta, context.getString(
                        item.kind() == BinItem.Kind.TASK ? string.record_task : string.record_note), item.farmName());
            };
        }

        @DrawableRes
        private static int icon(BinItem.Kind kind) {
            return switch (kind) {
                case FARM -> com.agroshift.core.ui.R.drawable.ic_ui_field;
                case TASK -> com.agroshift.core.ui.R.drawable.ic_ui_task;
                case EXPENSE -> com.agroshift.core.ui.R.drawable.ic_ui_cost;
                case SALE -> com.agroshift.core.ui.R.drawable.ic_ui_sale;
                case NOTE -> com.agroshift.core.ui.R.drawable.ic_ui_note;
            };
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView meta;
            final TextView note;
            final MaterialButton restore;

            Holder(@NonNull View view) {
                super(view);
                icon = view.findViewById(R.id.bin_icon);
                title = view.findViewById(R.id.bin_title);
                meta = view.findViewById(R.id.bin_meta);
                note = view.findViewById(R.id.bin_note);
                restore = view.findViewById(R.id.bin_restore);
            }
        }
    }
}
