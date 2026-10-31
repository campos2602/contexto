// Site público do Contexto: compartilhar, assinar com link de indicação e "Achei tendencioso".
(function () {
  var C = window.CONTEXTO || {};
  var HDR = { apikey: C.chave, Authorization: "Bearer " + C.chave, "Content-Type": "application/json", Prefer: "return=minimal" };
  function guarda(k, v) { try { if (v === undefined) return localStorage.getItem("ctx-" + k); localStorage.setItem("ctx-" + k, v); } catch (e) { return null; } }
  function esc(s) { return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) { return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]; }); }
  function toast(t) { var el = document.createElement("div"); el.className = "toast"; el.setAttribute("role", "status"); el.textContent = t; document.body.appendChild(el); setTimeout(function () { el.remove(); }, 2600); }
  function post(tabela, corpo) {
    return fetch(C.supabase + "/rest/v1/" + tabela, { method: "POST", headers: HDR, body: JSON.stringify(corpo) })
      .then(function (r) { if (r.status === 409) return "repetido"; if (!r.ok) throw new Error(r.status); return "ok"; });
  }

  // Indicação: ?ref=codigo fica guardado até a pessoa assinar.
  var q = new URLSearchParams(location.search);
  var ref = (q.get("ref") || "").toLowerCase();
  if (/^[a-z0-9]{6,12}$/.test(ref) && !guarda("codigo")) guarda("indicado", ref);
  var utm = q.get("utm_source") || q.get("origem") || "";

  function novoCodigo() { var a = "abcdefghijkmnpqrstuvwxyz23456789", s = ""; var r = (window.crypto && crypto.getRandomValues) ? crypto.getRandomValues(new Uint8Array(8)) : null; for (var i = 0; i < 8; i++) s += a[(r ? r[i] : Math.floor(Math.random() * 256)) % a.length]; return s; }
  function linkIndicacao(c) { return C.site + "/assinar/?ref=" + c; }

  function compartilhar(titulo, texto, url) {
    var cod = guarda("codigo");
    if (cod) url += (url.indexOf("?") < 0 ? (url.indexOf("#") < 0 ? "?" : "") : "&") ;
    if (cod && url.indexOf("#") < 0) url += "ref=" + cod;
    var msg = "*" + titulo + "*\n\n" + (texto || "") + "\n\nLeia no Contexto: " + url;
    if (navigator.share) { navigator.share({ title: titulo, text: msg.replace(/\n\nLeia no Contexto: .*$/, ""), url: url }).catch(function () {}); return; }
    window.open("https://wa.me/?text=" + encodeURIComponent(msg), "_blank", "noopener");
  }

  function mostrarIndicacao(box, cod, texto) {
    var msg = box.querySelector(".msg"), f = box.querySelector(".f-assinar");
    if (f) f.hidden = true;
    msg.innerHTML = esc(texto) + '<div class="ref"><span>Seu link de indicação. Quem assinar por ele conta para você:</span><code>' + esc(linkIndicacao(cod)) + '</code><div class="acoes"><button class="btn" type="button" data-copiar="' + esc(linkIndicacao(cod)) + '">Copiar link</button><button class="btn" type="button" data-zap="' + esc(linkIndicacao(cod)) + '">Mandar no WhatsApp</button></div></div>';
  }

  document.querySelectorAll(".assinar").forEach(function (box) {
    var cod = guarda("codigo");
    if (cod) { mostrarIndicacao(box, cod, "Você já assina o Contexto. Obrigado!"); return; }
    var f = box.querySelector(".f-assinar"), inp = f.querySelector("input"), msg = box.querySelector(".msg"), btn = f.querySelector("button");
    f.addEventListener("submit", function (e) {
      e.preventDefault();
      var email = inp.value.trim().toLowerCase();
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) { msg.textContent = "Confira o e-mail: parece faltar alguma coisa."; inp.focus(); return; }
      btn.disabled = true; msg.textContent = "Enviando…";
      var c = novoCodigo(), ind = guarda("indicado");
      var origem = (location.pathname.replace(C.base, "/") + " " + (box.dataset.origem || "") + (utm ? " " + utm : "")).slice(0, 200);
      post("contexto_inscritos", { email: email, codigo: c, indicado_por: ind && ind !== c ? ind : null, origem: origem })
        .then(function (r) {
          if (r === "repetido") { btn.disabled = false; msg.textContent = "Esse e-mail já está na lista. Obrigado!"; return; }
          guarda("codigo", c);
          document.querySelectorAll(".assinar").forEach(function (b) { mostrarIndicacao(b, c, "Pronto! Você está na lista do Contexto."); });
        })
        .catch(function () { btn.disabled = false; msg.textContent = "Não deu para enviar agora. Verifique a internet e tente de novo."; });
    });
  });

  // "Achei tendencioso": vai para a mesma caixa de comentários que a redação lê.
  var TIPOS = ["Achei tendencioso", "Erro de fato", "Sugestão"];
  function abrirFolha(b) {
    var sel = b.dataset.tipo || "Achei tendencioso", tipos = TIPOS.indexOf(sel) < 0 ? [sel] : TIPOS;
    var camada = document.createElement("div");
    camada.innerHTML = '<div class="veu"><form class="sheet" role="dialog" aria-modal="true" aria-labelledby="f-tit"><h2 id="f-tit">Fale com a Ouvidoria</h2><p class="alvo">' + esc(b.dataset.titulo) + '</p><div class="chips" role="group" aria-label="Tipo">' +
      tipos.map(function (t) { return '<button type="button" class="chip" aria-pressed="' + (t === sel) + '" data-t="' + esc(t) + '">' + esc(t) + "</button>"; }).join("") +
      '</div><label class="rotulo" for="f-texto">O que você viu?</label><textarea id="f-texto" placeholder="Pode ser curto. Não precisa se identificar."></textarea><p class="msg" aria-live="polite"></p><div class="linha"><button class="btn leve" type="button" data-fechar>Cancelar</button><button class="btn cheio" type="submit">Enviar</button></div></form></div>';
    document.body.appendChild(camada);
    var form = camada.querySelector("form"), ta = camada.querySelector("textarea"), msg = camada.querySelector(".msg");
    function fechar() { camada.remove(); }
    camada.querySelector(".veu").addEventListener("click", function (e) { if (e.target === e.currentTarget || e.target.hasAttribute("data-fechar")) fechar(); });
    form.querySelectorAll(".chip").forEach(function (c) { c.addEventListener("click", function () { sel = c.dataset.t; form.querySelectorAll(".chip").forEach(function (x) { x.setAttribute("aria-pressed", String(x === c)); }); }); });
    setTimeout(function () { ta.focus(); }, 60);
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      var t = ta.value.trim(); if (!t) { msg.textContent = "Escreva antes de enviar."; return; }
      msg.textContent = "Enviando…";
      post("contexto_comentarios", { edicao: b.dataset.avaliar || "", alvo: b.dataset.alvo, alvo_titulo: String(b.dataset.titulo || "").slice(0, 300), tipo: sel, texto: ("[leitor] " + t).slice(0, 4000) })
        .then(function () { fechar(); toast("Enviado. A Ouvidoria lê tudo."); })
        .catch(function () { msg.textContent = "Não foi enviado. Tente de novo."; });
    });
  }

  document.addEventListener("click", function (e) {
    var b = e.target.closest && e.target.closest("button");
    if (!b) return;
    if (b.classList.contains("compartilhar")) compartilhar(b.dataset.titulo, b.dataset.texto, b.dataset.url);
    else if (b.hasAttribute("data-avaliar")) abrirFolha(b);
    else if (b.dataset.copiar) { (navigator.clipboard ? navigator.clipboard.writeText(b.dataset.copiar) : Promise.reject()).then(function () { toast("Link copiado"); }, function () { prompt("Copie o link:", b.dataset.copiar); }); }
    else if (b.dataset.zap) window.open("https://wa.me/?text=" + encodeURIComponent("Eu leio o Contexto: o que importa hoje e por quê, em 5 minutos, sem torcida. É grátis: " + b.dataset.zap), "_blank", "noopener");
  });
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") { var v = document.querySelector(".veu"); if (v) v.parentNode.remove(); } });
})();
