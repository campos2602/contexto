package br.contexto.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;

import java.util.Locale;

/** Abre o app do Contexto (GitHub Pages) em tela cheia. Links de fora abrem no navegador. */
public class MainActivity extends Activity {
    private static final String ENDERECO = "https://campos2602.github.io/contexto/";
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsPronto;

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
