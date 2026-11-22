package com.agroshift.feature.farm;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.RecordRepository;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.snackbar.Snackbar;
import java.util.List;

/** Simple record list with tick, swipe to remove and undo. */
public final class RecordListBinder {
    private static final int UNDO_DURATION_MILLIS = 10_000;
    private final RecordAdapter adapter;

    public RecordListBinder(RecyclerView list, View header, View snackbarHost, RecordRepository repository) {
        adapter = new RecordAdapter(repository);
        if (list.getLayoutManager() == null) {
            list.setLayoutManager(new LinearLayoutManager(list.getContext()));
        }
        list.setAdapter(new ConcatAdapter(new HeaderAdapter(header), adapter));
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                    @NonNull RecyclerView.ViewHolder to) {
                return false;
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder holder) {
                return holder.getBindingAdapter() == adapter ? super.getSwipeDirs(rv, holder) : 0;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int position = holder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) {
                    return;
                }
                FarmRecord record = adapter.getCurrentList().get(position);
                repository.delete(record.id());
                Snackbar.make(snackbarHost, string.record_removed, UNDO_DURATION_MILLIS)
                        .setAction(string.action_undo, v -> repository.restore(record.id()))
                        .show();
            }
        }).attachToRecyclerView(list);
    }

    public void submit(List<FarmRecord> records) {
        adapter.submitList(records);
    }

    private static final class RecordAdapter extends ListAdapter<FarmRecord, RecordAdapter.Holder> {
        private static final DiffUtil.ItemCallback<FarmRecord> DIFF = new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull FarmRecord a, @NonNull FarmRecord b) {
                return a.id().equals(b.id());
            }

            @Override
            public boolean areContentsTheSame(@NonNull FarmRecord a, @NonNull FarmRecord b) {
                return a.equals(b);
            }
        };

        private final RecordRepository repository;

        RecordAdapter(RecordRepository repository) {
            super(DIFF);
            this.repository = repository;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_record, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FarmRecord record = getItem(position);
            Context context = holder.itemView.getContext();
            boolean task = record.type() == FarmRecord.Type.TASK;
            holder.done.setOnCheckedChangeListener(null);
            holder.done.setVisibility(task ? View.VISIBLE : View.GONE);
            holder.done.setChecked(record.done());
            holder.done.setContentDescription(record.title());
            holder.done.setOnCheckedChangeListener((button, checked) ->
                    repository.setDone(record.id(), checked));
            holder.title.setText(record.title());
            holder.title.setPaintFlags(task && record.done()
                    ? holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : holder.title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            String kind = context.getString(switch (record.type()) {
                case TASK -> string.record_task;
                case EXPENSE -> string.record_cost;
                case SALE -> string.record_sale;
                case NOTE -> string.record_note;
            });
            holder.type.setText(context.getString(string.record_meta, kind, DateUtils.getRelativeTimeSpanString(
                    record.dateMillis(), System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS)));
            holder.icon.setVisibility(task ? View.GONE : View.VISIBLE);
            holder.icon.setImageResource(switch (record.type()) {
                case TASK -> com.agroshift.core.ui.R.drawable.ic_ui_task;
                case EXPENSE -> com.agroshift.core.ui.R.drawable.ic_ui_cost;
                case SALE -> com.agroshift.core.ui.R.drawable.ic_ui_sale;
                case NOTE -> com.agroshift.core.ui.R.drawable.ic_ui_note;
            });
            int tint = ContextCompat.getColor(context, switch (record.type()) {
                case EXPENSE -> com.agroshift.core.ui.R.color.zameen_error;
                case SALE -> com.agroshift.core.ui.R.color.zameen_accent;
                default -> com.agroshift.core.ui.R.color.zameen_canal;
            });
            holder.icon.setImageTintList(ColorStateList.valueOf(tint));
            holder.title.setAlpha(task && record.done() ? 0.6f : 1f);
            boolean money = record.type() == FarmRecord.Type.EXPENSE || record.type() == FarmRecord.Type.SALE;
            holder.amount.setVisibility(money ? View.VISIBLE : View.GONE);
            holder.amount.setText(money ? (record.type() == FarmRecord.Type.SALE ? "+ " : "- ")
                    + EngineText.rupees(context, record.amountRupees()) : "");
            holder.amount.setTextColor(tint);
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final CheckBox done;
            final TextView title;
            final TextView type;
            final TextView amount;

            Holder(@NonNull View view) {
                super(view);
                ZameenUi.applySurface(view, com.agroshift.core.ui.R.color.zameen_surface,
                        com.agroshift.core.ui.R.dimen.zameen_radius_card);
                icon = view.findViewById(R.id.item_record_icon);
                ZameenUi.applySurface(icon, com.agroshift.core.ui.R.color.zameen_surface_accent,
                        com.agroshift.core.ui.R.dimen.zameen_radius_card);
                done = view.findViewById(R.id.item_record_done);
                title = view.findViewById(R.id.item_record_title);
                type = view.findViewById(R.id.item_record_type);
                amount = view.findViewById(R.id.item_record_amount);
            }
        }
    }
}
