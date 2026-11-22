package com.agroshift.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.agroshift.core.i18n.R.string;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import java.util.List;
import java.util.Locale;

public final class CommandMenuSheet extends BottomSheetDialogFragment {
    public enum Command {
        ADD_FIELD(string.action_add_field, R.drawable.ic_add),
        HOME(string.nav_home, R.drawable.ic_nav_home),
        FARM(string.nav_farm, R.drawable.ic_nav_farm),
        ADVISOR(string.nav_advisor, R.drawable.ic_nav_advisor),
        MARKET(string.nav_market, R.drawable.ic_nav_market),
        COMMUNITY(string.nav_community, R.drawable.ic_nav_community),
        LANGUAGE(string.command_language, R.drawable.ic_language);

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_command, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        LinearLayout rows = view.findViewById(R.id.command_rows);
        TextView empty = view.findViewById(R.id.command_empty);
        EditText search = view.findViewById(R.id.command_search);
        render(rows, empty, "");
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render(rows, empty, s.toString());
            }
        });
    }

    private void render(LinearLayout rows, TextView empty, String rawQuery) {
        String query = rawQuery.strip().toLowerCase(Locale.getDefault());
        rows.removeAllViews();
        LayoutInflater inflater = getLayoutInflater();
        for (Command command : List.of(Command.values())) {
            String title = getString(command.title);
            if (!title.toLowerCase(Locale.getDefault()).contains(query)) {
                continue;
            }
            MaterialButton row = (MaterialButton) inflater.inflate(
                    R.layout.item_command, rows, false);
            row.setText(title);
            row.setIconResource(command.icon);
            row.setOnClickListener(v -> {
                Host host = (Host) requireActivity();
                dismiss();
                host.runCommand(command);
            });
            rows.addView(row);
        }
        empty.setVisibility(rows.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }
}
