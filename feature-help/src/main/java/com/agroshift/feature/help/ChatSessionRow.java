package com.agroshift.feature.help;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.agroshift.core.domain.chat.ChatSession;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Map;
import java.util.function.Consumer;

/** One saved conversation in the recent list or full history. */
final class ChatSessionRow {
    private ChatSessionRow() {
    }

    static View create(LayoutInflater inflater, ViewGroup parent, ChatSession session,
            Map<String, String> fieldNames, Consumer<ChatSession> onOpen, Consumer<ChatSession> onDelete) {
        View row = inflater.inflate(R.layout.item_chat_session, parent, false);
        Context context = row.getContext();
        ZameenUi.applySurface(row, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ZameenUi.applySurface(row.findViewById(R.id.session_icon), com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        ((TextView) row.findViewById(R.id.session_title)).setText(session.title());
        String place = session.fieldId() == null ? context.getString(string.chat_general)
                : fieldNames.getOrDefault(session.fieldId(), context.getString(string.chat_general));
        CharSequence when = DateUtils.getRelativeTimeSpanString(session.updatedAtMillis(), System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS);
        ((TextView) row.findViewById(R.id.session_meta)).setText(context.getString(string.chat_session_meta, place,
                when, session.messageCount()));
        row.setContentDescription(context.getString(string.chat_open, session.title()));
        ZameenUi.attachPressSpring(row);
        row.setOnClickListener(v -> onOpen.accept(session));
        row.findViewById(R.id.session_delete).setOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle(string.chat_delete_title)
                .setMessage(session.title())
                .setNegativeButton(string.action_keep_editing, null)
                .setPositiveButton(string.chat_delete, (dialog, which) -> onDelete.accept(session))
                .show());
        return row;
    }
}
