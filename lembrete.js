// Lembrete diário: o app Android avisa no horário escolhido para ouvir o resumo do dia.
// Tocar no aviso abre o app já tocando o áudio (resumo "Em 30 segundos" ou a edição inteira).
//
// A folha "Lembrete diário" abre por qualquer botão [data-lembrete]. O agendamento é feito pelo
// Android, pela ponte ContextoLembrete (MainActivity.java), que funciona com o app fechado.
// No navegador a folha explica que o lembrete é do app Android e leva ao download.
(function () {
  "use strict";
  if (window.CtxLembrete) return;

  var ponte = window.ContextoLembrete && typeof window.ContextoLembrete.estado === "function" ? window.ContextoLembrete : null;
  var BAIXAR = "https://github.com/campos2602/contexto/releases/latest";
  var SINO = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M6 9a6 6 0 0 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15 6 9z"/><path d="M10 20a2 2 0 0 0 4 0"/></svg>';
  var carregou = !!window.__ctxCarregou;

  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function dois(n) { return (n < 10 ? "0" : "") + n; }
  function hhmm(e) { return dois(e.hora) + ":" + dois(e.minuto); }
  function estado() { try { return JSON.parse(ponte.estado()); } catch (e) { return null; } }
  function toast(t) { var el = document.createElement("div"); el.className = "toast"; el.setAttribute("role", "status"); el.textContent = t; document.body.appendChild(el); setTimeout(function () { el.remove(); }, 3200); }
  // Camada própria (como a folha "Personalizar"): não segura o redesenho da edição enquanto está aberta.
  function camada() {
    var c = document.getElementById("lb-camada");
    if (!c) { c = document.createElement("div"); c.id = "lb-camada"; document.body.appendChild(c); }
    return c;
  }

  function estilo() {
    var css = ".sino{display:inline-flex;align-items:center;gap:6px;background:none;border:1px solid rgba(232,237,242,.25);color:var(--bar-ink);border-radius:999px;min-height:36px;padding:4px 12px 4px 10px;font-family:var(--f-mono);font-size:13px;font-weight:600;cursor:pointer}" +
      ".sino[data-ativo=true]{border-color:#93cec7;color:#93cec7}" +
      ".topo-dir{display:flex;align-items:center;gap:10px;min-width:0}" +
      ".lb-linha{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 0;border-top:1px solid var(--line)}" +
      ".lb-linha label,.lb-linha .lb-rot{font-family:var(--f-display);font-weight:600;font-size:16px}" +
      ".lb-linha small{display:block;font-family:var(--f-display);font-weight:400;font-size:13px;color:var(--muted)}" +
      ".lb-hora{font-family:var(--f-mono);font-size:24px;font-weight:600;color:var(--ink);background:var(--bg);border:1px solid var(--line);border-radius:10px;padding:6px 10px;min-height:48px}" +
      ".lb-chave{appearance:none;-webkit-appearance:none;flex:none;width:52px;height:30px;border-radius:999px;background:var(--line);position:relative;cursor:pointer;margin:0;transition:background .15s}" +
      ".lb-chave::after{content:'';position:absolute;top:3px;left:3px;width:24px;height:24px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.3);transition:transform .15s}" +
      ".lb-chave:checked{background:var(--accent)}.lb-chave:checked::after{transform:translateX(22px)}" +
      ".lb-chave:focus-visible{outline:2px solid var(--accent);outline-offset:3px}" +
      ".lb-campos[aria-disabled=true]{opacity:.45;pointer-events:none}" +
      ".lb-alerta{background:var(--warn-soft,#fbecd6);color:var(--ink);border-radius:10px;padding:10px 12px;font-family:var(--f-display);font-size:14px;line-height:1.45;display:grid;gap:8px;justify-items:start}" +
      ".lb-alerta .btn{min-height:36px;padding:6px 12px;font-size:14px}" +
      ".lb-desc{font-family:var(--f-display);font-size:15px;color:var(--muted);margin:0}";
    var s = document.createElement("style"); s.textContent = css; document.head.appendChild(s);
  }

  /* ---------- Botão do topo ---------- */
  function rotulo() {
    var e = ponte && estado();
    return e && e.ativo ? { txt: hhmm(e), ativo: true, aria: "Lembrete diário às " + hhmm(e) + ". Toque para mudar." } : { txt: "", ativo: false, aria: "Criar lembrete diário para ouvir o resumo" };
  }
  function atualizarBotoes() {
    var r = rotulo();
    document.querySelectorAll("[data-lembrete]").forEach(function (b) {
      var h = SINO + (r.txt ? "<span>" + r.txt + "</span>" : "");
      if (b._h !== h) { b._h = h; b.innerHTML = h; }
      b.setAttribute("data-ativo", String(r.ativo));
      b.setAttribute("aria-label", r.aria);
    });
  }

  /* ---------- Folha ---------- */
  function abrirFolha() {
    var c = camada();
    if (!ponte) {
      c.innerHTML = '<div class="veu" id="lb-veu"><div class="sheet" role="dialog" aria-modal="true" aria-labelledby="lb-tit"><h2 id="lb-tit">Lembrete diário</h2>' +
        '<p class="lb-desc">O lembrete para ouvir o resumo do dia funciona no app do Contexto para Android: ele avisa no horário que você escolher, mesmo com o app fechado, e tocar no aviso já começa o áudio.</p>' +
        '<p class="lb-desc">Se você já tem o app instalado, baixe a versão nova para ganhar o lembrete.</p>' +
        '<div class="linha"><button class="btn leve" type="button" id="lb-fechar">Fechar</button><a class="btn cheio" href="' + BAIXAR + '" target="_blank" rel="noopener">Baixar o app Android</a></div></div></div>';
      ligarFechar(c);
      return;
    }
    var e = estado() || { ativo: false, hora: 7, minuto: 30, abrir: "resumo", soSeNaoOuviu: true, permissao: true, exato: true };
    var novo = !e.ativo;
    if (novo) e.ativo = true; // abrir a folha sem lembrete é para criar um
    c.innerHTML = '<div class="veu" id="lb-veu"><form class="sheet" id="lb-form" role="dialog" aria-modal="true" aria-labelledby="lb-tit"><h2 id="lb-tit">Lembrete diário</h2>' +
      '<p class="lb-desc">O app avisa no horário escolhido para você ouvir o resumo do dia. Tocar no aviso já começa o áudio.</p>' +
      '<div><div class="lb-linha"><label for="lb-ativo">Me lembrar todo dia</label><input type="checkbox" class="lb-chave" id="lb-ativo"' + (e.ativo ? " checked" : "") + "></div>" +
      '<div class="lb-campos" id="lb-campos">' +
      '<div class="lb-linha"><label for="lb-hora">Horário</label><input type="time" class="lb-hora" id="lb-hora" required value="' + hhmm(e) + '"></div>' +
      '<div class="lb-linha" style="display:grid;justify-content:stretch"><span class="lb-rot" id="lb-abrir-rot">Ao tocar no aviso, ouvir</span><div class="chips" role="group" aria-labelledby="lb-abrir-rot">' +
      '<button type="button" class="chip" data-abrir="resumo" aria-pressed="' + (e.abrir !== "edicao") + '">Resumo (Em 30 segundos)</button>' +
      '<button type="button" class="chip" data-abrir="edicao" aria-pressed="' + (e.abrir === "edicao") + '">Edição inteira</button></div></div>' +
      '<div class="lb-linha"><label for="lb-so">Só se eu ainda não tiver ouvido<small>Se você já ouviu o resumo ou a edição no dia, o app não cobra.</small></label><input type="checkbox" class="lb-chave" id="lb-so"' + (e.soSeNaoOuviu ? " checked" : "") + "></div>" +
      "</div></div>" +
      '<div id="lb-alertas" aria-live="polite" style="display:grid;gap:8px"></div>' +
      '<div class="linha"><button class="btn leve" type="button" id="lb-testar">Testar o aviso</button><button class="btn leve" type="button" id="lb-fechar">Cancelar</button><button class="btn cheio" type="submit">Salvar</button></div></form></div>';
    var form = document.getElementById("lb-form"), ativo = document.getElementById("lb-ativo"), campos = document.getElementById("lb-campos");
    var abrir = e.abrir === "edicao" ? "edicao" : "resumo";
    function campo() { campos.setAttribute("aria-disabled", String(!ativo.checked)); }
    campo();
    ativo.addEventListener("change", campo);
    form.querySelectorAll("[data-abrir]").forEach(function (b) {
      b.addEventListener("click", function () { abrir = b.dataset.abrir; form.querySelectorAll("[data-abrir]").forEach(function (x) { x.setAttribute("aria-pressed", String(x === b)); }); });
    });
    alertas();
    ligarFechar(c);
    document.getElementById("lb-testar").addEventListener("click", function () {
      var st = estado();
      if (st && !st.permissao) { ponte.pedirPermissao(); return; }
      ponte.testar(); toast("Aviso enviado. Olhe a barra de notificações.");
    });
    form.addEventListener("submit", function (ev) {
      ev.preventDefault();
      var m = /^(\d{1,2}):(\d{2})/.exec(document.getElementById("lb-hora").value || "");
      if (ativo.checked && !m) { toast("Escolha um horário."); return; }
      var dados = { ativo: ativo.checked, abrir: abrir, soSeNaoOuviu: document.getElementById("lb-so").checked };
      if (m) { dados.hora = +m[1]; dados.minuto = +m[2]; }
      var st; try { st = JSON.parse(ponte.salvar(JSON.stringify(dados))); } catch (x) { st = estado(); }
      fechar();
      if (!st) return;
      if (st.ativo && !st.permissao) { toast("Falta permitir as notificações do Contexto."); ponte.pedirPermissao(); return; }
      toast(st.ativo ? "Pronto: lembrete todo dia às " + hhmm(st) + "." : "Lembrete desligado.");
    });
  }
  function alertas() {
    var box = document.getElementById("lb-alertas"); if (!box) return;
    var e = estado(); if (!e) return;
    var h = [];
    if (!e.permissao) h.push('<div class="lb-alerta"><span>O Android está bloqueando as notificações do Contexto. Sem isso o lembrete não aparece.</span><button class="btn cheio" type="button" data-lb="permissao">Permitir notificações</button></div>');
    if (e.permissao && !e.exato) h.push('<div class="lb-alerta"><span>O aviso pode chegar alguns minutos depois do horário, para o Android poupar bateria. Para tocar no minuto certo, ligue "Permitir alarmes e lembretes".</span><button class="btn" type="button" data-lb="exato">Abrir ajuste</button></div>');
    box.innerHTML = h.join("");
    box.querySelectorAll("[data-lb]").forEach(function (b) {
      b.addEventListener("click", function () { if (b.dataset.lb === "permissao") ponte.pedirPermissao(); else ponte.abrirAjustesAlarme(); });
    });
  }
  function ligarFechar(c) {
    var veu = document.getElementById("lb-veu");
    veu.addEventListener("click", function (ev) { if (ev.target === veu) fechar(); });
    document.getElementById("lb-fechar").addEventListener("click", fechar);
  }
  function fechar() {
    camada().innerHTML = "";
    atualizarBotoes();
  }

  /* ---------- Abrir o áudio pela notificação ---------- */
  function esperarCarga(ms) {
    return new Promise(function (ok) {
      var t = setTimeout(ok, ms);
      document.addEventListener("ctx:carregou", function () { clearTimeout(t); ok(); }, { once: true });
    });
  }
  function esperarElemento(sel, ms) {
    return new Promise(function (ok) {
      var fim = Date.now() + ms;
      (function olha() { var el = document.querySelector(sel); if (el || Date.now() > fim) ok(el); else setTimeout(olha, 150); })();
    });
  }
  var abrindo = false;
  // aberto: o app já estava aberto (notificação tocada com o app em segundo plano).
  function abrirPendente(aberto) {
    if (!ponte || abrindo) return;
    var qual = ""; try { qual = ponte.pendente(); } catch (e) {}
    if (qual !== "resumo" && qual !== "edicao") return;
    abrindo = true;
    camada().innerHTML = "";
    var c = document.getElementById("camada"); if (c) c.innerHTML = "";
    if (window.ContextoOuvir) try { window.ContextoOuvir.parar(); } catch (e) {}
    var r = (location.hash || "").replace(/^#/, "");
    if (r && r !== "hoje") location.hash = "hoje"; else window.scrollTo(0, 0);
    // Espera a edição de hoje chegar da internet (o app pode estar mostrando a de ontem, guardada).
    // Com o app já aberto, ele recarrega as edições ao voltar para a tela: espera essa recarga um pouco.
    (carregou && !aberto ? Promise.resolve() : esperarCarga(carregou ? 4000 : 15000)).then(function () {
      if ((location.hash || "#hoje") !== "#hoje") location.hash = "hoje";
      var sel = qual === "edicao" ? '[data-ouvir="edicao"]' : "#ler-trinta";
      return esperarElemento(sel, 3000);
    }).then(function (el) {
      abrindo = false;
      if (!window.ContextoOuvir) { toast("Atualize o app para ouvir o resumo."); return; }
      if (!el && qual === "resumo") qual = "edicao"; // edição sem "Em 30 segundos": toca a edição inteira
      var b = document.querySelector(qual === "edicao" ? '[data-ouvir="edicao"]' : '[data-ouvir="#ler-trinta"]');
      if (!b) { toast("A edição de hoje ainda não chegou. Tente de novo em instantes."); return; }
      if (qual === "resumo") { var sec = document.getElementById("ler-trinta"); if (sec) sec.scrollIntoView({ block: "start" }); }
      window.ContextoOuvir.comecar(b.dataset.ouvir, b.dataset.titulo);
      marcarOuvido();
    });
  }

  function marcarOuvido() { if (ponte) try { ponte.ouviu(); } catch (e) {} }

  // Ouvir o resumo ou a edição pelo botão também conta como "ouvi hoje".
  document.addEventListener("click", function (e) {
    var t = e.target.closest && e.target.closest("[data-lembrete], [data-ouvir]");
    if (!t) return;
    if (t.hasAttribute("data-lembrete")) { e.preventDefault(); abrirFolha(); return; }
    var o = t.dataset.ouvir;
    if (o === "edicao" || o === "#ler-trinta") setTimeout(function () { if (window.ContextoOuvir && window.ContextoOuvir.estado.tocando) marcarOuvido(); }, 300);
  });

  // Chamado pelo Android: notificação tocada com o app aberto, resposta da permissão, volta dos ajustes.
  window.__ctxLembrete = function (ev, valor) {
    if (ev === "abrir") abrirPendente(true);
    else if (ev === "permissao") { alertas(); atualizarBotoes(); if (valor) { var e = estado(); toast(e && e.ativo ? "Pronto: lembrete todo dia às " + hhmm(e) + "." : "Notificações liberadas."); } }
    else if (ev === "voltou") { alertas(); atualizarBotoes(); }
  };
  document.addEventListener("ctx:carregou", function () { carregou = true; });

  document.addEventListener("keydown", function (e) { if (e.key === "Escape" && document.getElementById("lb-veu")) fechar(); });

  var agendado = false;
  new MutationObserver(function () {
    if (agendado) return; agendado = true;
    requestAnimationFrame(function () { agendado = false; atualizarBotoes(); });
  }).observe(document.body, { childList: true, subtree: true });

  estilo();
  atualizarBotoes();
  window.CtxLembrete = { abrir: abrirFolha, temApp: !!ponte };
  // App aberto pela notificação (estava fechado).
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", function () { abrirPendente(false); }); else abrirPendente(false);
})();
