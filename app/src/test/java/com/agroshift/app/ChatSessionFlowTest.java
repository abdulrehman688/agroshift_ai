package com.agroshift.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ApplicationProvider;
import com.agroshift.core.data.AgroShiftDatabase;
import com.agroshift.core.data.AppServices;
import com.agroshift.core.data.ChatRepository;
import com.agroshift.core.data.OnlineServices;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Saved chats return after restart and can be continued from history. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = TestAgroShiftApp.class)
public class ChatSessionFlowTest {
    private static void settle() {
        for (int i = 0; i < 25; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void newLaunchStartsNewSessionAndKeepsOldMessages() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        AgroShiftDatabase database = AgroShiftDatabase.create(context);
        ChatRepository first = new ChatRepository(database.chatDao(), Executors.newSingleThreadExecutor());
        OnlineServices launchOne = new OnlineServices(context, first);
        String oldSession = launchOne.currentSession(null);
        List<OnlineServices.ChatMessage> saved = List.of(
                new OnlineServices.ChatMessage(true, "When should I sow wheat?", List.of(), null, false, "en"),
                new OnlineServices.ChatMessage(false, "Sow in November", List.of("Prepare land"), "Ask officer", true, "en"));
        first.save(oldSession, null, saved);
        settle();

        ChatRepository second = new ChatRepository(database.chatDao(), Executors.newSingleThreadExecutor());
        OnlineServices launchTwo = new OnlineServices(context, second);
        assertNotEquals(oldSession, launchTwo.currentSession(null));
        CompletableFuture<List<OnlineServices.ChatMessage>> restored = new CompletableFuture<>();
        second.load(oldSession, restored::complete);
        assertEquals(saved, restored.get(5, TimeUnit.SECONDS));
        launchTwo.openSession(null, oldSession);
        assertEquals(oldSession, launchTwo.currentSession(null));
        database.close();
    }

    @Test
    public void historyOpensAndContinuesAChat() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("agroshift_language", Context.MODE_PRIVATE).edit()
                .putString("selected_tag", "en-PK").putBoolean("onboarding_seen", true).commit();
        android.provider.Settings.Global.putFloat(context.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        settle();
        AppServices services = (AppServices) context;
        AgroShiftDatabase database = AgroShiftDatabase.create(context);
        ChatRepository repository = new ChatRepository(database.chatDao(), Executors.newSingleThreadExecutor());
        repository.save("older-chat", null, List.of(
                new OnlineServices.ChatMessage(true, "Canal water question", List.of(), null, false, "en"),
                new OnlineServices.ChatMessage(false, "Saved answer", List.of(), null, false, "en")));
        settle();
        activity.openAssistant();
        settle();
        Fragment host = activity.getSupportFragmentManager().findFragmentById(R.id.nav_host);
        Fragment assistant = host.getChildFragmentManager().getPrimaryNavigationFragment();
        View page = assistant.requireView();
        ViewGroup recent = page.findViewById(com.agroshift.feature.help.R.id.assistant_recent);
        assertEquals(1, recent.getChildCount());
        page.findViewById(com.agroshift.feature.help.R.id.assistant_full_history).performClick();
        settle();
        DialogFragment history = (DialogFragment) assistant.getChildFragmentManager().findFragmentByTag("chat_history");
        assertNotNull(history);
        ViewGroup rows = history.requireView().findViewById(com.agroshift.feature.help.R.id.history_rows);
        assertEquals(1, rows.getChildCount());
        rows.getChildAt(0).performClick();
        settle();
        assertEquals("older-chat", services.onlineServices().currentSession(null));
        ViewGroup messages = page.findViewById(com.agroshift.feature.help.R.id.assistant_messages);
        assertEquals(2, messages.getChildCount());
        assertEquals("Canal water question", ((TextView) messages.getChildAt(0)).getText().toString());
        database.close();
    }
}
