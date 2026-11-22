package com.agroshift.feature.help;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.OnlineServices;
import com.agroshift.core.domain.chat.ChatSession;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.i18n.R.plurals;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Full chat history. Opening a conversation continues it. */
public final class ChatHistorySheet extends BottomSheetDialogFragment {
    static final String RESULT = "chat_history_open";
    static final String KEY_SESSION = "session";
    static final String KEY_FIELD = "field";
    private static final float SHEET_HEIGHT = 0.88f;

    private final Map<String, String> fieldNames = new HashMap<>();
    private List<ChatSession> sessions = List.of();
    private OnlineServices online;
    private LinearLayout rows;
    private TextView empty;
    private TextView count;
    private EditText search;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_chat_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppServices services = (AppServices) requireContext().getApplicationContext();
        online = services.onlineServices();
        rows = view.findViewById(R.id.history_rows);
        empty = view.findViewById(R.id.history_empty);
        count = view.findViewById(R.id.history_count);
        search = view.findViewById(R.id.history_search);
        ZameenUi.applySurface(view.findViewById(R.id.history_bar), com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_action);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                render();
            }
        });
        services.fieldRepository().observeFields().observe(getViewLifecycleOwner(), fields -> {
            fieldNames.clear();
            for (FarmField field : fields) {
                fieldNames.put(field.id(), field.name());
            }
            render();
        });
        online.observeSessions().observe(getViewLifecycleOwner(), next -> {
            sessions = next;
            render();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog sheet) {
            View root = requireView();
            ViewGroup.LayoutParams params = root.getLayoutParams();
            params.height = Math.round(getResources().getDisplayMetrics().heightPixels * SHEET_HEIGHT);
            root.setLayoutParams(params);
            sheet.getBehavior().setSkipCollapsed(true);
            sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void render() {
        if (rows == null) {
            return;
        }
        List<ChatSession> found = ChatSession.search(sessions, search.getText().toString());
        count.setText(getResources().getQuantityString(plurals.chat_count, sessions.size(), sessions.size()));
        rows.removeAllViews();
        for (ChatSession session : found) {
            rows.addView(ChatSessionRow.create(getLayoutInflater(), rows, session, fieldNames, this::open,
                    item -> online.deleteSession(item.id())));
        }
        empty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void open(ChatSession session) {
        Bundle result = new Bundle();
        result.putString(KEY_SESSION, session.id());
        result.putString(KEY_FIELD, session.fieldId());
        getParentFragmentManager().setFragmentResult(RESULT, result);
        dismiss();
    }

    @Override
    public void onDestroyView() {
        rows = null;
        empty = null;
        count = null;
        search = null;
        super.onDestroyView();
    }
}
