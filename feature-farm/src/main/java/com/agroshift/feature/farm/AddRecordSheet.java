package com.agroshift.feature.farm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.domain.Result;
import com.agroshift.core.domain.farm.FarmRecord;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** One-step entry for a task, cost, sale or note. */
public final class AddRecordSheet extends BottomSheetDialogFragment {
    private static final String ARG_FIELD = "field";
    private static final String ARG_TYPE = "type";

    public static AddRecordSheet create(String fieldId, FarmRecord.Type type) {
        Bundle args = new Bundle();
        args.putString(ARG_FIELD, fieldId);
        args.putString(ARG_TYPE, type.name());
        AddRecordSheet sheet = new AddRecordSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_add_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        FarmRecord.Type type = FarmRecord.Type.valueOf(requireArguments().getString(ARG_TYPE));
        String fieldId = requireArguments().getString(ARG_FIELD);
        boolean money = type == FarmRecord.Type.EXPENSE || type == FarmRecord.Type.SALE;
        ((TextView) view.findViewById(R.id.record_heading)).setText(switch (type) {
            case TASK -> string.record_task;
            case EXPENSE -> string.record_cost;
            case SALE -> string.record_sale;
            case NOTE -> string.record_note;
        });
        TextInputLayout titleLayout = view.findViewById(R.id.record_title_layout);
        TextInputLayout amountLayout = view.findViewById(R.id.record_amount_layout);
        titleLayout.setHint(switch (type) {
            case TASK -> string.record_title_task;
            case EXPENSE -> string.record_title_cost;
            case SALE -> string.record_title_sale;
            case NOTE -> string.record_title_note;
        });
        amountLayout.setVisibility(money ? View.VISIBLE : View.GONE);
        TextInputEditText title = view.findViewById(R.id.record_title);
        TextInputEditText amount = view.findViewById(R.id.record_amount);
        MaterialButton save = view.findViewById(R.id.record_save);
        ZameenUi.polishPrimary(save);
        save.setOnClickListener(v -> {
            titleLayout.setError(null);
            amountLayout.setError(null);
            double value = money ? FieldSetupFragment.parseArea(text(amount)) : 0;
            Result<FarmRecord> result = ((AppServices) requireContext().getApplicationContext())
                    .recordRepository().add(fieldId, type, text(title), value);
            if (result.isSuccess()) {
                dismiss();
            } else if (FarmRecord.ERROR_TITLE.equals(result.errorCode())) {
                titleLayout.setError(getString(string.error_record_title));
            } else {
                amountLayout.setError(getString(string.error_record_amount));
            }
        });
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
