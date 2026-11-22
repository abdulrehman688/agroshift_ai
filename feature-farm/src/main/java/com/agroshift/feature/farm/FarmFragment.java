package com.agroshift.feature.farm;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.FieldRepository;
import com.agroshift.core.ui.AppNavigator;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

public final class FarmFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_farm, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        ZameenUi.applySafeArea(view, false);
        FieldRepository repository =
                ((AppServices) requireContext().getApplicationContext()).fieldRepository();
        AppNavigator navigator = (AppNavigator) requireActivity();
        RecyclerView list = view.findViewById(R.id.farm_fields);
        View empty = view.findViewById(R.id.farm_empty);
        FieldListBinder binder = new FieldListBinder(list, view, repository,
                field -> navigator.openField(field.id()));
        repository.observeFields().observe(getViewLifecycleOwner(), fields -> {
            binder.submit(fields);
            empty.setVisibility(fields.isEmpty() ? View.VISIBLE : View.GONE);
        });
        showSwipeHintOnce(view);
        MaterialButton add = view.findViewById(R.id.farm_add);
        ZameenUi.polishPrimary(add);
        add.setOnClickListener(v -> navigator.openFieldSetup());
    }

    private void showSwipeHintOnce(View view) {
        SharedPreferences prefs = requireContext().getSharedPreferences("agroshift_hints",
                Context.MODE_PRIVATE);
        if (!prefs.getBoolean("swipe_field", false)) {
            prefs.edit().putBoolean("swipe_field", true).apply();
            Snackbar.make(view, com.agroshift.core.i18n.R.string.swipe_hint, Snackbar.LENGTH_LONG).show();
        }
    }
}
