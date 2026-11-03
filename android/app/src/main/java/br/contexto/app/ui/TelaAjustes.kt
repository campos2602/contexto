package br.contexto.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.contexto.app.BuildConfig
import br.contexto.app.Comentarios
import br.contexto.app.Lembrete
import br.contexto.app.Leitor
import br.contexto.app.Voz
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaAjustes(leitor: Leitor, rodape: PaddingValues) {
    val c = LocalCores.current
    val ctx = LocalContext.current
    val catalogo by leitor.catalogo.collectAsState()
    val escolhidasSalvas by leitor.prefs.escolhidas.collectAsState()
    val voz by Voz.estado.collectAsState()
    val tema by leitor.prefs.temaFlow.collectAsState()
    val tamanho by leitor.prefs.tamanhoFlow.collectAsState()
    val ordem = escolhidasSalvas ?: leitor.prefs.efetivas(catalogo)
    var sugestao by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = rodape.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Ajustes", style = Tipos.marca(), color = c.tinta, modifier = Modifier.padding(top = 8.dp).semantics { heading() }) }

        item {
            Cartao {
                Text("Minha edição", style = Tipos.titulo(), color = c.tinta)
                Text("Marque as seções que você quer ver e use as setas para mudar a ordem. As outras ficam em \"Outras seções de hoje\".",
                    style = Tipos.pequeno(), color = c.apagado)
                val marcadas = ordem.mapNotNull { catalogo.porId[it] }
                val resto = catalogo.lista.filter { it.id !in ordem }
                (marcadas + resto).forEach { cat ->
                    val pos = ordem.indexOf(cat.id)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = pos >= 0,
                            onCheckedChange = { sim ->
                                val nova = if (sim) ordem + cat.id else ordem - cat.id
                                if (nova.isNotEmpty()) leitor.prefs.salvar(nova)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = c.acento),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(cat.nome, style = Tipos.subtitulo(), color = c.tinta)
                            if (cat.desc.isNotBlank()) Text(cat.desc, style = Tipos.pequeno(), color = c.apagado)
                        }
                        if (pos >= 0) {
                            TextButton(enabled = pos > 0, onClick = {
                                leitor.prefs.salvar(ordem.toMutableList().apply { add(pos - 1, removeAt(pos)) })
                            }) { Text("▲") }
                            TextButton(enabled = pos < ordem.size - 1, onClick = {
                                leitor.prefs.salvar(ordem.toMutableList().apply { add(pos + 1, removeAt(pos)) })
                            }) { Text("▼") }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Botao("Voltar ao padrão", TipoBotao.Leve) { leitor.prefs.salvar(null) }
                    Botao("Sugerir uma categoria", TipoBotao.Leve) { sugestao = true }
                }
            }
        }

        item { CartaoLembrete() }

        item {
            Cartao {
                Text("Voz", style = Tipos.titulo(), color = c.tinta)
                Text("O botão Ouvir usa o leitor de voz do próprio celular e segue lendo com a tela apagada.",
                    style = Tipos.pequeno(), color = c.apagado)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Botao("Velocidade: " + vel(voz.velocidade), TipoBotao.Leve) { Voz.mudarVelocidade() }
                    Botao("Testar a voz", TipoBotao.Leve, "▶") {
                        Voz.tocar("Teste de voz", listOf("Bom dia. Esta é a voz que vai ler o Contexto para você."))
                    }
                    Botao("Instalar voz em português", TipoBotao.Leve) { Voz.instalarVoz(ctx) }
                }
                if (voz.semVoz) Text("Seu celular não tem voz em português ativa. Toque em \"Instalar voz em português\".",
                    style = Tipos.pequeno(), color = c.alerta)
            }
        }

        item {
            Cartao {
                Text("Leitura", style = Tipos.titulo(), color = c.tinta)
                Text("Tema", style = Tipos.rotulo(), color = c.apagado)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("sistema" to "Igual ao celular", "claro" to "Claro", "escuro" to "Escuro").forEach { (v, nome) ->
                        Botao(nome, if (tema == v) TipoBotao.Cheio else TipoBotao.Leve) { leitor.prefs.tema = v }
                    }
                }
                Text("Tamanho do texto", style = Tipos.rotulo(), color = c.apagado)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.9f to "Menor", 1f to "Normal", 1.15f to "Maior", 1.3f to "Bem maior").forEach { (v, nome) ->
                        Botao(nome, if (tamanho == v) TipoBotao.Cheio else TipoBotao.Leve) { leitor.prefs.tamanhoTexto = v }
                    }
                }
            }
        }

        item {
            Cartao {
                Text("Sobre o Contexto", style = Tipos.titulo(), color = c.tinta)
                Paragrafo("O Contexto é escrito por inteligência artificial, com o Claude, da Anthropic. Toda edição passa por checagem, pela Ouvidoria de neutralidade e pela revisão final do editor-chefe, todos agentes de IA, antes de sair.")
                val abrir = androidx.compose.ui.platform.LocalUriHandler.current
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Botao("Privacidade", TipoBotao.Leve) { abrir.openUri(br.contexto.app.SITE + "privacidade/") }
                    Botao("Atualizar agora", TipoBotao.Leve) { leitor.atualizar(true) }
                }
                Text("Versão " + BuildConfig.VERSION_NAME, style = Tipos.pequeno(), color = c.apagado)
            }
        }
    }

    if (sugestao) DialogoTexto(
        titulo = "Sugerir uma categoria",
        dica = "Que assunto você gostaria de ler no Contexto?",
        aoFechar = { sugestao = false },
        aoEnviar = { t -> Comentarios.enviar("", "categoria", "Nova categoria", "Sugestão de categoria", t) },
    )
}

