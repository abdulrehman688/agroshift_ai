package com.agroshift.feature.help;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import com.agroshift.core.data.AnalysisRepository;
import com.agroshift.core.data.AnalysisState;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.OnlineServices;
import com.agroshift.core.domain.chat.ChatSession;
import com.agroshift.core.domain.farm.FarmField;
import com.agroshift.core.domain.language.ChatLanguage;
import com.agroshift.core.i18n.EngineText;
import com.agroshift.core.i18n.R.string;
import com.agroshift.core.ui.ZameenUi;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Text and voice chat grounded in the selected field's NASA analysis. */
public final class AssistantFragment extends Fragment {
    private static final int MAX_RECENT = 3;
    private static final String TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";
    private static final int[] PROMPTS = {string.assistant_prompt_1, string.assistant_prompt_2,
            string.assistant_prompt_3};

    private OnlineServices online;
    private AnalysisRepository analysis;
    private TextToSpeech speech;
    private boolean speechReady;
    private boolean waiting;
    private boolean bindingChips;
    private int speakingIndex = -1;
    private int renderedCount = -1;
    private List<OnlineServices.ChatMessage> currentMessages = List.of();
    private final List<FarmField> fields = new ArrayList<>();
    private FarmField selected;
    private AnalysisState analysisState;
    private LiveData<AnalysisState> activeAnalysis;
    private LiveData<List<OnlineServices.ChatMessage>> activeChat;
    private final Observer<AnalysisState> analysisObserver = state -> {
        analysisState = state;
        renderContext();
    };
    private final Observer<List<OnlineServices.ChatMessage>> chatObserver = this::renderMessages;

    private LinearLayout messages;
    private NestedScrollView scroll;
    private EditText input;
    private View thinking;
    private View empty;
    private View clear;
    private ImageButton send;
    private MaterialButton language;
    private ChipGroup fieldChips;
    private View fieldScroll;
    private TextView contextLine;
    private View recentSection;
    private LinearLayout recentRows;
    private List<ChatSession> sessions = List.of();

