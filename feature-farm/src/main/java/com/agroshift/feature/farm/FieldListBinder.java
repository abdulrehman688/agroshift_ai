package com.agroshift.feature.farm;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.FieldRepository;
import com.agroshift.core.domain.engine.RotationPlan;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.RecordStats;
import com.agroshift.core.domain.season.SeasonTimeline;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.CropPhotos;
import com.agroshift.core.ui.PhotoView;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.snackbar.Snackbar;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Field list with tap to open, swipe to remove and undo. */
public final class FieldListBinder {
    private static final int UNDO_DURATION_MILLIS = 10_000;

    private final FieldAdapter adapter;

    public FieldListBinder(RecyclerView list, View header, View snackbarHost, FieldRepository repository,
            Consumer<FarmField> onOpen) {
        adapter = new FieldAdapter(onOpen);
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
                FarmField field = adapter.getCurrentList().get(position);
                repository.delete(field.id());
                Snackbar.make(snackbarHost, string.field_deleted, UNDO_DURATION_MILLIS)
                        .setAction(string.action_undo, v -> repository.restore(field.id()))
                        .show();
            }
        }).attachToRecyclerView(list);
    }

    public void submit(List<FarmField> fields, Map<String, RecordStats.Summary> stats) {
        adapter.setStats(stats);
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
        private Map<String, RecordStats.Summary> stats = Map.of();

        FieldAdapter(Consumer<FarmField> onOpen) {
            super(DIFF);
            this.onOpen = onOpen;
        }

        void setStats(Map<String, RecordStats.Summary> next) {
            if (!next.equals(stats)) {
                stats = next;
                notifyItemRangeChanged(0, getItemCount());
            }
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
            RecordStats.Summary summary = stats.getOrDefault(field.id(), RecordStats.Summary.EMPTY);
            holder.tasks.setText(EngineText.number(summary.openTasks(), 0));
            holder.money.setText(EngineText.rupees(context, summary.profit()));
            SeasonTimeline.Moment moment = moment(field);
            holder.growth.setPhoto(moment.hasCrop() ? CropPhotos.nearest(moment.cropSlug(), (float) moment.growth()).photo()
                    : com.agroshift.core.ui.R.drawable.photo_home_rabi);
            holder.stage.setText(moment.hasCrop()
                    ? context.getString(string.farm_now_stage, EngineText.cropName(context, moment.cropSlug()),
                            EngineText.stage(context, moment.stageKey()))
                    : context.getString(string.timeline_bare));
            holder.itemView.setContentDescription(context.getString(string.field_open, field.name()));
            holder.itemView.setOnClickListener(v -> onOpen.accept(field));
        }

        private static SeasonTimeline.Moment moment(FarmField field) {
            RotationPlan plan = null;
            if (field.rotationKey() != null) {
                try {
                    plan = RotationPlan.template(field.rotationKey());
                } catch (IllegalArgumentException unknown) {
                    plan = null;
                }
            }
            return SeasonTimeline.at(plan, YearMonth.now());
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView crop;
            final TextView area;
            final TextView stage;
            final TextView tasks;
            final TextView money;
            final PhotoView growth;

            Holder(@NonNull View view) {
                super(view);
                ZameenUi.applySurface(view, com.agroshift.core.ui.R.color.zameen_surface,
                        com.agroshift.core.ui.R.dimen.zameen_radius_card);
                name = view.findViewById(R.id.field_name);
                crop = view.findViewById(R.id.field_crop);
                area = view.findViewById(R.id.field_area);
                stage = view.findViewById(R.id.field_stage);
                tasks = view.findViewById(R.id.field_tasks);
                money = view.findViewById(R.id.field_money);
                growth = view.findViewById(R.id.field_growth);
                ZameenUi.attachPressSpring(view);
            }
        }
    }
}
