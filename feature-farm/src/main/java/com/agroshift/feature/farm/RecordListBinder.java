package com.agroshift.feature.farm;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
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

    public RecordListBinder(RecyclerView list, View snackbarHost, RecordRepository repository) {
        adapter = new RecordAdapter(repository);
        list.setLayoutManager(new LinearLayoutManager(list.getContext()));
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                    @NonNull RecyclerView.ViewHolder to) {
                return false;
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
            holder.type.setText(switch (record.type()) {
                case TASK -> string.record_task;
                case EXPENSE -> string.record_cost;
                case SALE -> string.record_sale;
                case NOTE -> string.record_note;
            });
            boolean money = record.type() == FarmRecord.Type.EXPENSE || record.type() == FarmRecord.Type.SALE;
            holder.amount.setVisibility(money ? View.VISIBLE : View.GONE);
            holder.amount.setText(money ? EngineText.rupees(context, record.amountRupees()) : "");
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final CheckBox done;
            final TextView title;
            final TextView type;
            final TextView amount;

            Holder(@NonNull View view) {
                super(view);
                ZameenUi.applySurface(view, com.agroshift.core.ui.R.color.zameen_surface,
                        com.agroshift.core.ui.R.dimen.zameen_radius_card);
                done = view.findViewById(R.id.item_record_done);
                title = view.findViewById(R.id.item_record_title);
                type = view.findViewById(R.id.item_record_type);
                amount = view.findViewById(R.id.item_record_amount);
            }
        }
    }
}
