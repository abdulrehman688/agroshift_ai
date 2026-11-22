package com.agroshift.feature.farm;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.FieldRepository;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.snackbar.Snackbar;
import java.text.NumberFormat;
import java.util.List;
import java.util.function.Consumer;

/** Field list with tap to open, swipe to remove and undo. */
public final class FieldListBinder {
    private static final int UNDO_DURATION_MILLIS = 10_000;

    private final FieldAdapter adapter;

    public FieldListBinder(RecyclerView list, View snackbarHost, FieldRepository repository,
            Consumer<FarmField> onOpen) {
        adapter = new FieldAdapter(onOpen);
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
                FarmField field = adapter.getCurrentList().get(position);
                repository.delete(field.id());
                Snackbar.make(snackbarHost, string.field_deleted, UNDO_DURATION_MILLIS)
                        .setAction(string.action_undo, v -> repository.restore(field.id()))
                        .show();
            }
        }).attachToRecyclerView(list);
    }

    public void submit(List<FarmField> fields) {
        adapter.submitList(fields);
    }

    private static final class FieldAdapter extends ListAdapter<FarmField, FieldAdapter.Holder> {
        private static final DiffUtil.ItemCallback<FarmField> DIFF = new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull FarmField a, @NonNull FarmField b) {
                return a.id().equals(b.id());
            }

            @Override
            public boolean areContentsTheSame(@NonNull FarmField a, @NonNull FarmField b) {
                return a.equals(b);
            }
        };

        private final Consumer<FarmField> onOpen;

        FieldAdapter(Consumer<FarmField> onOpen) {
            super(DIFF);
            this.onOpen = onOpen;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_field, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FarmField field = getItem(position);
            Context context = holder.itemView.getContext();
            holder.name.setText(field.name());
            holder.crop.setText(field.hasLocation()
                    ? EngineText.rotationName(context, field.rotationKey())
                    : context.getString(string.home_add_location));
            NumberFormat format = NumberFormat.getNumberInstance();
            format.setMaximumFractionDigits(2);
            holder.area.setText(context.getString(string.field_area_value,
                    format.format(field.areaAcres())));
            holder.itemView.setContentDescription(context.getString(string.field_open, field.name()));
            holder.itemView.setOnClickListener(v -> onOpen.accept(field));
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView crop;
            final TextView area;

            Holder(@NonNull View view) {
                super(view);
                ZameenUi.applySurface(view, com.agroshift.core.ui.R.color.zameen_surface,
                        com.agroshift.core.ui.R.dimen.zameen_radius_card);
                name = view.findViewById(R.id.field_name);
                crop = view.findViewById(R.id.field_crop);
                area = view.findViewById(R.id.field_area);
            }
        }
    }
}
