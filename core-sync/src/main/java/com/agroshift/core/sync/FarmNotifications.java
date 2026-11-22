package com.agroshift.core.sync;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.agroshift.core.i18n.R.string;

/** Posts canal turn and danger alert notifications when the farmer has allowed them. */
final class FarmNotifications {
    private static final String CHANNEL_TURN = "canal_turn_alarm";
    private static final String CHANNEL_DANGER = "danger_alerts";

    private FarmNotifications() {
    }

    static void postTurn(Context context, int id, String title, String body) {
        post(context, CHANNEL_TURN, id, title, body, NotificationCompat.CATEGORY_ALARM);
    }

    static void postDanger(Context context, int id, String title, String body) {
        post(context, CHANNEL_DANGER, id, title, body, NotificationCompat.CATEGORY_RECOMMENDATION);
    }

    private static void post(Context context, String channel, int id, String title, String body, String category) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createChannels(context);
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent tap = open == null ? null : PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManagerCompat.from(context).notify(id, new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(category)
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build());
    }

    private static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel turn = new NotificationChannel(CHANNEL_TURN,
                context.getString(string.channel_turn), NotificationManager.IMPORTANCE_HIGH);
        turn.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        turn.enableVibration(true);
        manager.createNotificationChannel(turn);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_DANGER,
                context.getString(string.channel_danger), NotificationManager.IMPORTANCE_HIGH));
    }
}
