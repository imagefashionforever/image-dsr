package com.imagefashion.dsr;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Sale-report reminders from the server (Push.gs) -> a normal phone notification,
 * also when the app is closed. Tapping it opens Image DSR.
 */
public class PushService extends FirebaseMessagingService {

    static final String CHANNEL = "sale_report";
    private static final String PREFS = "dsr_push";

    static void saveToken(Context c, String t) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("token", t).apply();
    }

    static String token(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", "");
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Sale report reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Reminder when your daily sale report is pending");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 300, 150, 300});
        ch.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        nm.createNotificationChannel(ch);
    }

    @Override
    public void onNewToken(String t) {
        saveToken(this, t);
    }

    @Override
    public void onMessageReceived(RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        String title = d.get("title"), body = d.get("body"), tag = d.get("tag");
        RemoteMessage.Notification n = msg.getNotification();
        if (title == null && n != null) title = n.getTitle();
        if (body == null && n != null) body = n.getBody();
        show(this, title == null ? "Image DSR" : title, body == null ? "Please fill your sale report." : body,
                tag == null ? "dsr-pending" : tag);
    }

    static void show(Context c, String title, String body, String tag) {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        ensureChannel(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_dsr)
                .setLargeIcon(BitmapFactory.decodeResource(c.getResources(), R.mipmap.ic_launcher))
                .setColor(ContextCompat.getColor(c, R.color.brand))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            if (nm != null) nm.notify(tag, 1, b.build());      // same tag -> the old reminder is replaced, not stacked
        } catch (SecurityException ignored) { }
    }
}