    private final ActivityResultLauncher<Intent> voice = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> words = result.getData()
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (words != null && !words.isEmpty() && canAsk()) {
                        ask(words.get(0));
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_assistant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        AppServices services = (AppServices) requireContext().getApplicationContext();
        online = services.onlineServices();
        analysis = services.analysisRepository();
        messages = view.findViewById(R.id.assistant_messages);
        scroll = view.findViewById(R.id.assistant_scroll);
        input = view.findViewById(R.id.assistant_input);
        thinking = view.findViewById(R.id.assistant_thinking);
        empty = view.findViewById(R.id.assistant_empty);
        clear = view.findViewById(R.id.assistant_clear);
        send = view.findViewById(R.id.assistant_send);
        language = view.findViewById(R.id.assistant_language);
        fieldChips = view.findViewById(R.id.assistant_fields);
        fieldScroll = view.findViewById(R.id.assistant_fields_scroll);
        contextLine = view.findViewById(R.id.assistant_context);

        int radius = com.agroshift.core.ui.R.dimen.zameen_radius_card;
        ZameenUi.applySurface(view.findViewById(R.id.assistant_mark),
                com.agroshift.core.ui.R.color.zameen_accent, radius);
        ZameenUi.applySurface(view.findViewById(R.id.assistant_composer),
                com.agroshift.core.ui.R.color.zameen_surface, com.agroshift.core.ui.R.dimen.zameen_radius_action);
        ZameenUi.applySurface(send, com.agroshift.core.ui.R.color.zameen_accent, radius);
        ZameenUi.attachPressSpring(send);

        int bottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), safe.top, v.getPaddingRight(), bottom + safe.bottom);
            return insets;
        });
        view.findViewById(R.id.assistant_back).setOnClickListener(
                v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        send.setOnClickListener(v -> {
            String question = input.getText().toString().strip();
            if (!question.isEmpty() && canAsk()) {
                input.setText("");
                ask(question);
            }
        });
        clear.setOnClickListener(v -> {
            stopSpeaking();
            online.startNewSession(selected == null ? null : selected.id());
            attachChat();
        });
        recentSection = view.findViewById(R.id.assistant_recent_section);
        recentRows = view.findViewById(R.id.assistant_recent);
        View.OnClickListener history = v -> {
            if (getChildFragmentManager().findFragmentByTag("chat_history") == null) {
                new ChatHistorySheet().show(getChildFragmentManager(), "chat_history");
            }
        };
        view.findViewById(R.id.assistant_history).setOnClickListener(history);
        view.findViewById(R.id.assistant_full_history).setOnClickListener(history);
        getChildFragmentManager().setFragmentResultListener(ChatHistorySheet.RESULT, getViewLifecycleOwner(),
                (key, result) -> openSession(result.getString(ChatHistorySheet.KEY_SESSION),
                        result.getString(ChatHistorySheet.KEY_FIELD)));
        online.observeSessions().observe(getViewLifecycleOwner(), next -> {
            sessions = next;
            renderRecent();
        });
        view.findViewById(R.id.assistant_mic).setOnClickListener(v -> startVoice());
        language.setOnClickListener(v -> new ChatLanguageSheet().show(getChildFragmentManager(), "chat_language"));
        getChildFragmentManager().setFragmentResultListener(ChatLanguageSheet.RESULT, getViewLifecycleOwner(),
                (key, result) -> renderLanguage());
        renderLanguage();
        addPrompts(view.findViewById(R.id.assistant_prompts));
        setupSpeech();

        fieldChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (!bindingChips && !ids.isEmpty()) {
                analysis.selectField((String) group.findViewById(ids.get(0)).getTag());
            }
        });
        services.fieldRepository().observeFields().observe(getViewLifecycleOwner(), list -> {
            fields.clear();
            for (FarmField field : list) {
                if (field.hasLocation()) {
                    fields.add(field);
                }
            }
            bindField(analysis.selectedFieldId().getValue());
        });
        analysis.selectedFieldId().observe(getViewLifecycleOwner(), this::bindField);
    }

    private void addPrompts(LinearLayout parent) {
        for (int prompt : PROMPTS) {
            TextView row = (TextView) getLayoutInflater().inflate(R.layout.item_chat_prompt, parent, false);
            row.setText(prompt);
            ZameenUi.applySurface(row, com.agroshift.core.ui.R.color.zameen_surface,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
            ZameenUi.attachPressSpring(row);
            row.setOnClickListener(v -> {
                if (canAsk()) {
                    ask(getString(prompt));
                }
            });
            parent.addView(row);
        }
    }

    private void setupSpeech() {
        speech = new TextToSpeech(requireContext().getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS && speech != null) {
                speechReady = true;
                speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                    }

                    @Override
                    public void onDone(String id) {
                        finishSpeaking();
                    }

                    @Override
                    public void onError(String id) {
                        finishSpeaking();
                    }

                    @Override
                    public void onStop(String id, boolean interrupted) {
                        if (!interrupted) {
                            finishSpeaking();
                        }
                    }
                });
            }
        });
    }

    private void finishSpeaking() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (getView() != null) {
                    speakingIndex = -1;
                    renderMessages(currentMessages);
                }
            });
        }
    }

    private void renderLanguage() {
        language.setText(online.chatLanguage().nativeName());
    }

    private void bindField(@Nullable String requestedId) {
        FarmField next = null;
        for (FarmField field : fields) {
            if (field.id().equals(requestedId)) {
                next = field;
            }
        }
        if (next == null && !fields.isEmpty()) {
            next = fields.get(0);
        }
        boolean changed = selected == null || next == null || !selected.id().equals(next.id());
        selected = next;
        renderFieldChips();
        if (changed || activeChat == null) {
            stopSpeaking();
            detach();
            analysisState = null;
            renderedCount = -1;
            attachChat();
            if (next != null) {
                activeAnalysis = analysis.observe(next.id());
                activeAnalysis.observe(getViewLifecycleOwner(), analysisObserver);
            }
        }
        if (next != null) {
            analysis.load(next, false);
        }
        renderContext();
    }

    private void attachChat() {
        if (activeChat != null) {
            activeChat.removeObserver(chatObserver);
        }
        renderedCount = -1;
        activeChat = online.chatMessages(chatKey());
        activeChat.observe(getViewLifecycleOwner(), chatObserver);
        renderRecent();
    }

    private void openSession(@Nullable String sessionId, @Nullable String fieldId) {
        if (sessionId == null) {
            return;
        }
        stopSpeaking();
        boolean known = false;
        for (FarmField field : fields) {
            known |= field.id().equals(fieldId);
        }
        if (known && (selected == null || !selected.id().equals(fieldId))) {
            online.openSession(fieldId, sessionId);
            analysis.selectField(fieldId);
        } else {
            online.openSession(selected == null ? null : selected.id(), sessionId);
            attachChat();
        }
    }

    private void renderRecent() {
        if (recentRows == null) {
            return;
        }
        List<ChatSession> recent = ChatSession.recent(sessions, chatKey(), MAX_RECENT);
        Map<String, String> names = new HashMap<>();
        for (FarmField field : fields) {
            names.put(field.id(), field.name());
        }
        recentRows.removeAllViews();
        for (ChatSession session : recent) {
            recentRows.addView(ChatSessionRow.create(getLayoutInflater(), recentRows, session, names,
                    item -> openSession(item.id(), item.fieldId()), item -> online.deleteSession(item.id())));
        }
        recentSection.setVisibility(sessions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void detach() {
        if (activeAnalysis != null) {
            activeAnalysis.removeObserver(analysisObserver);
            activeAnalysis = null;
        }
        if (activeChat != null) {
            activeChat.removeObserver(chatObserver);
            activeChat = null;
        }
    }

    private String chatKey() {
        return online.currentSession(selected == null ? null : selected.id());
    }

    private void renderFieldChips() {
        bindingChips = true;
        fieldChips.removeAllViews();
        for (FarmField field : fields) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_assistant_field_chip, fieldChips, false);
            chip.setId(View.generateViewId());
            chip.setText(field.name());
            chip.setTag(field.id());
            fieldChips.addView(chip);
            if (selected != null && field.id().equals(selected.id())) {
                fieldChips.check(chip.getId());
            }
        }
        bindingChips = false;
        fieldScroll.setVisibility(fields.size() > 1 ? View.VISIBLE : View.GONE);
    }

    private void renderContext() {
        if (getView() == null) {
            return;
        }
        if (selected == null) {
            contextLine.setText(string.assistant_context_none);
        } else if (isReady()) {
            contextLine.setText(getString(string.assistant_context_ready, selected.name()));
        } else if (isLoading()) {
            contextLine.setText(getString(string.assistant_context_loading, selected.name()));
        } else {
            contextLine.setText(string.assistant_context_failed);
        }
        boolean enabled = canAsk();
        send.setEnabled(enabled);
        send.setAlpha(enabled ? 1f : 0.45f);
    }

    private boolean isReady() {
        return analysisState != null && analysisState.status() == AnalysisState.Status.READY
                && analysisState.advice() != null;
    }

    private boolean isLoading() {
        return selected != null && (analysisState == null
                || analysisState.status() == AnalysisState.Status.LOADING);
    }

    private boolean canAsk() {
        return !waiting && !isLoading();
    }

    private void renderMessages(List<OnlineServices.ChatMessage> list) {
        if (getView() == null) {
            return;
        }
        currentMessages = list;
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        clear.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        messages.removeAllViews();
        for (int i = 0; i < list.size(); i++) {
            OnlineServices.ChatMessage message = list.get(i);
            messages.addView(message.fromFarmer() ? userBubble(message) : answerCard(message, i));
        }
        if (list.size() != renderedCount) {
            renderedCount = list.size();
            scrollToEnd();
        }
    }

    private void scrollToEnd() {
        scroll.post(() -> {
            if (scroll != null) {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private View userBubble(OnlineServices.ChatMessage message) {
        TextView bubble = (TextView) getLayoutInflater().inflate(R.layout.item_chat_user, messages, false);
        bubble.setText(message.text());
        ZameenUi.applySurface(bubble, com.agroshift.core.ui.R.color.zameen_surface_accent,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        return bubble;
    }

    private View answerCard(OnlineServices.ChatMessage message, int index) {
        View card = getLayoutInflater().inflate(R.layout.item_chat_answer, messages, false);
        ZameenUi.applySurface(card, com.agroshift.core.ui.R.color.zameen_surface,
                com.agroshift.core.ui.R.dimen.zameen_radius_card);
        card.findViewById(R.id.answer_data).setVisibility(message.usedFieldData() ? View.VISIBLE : View.GONE);
        ((TextView) card.findViewById(R.id.answer_text)).setText(message.text());
        if (!message.actions().isEmpty()) {
            card.findViewById(R.id.answer_actions_section).setVisibility(View.VISIBLE);
            LinearLayout actions = card.findViewById(R.id.answer_actions);
            for (String action : message.actions()) {
                View row = getLayoutInflater().inflate(R.layout.item_chat_action, actions, false);
                ((TextView) row.findViewById(R.id.action_text)).setText(action);
                actions.addView(row);
            }
        }
        if (message.expert() != null) {
            View section = card.findViewById(R.id.answer_expert_section);
            section.setVisibility(View.VISIBLE);
            ZameenUi.applySurface(section, com.agroshift.core.ui.R.color.zameen_surface_accent,
                    com.agroshift.core.ui.R.dimen.zameen_radius_card);
            ((TextView) card.findViewById(R.id.answer_expert)).setText(message.expert());
        }
        MaterialButton read = card.findViewById(R.id.answer_read);
        boolean speaking = speakingIndex == index;
        read.setText(speaking ? string.assistant_stop : string.assistant_read_aloud);
        read.setIconResource(speaking ? R.drawable.ic_chat_stop : R.drawable.ic_chat_speaker);
        card.findViewById(R.id.answer_wave).setVisibility(speaking ? View.VISIBLE : View.GONE);
        read.setOnClickListener(v -> toggleSpeech(message, index));
        card.findViewById(R.id.answer_copy).setOnClickListener(v -> copy(message));
        return card;
    }

    private static String fullText(OnlineServices.ChatMessage message) {
        StringBuilder text = new StringBuilder(message.text());
        for (String action : message.actions()) {
            text.append("\n").append(action);
        }
        if (message.expert() != null) {
            text.append("\n").append(message.expert());
        }
        return text.toString();
    }

    private void toggleSpeech(OnlineServices.ChatMessage message, int index) {
        if (speakingIndex == index) {
            stopSpeaking();
            renderMessages(currentMessages);
            return;
        }
        if (!speechReady || speech == null) {
            showVoiceMissing();
            return;
        }
        ChatLanguage messageLanguage = ChatLanguage.fromCode(message.languageCode());
        if (messageLanguage == null) {
            messageLanguage = ChatLanguage.ENGLISH;
        }
        Locale locale = Locale.forLanguageTag(messageLanguage.speechTag());
        boolean fallback = false;
        if (speech.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) {
            Locale urdu = Locale.forLanguageTag(ChatLanguage.URDU.speechTag());
            if (messageLanguage.usesArabicScript()
                    && speech.isLanguageAvailable(urdu) >= TextToSpeech.LANG_AVAILABLE) {
                locale = urdu;
                fallback = messageLanguage != ChatLanguage.URDU;
            } else {
                showVoiceMissing();
                return;
            }
        }
        int result = speech.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            showVoiceMissing();
            return;
        }
        speakingIndex = index;
        speech.speak(fullText(message), TextToSpeech.QUEUE_FLUSH, null, "answer-" + index);
        renderMessages(currentMessages);
        if (fallback) {
            Snackbar.make(requireView(), string.voice_urdu_fallback, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void stopSpeaking() {
        speakingIndex = -1;
        if (speech != null) {
            speech.stop();
        }
    }

    private void showVoiceMissing() {
        Snackbar.make(requireView(), string.voice_unavailable, Snackbar.LENGTH_LONG)
                .setAction(string.voice_settings, v -> {
                    try {
                        startActivity(new Intent(TTS_SETTINGS));
                    } catch (ActivityNotFoundException unavailable) {
                        Snackbar.make(requireView(), string.assistant_voice_error, Snackbar.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void copy(OnlineServices.ChatMessage message) {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(string.assistant_title), fullText(message)));
        Snackbar.make(requireView(), string.assistant_copied, Snackbar.LENGTH_SHORT).show();
    }

    private void ask(String question) {
        stopSpeaking();
        waiting = true;
        thinking.setVisibility(View.VISIBLE);
        renderContext();
        scrollToEnd();
        String crops = selected == null ? "" : EngineText.rotationName(requireContext(), selected.rotationKey());
        online.ask(chatKey(), question, selected, isReady() ? analysisState.outlooks() : null, crops,
                online.chatLanguage(), failure -> {
                    waiting = false;
                    if (getView() == null) {
                        return;
                    }
                    thinking.setVisibility(View.GONE);
                    renderContext();
                    scrollToEnd();
                    if (failure != null) {
                        Snackbar.make(requireView(), switch (failure) {
                            case RATE_LIMITED -> string.chat_error_busy;
                            case SERVER -> string.chat_error_server;
                            case NETWORK -> string.chat_error_network;
                            default -> string.assistant_error;
                        }, Snackbar.LENGTH_LONG).show();
                    }
                });
    }

    private void startVoice() {
        if (!canAsk()) {
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, online.chatLanguage().speechTag())
                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(string.assistant_voice));
        try {
            voice.launch(intent);
        } catch (ActivityNotFoundException unavailable) {
            Snackbar.make(requireView(), string.assistant_voice_error, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onDestroyView() {
        detach();
        if (speech != null) {
            speech.stop();
            speech.shutdown();
            speech = null;
        }
        speechReady = false;
        speakingIndex = -1;
        renderedCount = -1;
        currentMessages = List.of();
        selected = null;
        analysisState = null;
        messages = null;
        scroll = null;
        input = null;
        thinking = null;
        empty = null;
        clear = null;
        send = null;
        language = null;
        fieldChips = null;
        fieldScroll = null;
        contextLine = null;
        recentSection = null;
        recentRows = null;
        super.onDestroyView();
    }
}
