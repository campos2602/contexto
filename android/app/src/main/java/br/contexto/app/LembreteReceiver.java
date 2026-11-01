package br.contexto.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Recebe o alarme do lembrete, o botão "Lembrar em 30 min" e os avisos do sistema que apagam alarmes. */
public class LembreteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String acao = intent.getAction();
        if (Lembrete.ACAO_DISPARAR.equals(acao)) {
            Lembrete.disparar(c, false);
            Lembrete.agendar(c); // alarmes do Android tocam uma vez só: já deixa marcado o de amanhã
        } else if (Lembrete.ACAO_ADIAR.equals(acao)) {
            Lembrete.adiar(c);
        } else {
            // Celular ligado de novo, app atualizado, relógio ou fuso mudou, permissão de alarme exato alterada.
            Lembrete.agendar(c);
        }
    }
}
