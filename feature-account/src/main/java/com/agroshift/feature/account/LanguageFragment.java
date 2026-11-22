package com.agroshift.feature.account;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.agroshift.core.domain.language.AppLanguage;
import com.agroshift.core.i18n.LanguageController;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

public final class LanguageFragment extends Fragment {
    public interface Host {
        void onLanguageChosen(AppLanguage language);
    }

    private static final String KEY_SELECTED = "selected";

    private final List<MaterialButton> buttons = new ArrayList<>();
    private String selectedTag;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_language, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        selectedTag = state != null
                ? state.getString(KEY_SELECTED)
                : new LanguageController(requireContext()).selectedTag();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), 0, v.getPaddingRight(), bars.bottom);
            return insets;
        });

        LinearLayout list = view.findViewById(R.id.language_list);
        LayoutInflater inflater = getLayoutInflater();
        for (AppLanguage language : AppLanguage.WAVE_ONE) {
            MaterialButton button = (MaterialButton) inflater.inflate(
                    R.layout.item_language, list, false);
            button.setText(language.nativeName());
            button.setTextDirection(language.rtl()
                    ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
            button.setTag(language.tag());
            button.setOnClickListener(v -> {
                selectedTag = language.tag();
                renderSelection();
            });
            buttons.add(button);
            list.addView(button);
        }
        renderSelection();

        view.findViewById(R.id.language_continue).setOnClickListener(v ->
                AppLanguage.find(selectedTag).ifPresent(language ->
                        ((Host) requireActivity()).onLanguageChosen(language)));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEY_SELECTED, selectedTag);
    }

    @Override
    public void onDestroyView() {
        buttons.clear();
        super.onDestroyView();
    }

    private void renderSelection() {
        int accent = ContextCompat.getColor(requireContext(),
                com.agroshift.core.ui.R.color.zameen_accent);
        int hairline = ContextCompat.getColor(requireContext(),
                com.agroshift.core.ui.R.color.zameen_hairline);
        int density = Math.round(getResources().getDisplayMetrics().density);
        for (MaterialButton button : buttons) {
            boolean selected = button.getTag().equals(selectedTag);
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(
                    selected ? accent : hairline));
            button.setStrokeWidth((selected ? 2 : 1) * density);
            button.setSelected(selected);
        }
    }
}
