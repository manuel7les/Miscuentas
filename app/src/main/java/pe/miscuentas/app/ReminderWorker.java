package pe.miscuentas.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Revisa los pagos guardados por la app y avisa cuando se acerca el día de pago. */
public class ReminderWorker extends Worker {
    static final String PREFS = "pagos";
    private static final String CHANNEL = "pagos";
    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "set", "oct", "nov", "dic"};

    public ReminderWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    static void schedule(Context c) {
        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(ReminderWorker.class, 4, TimeUnit.HOURS).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("pagos", ExistingPeriodicWorkPolicy.KEEP, r);
    }

    static void runNow(Context c) {
        WorkManager.getInstance(c).enqueueUniqueWork("pagos-ahora", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(ReminderWorker.class).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString("items", "[]");
        Set<String> sent = new HashSet<>(prefs.getStringSet("sent", new HashSet<>()));
        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.from(today);
        String ymKey = ym.toString();
        String prevKey = ym.minusMonths(1).toString();
        boolean changed = false;

        // Limpia avisos de meses anteriores
        Set<String> keep = new HashSet<>();
        for (String k : sent) if (k.contains("|" + ymKey + "|") || k.contains("|" + prevKey + "|")) keep.add(k);
        if (keep.size() != sent.size()) { sent = keep; changed = true; }

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject it = arr.getJSONObject(i);
                String id = it.optString("id");
                String name = it.optString("name");
                int day = it.optInt("day", 1);
                int before = it.optInt("before", 3);
                String amount = it.optString("amount", "");
                String paid = it.optString("paid", "");
                if (ymKey.equals(paid)) continue;

                LocalDate due = ym.atDay(Math.min(Math.max(day, 1), ym.lengthOfMonth()));
                long left = ChronoUnit.DAYS.between(today, due);
                String stage, text;
                String fecha = due.getDayOfMonth() + " " + MESES[due.getMonthValue() - 1];
                if (left > 1 && left <= before) { stage = "pre"; text = "Vence en " + left + " días (" + fecha + ")"; }
                else if (left == 1) { stage = "1"; text = "Vence mañana (" + fecha + ")"; }
                else if (left == 0) { stage = "0"; text = "Vence hoy (" + fecha + ")"; }
                else if (left < 0 && left >= -3) { stage = "late"; text = "Venció el " + fecha + ". Si ya pagaste, márcalo como pagado."; }
                else continue;

                String key = id + "|" + ymKey + "|" + stage;
                if (sent.contains(key)) continue;
                if (!amount.isEmpty()) text = text + " · " + amount;
                if (notify(ctx, (id + stage).hashCode(), "Pago: " + name, text)) {
                    sent.add(key);
                    changed = true;
                }
            }
        } catch (Exception e) {
            // datos inválidos: no avisar
        }
        if (changed) prefs.edit().putStringSet("sent", sent).apply();
        return Result.success();
    }

    private boolean notify(Context ctx, int nid, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "Avisos de pago", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Te avisa antes de la fecha de pago de tus tarjetas y pagos mensuales");
                nm.createNotificationChannel(ch);
            }
        }
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(ctx).notify(nid, b.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
