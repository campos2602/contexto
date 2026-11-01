package br.contexto.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Abre o app do Contexto (GitHub Pages) em tela cheia. Links de fora abrem no navegador. */
public class MainActivity extends Activity {
    private static final String ENDERECO = "https://campos2602.github.io/contexto/";
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsPronto;
    private static final int PEDIDO_NOTIFICACAO = 1;
    /** Áudio a abrir porque a pessoa tocou na notificação do lembrete: "resumo", "edicao" ou null. */
    private volatile String pendente;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u.toString().startsWith(ENDERECO)) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u));
                return true;
            }
        });
        prepararVoz();
        // O WebView do Android não fala sozinho (a voz do navegador fica muda dentro de apps).
        // Esta ponte entrega o texto ao leitor de voz do próprio celular, que segue falando com a tela apagada.
        web.addJavascriptInterface(new Voz(), "ContextoVoz");
        // Lembrete diário: a folha "Lembrete diário" (lembrete.js) guarda o horário por esta ponte.
        web.addJavascriptInterface(new LembreteJs(), "ContextoLembrete");
        Lembrete.criarCanal(this);
        Lembrete.agendar(this);
        lerIntent(getIntent());
        setContentView(web);
        if (estado != null) web.restoreState(estado);
        else web.loadUrl(ENDERECO);
    }

    private void prepararVoz() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(new Locale("pt", "BR"));
            if (r < 0) r = tts.setLanguage(new Locale("pt"));
            ttsPronto = r >= 0;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { avisar("inicio", id); }
                @Override public void onDone(String id) { avisar("fim", id); }
                @Override public void onError(String id) { avisar("erro", id); }
                @Override public void onStop(String id, boolean interrompido) { /* parada pedida pelo app */ }
            });
        });
    }

    private void avisar(String evento, String id) {
        if (web == null || id == null || !id.matches("\\d+:\\d+")) return;
        final String js = "window.__ctxVoz&&window.__ctxVoz('" + evento + "','" + id + "')";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    /** Chamado pelo ler/ouvir.js. */
    private class Voz {
        @JavascriptInterface
        public boolean pronta() { return ttsPronto; }

        /** frases: lista JSON de textos; id: "geracao:indiceInicial"; velocidade: 1 = normal. */
        @JavascriptInterface
        public void falar(String frases, String id, float velocidade) {
            if (tts == null) return;
            try {
                JSONArray a = new JSONArray(frases);
                String[] p = id.split(":");
                int base = Integer.parseInt(p[1]);
                tts.stop();
                tts.setSpeechRate(Math.max(0.5f, Math.min(2f, velocidade)));
                for (int i = 0; i < a.length(); i++) {
                    tts.speak(a.getString(i), i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, p[0] + ":" + (base + i));
                }
            } catch (Exception e) {
                avisar("erro", id);
            }
        }

        @JavascriptInterface
        public void parar() { if (tts != null) tts.stop(); }
    }

    /** Chamado pelo lembrete.js. */
    private class LembreteJs {
        @JavascriptInterface
        public String estado() { return Lembrete.estado(MainActivity.this).toString(); }

        @JavascriptInterface
        public String salvar(String json) {
            try { Lembrete.salvar(MainActivity.this, new JSONObject(json)); } catch (Exception ignorado) { /* JSON inválido: mantém o que havia */ }
            return estado();
        }

        /** Android 13+: pede a permissão de notificação. A resposta volta em window.__ctxLembrete("permissao", sim). */
        @JavascriptInterface
        public void pedirPermissao() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && !getSharedPreferences("lembrete", MODE_PRIVATE).getBoolean("negouSempre", false)) {
                    requestPermissions(new String[] { android.Manifest.permission.POST_NOTIFICATIONS }, PEDIDO_NOTIFICACAO);
                } else if (!Lembrete.podeNotificar(MainActivity.this)) {
                    abrirAjustesNotificacao();
                } else {
                    avisarLembrete("permissao", true);
                }
            });
        }

        @JavascriptInterface
        public void abrirAjustesNotificacao() { runOnUiThread(MainActivity.this::abrirAjustesNotificacao); }

        /** Android 12+: tela "Alarmes e lembretes", para o aviso tocar no minuto exato. */
        @JavascriptInterface
        public void abrirAjustesAlarme() {
            if (Build.VERSION.SDK_INT < 31) return;
            runOnUiThread(() -> abrir(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))));
        }

        @JavascriptInterface
        public void testar() { Lembrete.disparar(MainActivity.this, true); }

        @JavascriptInterface
        public void ouviu() { Lembrete.marcarOuvido(MainActivity.this); }

        /** Devolve (uma vez só) o áudio pedido pela notificação. */
        @JavascriptInterface
        public String pendente() { String p = pendente; pendente = null; return p == null ? "" : p; }
    }

    private void abrirAjustesNotificacao() {
        Intent i = Build.VERSION.SDK_INT >= 26
            ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
            : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        abrir(i);
    }

    private void abrir(Intent i) {
        try { startActivity(i); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
    }

    private void lerIntent(Intent i) {
        if (i != null && Lembrete.ACAO_OUVIR.equals(i.getAction())) {
            String a = i.getStringExtra(Lembrete.EXTRA_ABRIR);
            pendente = "edicao".equals(a) ? "edicao" : "resumo";
            i.setAction(null); // girar a tela não pode tocar de novo
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        lerIntent(intent);
        // App já aberto: avisa a página, que busca o pedido em ContextoLembrete.pendente().
        if (pendente != null && web != null) web.evaluateJavascript("window.__ctxLembrete&&window.__ctxLembrete('abrir')", null);
    }

    @Override
    public void onRequestPermissionsResult(int pedido, String[] permissoes, int[] resultados) {
        super.onRequestPermissionsResult(pedido, permissoes, resultados);
        if (pedido != PEDIDO_NOTIFICACAO) return;
        boolean sim = resultados.length > 0 && resultados[0] == PackageManager.PERMISSION_GRANTED;
        // Negou duas vezes: o Android não mostra mais a pergunta, então da próxima vez abre os ajustes.
        if (!sim && Build.VERSION.SDK_INT >= 33 && !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))
            getSharedPreferences("lembrete", MODE_PRIVATE).edit().putBoolean("negouSempre", true).apply();
        avisarLembrete("permissao", sim);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Volta dos ajustes do Android: a página atualiza os avisos de permissão.
        if (web != null) web.evaluateJavascript("window.__ctxLembrete&&window.__ctxLembrete('voltou')", null);
    }

    private void avisarLembrete(String evento, boolean valor) {
        if (web == null) return;
        final String js = "window.__ctxLembrete&&window.__ctxLembrete('" + evento + "'," + valor + ")";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
