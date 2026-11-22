package com.agroshift.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.engine.Crop;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.domain.farm.FarmSelection;
import com.agroshift.core.domain.search.SearchIndex;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.ZameenUi;
import com.agroshift.feature.help.CropGuideSheet;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Global search across farms, records, crops and app actions. */
public final class CommandMenuSheet extends BottomSheetDialogFragment {
    public enum Command {
        ADD_FIELD(string.action_add_field, com.agroshift.core.ui.R.drawable.ic_ui_plus),
        WATER(string.water_plan, com.agroshift.core.ui.R.drawable.ic_ui_drop),
        ASK(string.help_ask, com.agroshift.core.ui.R.drawable.ic_ui_chat),
        ADVISOR(string.nav_advisor, com.agroshift.core.ui.R.drawable.ic_ui_leaf),
        FARM(string.nav_farm, com.agroshift.core.ui.R.drawable.ic_ui_field),
        MARKET(string.nav_market, com.agroshift.core.ui.R.drawable.ic_ui_trend),
        COMMUNITY(string.nav_community, com.agroshift.core.ui.R.drawable.ic_ui_star),
        LANGUAGE(string.command_language, com.agroshift.core.ui.R.drawable.ic_ui_language),
        HOME(string.nav_home, com.agroshift.core.ui.R.drawable.ic_ui_home);

        @StringRes final int title;
        @DrawableRes final int icon;

        Command(@StringRes int title, @DrawableRes int icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    public interface Host {
        void runCommand(Command command);
    }

    private static final String PREFS = "agroshift_search";
    private static final String KEY_RECENT = "recent";
    private static final String RECENT_SEPARATOR = "\n";
    private static final int MAX_RECENT = 5;
    private static final int MAX_RESULTS = 40;
    private static final int TILE_COLUMNS = 4;
    private static final int TILE_COUNT = 8;
    private static final float SHEET_HEIGHT = 0.88f;

    private final Map<String, FarmField> fieldsById = new HashMap<>();
    private final Map<String, String> recordFields = new HashMap<>();
    private List<FarmField> fields = List.of();
    private List<FarmRecord> records = List.of();
    private List<SearchIndex.Entry> entries = List.of();
    private SearchIndex.Kind filter;
    private EditText search;
    private LinearLayout rows;
    private View start;
    private View empty;
    private View clear;

    private final ActivityResultLauncher<Intent> voice = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && search != null) {
                    ArrayList<String> words = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (words != null && !words.isEmpty()) {
                        search.setText(words.get(0));
                        search.setSelection(search.length());
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_command, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppServices services = (AppServices) requireContext().getApplicationContext();
        rows = view.findViewById(R.id.command_rows);
        start = view.findViewById(R.id.command_start);
        empty = view.findViewById(R.id.command_empty);
        clear = view.findViewById(R.id.command_clear);
        search = view.findViewById(R.id.command_search);
        ZameenUi.applySurface(view.findViewById(R.id.command_bar), com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_action);
        bindFilters(view.findViewById(R.id.command_filters));
        bindTiles(view.findViewById(R.id.command_tiles));
        renderRecent();
        clear.setOnClickListener(v -> search.setText(""));
        view.findViewById(R.id.command_voice).setOnClickListener(v -> startVoice());
        view.findViewById(R.id.command_ask).setOnClickListener(v -> run(Command.ASK));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int begin, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int begin, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render();
            }
        });
        search.setOnEditorActionListener((input, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                ((InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(input.getWindowToken(), 0);
                remember(query());
                return true;
            }
            return false;
        });
        services.fieldRepository().observeFields().observe(getViewLifecycleOwner(), next -> {
            fields = next;
            rebuild();
        });
        services.recordRepository().observeAll().observe(getViewLifecycleOwner(), next -> {
            records = next;
            rebuild();
        });
        rebuild();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog sheet) {
            int height = Math.round(getResources().getDisplayMetrics().heightPixels * SHEET_HEIGHT);
            View root = requireView();
            ViewGroup.LayoutParams params = root.getLayoutParams();
            params.height = height;
            root.setLayoutParams(params);
            BottomSheetBehavior<?> behavior = sheet.getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void bindFilters(ChipGroup group) {
        addFilter(group, string.filter_all, null);
        addFilter(group, string.search_farms, SearchIndex.Kind.FARM);
        addFilter(group, string.search_records, SearchIndex.Kind.RECORD);
        addFilter(group, string.search_crops, SearchIndex.Kind.CROP);
        addFilter(group, string.search_actions, SearchIndex.Kind.ACTION);
        group.setOnCheckedStateChangeListener((chips, ids) -> {
            if (!ids.isEmpty()) {
                filter = (SearchIndex.Kind) chips.findViewById(ids.get(0)).getTag();
                render();
            }
        });
    }

    private void addFilter(ChipGroup group, int label, @Nullable SearchIndex.Kind kind) {
        Chip chip = new Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle);
        chip.setId(View.generateViewId());
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        chip.setText(label);
        chip.setTag(kind);
        chip.setChecked(kind == null);
        group.addView(chip);
    }