private fun vel(v: Float) = when (v) { 0.8f -> "devagar"; 1f -> "normal"; 1.2f -> "rápida"; else -> "bem rápida" }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CartaoLembrete() {
    val c = LocalCores.current
    val ctx = LocalContext.current
    var versao by remember { mutableIntStateOf(0) }
    val est = remember(versao) { Lembrete.estado(ctx) }
    var escolherHora by remember { mutableStateOf(false) }
    val dono = LocalLifecycleOwner.current
    // Ao voltar dos ajustes do Android, relê as permissões.
    LaunchedEffect(dono) { dono.repeatOnLifecycle(Lifecycle.State.RESUMED) { versao++ } }

    fun salvar(o: JSONObject) { Lembrete.salvar(ctx, o); versao++ }

    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { sim ->
        if (sim) salvar(JSONObject().put("ativo", true))
        else ctx.startActivity(ajustesNotificacao(ctx.packageName))
        versao++
    }

    Cartao {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lembrete diário", style = Tipos.titulo(), color = c.tinta)
                Text("Um aviso por dia para ouvir o resumo, no horário que você escolher.", style = Tipos.pequeno(), color = c.apagado)
            }
            Switch(
                checked = est.optBoolean("ativo"),
                onCheckedChange = { sim ->
                    if (sim && !Lembrete.podeNotificar(ctx)) {
                        if (Build.VERSION.SDK_INT >= 33) pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else ctx.startActivity(ajustesNotificacao(ctx.packageName))
                    } else salvar(JSONObject().put("ativo", sim))
                },
                colors = SwitchDefaults.colors(checkedTrackColor = c.acento),
            )
        }
        if (est.optBoolean("ativo")) {
            val h = est.optInt("hora", 7); val m = est.optInt("minuto", 30)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Botao("Horário: %02d:%02d".format(h, m), TipoBotao.Leve) { escolherHora = true }
                val abrirEdicao = est.optString("abrir") == "edicao"
                Botao(if (abrirEdicao) "Abre: edição inteira" else "Abre: resumo de 30 segundos", TipoBotao.Leve) {
                    salvar(JSONObject().put("abrir", if (abrirEdicao) "resumo" else "edicao"))
                }
                Botao("Testar o aviso", TipoBotao.Leve) { Lembrete.disparar(ctx, true) }
            }
            Row(Modifier.fillMaxWidth().clickable { salvar(JSONObject().put("soSeNaoOuviu", !est.optBoolean("soSeNaoOuviu", true))) },
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = est.optBoolean("soSeNaoOuviu", true),
                    onCheckedChange = { salvar(JSONObject().put("soSeNaoOuviu", it)) },
                    colors = CheckboxDefaults.colors(checkedColor = c.acento))
                Text("Não avisar se eu já tiver ouvido hoje", style = Tipos.pequeno(), color = c.tinta)
            }
            if (!est.optBoolean("permissao")) Text("As notificações do Contexto estão desligadas no celular. Toque no botão acima para ligar.",
                style = Tipos.pequeno(), color = c.alerta)
            if (!est.optBoolean("exato") && Build.VERSION.SDK_INT >= 31) {
                Text("Para o aviso tocar no minuto exato, permita \"Alarmes e lembretes\". Sem isso, ele pode atrasar alguns minutos.",
                    style = Tipos.pequeno(), color = c.apagado)
                Botao("Permitir alarmes", TipoBotao.Leve) {
                    runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
                }
            }
        }
    }

    if (escolherHora) {
        val estado = rememberTimePickerState(initialHour = est.optInt("hora", 7), initialMinute = est.optInt("minuto", 30), is24Hour = true)
        AlertDialog(
            onDismissRequest = { escolherHora = false },
            confirmButton = {
                TextButton(onClick = { salvar(JSONObject().put("hora", estado.hour).put("minuto", estado.minute)); escolherHora = false }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { escolherHora = false }) { Text("Cancelar") } },
            title = { Text("Horário do lembrete") },
            text = { TimePicker(state = estado) },
        )
    }
}

private fun ajustesNotificacao(pacote: String): Intent =
    (if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pacote)
    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pacote"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Caixa de texto com envio para os comentários do Contexto. */
@Composable
fun DialogoTexto(
    titulo: String, dica: String, aoFechar: () -> Unit, aoEnviar: suspend (String) -> Boolean,
    tipos: List<String> = emptyList(), tipoInicial: String? = null, aoEnviarTipo: (suspend (String, String) -> Boolean)? = null,
) {
    val c = LocalCores.current
    var texto by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf(tipoInicial ?: tipos.firstOrNull() ?: "") }
    var enviando by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf<Boolean?>(null) }
    val escopo = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (resultado) {
                    true -> Paragrafo("Obrigado! A redação lê todos os comentários antes da próxima edição.")
                    false -> Text("Não foi possível enviar agora. Confira a internet e tente de novo.", style = Tipos.pequeno(), color = c.alerta)
                    null -> {}
                }
                if (resultado != true) {
                    if (tipos.isNotEmpty()) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            tipos.forEach { t -> Botao(t, if (t == tipo) TipoBotao.Cheio else TipoBotao.Leve) { tipo = t } }
                        }
                    }
                    OutlinedTextField(value = texto, onValueChange = { texto = it.take(4000) }, placeholder = { Text(dica) },
                        minLines = 4, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            if (resultado == true) TextButton(onClick = aoFechar) { Text("Fechar") }
            else TextButton(enabled = texto.isNotBlank() && !enviando, onClick = {
                enviando = true
                escopo.launch {
                    resultado = if (aoEnviarTipo != null) aoEnviarTipo(tipo, texto) else aoEnviar(texto)
                    enviando = false
                }
            }) { Text(if (enviando) "Enviando..." else "Enviar") }
        },
        dismissButton = { if (resultado != true) TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}
