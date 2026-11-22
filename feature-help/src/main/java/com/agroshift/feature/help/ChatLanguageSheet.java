package com.agroshift.feature.help;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.OnlineServices;
import com.agroshift.core.domain.language.ChatLanguage;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Chooses the chatbot language without changing the whole app language. */
public final class ChatLanguageSheet extends BottomSheetDialogFragment {
    static final String RESULT = "chat_language_changed";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_chat_language, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        OnlineServices online = ((AppServices) requireContext().getApplicationContext()).onlineServices();
        ChatLanguage current = online.chatLanguage();
        LinearLayout rows = view.findViewById(R.id.chat_language_rows);
        LayoutInflater inflater = getLayoutInflater();
        for (ChatLanguage language : ChatLanguage.values()) {
            View row = inflater.inflate(R.layout.item_chat_language, rows, false);
            ZameenUi.applySurface(row, language == current
                            ? com.agroshift.core.ui.R.color.zameen_surface_accent
                            : com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
            TextView name = row.findViewById(R.id.language_name);
            name.setText(language.nativeName());
            name.setTextDirection(language.usesArabicScript()
                    ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
            TextView status = row.findViewById(R.id.language_status);
            if (!language.available()) {
                status.setText(string.chat_language_soon);
                row.setAlpha(0.55f);
                row.setEnabled(false);
            } else if (language.beta()) {
                status.setText(string.chat_language_beta);
            }
            row.findViewById(R.id.language_check)
                    .setVisibility(language == current ? View.VISIBLE : View.GONE);
            if (language.available()) {
                row.setClickable(true);
                row.setFocusable(true);
                row.setOnClickListener(v -> {
                    online.setChatLanguage(language);
                    getParentFragmentManager().setFragmentResult(RESULT, new Bundle());
                    dismiss();
                });
            }
            rows.addView(row);
        }
    }
}