    private void bindTiles(LinearLayout parent) {
        Command[] commands = Command.values();
        int gap = getResources().getDimensionPixelSize(com.agroshift.core.ui.R.dimen.zameen_gap);
        LinearLayout row = null;
        for (int i = 0; i < TILE_COUNT; i++) {
            if (i % TILE_COLUMNS == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = gap;
                parent.addView(row, params);
            }
            Command command = commands[i];
            View tile = getLayoutInflater().inflate(R.layout.item_command_tile, row, false);
            ((ImageView) tile.findViewById(R.id.tile_icon)).setImageResource(command.icon);
            ((TextView) tile.findViewById(R.id.tile_label)).setText(command.title);
            ZameenUi.applySurface(tile, com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
            if (i % TILE_COLUMNS != 0) {
                ((LinearLayout.LayoutParams) tile.getLayoutParams()).setMarginStart(gap);
            }
            ZameenUi.attachPressSpring(tile);
            tile.setOnClickListener(v -> run(command));
            row.addView(tile);
        }
    }

    private void rebuild() {
        if (getView() == null) {
            return;
        }
        Context context = requireContext();
        List<SearchIndex.Entry> next = new ArrayList<>();
        fieldsById.clear();
        recordFields.clear();
        for (FarmField field : fields) {
            fieldsById.put(field.id(), field);
            next.add(new SearchIndex.Entry(SearchIndex.Kind.FARM, field.id(), field.name(),
                    getString(string.farm_summary, EngineText.rotationName(context, field.rotationKey()),
                            EngineText.number(field.areaAcres(), 1))));
        }
        for (FarmRecord record : records) {
            FarmField field = fieldsById.get(record.fieldId());
            recordFields.put(record.id(), record.fieldId());
            String type = getString(switch (record.type()) {
                case TASK -> string.record_task;
                case EXPENSE -> string.record_cost;
                case SALE -> string.record_sale;
                case NOTE -> string.record_note;
            });
            boolean money = record.type() == FarmRecord.Type.EXPENSE || record.type() == FarmRecord.Type.SALE;
            String detail = money ? type + ", " + EngineText.rupees(context, record.amountRupees()) : type;
            next.add(new SearchIndex.Entry(SearchIndex.Kind.RECORD, record.id(), record.title(),
                    field == null ? detail : detail + "  ·  " + field.name()));
        }
        List<String> crops = new ArrayList<>(Crop.KHARIF);
        crops.addAll(Crop.RABI);
        crops.add(Crop.YEAR_ROUND);
        for (String slug : crops) {
            String cropName = EngineText.cropName(context, slug);
            String season = getString(Crop.KHARIF.contains(slug) ? string.season_kharif
                    : Crop.RABI.contains(slug) ? string.season_rabi : string.crop_year_round);
            next.add(new SearchIndex.Entry(SearchIndex.Kind.CROP, slug, cropName,
                    cropName.equalsIgnoreCase(slug) ? season : season + "  ·  " + slug));
        }
        for (Command command : Command.values()) {
            next.add(new SearchIndex.Entry(SearchIndex.Kind.ACTION, command.name(), getString(command.title),
                    getString(string.search_action_label)));
        }
        entries = next;
        render();
    }

    private String query() {
        return search == null || search.getText() == null ? "" : search.getText().toString().strip();
    }

    private void render() {
        if (rows == null) {
            return;
        }
        String query = query();
        clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
        boolean browsing = query.isEmpty() && filter == null;
        start.setVisibility(browsing ? View.VISIBLE : View.GONE);
        rows.removeAllViews();
        if (browsing) {
            empty.setVisibility(View.GONE);
            renderBrowse();
            return;
        }
        List<SearchIndex.Entry> found = SearchIndex.find(entries, query, filter, MAX_RESULTS);
        empty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
        if (filter != null) {
            for (SearchIndex.Entry entry : found) {
                rows.addView(resultRow(entry, query));
            }
            return;
        }
        Map<SearchIndex.Kind, List<SearchIndex.Entry>> groups = new EnumMap<>(SearchIndex.Kind.class);
        for (SearchIndex.Entry entry : found) {
            groups.computeIfAbsent(entry.kind(), kind -> new ArrayList<>()).add(entry);
        }
        for (SearchIndex.Kind kind : new SearchIndex.Kind[] {SearchIndex.Kind.FARM, SearchIndex.Kind.RECORD,
                SearchIndex.Kind.CROP, SearchIndex.Kind.ACTION}) {
            List<SearchIndex.Entry> group = groups.get(kind);
            if (group != null) {
                rows.addView(sectionTitle(getString(string.search_section, getString(kindLabel(kind)), group.size())));
                for (SearchIndex.Entry entry : group) {
                    rows.addView(resultRow(entry, query));
                }
            }
        }
    }

    private void renderBrowse() {
        List<SearchIndex.Entry> farms = SearchIndex.find(entries, "", SearchIndex.Kind.FARM, 3);
        if (!farms.isEmpty()) {
            rows.addView(sectionTitle(getString(string.farm_strip_title)));
            for (SearchIndex.Entry farm : farms) {
                rows.addView(resultRow(farm, ""));
            }
        }
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(requireContext());
        title.setTextAppearance(com.agroshift.core.ui.R.style.TextAppearance_AgroShift_Caption);
        title.setTextColor(ContextCompat.getColor(requireContext(), com.agroshift.core.ui.R.color.zameen_accent));
        title.setText(text);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        int vertical = Math.round(8 * getResources().getDisplayMetrics().density);
        title.setPadding(0, vertical, 0, vertical / 2);
        return title;
    }

    private View resultRow(SearchIndex.Entry entry, String query) {
        View row = getLayoutInflater().inflate(R.layout.item_command, rows, false);
        ZameenUi.applySurface(row, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ImageView icon = row.findViewById(R.id.result_icon);
        ZameenUi.applySurface(icon, com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        icon.setImageResource(iconFor(entry));
        ((TextView) row.findViewById(R.id.result_title)).setText(highlight(entry.title(), query));
        ((TextView) row.findViewById(R.id.result_subtitle)).setText(entry.subtitle());
        row.setContentDescription(entry.title() + ", " + entry.subtitle());
        ZameenUi.attachPressSpring(row);
        row.setOnClickListener(v -> open(entry));
        return row;
    }

    private CharSequence highlight(String title, String query) {
        if (query.isEmpty()) {
            return title;
        }
        int at = title.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (at < 0 || at + query.length() > title.length()) {
            return title;
        }
        SpannableString marked = new SpannableString(title);
        marked.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(),
                com.agroshift.core.ui.R.color.zameen_accent)), at, at + query.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return marked;
    }

    private int iconFor(SearchIndex.Entry entry) {
        return switch (entry.kind()) {
            case FARM -> com.agroshift.core.ui.R.drawable.ic_ui_field;
            case CROP -> com.agroshift.core.ui.R.drawable.ic_ui_leaf;
            case ACTION -> Command.valueOf(entry.id()).icon;
            case RECORD -> recordIcon(entry.id());
        };
    }

    private int recordIcon(String id) {
        for (FarmRecord record : records) {
            if (record.id().equals(id)) {
                return switch (record.type()) {
                    case TASK -> com.agroshift.core.ui.R.drawable.ic_ui_task;
                    case EXPENSE -> com.agroshift.core.ui.R.drawable.ic_ui_cost;
                    case SALE -> com.agroshift.core.ui.R.drawable.ic_ui_sale;
                    case NOTE -> com.agroshift.core.ui.R.drawable.ic_ui_note;
                };
            }
        }
        return com.agroshift.core.ui.R.drawable.ic_ui_note;
    }

    private static int kindLabel(SearchIndex.Kind kind) {
        return switch (kind) {
            case FARM -> string.search_farms;
            case RECORD -> string.search_records;
            case CROP -> string.search_crops;
            case ACTION -> string.search_actions;
        };
    }

    private void open(SearchIndex.Entry entry) {
        remember(query());
        AppNavigator navigator = (AppNavigator) requireActivity();
        switch (entry.kind()) {
            case ACTION -> run(Command.valueOf(entry.id()));
            case FARM -> {
                dismiss();
                navigator.openField(entry.id());
            }
            case RECORD -> {
                String fieldId = recordFields.get(entry.id());
                dismiss();
                if (fieldId != null) {
                    navigator.openField(fieldId);
                }
            }
            case CROP -> {
                FragmentManager manager = getParentFragmentManager();
                dismiss();
                CropGuideSheet.create(entry.id()).show(manager, "crop_guide");
            }
        }
    }

    private void run(Command command) {
        AppServices services = (AppServices) requireContext().getApplicationContext();
        AppNavigator navigator = (AppNavigator) requireActivity();
        Host host = (Host) requireActivity();
        String selectedId = services.analysisRepository().selectedFieldId().getValue();
        FarmField selected = FarmSelection.choose(fields, selectedId);
        dismiss();
        switch (command) {
            case WATER -> {
                if (selected == null) {
                    navigator.openFieldSetup();
                } else {
                    navigator.openWater(selected.id());
                }
            }
            case ASK -> navigator.openAssistant();
            default -> host.runCommand(command);
        }
    }

    private void startVoice() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(string.search_hint));
        try {
            voice.launch(intent);
        } catch (ActivityNotFoundException unavailable) {
            Snackbar.make(requireView(), string.assistant_voice_error, Snackbar.LENGTH_LONG).show();
        }
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private List<String> recent() {
        String saved = prefs().getString(KEY_RECENT, "");
        List<String> values = new ArrayList<>();
        for (String value : saved.split(RECENT_SEPARATOR)) {
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private void remember(String value) {
        if (value.length() < 2) {
            return;
        }
        List<String> values = recent();
        values.removeIf(item -> item.equalsIgnoreCase(value));
        values.add(0, value);
        prefs().edit().putString(KEY_RECENT, String.join(RECENT_SEPARATOR,
                values.subList(0, Math.min(MAX_RECENT, values.size())))).apply();
    }

    private void renderRecent() {
        View root = requireView();
        ChipGroup group = root.findViewById(R.id.command_recent);
        List<String> values = recent();
        root.findViewById(R.id.command_recent_section).setVisibility(values.isEmpty() ? View.GONE : View.VISIBLE);
        group.removeAllViews();
        for (String value : values) {
            Chip chip = new Chip(requireContext());
            chip.setText(value);
            chip.setOnClickListener(v -> {
                search.setText(value);
                search.setSelection(search.length());
            });
            group.addView(chip);
        }
    }

    @Override
    public void onDestroyView() {
        search = null;
        rows = null;
        start = null;
        empty = null;
        clear = null;
        super.onDestroyView();
    }
}
