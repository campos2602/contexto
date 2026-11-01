// Ouvir o Contexto: narra em voz alta cada notícia, resumo e página de Contexto.
// Usado pelo app do editor (index.html) e pelo site público (ler/).
//
// Como marcar a página:
//   <button data-ouvir="#nota-x" data-titulo="...">  narra o elemento #nota-x
//   <button data-ouvir="edicao" data-titulo="...">   narra, em ordem, todos os [data-ler] da página (modo podcast)
//   [data-ler="Rótulo"]                              trecho que entra no modo podcast; data-secao anuncia a seção
//   [data-nao-ler], .acoes, .fonte, .meta, .aviso    nunca são narrados
//
// Voz: no app Android usa o leitor de voz do próprio celular (ponte ContextoVoz, toca com a tela
// apagada); no navegador usa a voz pt-BR do aparelho (Web Speech). Se um trecho tiver data-audio
// com um MP3 gravado, toca o MP3 no lugar da voz do aparelho.
(function () {
  "use strict";
  if (window.__ctxOuvir) return;
  window.__ctxOuvir = true;

  var NAO_LER = ".acoes, .fonte, .meta, .aviso, button, script, style, [data-nao-ler], .sr-only";
  var BLOCOS = "h1, h2, h3, h4, p, li, dt, dd, .etq, .numero > b, .d";
  var MESES = { jan: "janeiro", fev: "fevereiro", mar: "março", abr: "abril", mai: "maio", jun: "junho", jul: "julho", ago: "agosto", set: "setembro", out: "outubro", nov: "novembro", dez: "dezembro" };
  var VELS = [1, 1.25, 1.5, 0.85];

  var nativo = window.ContextoVoz && typeof window.ContextoVoz.falar === "function" ? window.ContextoVoz : null;
  var web = !nativo && "speechSynthesis" in window && "SpeechSynthesisUtterance" in window ? window.speechSynthesis : null;
  var audio = null;

  var P = { itens: [], i: 0, tocando: false, fonte: "", titulo: "", gen: 0, vel: lerVel(), refs: [] };
  var voz = null, avisouSemVoz = false;

  function guarda(k, v) { try { if (v === undefined) return localStorage.getItem("ctx-" + k); localStorage.setItem("ctx-" + k, v); } catch (e) { return null; } }
  function lerVel() { var v = parseFloat(guarda("ouvir-vel")); return VELS.indexOf(v) >= 0 ? v : 1; }
  function toast(t) { var el = document.createElement("div"); el.className = "toast"; el.setAttribute("role", "status"); el.textContent = t; document.body.appendChild(el); setTimeout(function () { el.remove(); }, 4200); }

  /* ---------- Texto ---------- */
  function textoDe(el) {
    var c = el.cloneNode(true);
    c.querySelectorAll(NAO_LER).forEach(function (x) { x.remove(); });
    // Nome em negrito no começo de um item ("Governo: ...") ganha dois-pontos.
    c.querySelectorAll("li > b:first-child, p > b:first-child").forEach(function (b) {
      if (!/[.!?:;…]$/.test(b.textContent.trim())) b.appendChild(document.createTextNode(":"));
    });
    // Títulos, itens e rótulos ganham ponto final para a voz fazer a pausa certa.
    c.querySelectorAll(BLOCOS).forEach(function (b) {
      var t = b.textContent.trim();
      if (t && !/[.!?:;…]["”»)]*$/.test(t)) b.appendChild(document.createTextNode("."));
      b.appendChild(document.createTextNode(" "));
    });
    return limpar(c.textContent);
  }
  function limpar(t) {
    return String(t || "")
      .replace(/\s+/g, " ")
      .replace(/\s*·\s*/g, ", ")
      .replace(/\b(\d{1,2}) (jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)\.?(?= \d{4}|\b)/gi, function (m, d, mes) { return d + " de " + MESES[mes.toLowerCase()]; })
      .replace(/(de \w+) (\d{4})\b/g, "$1 de $2")
      .replace(/\.\s*\./g, ".")
      .trim();
  }
  // Frases de até ~220 caracteres: evita o corte de falas longas no Chrome e permite voltar/avançar.
  function partir(t) {
    // Só corta onde a pontuação vem seguida de espaço: "167.921 pontos" e "R$ 5,4" ficam inteiros.
    var frases = t.match(/[\s\S]+?(?:[.!?…]+["”»)]*(?=\s|$)|$)/g) || [t], out = [];
    frases.forEach(function (f) {
      f = f.trim(); if (!f) return;
      while (f.length > 220) {
        var corte = f.lastIndexOf(", ", 220); if (corte < 80) corte = f.lastIndexOf(" ", 220); if (corte < 40) corte = 220;
        out.push(f.slice(0, corte + 1).trim()); f = f.slice(corte + 1).trim();
      }
      if (f) out.push(f);
    });
    return out;
  }
  function oculto(el) { return !!el.closest("[hidden], details:not([open])") || !el.getClientRects().length; }
  function minutos(t) { var p = (t.match(/\S+/g) || []).length; return Math.max(1, Math.round(p / 150)); }

  // Monta a fila: cada item é uma frase (ou um MP3), com o trecho a destacar e o índice do capítulo.
  function montar(fonte) {
    var trechos = [];
    if (fonte === "edicao") {
      var raiz = document.querySelector("[data-ler-raiz]") || document;
      var ultimaSecao = "";
      raiz.querySelectorAll("[data-ler]").forEach(function (el) {
        if (oculto(el)) return; // seções escondidas (ex.: categorias que o leitor desligou) não são lidas
        var pre = "";
        if (el.dataset.secao && el.dataset.secao !== ultimaSecao) { pre = el.dataset.secao + ". "; ultimaSecao = el.dataset.secao; }
        trechos.push({ el: el, pre: pre });
      });
    } else {
      var el = document.querySelector(fonte);
      if (el) trechos.push({ el: el, pre: "" });
    }
    var itens = [];
    trechos.forEach(function (tr, cap) {
      if (!tr.el.id) tr.el.id = "ler-" + Math.random().toString(36).slice(2, 8);
      var alvo = "#" + tr.el.id;
      if (tr.el.dataset.audio) { itens.push({ src: tr.el.dataset.audio, alvo: alvo, cap: cap }); return; }
      partir(limpar(tr.pre) + (tr.pre ? " " : "") + textoDe(tr.el)).forEach(function (f) { itens.push({ texto: f, alvo: alvo, cap: cap }); });
    });
    return itens;
  }

  /* ---------- Vozes ---------- */
  function escolherVoz() {
    if (!web) return;
    var vs = web.getVoices() || [];
    var pt = vs.filter(function (v) { return /^pt[-_]br/i.test(v.lang); });
    if (!pt.length) pt = vs.filter(function (v) { return /^pt/i.test(v.lang); });
    var boa = /natural|neural|online|premium|enhanced|aprimorad|google|francisca|thalita|luciana|felipe/i;
    voz = pt.filter(function (v) { return boa.test(v.name); })[0] || pt[0] || null;
    if (vs.length && !pt.length && !avisouSemVoz && P.tocando) {
      avisouSemVoz = true;
      toast("Seu aparelho não tem voz em português instalada. Ative em Configurações > Acessibilidade > Texto para fala.");
    }
  }
  if (web) { escolherVoz(); if ("onvoiceschanged" in web) web.addEventListener("voiceschanged", escolherVoz); }

  /* ---------- Reprodução ---------- */
  function parar() {
    P.gen++;
    if (nativo) try { nativo.parar(); } catch (e) {}
    if (web) web.cancel();
    if (audio) { audio.pause(); audio.onended = null; }
  }

  function tocar(desde) {
    parar();
    if (desde >= P.itens.length) { fim(); return; }
    P.i = Math.max(0, desde); P.tocando = true;
    var g = P.gen, it = P.itens[P.i];
    atualizar();
    if (it.src) { tocarMp3(it, g); return; }
    // Junta as frases seguidas (até o próximo MP3) numa fila só: o aparelho segue falando com a tela apagada.
    var lote = [];
    for (var j = P.i; j < P.itens.length && !P.itens[j].src; j++) lote.push(P.itens[j].texto);
    var base = P.i;
    if (nativo) {
      if (P.i === 0 && nativo.pronta && !nativo.pronta()) toast("Se a voz não sair em português, instale a voz pt-BR em Configurações > Acessibilidade > Saída de texto para fala.");
      try { nativo.falar(JSON.stringify(lote), g + ":" + base, P.vel); }
      catch (e) { nativo = null; web = "speechSynthesis" in window ? window.speechSynthesis : null; tocar(base); }
      return;
    }
    if (!web) { P.tocando = false; atualizar(); toast("Este navegador não lê em voz alta. Abra o Contexto no Chrome ou no Safari."); return; }
    escolherVoz();
    P.refs = [];
    setTimeout(function () {
      if (g !== P.gen) return;
      lote.forEach(function (t, k) {
        var u = new SpeechSynthesisUtterance(t);
        u.lang = (voz && voz.lang) || "pt-BR"; if (voz) u.voice = voz; u.rate = P.vel;
        u.onstart = function () { if (g === P.gen) { P.i = base + k; atualizar(); } };
        if (k === lote.length - 1) u.onend = function () { if (g === P.gen) tocar(base + lote.length); };
        u.onerror = function (e) { if (g === P.gen && e.error !== "interrupted" && e.error !== "canceled") { P.tocando = false; atualizar(); toast("A voz do aparelho parou. Toque em ouvir de novo."); } };
        P.refs.push(u); // o Chrome descarta falas sem referência e o fim nunca chega
        web.speak(u);
      });
    }, 60);
  }

  function tocarMp3(it, g) {
    if (!audio) { audio = new Audio(); audio.preload = "auto"; }
    audio.src = it.src; audio.playbackRate = P.vel;
    audio.onended = function () { if (g === P.gen) tocar(P.i + 1); };
    audio.onerror = function () {
      if (g !== P.gen) return;
      // MP3 indisponível: cai para a voz do aparelho no mesmo trecho.
      var el = document.querySelector(it.alvo), novos = el ? partir(textoDe(el)).map(function (f) { return { texto: f, alvo: it.alvo, cap: it.cap }; }) : [];
      P.itens.splice.apply(P.itens, [P.i, 1].concat(novos)); tocar(P.i);
    };
    if ("mediaSession" in navigator) try { navigator.mediaSession.metadata = new MediaMetadata({ title: P.titulo, artist: "Contexto" }); } catch (e) {}
    audio.play().catch(function () { if (g === P.gen) { P.tocando = false; atualizar(); } });
  }

  // Chamado pelo app Android a cada frase que começa ou termina.
  window.__ctxVoz = function (ev, id) {
    var m = /^(\d+):(\d+)$/.exec(String(id)); if (!m || +m[1] !== P.gen) return;
    var base = +m[2];
    if (ev === "inicio") { P.i = base; atualizar(); }
    else if (ev === "fim" && (base + 1 >= P.itens.length || P.itens[base + 1].src)) tocar(base + 1);
    else if (ev === "erro") { P.tocando = false; atualizar(); toast("A voz do celular parou. Toque em ouvir de novo."); }
  };

  function fim() { P.gen++; P.tocando = false; P.i = 0; P.fonte = ""; atualizar(); fecharPlayer(); }

  function comecar(fonte, titulo) {
    var itens = montar(fonte);
    if (!itens.length) { toast("Não há texto para ouvir aqui."); return; }
    P.itens = itens; P.fonte = fonte; P.titulo = titulo || "Contexto"; P.trecho = "";
    abrirPlayer(); tocar(0);
  }
  function pausar() { if (!P.tocando) return; parar(); P.tocando = false; atualizar(); }
  function continuar() { tocar(P.i); }
  function capitulo(d) {
    var cap = P.itens[P.i] ? P.itens[P.i].cap : 0, alvoCap;
    if (d > 0) alvoCap = cap + 1;
    else { var ini = indiceCap(cap); alvoCap = P.i - ini > 1 ? cap : cap - 1; }
    var k = indiceCap(Math.max(0, alvoCap));
    if (k < 0) { // não há capítulo seguinte: avança uma frase, ou termina
      if (d > 0) { if (P.i + 1 < P.itens.length) tocar(P.i + 1); else fim(); } else tocar(0);
      return;
    }
    if (d > 0 && k === indiceCap(cap)) k = Math.min(P.i + 1, P.itens.length - 1); // modo de uma só nota: avança uma frase
    tocar(k);
  }
  function indiceCap(c) { for (var k = 0; k < P.itens.length; k++) if (P.itens[k].cap === c) return k; return -1; }

  /* ---------- Player ---------- */
  var player = null;
  var ICONE = {
    ouvir: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="5" height="7" rx="1.5"/><rect x="16" y="14" width="5" height="7" rx="1.5"/></svg>',
    pausa: '<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>',
    play: '<svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5z"/></svg>',
    ant: '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="4" y="5" width="2.5" height="14" rx="1"/><path d="M20 5.5v13a1 1 0 0 1-1.5.86L8 13a1.2 1.2 0 0 1 0-2l10.5-6.36A1 1 0 0 1 20 5.5z"/></svg>',
    prox: '<svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="17.5" y="5" width="2.5" height="14" rx="1"/><path d="M4 5.5v13a1 1 0 0 0 1.5.86L16 13a1.2 1.2 0 0 0 0-2L5.5 4.64A1 1 0 0 0 4 5.5z"/></svg>',
    x: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>'
  };

  function estilo() {
    var css = ".btn.ouvir .min{font-family:var(--f-mono);font-size:12px;opacity:.8}" +
      ".btn.ouvir[aria-pressed=true]{background:var(--accent);color:var(--on-accent)}" +
      ".lendo{background:var(--accent-soft);box-shadow:0 0 0 8px var(--accent-soft);border-radius:2px}" +
      ".ouvinte{position:fixed;left:0;right:0;z-index:7;bottom:calc(var(--ov-base,0px) + 8px);padding:0 max(8px,env(safe-area-inset-left,0px));pointer-events:none}" +
      ".ouvinte-in{pointer-events:auto;max-width:664px;margin:0 auto;background:var(--bar);color:var(--bar-ink);border-radius:14px;padding:12px 10px 8px;display:grid;gap:6px;box-shadow:0 6px 24px rgba(0,0,0,.28)}" +
      ".ov-info{min-width:0;display:grid;gap:2px;padding:0 6px}" +
      ".ov-rot{font-family:var(--f-mono);font-size:11px;letter-spacing:.1em;text-transform:uppercase;color:#93cec7}" +
      ".ov-tit{font-family:var(--f-display);font-weight:700;font-size:15px;line-height:1.3;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}" +
      ".ov-ctl{display:grid;grid-template-columns:1fr auto 1fr;align-items:center}.ov-meio{display:flex;align-items:center;gap:14px}.ov-ctl .ov-x{justify-self:end}" +
      ".ov-ctl button{background:none;border:0;color:inherit;min-width:40px;min-height:40px;border-radius:999px;display:inline-grid;place-items:center;cursor:pointer;padding:0}" +
      ".ov-ctl .ov-play{background:#93cec7;color:#121821;min-width:46px;min-height:46px}" +
      ".ov-ctl .ov-vel{font-family:var(--f-mono);font-size:13px;font-weight:600;min-width:46px}" +
      ".ov-barra{margin:0 6px;height:3px;background:rgba(255,255,255,.15);border-radius:2px;overflow:hidden}" +
      ".ov-barra i{display:block;height:100%;background:#93cec7;width:0;transition:width .3s}" +
      "body.com-ouvinte::after{content:'';display:block;height:136px}" +
      ".ov-ouvir-ed{display:flex;flex-wrap:wrap;gap:8px;align-items:center}";
    var s = document.createElement("style"); s.textContent = css; document.head.appendChild(s);
  }

  function abrirPlayer() {
    document.body.classList.add("com-ouvinte");
    if (player) return;
    player = document.createElement("div");
    player.className = "ouvinte";
    player.setAttribute("role", "region");
    player.setAttribute("aria-label", "Player de áudio");
    player.innerHTML = '<div class="ouvinte-in"><div class="ov-info"><span class="ov-rot" aria-live="polite"></span><span class="ov-tit"></span></div>' +
      '<div class="ov-barra" aria-hidden="true"><i></i></div>' +
      '<div class="ov-ctl"><button type="button" class="ov-vel" aria-label="Velocidade"></button><div class="ov-meio">' +
      '<button type="button" class="ov-ant" aria-label="Voltar">' + ICONE.ant + '</button><button type="button" class="ov-play"></button>' +
      '<button type="button" class="ov-prox" aria-label="Avançar">' + ICONE.prox + "</button></div>" +
      '<button type="button" class="ov-x" aria-label="Fechar o áudio">' + ICONE.x + "</button></div></div>";
    document.body.appendChild(player);
    player.querySelector(".ov-play").addEventListener("click", function () { P.tocando ? pausar() : continuar(); });
    player.querySelector(".ov-ant").addEventListener("click", function () { capitulo(-1); });
    player.querySelector(".ov-prox").addEventListener("click", function () { capitulo(1); });
    player.querySelector(".ov-x").addEventListener("click", function () { parar(); fim(); });
    player.querySelector(".ov-vel").addEventListener("click", function () {
      P.vel = VELS[(VELS.indexOf(P.vel) + 1) % VELS.length]; guarda("ouvir-vel", String(P.vel));
      if (audio && P.itens[P.i] && P.itens[P.i].src) { audio.playbackRate = P.vel; atualizar(); }
      else if (P.tocando) tocar(P.i); else atualizar();
    });
    posicionar();
  }
  function fecharPlayer() { if (player) { player.remove(); player = null; } document.body.classList.remove("com-ouvinte"); destacar(); }
  function posicionar() {
    var abas = document.querySelector(".abas");
    document.documentElement.style.setProperty("--ov-base", abas ? abas.offsetHeight + "px" : "env(safe-area-inset-bottom, 0px)");
  }
  window.addEventListener("resize", posicionar);

  function atualizar() {
    if (player) {
      var cap = P.itens[P.i] ? P.itens[P.i].cap : 0, total = P.itens.length ? P.itens[P.itens.length - 1].cap + 1 : 1;
      // Na edição inteira, mostra o trecho atual (o título da nota); senão, o título do que se está ouvindo.
      var el = total > 1 && P.itens[P.i] && document.querySelector(P.itens[P.i].alvo);
      var trecho = el && el.dataset.ler ? el.dataset.ler : "";
      if (trecho) P.trecho = trecho;
      player.querySelector(".ov-rot").textContent = (P.tocando ? "Ouvindo" : "Pausado") + (total > 1 ? " · " + (cap + 1) + " de " + total : "");
      player.querySelector(".ov-tit").textContent = (total > 1 && P.trecho) || P.titulo;
      var b = player.querySelector(".ov-play");
      b.innerHTML = P.tocando ? ICONE.pausa : ICONE.play;
      b.setAttribute("aria-label", P.tocando ? "Pausar" : "Continuar");
      var v = player.querySelector(".ov-vel");
      v.textContent = String(P.vel).replace(".", ",") + "x";
      v.setAttribute("aria-label", "Velocidade " + String(P.vel).replace(".", ",") + " vezes. Toque para mudar.");
      player.querySelector(".ov-barra i").style.width = (P.itens.length ? Math.round(100 * (P.i + (P.tocando ? 0.5 : 0)) / P.itens.length) : 0) + "%";
    }
    botoes();
    destacar();
  }
  function destacar() {
    document.querySelectorAll(".lendo").forEach(function (x) { x.classList.remove("lendo"); });
    var it = P.fonte && P.itens[P.i];
    if (it && P.fonte === "edicao") { var el = document.querySelector(it.alvo); if (el) el.classList.add("lendo"); }
  }

  // Prepara os botões "Ouvir": tempo estimado e estado (tocando ou não).
  function botoes() {
    document.querySelectorAll("[data-ouvir]").forEach(function (b) {
      if (!b.dataset.pronto) {
        b.dataset.pronto = "1";
        var txt = "";
        if (b.dataset.ouvir === "edicao") document.querySelectorAll("[data-ler]").forEach(function (el) { if (!oculto(el)) txt += " " + textoDe(el); });
        else { var el = document.querySelector(b.dataset.ouvir); if (el) txt = textoDe(el); }
        if (!txt.trim()) { b.hidden = true; return; }
        b.dataset.rotulo = b.dataset.rotulo || b.textContent.trim() || "Ouvir";
        b.dataset.min = minutos(txt);
      }
      var ativo = P.fonte === b.dataset.ouvir;
      b.setAttribute("aria-pressed", String(ativo && P.tocando));
      var h = ICONE.ouvir + (ativo && P.tocando ? "Pausar" : ativo ? "Continuar" : esc(b.dataset.rotulo)) + ' <span class="min">' + b.dataset.min + " min</span>";
      if (b._h !== h) { b._h = h; b.innerHTML = h; } // só mexe no botão quando muda (evita laço com o observador)
      b.setAttribute("aria-label", (ativo && P.tocando ? "Pausar o áudio de: " : "Ouvir em voz alta: ") + (b.dataset.titulo || "") + ", cerca de " + b.dataset.min + (b.dataset.min === "1" ? " minuto" : " minutos"));
    });
  }
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }

  document.addEventListener("click", function (e) {
    var b = e.target.closest && e.target.closest("[data-ouvir]");
    if (!b) return;
    e.preventDefault();
    if (P.fonte === b.dataset.ouvir) { P.tocando ? pausar() : continuar(); return; }
    comecar(b.dataset.ouvir, b.dataset.titulo);
  });

  // O app do editor redesenha a tela quando chegam dados novos: prepara os botões novos e refaz o destaque.
  var agendado = false;
  new MutationObserver(function () {
    if (agendado) return; agendado = true;
    requestAnimationFrame(function () { agendado = false; botoes(); destacar(); posicionar(); });
  }).observe(document.body, { childList: true, subtree: true });

  // Fechar o app ou a aba para a voz do navegador (o Chrome continua falando se não pedirmos).
  window.addEventListener("pagehide", function () { if (!nativo) parar(); });

  estilo();
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", botoes); else botoes();
  window.ContextoOuvir = { comecar: comecar, pausar: pausar, continuar: continuar, parar: function () { parar(); fim(); }, estado: P };
})();
