package br.contexto.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Lembrete diário para ouvir o resumo do dia.
 * Guarda o horário escolhido no app, agenda o alarme do Android e mostra a notificação.
 * Tocar na notificação abre o app já tocando o áudio (ver MainActivity e lembrete.js).
 */
public final class Lembrete {
    public static final String CANAL = "lembrete-diario";
    public static final int ID_NOTIFICACAO = 1;
    public static final String ACAO_DISPARAR = "br.contexto.app.LEMBRETE";
    public static final String ACAO_ADIAR = "br.contexto.app.LEMBRETE_ADIAR";
    public static final String ACAO_OUVIR = "br.contexto.app.OUVIR";
    public static final String EXTRA_ABRIR = "abrir";
    public static final int MINUTOS_ADIAR = 30;

    private static final String PREFS = "lembrete";
    private static final int REQ_DIARIO = 10, REQ_ADIADO = 11, REQ_ABRIR = 12, REQ_BOTAO_ADIAR = 13;

    private Lembrete() {}

    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static String hoje() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()); }

    /** O que a página mostra na folha "Lembrete diário". */
    public static JSONObject estado(Context c) {
        SharedPreferences p = prefs(c);
        JSONObject o = new JSONObject();
        try {
            o.put("ativo", p.getBoolean("ativo", false));
            o.put("hora", p.getInt("hora", 7));
            o.put("minuto", p.getInt("minuto", 30));
            o.put("abrir", p.getString("abrir", "resumo"));
            o.put("soSeNaoOuviu", p.getBoolean("soSeNaoOuviu", true));
            o.put("ouviuHoje", hoje().equals(p.getString("ouviuEm", "")));
            o.put("permissao", podeNotificar(c));
            o.put("exato", alarmeExatoPermitido(c));
            o.put("proximo", p.getBoolean("ativo", false) ? proximo(p.getInt("hora", 7), p.getInt("minuto", 30)) : 0);
        } catch (Exception ignorado) { /* JSONObject.put só falha com número inválido */ }
        return o;
    }

    /** Recebe {"ativo":true,"hora":7,"minuto":30,"abrir":"resumo","soSeNaoOuviu":true} e reagenda. */
    public static void salvar(Context c, JSONObject o) {
        SharedPreferences p = prefs(c);
        String abrir = o.optString("abrir", p.getString("abrir", "resumo"));
        p.edit()
            .putBoolean("ativo", o.optBoolean("ativo", p.getBoolean("ativo", false)))
            .putInt("hora", Math.max(0, Math.min(23, o.optInt("hora", p.getInt("hora", 7)))))
            .putInt("minuto", Math.max(0, Math.min(59, o.optInt("minuto", p.getInt("minuto", 30)))))
            .putString("abrir", "edicao".equals(abrir) ? "edicao" : "resumo")
            .putBoolean("soSeNaoOuviu", o.optBoolean("soSeNaoOuviu", p.getBoolean("soSeNaoOuviu", true)))
            .apply();
        cancelarAdiado(c);
        agendar(c);
    }

    public static void marcarOuvido(Context c) {
        prefs(c).edit().putString("ouviuEm", hoje()).apply();
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ID_NOTIFICACAO);
        cancelarAdiado(c);
    }

    public static boolean podeNotificar(Context c) {
        if (Build.VERSION.SDK_INT >= 33
            && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm == null || nm.areNotificationsEnabled();
    }

    public static boolean alarmeExatoPermitido(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    /** Próximo horário do lembrete, em milissegundos. */
    public static long proximo(int hora, int minuto) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hora);
        cal.set(Calendar.MINUTE, minuto);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
        return cal.getTimeInMillis();
    }

    /** Agenda (ou desmarca) o próximo lembrete diário. Chamado ao salvar, ao disparar, ao ligar o celular e ao atualizar o app. */
    public static void agendar(Context c) {
        SharedPreferences p = prefs(c);
        PendingIntent pi = alarme(c, ACAO_DISPARAR, REQ_DIARIO);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        am.cancel(pi);
        if (!p.getBoolean("ativo", false)) return;
        marcar(c, am, proximo(p.getInt("hora", 7), p.getInt("minuto", 30)), pi);
    }

    public static void adiar(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ID_NOTIFICACAO);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) marcar(c, am, System.currentTimeMillis() + MINUTOS_ADIAR * 60_000L, alarme(c, ACAO_DISPARAR, REQ_ADIADO));
    }

    private static void cancelarAdiado(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(alarme(c, ACAO_DISPARAR, REQ_ADIADO));
    }

    private static void marcar(Context c, AlarmManager am, long quando, PendingIntent pi) {
        // Com a permissão "Alarmes e lembretes", toca no minuto exato mesmo com o celular parado.
        // Sem ela, o Android pode atrasar alguns minutos para poupar bateria.
        try {
            if (alarmeExatoPermitido(c)) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi); return; }
        } catch (SecurityException semPermissao) { /* cai no agendamento sem hora exata */ }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, pi);
    }

    private static PendingIntent alarme(Context c, String acao, int req) {
        Intent i = new Intent(c, LembreteReceiver.class).setAction(acao);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Hora do lembrete: mostra a notificação, a não ser que a pessoa já tenha ouvido hoje. */
    public static void disparar(Context c, boolean teste) {
        SharedPreferences p = prefs(c);
        if (!teste && p.getBoolean("soSeNaoOuviu", true) && hoje().equals(p.getString("ouviuEm", ""))) return;
        notificar(c, p.getString("abrir", "resumo"));
    }

    public static void criarCanal(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CANAL) != null) return;
        NotificationChannel ch = new NotificationChannel(CANAL, "Lembrete diário", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Avisa no horário escolhido para ouvir o resumo do dia.");
        nm.createNotificationChannel(ch);
    }

    private static void notificar(Context c, String abrir) {
        if (!podeNotificar(c)) return;
        criarCanal(c);
        Intent abre = new Intent(c, MainActivity.class)
            .setAction(ACAO_OUVIR)
            .putExtra(EXTRA_ABRIR, abrir)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piAbre = PendingIntent.getActivity(c, REQ_ABRIR, abre, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piAdiar = PendingIntent.getBroadcast(c, REQ_BOTAO_ADIAR,
            new Intent(c, LembreteReceiver.class).setAction(ACAO_ADIAR), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        boolean edicao = "edicao".equals(abrir);
        String texto = edicao
            ? "A edição de hoje do Contexto está pronta. Toque para ouvir do começo ao fim."
            : "Em 30 segundos, o que importa hoje. Toque para ouvir.";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CANAL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notificacao)
            .setColor(0xFF0B7A75)
            .setContentTitle("Hora de ouvir o resumo do dia")
            .setContentText(texto)
            .setStyle(new Notification.BigTextStyle().bigText(texto))
            .setContentIntent(piAbre)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .addAction(new Notification.Action.Builder(null, "Ouvir agora", piAbre).build())
            .addAction(new Notification.Action.Builder(null, "Lembrar em " + MINUTOS_ADIAR + " min", piAdiar).build());
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(ID_NOTIFICACAO, b.build());
    }
}
