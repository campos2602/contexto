// Minha edição: cada leitor escolhe as categorias que quer ver e em que ordem.
// Usado pelo app (index.html) e pelo site público (ler/).
//
// Como marcar a página: cada seção de notícias é <section data-cat="esportes">.
// CtxCat.aplicar(raiz) reordena essas seções pela escolha do leitor; as que ele não escolheu
// vão para um <details class="ctx-outras"> fechado no fim ("Outras seções de hoje").
// Em cada seção o leitor vê só as N primeiras notas (N escolhido por categoria; padrão no
// campo "qtd" do catálogo); as demais ficam num <details class="ctx-mais"> ("Mais 2 de Mundo").
// Quem não personalizou vê as categorias "padrao" com a quantidade padrão.
// Botões com [data-ctx-cat] abrem a folha "Personalizar". O catálogo fica em categorias.json.
//
// Ganchos para a página: CtxCat.aoSalvar.push(fn(lista)) e CtxCat.sugerir = fn(texto) -> Promise.
(function () {
  "use strict";
  if (window.CtxCat) return;
  var script = document.currentScript;
  var URL_CAT = new URL("categorias.json", script && script.src ? script.src : location.href).href;
  var CHAVE = "ctx-categorias";

  var C = { lista: [], porId: {}, pronto: null, aoSalvar: [], sugerir: null, ultimaRaiz: null };

  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function norm(s) { return String(s || "").normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().trim(); }
  function toast(t) { var el = document.createElement("div"); el.className = "toast"; el.setAttribute("role", "status"); el.textContent = t; document.body.appendChild(el); setTimeout(function () { el.remove(); }, 2600); }

  /* ---------- Catálogo ---------- */
  function usar(lista) {
    C.lista = lista; C.porId = {};
    lista.forEach(function (c) { C.porId[c.id] = c; });
    estilo();
  }
  C.carregar = function () {
    if (!C.pronto) C.pronto = fetch(URL_CAT, { cache: "no-cache" })
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function (j) { usar(j.categorias || []); try { localStorage.setItem(CHAVE + "-catalogo", JSON.stringify(j.categorias || [])); } catch (e) {} return C.lista; })
      .catch(function () { var s = null; try { s = JSON.parse(localStorage.getItem(CHAVE + "-catalogo")); } catch (e) {} usar(s || []); return C.lista; });
    return C.pronto;
  };
  // Nome de seção ("Inovação", "Tecnologia") -> id do catálogo ("inovacao").
  C.idDe = function (nome, categoria) {
    if (categoria && (C.porId[categoria] || !C.lista.length)) return categoria;
    var n = norm(nome);
    for (var i = 0; i < C.lista.length; i++) {
      var c = C.lista[i];
      if (norm(c.nome) === n || c.id === n || (c.aliases || []).some(function (a) { return norm(a) === n; })) return c.id;
    }
    return n.replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  };
  C.nome = function (id) { return C.porId[id] ? C.porId[id].nome : id; };

  /* ---------- Preferência do leitor ---------- */
  C.escolhidas = function () {
    try { var v = JSON.parse(localStorage.getItem(CHAVE)); return Array.isArray(v) && v.length ? v : null; } catch (e) { return null; }
  };
  C.quantidades = function () {
    try { var v = JSON.parse(localStorage.getItem(CHAVE + "-qtd")); return v && typeof v === "object" ? v : {}; } catch (e) { return {}; }
  };
  C.MAX = 5;
  C.qtdPadrao = function (id) { var c = C.porId[id]; return Math.min(C.MAX, Math.max(1, (c && c.qtd) || 1)); };
  C.qtd = function (id) { var q = +C.quantidades()[id]; return q >= 1 && q <= C.MAX ? q : C.qtdPadrao(id); };
  C.padrao = function () { return C.lista.filter(function (c) { return c.padrao; }).map(function (c) { return c.id; }); };
  C.salvar = function (lista, qtds) {
    var q = {};
    (lista || []).forEach(function (id) { q[id] = qtds && qtds[id] ? qtds[id] : C.qtd(id); });
    try {
      if (lista && lista.length) { localStorage.setItem(CHAVE, JSON.stringify(lista)); localStorage.setItem(CHAVE + "-qtd", JSON.stringify(q)); }
      else { localStorage.removeItem(CHAVE); localStorage.removeItem(CHAVE + "-qtd"); }
    } catch (e) {}
    C.aoSalvar.forEach(function (fn) { try { fn(lista && lista.length ? lista : null, lista && lista.length ? q : null); } catch (e) {} });
    if (C.ultimaRaiz) C.aplicar(C.ultimaRaiz);
  };

  /* ---------- Edição personalizada ---------- */
  C.aplicar = function (raiz) {
    raiz = raiz || document;
    C.ultimaRaiz = raiz;
    raiz.querySelectorAll(".ctx-barra, .ctx-outras, .ctx-mais").forEach(function (el) {
      // Devolve as seções e notas guardadas antes de remontar.
      if (el.classList.contains("ctx-outras")) el.querySelectorAll("section[data-cat]").forEach(function (s) { el.parentNode.insertBefore(s, el); });
      if (el.classList.contains("ctx-mais")) Array.prototype.slice.call(el.children).forEach(function (n) { if (n.tagName !== "SUMMARY") el.parentNode.insertBefore(n, el); });
      el.remove();
    });
    var secs = Array.prototype.slice.call(raiz.querySelectorAll("section[data-cat]"));
    if (!secs.length) return;
    var pessoal = C.escolhidas(), esc_ = pessoal || C.padrao();
    if (!esc_.length) esc_ = secs.map(function (s) { return s.dataset.cat; });
    var barra = document.createElement("div");
    barra.className = "ctx-barra";
    barra.setAttribute("data-nao-ler", "");

    // Quantas notas por categoria: as primeiras ficam à vista, o resto em "Mais N de X".
    secs.forEach(function (s) {
      var notas = Array.prototype.slice.call(s.children).filter(function (n) { return n.matches("article.nota"); });
      var q = C.qtd(s.dataset.cat);
      if (notas.length <= q) return;
      var resto = notas.slice(q), d = document.createElement("details");
      d.className = "ctx-mais";
      d.innerHTML = "<summary>Ver mais " + resto.length + " de " + esc(C.nome(s.dataset.cat)) + "</summary>";
      resto[0].parentNode.insertBefore(d, resto[0]);
      resto.forEach(function (n) { d.appendChild(n); });
    });

    // Guarda o lugar de cada seção para manter o que estiver entre elas (ex.: caixa de assinatura).
    var vagas = secs.map(function (s) { var m = document.createComment("ctx"); s.parentNode.replaceChild(m, s); return m; });
    var ordem = [], fora = [], presentes = {};
    esc_.forEach(function (id) { secs.forEach(function (s) { if (s.dataset.cat === id) { ordem.push(s); presentes[id] = 1; } }); });
    secs.forEach(function (s) { if (esc_.indexOf(s.dataset.cat) < 0) fora.push(s); });
    var ultima = null;
    vagas.forEach(function (m, i) {
      if (i < ordem.length) { m.parentNode.replaceChild(ordem[i], m); ultima = ordem[i]; }
    });
    var ancora = ultima ? ultima.nextSibling : vagas[0];
    var pai = ultima ? ultima.parentNode : vagas[0].parentNode;
    if (fora.length) {
      var d = document.createElement("details");
      d.className = "ctx-outras";
      d.innerHTML = '<summary>Outras seções de hoje <span>' + esc(fora.map(function (s) { return C.nome(s.dataset.cat); }).join(", ")) + "</span></summary>";
      fora.forEach(function (s) { d.appendChild(s); });
      pai.insertBefore(d, ancora);
    }
    vagas.forEach(function (m) { if (m.parentNode) m.parentNode.removeChild(m); });

    // Nota de uma categoria que foi para "O que você não pode deixar de saber" conta como presente.
    raiz.querySelectorAll("article[data-cat]").forEach(function (a) { presentes[a.dataset.cat] = 1; });
    var faltam = esc_.filter(function (id) { return !presentes[id]; }).map(C.nome);
    var lista = esc(esc_.map(function (id) { return C.nome(id) + " (" + C.qtd(id) + ")"; }).join(", "));
    barra.innerHTML = (pessoal
      ? '<p><b>Sua edição:</b> ' + lista + "." +
        (faltam.length ? ' <span class="ctx-falta">Hoje não saiu nada de ' + esc(juntar(faltam)) + ".</span>" : "") +
        '</p><button class="btn leve" type="button" data-ctx-cat>Mudar</button>'
      : '<p><b>Monte a sua edição.</b> Escolha as categorias, quantas notícias de cada uma e a ordem. Agora: ' + lista + ".</p>" +
        '<button class="btn" type="button" data-ctx-cat>Personalizar</button>');
    var primeira = ordem[0] || pai.querySelector(".ctx-outras");
    if (primeira) primeira.parentNode.insertBefore(barra, primeira);
  };
  function juntar(a) { return a.length < 2 ? a.join("") : a.slice(0, -1).join(", ") + " nem de " + a[a.length - 1]; }

  /* ---------- Folha "Personalizar" ---------- */
  C.abrir = function () {
    C.carregar().then(function () {
      var sel = (C.escolhidas() || C.padrao()).filter(function (id) { return C.porId[id]; });
      var qt = {}; sel.forEach(function (id) { qt[id] = C.qtd(id); });
      var camada = document.createElement("div");
      camada.innerHTML = '<div class="veu"><form class="sheet ctx-folha" role="dialog" aria-modal="true" aria-labelledby="ctx-tit">' +
        '<h2 id="ctx-tit">Monte a sua edição</h2><p class="alvo">Escolha as categorias e quantas notícias quer de cada uma. As setas mudam a ordem. O destaque "O que você não pode deixar de saber hoje" aparece sempre no topo.</p>' +
        '<span class="rotulo">Na sua edição</span><ol class="ctx-sel"></ol>' +
        '<span class="rotulo">Adicionar</span><div class="chips ctx-add" role="group" aria-label="Adicionar categoria"></div>' +
        (C.sugerir ? '<label class="rotulo" for="ctx-sug">Não achou? Sugira uma categoria</label><div class="ctx-sug"><input id="ctx-sug" type="text" maxlength="60" placeholder="Ex.: Agronegócio, Games, Moda"><button class="btn leve" type="button" data-sugerir>Enviar</button></div>' : "") +
        '<p class="msg" aria-live="polite"></p>' +
        '<div class="linha"><button class="btn leve" type="button" data-padrao>Voltar ao padrão</button><button class="btn leve" type="button" data-fechar>Cancelar</button><button class="btn cheio" type="submit">Salvar</button></div></form></div>';
      document.body.appendChild(camada);
      var form = camada.querySelector("form"), ol = form.querySelector(".ctx-sel"), add = form.querySelector(".ctx-add"), msg = form.querySelector(".msg");
      function desenhar() {
        ol.innerHTML = sel.length ? sel.map(function (id, i) {
          var c = C.porId[id];
          var q = qt[id] || C.qtd(id);
          return '<li><span class="etq ctx-' + esc(id) + '">' + esc(c.nome) + '</span><span class="ctx-desc">' + esc(c.desc || "") + '</span>' +
            '<span class="ctx-qtd" role="group" aria-label="Notícias de ' + esc(c.nome) + '">' +
            '<button type="button" aria-label="Menos notícias de ' + esc(c.nome) + '" data-menos="' + esc(id) + '"' + (q > 1 ? "" : " disabled") + ">−</button>" +
            '<output aria-live="polite">' + q + " " + (q > 1 ? "notícias" : "notícia") + "</output>" +
            '<button type="button" aria-label="Mais notícias de ' + esc(c.nome) + '" data-mais="' + esc(id) + '"' + (q < C.MAX ? "" : " disabled") + ">+</button></span>" +
            '<span class="ctx-mov">' +
            '<button type="button" aria-label="Subir ' + esc(c.nome) + '" data-sobe="' + i + '"' + (i ? "" : " disabled") + ">↑</button>" +
            '<button type="button" aria-label="Descer ' + esc(c.nome) + '" data-desce="' + i + '"' + (i < sel.length - 1 ? "" : " disabled") + ">↓</button>" +
            '<button type="button" aria-label="Tirar ' + esc(c.nome) + '" data-tira="' + i + '">✕</button></span></li>';
        }).join("") : '<li class="ctx-vazio">Nenhuma categoria. Adicione pelo menos uma abaixo.</li>';
        var resto = C.lista.filter(function (c) { return sel.indexOf(c.id) < 0; });
        add.innerHTML = resto.length ? resto.map(function (c) { return '<button type="button" class="chip" data-poe="' + esc(c.id) + '" title="' + esc(c.desc || "") + '">+ ' + esc(c.nome) + "</button>"; }).join("") : '<span class="ctx-desc">Você já escolheu todas.</span>';
      }
      desenhar();
      function fechar() { camada.remove(); }
      camada.querySelector(".veu").addEventListener("click", function (e) { if (e.target === e.currentTarget || e.target.hasAttribute("data-fechar")) fechar(); });
      form.addEventListener("click", function (e) {
        var b = e.target.closest("button"); if (!b) return;
        var d = b.dataset, i;
        if (d.poe) { sel.push(d.poe); qt[d.poe] = qt[d.poe] || C.qtd(d.poe); desenhar(); }
        else if (d.menos || d.mais) {
          var id = d.menos || d.mais; qt[id] = Math.min(C.MAX, Math.max(1, (qt[id] || 1) + (d.mais ? 1 : -1))); desenhar();
          var alvo = ol.querySelector('[data-' + (d.mais ? "mais" : "menos") + '="' + id + '"]:not([disabled])') || ol.querySelector('[data-' + (d.mais ? "menos" : "mais") + '="' + id + '"]'); if (alvo) alvo.focus();
        }
        else if (d.tira != null) { sel.splice(+d.tira, 1); desenhar(); }
        else if (d.sobe != null) { i = +d.sobe; sel.splice(i - 1, 0, sel.splice(i, 1)[0]); desenhar(); ol.querySelectorAll("[data-sobe]")[Math.max(i - 1, 0)].focus(); }
        else if (d.desce != null) { i = +d.desce; sel.splice(i + 1, 0, sel.splice(i, 1)[0]); desenhar(); ol.querySelectorAll("[data-desce]")[Math.min(i + 1, sel.length - 1)].focus(); }
        else if (b.hasAttribute("data-padrao")) { C.salvar(null); fechar(); toast("Voltou para a edição padrão"); }
        else if (b.hasAttribute("data-sugerir")) {
          var inp = form.querySelector("#ctx-sug"), t = inp.value.trim();
          if (!t) { msg.textContent = "Escreva a categoria antes de enviar."; inp.focus(); return; }
          b.disabled = true; msg.textContent = "Enviando…";
          Promise.resolve(C.sugerir(t)).then(function () { inp.value = ""; b.disabled = false; msg.textContent = "Sugestão enviada à redação. Obrigado!"; })
            .catch(function () { b.disabled = false; msg.textContent = "Não foi enviada. Verifique a internet e tente de novo."; });
        }
      });
      form.addEventListener("submit", function (e) {
        e.preventDefault();
        if (!sel.length) { msg.textContent = "Escolha pelo menos uma categoria, ou toque em Voltar ao padrão."; return; }
        C.salvar(sel.slice(), qt); fechar(); toast("Sua edição foi atualizada");
      });
      setTimeout(function () { var p = form.querySelector("[data-poe], [data-tira]"); if (p) p.focus(); }, 60);
    });
  };
  document.addEventListener("click", function (e) { var b = e.target.closest && e.target.closest("[data-ctx-cat]"); if (b) { e.preventDefault(); C.abrir(); } });
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") { var f = document.querySelector(".ctx-folha"); if (f) f.closest(".veu").parentNode.remove(); } });

  /* ---------- Estilo ---------- */
  function estilo() {
    var el = document.getElementById("ctx-cat-css") || document.head.appendChild(Object.assign(document.createElement("style"), { id: "ctx-cat-css" }));
    function regras(pre, i) {
      return C.lista.filter(function (c) { return /^[a-z0-9-]+$/.test(c.id) && c.cor; }).map(function (c) {
        return pre + ".etq." + c.id + "," + pre + ".etq.ctx-" + c.id + "{background:" + (c.cor[i] || c.cor[0]) + "}";
      }).join("");
    }
    el.textContent = regras("", 0) +
      "@media (prefers-color-scheme: dark){" + regras(":root:not([data-theme=light]) ", 1) + "}" + regras(":root[data-theme=dark] ", 1) +
      ".ctx-barra{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;padding:12px 14px;border:1px dashed var(--line);border-radius:10px;font-family:var(--f-display);font-size:15px;line-height:1.45}" +
      ".ctx-barra p{flex:1 1 220px}.ctx-falta{color:var(--muted)}" +
      ".ctx-outras{border-top:1px solid var(--line);padding-top:14px}.ctx-outras>summary{cursor:pointer;font-family:var(--f-display);font-weight:600;color:var(--accent);min-height:40px;display:flex;align-items:center;gap:8px;flex-wrap:wrap}" +
      ".ctx-outras>summary span{font-weight:400;color:var(--muted);font-size:14px}.ctx-outras[open]{display:grid;gap:28px}" +
      ".ctx-folha{max-height:88vh;overflow:auto}.ctx-sel{list-style:none;margin:0;padding:0;display:grid;gap:8px}" +
      ".ctx-sel li{display:grid;grid-template-columns:auto 1fr auto auto;align-items:center;gap:10px;padding:8px 0;border-bottom:1px solid var(--line)}" +
      ".ctx-qtd{display:flex;align-items:center;gap:4px}.ctx-qtd output{font-family:var(--f-display);font-size:14px;font-weight:600;min-width:78px;text-align:center}" +
      ".ctx-qtd button{width:36px;height:36px;border:1px solid var(--accent);border-radius:8px;background:none;cursor:pointer;color:var(--accent);font-size:18px;line-height:1}.ctx-qtd button:disabled{opacity:.35;cursor:default}" +
      ".ctx-mais>summary{cursor:pointer;font-family:var(--f-display);font-weight:600;color:var(--accent);min-height:40px;display:flex;align-items:center;border-top:1px solid var(--line);padding-top:10px}.ctx-mais[open]{display:grid;gap:18px}" +
      ".ctx-desc{font-family:var(--f-display);font-size:13px;color:var(--muted);line-height:1.35}.ctx-vazio{color:var(--muted);font-size:15px}" +
      ".ctx-mov{display:flex;gap:4px}.ctx-mov button{width:36px;height:36px;border:1px solid var(--line);border-radius:8px;background:none;cursor:pointer;color:var(--ink)}.ctx-mov button:disabled{opacity:.35;cursor:default}" +
      ".ctx-sug{display:flex;gap:8px}.ctx-sug input{flex:1;min-width:0;min-height:40px;border:1px solid var(--line);border-radius:8px;padding:8px 10px;background:var(--surface);color:var(--ink);font:inherit;font-size:15px}" +
      "@media (max-width:520px){.ctx-sel li{grid-template-columns:1fr auto;row-gap:6px}.ctx-sel .ctx-desc{grid-column:1/-1;grid-row:2}.ctx-sel .ctx-qtd{grid-column:1;grid-row:3}.ctx-sel .ctx-mov{grid-column:2;grid-row:1}}";
  }

  window.CtxCat = C;
  C.carregar();
})();
