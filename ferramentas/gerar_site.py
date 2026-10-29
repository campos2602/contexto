#!/usr/bin/env python3
"""Gera o site público do Contexto a partir de edicoes/ e contextos/.

Saída (tudo versionado e servido pelo GitHub Pages):
  ler/                      edição mais recente
  ler/<id>/                 uma edição
  ler/<id>/<nota>/          Notícia completa de uma nota
  ler/contexto/<slug>/      página de Contexto
  ler/arquivo/              lista das edições
  assinar/                  página de inscrição (link da bio)
  privacidade/              como os dados são usados
  feed.xml, sitemap.xml, robots.txt

O app de leitura do editor (index.html) não muda: o site público esconde o
parecer da Ouvidoria e o status de aprovação.

Uso: python3 ferramentas/gerar_site.py
"""
import html
import json
import os
import re
import shutil
from datetime import datetime, timezone
from email.utils import format_datetime

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONFIG = json.load(open(os.path.join(RAIZ, "ferramentas", "site.json"), encoding="utf-8"))
SITE = CONFIG["site"].rstrip("/")          # ex.: https://campos2602.github.io/contexto
BASE = re.sub(r"^https?://[^/]+", "", SITE) + "/"  # ex.: /contexto/
SECAO = {"Mundo": "mundo", "Brasil": "brasil", "Inovação": "inovacao"}
MESES = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"]


def esc(s):
    return html.escape("" if s is None else str(s), quote=True)


def link(u):
    u = str(u or "")
    return esc(u) if u.startswith("https://") else "#"


def rich(x):
    if isinstance(x, dict):
        return "<b>%s</b> %s" % (esc(x.get("b")), esc(x.get("t")))
    return esc(x)


def data_curta(d):
    m = re.match(r"(\d{4})-(\d{2})-(\d{2})", d or "")
    return "%d %s %s" % (int(m.group(3)), MESES[int(m.group(2)) - 1], m.group(1)) if m else (d or "")


def ler_json(*p):
    with open(os.path.join(RAIZ, *p), encoding="utf-8") as f:
        return json.load(f)


def gravar(rel, conteudo):
    caminho = os.path.join(RAIZ, rel)
    os.makedirs(os.path.dirname(caminho), exist_ok=True)
    with open(caminho, "w", encoding="utf-8") as f:
        f.write(conteudo)


def fontes(arr):
    if not arr:
        return ""
    rot = "Fontes: " if len(arr) > 1 else "Fonte: "
    return '<p class="fonte">%s%s</p>' % (rot, ", ".join(
        '<a href="%s" target="_blank" rel="noopener">%s</a>' % (link(f.get("u")), esc(f.get("t"))) for f in arr))


def resumo(txt, n=160):
    txt = re.sub(r"\s+", " ", txt or "").strip()
    return txt if len(txt) <= n else txt[: n - 1].rsplit(" ", 1)[0] + "…"


# ---------- Moldura ----------

def pagina(caminho, titulo, descricao, corpo, tipo="website", publicado=None, indexar=True):
    url = SITE + "/" + caminho
    img = SITE + "/icones/capa-compartilhamento.png"
    titulo_completo = titulo if titulo.startswith("Contexto") else titulo + " · Contexto"
    meta_pub = '<meta property="article:published_time" content="%s">' % esc(publicado) if publicado else ""
    return """<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>{t}</title>
<meta name="description" content="{d}">
<link rel="canonical" href="{u}">
{robots}<meta property="og:site_name" content="Contexto">
<meta property="og:locale" content="pt_BR">
<meta property="og:type" content="{tipo}">
<meta property="og:title" content="{t}">
<meta property="og:description" content="{d}">
<meta property="og:url" content="{u}">
<meta property="og:image" content="{img}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
{pub}
<link rel="alternate" type="application/rss+xml" title="Contexto" href="{b}feed.xml">
<link rel="icon" href="{b}icones/favicon-64.png">
<link rel="apple-touch-icon" href="{b}icones/icone-180.png">
<link rel="manifest" href="{b}manifest.webmanifest">
<meta name="theme-color" content="#121821">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Schibsted+Grotesk:wght@400;600;700;800&family=Source+Serif+4:opsz,wght@8..60,400;8..60,600&family=IBM+Plex+Mono:wght@500;600&display=swap">
<link rel="stylesheet" href="{b}ler/estilo.css">
</head>
<body>
<header class="topo"><div class="topo-in">
<a class="wm" href="{b}ler/" aria-label="Contexto, edição de hoje"><span class="a">con</span><span class="b">tex</span><span class="c">to</span></a>
<nav class="menu" aria-label="Site"><a href="{b}ler/arquivo/">Arquivo</a><a class="assine" href="{b}assinar/">Assinar grátis</a></nav>
</div></header>
<main>
{corpo}
</main>
<footer class="rodape"><div class="rodape-in">
<p><b>Contexto</b>: o que importa hoje e por quê, em 5 minutos. Mundo, Brasil e Inovação, sem torcida.</p>
<p>Escrito por inteligência artificial, com checagem de fontes e Ouvidoria de neutralidade antes de sair. Erros são corrigidos em público.</p>
<p><a href="{b}assinar/">Assinar</a> · <a href="{b}ler/arquivo/">Arquivo</a> · <a href="{b}feed.xml">RSS</a> · <a href="{b}privacidade/">Privacidade</a></p>
</div></footer>
<script>window.CONTEXTO = {{ site: "{s}", base: "{b}", supabase: "{sb}", chave: "{key}" }};</script>
<script src="{b}ler/leitor.js" defer></script>
</body>
</html>
""".format(corpo=corpo, t=esc(titulo_completo), d=esc(descricao), u=esc(url), img=esc(img), tipo=tipo, pub=meta_pub, b=BASE, s=SITE,
           sb=esc(CONFIG["supabase"]), key=esc(CONFIG["chave_publica"]),
           robots="" if indexar else '<meta name="robots" content="noindex">\n')


def caixa_assinar(origem, titulo="Receba o Contexto no seu e-mail", texto="Grátis, sem torcida e sem ruído. Cancele quando quiser."):
    return """<section class="assinar" data-origem="{o}">
<h2>{t}</h2>
<p>{x}</p>
<form class="f-assinar" novalidate>
<label class="sr" for="em-{o}">Seu melhor e-mail</label>
<input id="em-{o}" type="email" name="email" placeholder="Seu melhor e-mail" autocomplete="email" required>
<button class="btn cheio" type="submit">Quero receber</button>
</form>
<p class="msg" aria-live="polite"></p>
<p class="miudo">Usamos seu e-mail só para enviar o Contexto. <a href="{b}privacidade/">Privacidade</a></p>
</section>""".format(o=esc(origem), t=esc(titulo), x=esc(texto), b=BASE)


def botao_compartilhar(titulo, texto, url):
    return ('<button class="btn leve compartilhar" type="button" data-titulo="%s" data-texto="%s" data-url="%s">'
            '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M12 3v13M7 8l5-5 5 5M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5"/></svg>'
            'Compartilhar</button>') % (esc(titulo), esc(texto), esc(url))


# ---------- Páginas ----------

def v_edicao(ed, contextos, rotulo_topo=None):
    k = ed["_id"]
    url_ed = SITE + "/ler/%s/" % k
    h = ['<article class="folha">', '<header class="edhead"><div class="linha"><span class="rotulo">%s</span><span class="rotulo">%s de leitura</span></div>'
         % (esc(rotulo_topo or ed.get("dia") or data_curta(ed.get("data"))), esc(ed.get("leitura") or "5 min"))]
    h.append("<h1 class=\"sr\">Contexto de %s</h1></header>" % esc(ed.get("dia") or data_curta(ed.get("data"))))
    if ed.get("abre"):
        h.append('<p class="abre">%s</p>' % esc(ed["abre"]))
    if ed.get("trinta"):
        h.append('<section class="sec"><span class="etq">Em 30 segundos</span><ol class="trinta">%s</ol>%s</section>' % (
            "".join("<li><span>%s</span></li>" % esc(t) for t in ed["trinta"]),
            '<div class="acoes">%s</div>' % botao_compartilhar("Contexto em 30 segundos", "\n".join("• " + t for t in ed["trinta"]), url_ed)))
    for s in ed.get("secoes") or []:
        h.append('<section class="sec"><span class="etq %s">%s</span>' % (SECAO.get(s.get("nome"), ""), esc(s.get("nome"))))
        for n in s.get("notas") or []:
            tem_nc = bool(n.get("completa"))
            tem_cx = bool(n.get("contexto") and n["contexto"] in contextos)
            url_nota = SITE + ("/ler/%s/%s/" % (k, n["id"]) if tem_nc else "/ler/%s/#nota-%s" % (k, n["id"]))
            h.append('<article class="nota" id="nota-%s"><h3>%s</h3><p>%s</p>' % (esc(n["id"]), esc(n.get("titulo")), esc(n.get("texto"))))
            if n.get("pq"):
                h.append('<p class="pq"><b>Por que importa:</b> %s</p>' % esc(n["pq"]))
            h.append(fontes(n.get("fontes")))
            h.append('<div class="acoes">')
            if tem_nc:
                h.append('<a class="btn cheio" href="%sler/%s/%s/">Notícia completa</a>' % (BASE, k, esc(n["id"])))
            if tem_cx:
                h.append('<a class="btn" href="%sler/contexto/%s/">Contexto</a>' % (BASE, esc(n["contexto"])))
            h.append(botao_compartilhar(n.get("titulo"), n.get("texto"), url_nota))
            h.append("</div></article>")
        h.append("</section>")
        if s is (ed.get("secoes") or [None])[0]:
            h.append(caixa_assinar("edicao-meio", "Gostou até aqui?", "Receba o Contexto no e-mail, de graça. Leva 5 minutos por dia."))
    if ed.get("numero"):
        nu = ed["numero"]
        h.append('<section class="sec"><span class="etq">Número do dia</span><div class="numero"><b>%s</b><p>%s</p>%s</div></section>'
                 % (esc(nu.get("valor")), esc(nu.get("texto")), fontes(nu.get("fontes"))))
    if ed.get("fechar"):
        h.append('<section class="sec"><span class="etq">Para fechar</span><p>%s</p>%s</section>' % (esc(ed["fechar"].get("texto")), fontes(ed["fechar"].get("fontes"))))
    if ed.get("corrigimos"):
        h.append('<section class="sec"><span class="etq">Corrigimos</span>%s</section>' % "".join("<p>%s</p>" % esc(c) for c in ed["corrigimos"]))
    h.append('<section class="sec"><div class="acoes">%s<button class="btn" type="button" data-avaliar="%s" data-alvo="edicao" data-titulo="Edição inteira">Achei tendencioso</button></div>'
             % (botao_compartilhar("Contexto de " + (ed.get("dia") or ""), (ed.get("trinta") or [""])[0], url_ed), esc(k)))
    if ed.get("transp"):
        h.append('<p class="transp">%s</p>' % esc(ed["transp"]))
    h.append("</section></article>")
    h.append(caixa_assinar("edicao-fim"))
    return "".join(h)


def v_completa(ed, n, secao, contextos):
    c = n["completa"]
    k = ed["_id"]
    h = ['<article class="folha pag">', '<span class="etq %s">%s</span>' % (SECAO.get(secao, ""), esc(secao)), "<h1>%s</h1>" % esc(n.get("titulo"))]
    if c.get("fina"):
        h.append('<p class="fina">%s</p>' % esc(c["fina"]))
    h.append('<p class="meta"><span class="selo">%s</span><span>Atualizada em %s</span></p>' % (esc(c.get("selo") or n.get("selo") or "Checada"), esc(c.get("atualizada") or data_curta(ed.get("data")))))
    if c.get("fato"):
        h.append("<p>%s</p>" % esc(c["fato"]))
    for b in c.get("blocos") or []:
        if b.get("h"):
            h.append("<h2>%s</h2>" % esc(b["h"]))
        h.extend("<p>%s</p>" % rich(x) for x in b.get("p") or [])
        if b.get("li"):
            h.append("<ul>%s</ul>" % "".join("<li>%s</li>" % rich(x) for x in b["li"]))
    if c.get("fontes"):
        h.append('<h2>Fontes</h2><ul class="fonte">%s</ul>' % "".join('<li><a href="%s" target="_blank" rel="noopener">%s</a></li>' % (link(f.get("u")), esc(f.get("t"))) for f in c["fontes"]))
    h.append('<div class="acoes">')
    if n.get("contexto") in contextos:
        h.append('<a class="btn" href="%sler/contexto/%s/">Entenda o Contexto</a>' % (BASE, esc(n["contexto"])))
    h.append(botao_compartilhar(n.get("titulo"), c.get("fina") or n.get("texto"), SITE + "/ler/%s/%s/" % (k, n["id"])))
    h.append('<button class="btn" type="button" data-avaliar="%s" data-alvo="%s" data-titulo="%s">Achei tendencioso</button>' % (esc(k), esc(n["id"]), esc(n.get("titulo"))))
    h.append("</div>")
    h.append('<p class="aviso">Esta notícia saiu na <a href="%sler/%s/">edição de %s</a>. %s</p>' % (BASE, k, esc(data_curta(ed.get("data"))), esc(ed.get("transp") or "")))
    h.append("</article>")
    h.append(caixa_assinar("completa"))
    return "".join(h)


def v_contexto(slug, cx):
    h = ['<article class="folha pag">', '<span class="etq">Contexto</span>', "<h1>%s</h1>" % esc(cx.get("titulo"))]
    if cx.get("frase"):
        h.append('<p class="fina">%s</p>' % esc(cx["frase"]))
    h.append('<p class="meta"><span class="selo">%s</span><span>Atualizado em %s</span></p>' % (esc(cx.get("selo") or "Checado"), esc(cx.get("atualizado") or "")))
    if cx.get("linha"):
        h.append('<h2>Como chegamos aqui</h2><ol class="tempo">%s</ol>' % "".join('<li><span class="d">%s</span><span>%s</span></li>' % (esc(x.get("d")), esc(x.get("t"))) for x in cx["linha"]))
    if cx.get("termos"):
        h.append('<h2>Termos que você vai ver</h2><dl class="gloss">%s</dl>' % "".join("<div><dt>%s</dt><dd>%s</dd></div>" % (esc(x.get("t")), esc(x.get("d"))) for x in cx["termos"]))
    for chave, nome in (("quem", "Quem é quem"), ("disputa", "O que está em disputa"), ("observar", "O que observar")):
        if cx.get(chave):
            h.append("<h2>%s</h2><ul>%s</ul>" % (nome, "".join("<li>%s</li>" % rich(x) for x in cx[chave])))
    if cx.get("fontes"):
        h.append('<h2>Fontes</h2><ul class="fonte">%s</ul>' % "".join('<li><a href="%s" target="_blank" rel="noopener">%s</a></li>' % (link(f.get("u")), esc(f.get("t"))) for f in cx["fontes"]))
    h.append('<div class="acoes">%s<button class="btn" type="button" data-avaliar="" data-alvo="cx-%s" data-titulo="%s">Achei tendencioso</button></div>'
             % (botao_compartilhar(cx.get("titulo"), cx.get("frase"), SITE + "/ler/contexto/%s/" % slug), esc(slug), esc(cx.get("titulo"))))
    h.append('<p class="aviso">O Contexto explica do zero como chegamos até aqui e não traz fato novo. É escrito por IA e checado contra as fontes acima.</p></article>')
    h.append(caixa_assinar("contexto", "Entenda as notícias todo dia", "Toda edição do Contexto traz o fato, por que importa e a fonte. Grátis."))
    return "".join(h)


def v_arquivo(eds):
    itens = "".join('<a class="item" href="%sler/%s/"><span class="rotulo">%s</span><h3>%s</h3><span class="sub">%s</span></a>' % (
        BASE, esc(e["_id"]), esc(e.get("dia") or data_curta(e.get("data"))), esc((e.get("trinta") or [""])[0]), esc(" · ".join(n.get("titulo", "") for s in e.get("secoes") or [] for n in (s.get("notas") or [])[:1])))
        for e in eds)
    return '<div class="folha"><h1>Arquivo</h1><div class="lista">%s</div></div>%s' % (itens, caixa_assinar("arquivo"))


def v_assinar(ultima):
    previa = ""
    if ultima and ultima.get("trinta"):
        previa = '<section class="folha previa"><span class="rotulo">A edição de %s, em 30 segundos</span><ol class="trinta">%s</ol><a class="btn" href="%sler/">Ler a edição inteira</a></section>' % (
            esc(data_curta(ultima.get("data"))), "".join("<li><span>%s</span></li>" % esc(t) for t in ultima["trinta"]), BASE)
    return """<section class="heroi">
<span class="etq">Newsletter grátis</span>
<h1>O que importa hoje e por quê, em 5 minutos.</h1>
<p class="fina">Mundo, Brasil e Inovação no seu e-mail, sem torcida e sem ruído.</p>
{caixa}
</section>
{previa}
<section class="folha blocos">
<h2>O que você recebe</h2>
<ul>
<li><b>Em 30 segundos:</b> as 3 notícias do dia, uma linha cada, para quem só tem um minuto.</li>
<li><b>Mundo, Brasil e Inovação:</b> o fato, por que importa e a fonte, em até 120 palavras por nota.</li>
<li><b>Número do dia:</b> um dado checado que explica o momento.</li>
<li><b>Contexto:</b> para cada tema difícil, uma página que explica do zero como chegamos até aqui.</li>
</ul>
<h2>Como fazemos</h2>
<ul>
<li><b>Sem lado:</b> verbos neutros, os dois lados com o mesmo espaço e opinião só entre aspas, com nome.</li>
<li><b>Com fonte:</b> todo número tem fonte e data, e cada notícia precisa de duas fontes independentes.</li>
<li><b>Escrito por IA, com controle:</b> a redação é feita por inteligência artificial e diz isso em toda edição. Antes de sair, cada edição passa por checagem, por uma Ouvidoria de neutralidade e pela aprovação de uma pessoa.</li>
<li><b>Erro corrigido em público:</b> a seção Corrigimos mostra o que estava errado e o que é o certo.</li>
</ul>
<h2>Perguntas rápidas</h2>
<dl class="gloss">
<div><dt>Quanto custa?</dt><dd>Nada.</dd></div>
<div><dt>Quem escreve?</dt><dd>Uma redação de agentes de IA, com checagem e Ouvidoria. Nenhuma pessoa escreve as notas, e a gente não finge que escreve.</dd></div>
<div><dt>Vocês têm lado político?</dt><dd>Não. Se achar uma nota tendenciosa, o botão "Achei tendencioso" em cada edição vai direto para a Ouvidoria.</dd></div>
<div><dt>Quando começa?</dt><dd>As edições já saem aqui no site toda manhã. O envio por e-mail começa em breve, e quem se inscrever agora recebe a primeira.</dd></div>
<div><dt>Vou receber spam?</dt><dd>Não. Só a edição, e o e-mail não é vendido nem repassado.</dd></div>
</dl>
</section>
{caixa2}""".format(caixa=caixa_assinar("assinar-topo", "Assine grátis", "Deixe o e-mail e receba a primeira edição."), previa=previa,
                   caixa2=caixa_assinar("assinar-fim", "Assinar grátis", "Leva 10 segundos."))


def v_privacidade():
    return """<article class="folha pag">
<h1>Privacidade</h1>
<p>O Contexto pede só o seu e-mail, e só para enviar a newsletter.</p>
<h2>O que guardamos</h2>
<ul><li>O e-mail que você digitou e a data da inscrição.</li><li>Um código de indicação, para saber quem trouxe novos leitores.</li><li>A página em que você se inscreveu, para saber o que funciona.</li></ul>
<h2>O que não fazemos</h2>
<ul><li>Não vendemos nem repassamos o seu e-mail.</li><li>Não usamos cookies de rastreamento nem anúncios neste site.</li></ul>
<h2>Onde fica</h2>
<p>A lista fica num banco de dados da Supabase, sem leitura pública. Quando o envio por e-mail começar, ela passa para a plataforma de envio da newsletter.</p>
<h2>Como sair</h2>
<p>Todo e-mail do Contexto terá um link para cancelar com um clique. Antes disso, peça a remoção pelo botão abaixo, informando o e-mail.</p>
<div class="acoes"><button class="btn" type="button" data-avaliar="" data-alvo="privacidade" data-titulo="Privacidade" data-tipo="Privacidade">Pedir remoção do meu e-mail</button></div>
<p class="aviso">Base legal: consentimento, conforme a Lei Geral de Proteção de Dados (Lei 13.709/2018).</p>
</article>"""


# ---------- Feed e sitemap ----------

def rfc822(iso, data):
    try:
        d = datetime.fromisoformat((iso or "").replace("Z", "+00:00"))
    except ValueError:
        d = datetime.fromisoformat(data + "T09:00:00+00:00")
    return format_datetime(d.astimezone(timezone.utc), usegmt=True)


def feed(eds):
    itens = []
    for e in eds[:30]:
        corpo = "".join("<p>• %s</p>" % esc(t) for t in e.get("trinta") or [])
        for s in e.get("secoes") or []:
            for n in s.get("notas") or []:
                corpo += "<h3>%s</h3><p>%s</p><p><b>Por que importa:</b> %s</p>" % (esc(n.get("titulo")), esc(n.get("texto")), esc(n.get("pq")))
        itens.append("<item><title>Contexto de %s</title><link>%s/ler/%s/</link><guid isPermaLink=\"true\">%s/ler/%s/</guid><pubDate>%s</pubDate><description>%s</description></item>" % (
            esc(e.get("dia") or e.get("data")), SITE, e["_id"], SITE, e["_id"], rfc822(e.get("geradaEm"), e.get("data")), esc(corpo)))
    return ('<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0"><channel><title>Contexto</title><link>%s/ler/</link>'
            "<description>O que importa hoje e por quê, em 5 minutos. Mundo, Brasil e Inovação.</description><language>pt-BR</language>%s</channel></rss>\n") % (SITE, "".join(itens))


def main():
    lista = ler_json("edicoes", "index.json")
    eds = []
    for it in lista:
        try:
            e = ler_json("edicoes", it["id"] + ".json")
        except (OSError, ValueError):
            continue
        e["_id"] = it["id"]
        eds.append(e)
    eds.sort(key=lambda e: (str(e.get("data")), str(e.get("geradaEm") or "")), reverse=True)
    publicas = [e for e in eds if not e["_id"].startswith("piloto")]
    contextos = {}
    for nome in sorted(os.listdir(os.path.join(RAIZ, "contextos"))):
        if nome.endswith(".json"):
            contextos[nome[:-5]] = ler_json("contextos", nome)

    # Recria as pastas geradas para não sobrar página de edição apagada.
    for pasta in os.listdir(os.path.join(RAIZ, "ler")) if os.path.isdir(os.path.join(RAIZ, "ler")) else []:
        p = os.path.join(RAIZ, "ler", pasta)
        if os.path.isdir(p):
            shutil.rmtree(p)
    urls = []

    for e in eds:
        desc = resumo(" ".join(e.get("trinta") or []) or e.get("abre"))
        gravar("ler/%s/index.html" % e["_id"], pagina("ler/%s/" % e["_id"], "Contexto de %s" % (e.get("dia") or e.get("data")), desc,
                                                       v_edicao(e, contextos), "article", e.get("geradaEm"), indexar=e in publicas))
        if e in publicas:
            urls.append(("ler/%s/" % e["_id"], e.get("data")))
        for s in e.get("secoes") or []:
            for n in s.get("notas") or []:
                if n.get("completa"):
                    rel = "ler/%s/%s/" % (e["_id"], n["id"])
                    gravar(rel + "index.html", pagina(rel, n.get("titulo"), resumo(n["completa"].get("fina") or n.get("texto")),
                                                      v_completa(e, n, s.get("nome"), contextos), "article", e.get("geradaEm"), indexar=e in publicas))
                    if e in publicas:
                        urls.append((rel, e.get("data")))
    for slug, cx in contextos.items():
        rel = "ler/contexto/%s/" % slug
        gravar(rel + "index.html", pagina(rel, cx.get("titulo"), resumo(cx.get("frase")), v_contexto(slug, cx), "article"))
        urls.append((rel, cx.get("atualizado")))

    ultima = publicas[0] if publicas else (eds[0] if eds else None)
    if ultima:
        gravar("ler/index.html", pagina("ler/%s/" % ultima["_id"], "Contexto: o que importa hoje e por quê", resumo(" ".join(ultima.get("trinta") or [])),
                                        v_edicao(ultima, contextos, "Hoje · " + (ultima.get("dia") or ""))))
    gravar("ler/arquivo/index.html", pagina("ler/arquivo/", "Arquivo", "Todas as edições do Contexto.", v_arquivo(publicas)))
    gravar("assinar/index.html", pagina("assinar/", "Contexto: o que importa hoje e por quê, em 5 minutos",
                                        "Newsletter grátis sobre Mundo, Brasil e Inovação, sem torcida e sem ruído. Escrita por IA e checada antes de sair.", v_assinar(ultima)))
    gravar("privacidade/index.html", pagina("privacidade/", "Privacidade", "Como o Contexto usa o seu e-mail.", v_privacidade()))
    urls += [("assinar/", None), ("ler/", ultima and ultima.get("data")), ("ler/arquivo/", None)]

    gravar("feed.xml", feed(publicas))
    gravar("sitemap.xml", '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">%s</urlset>\n' % "".join(
        "<url><loc>%s/%s</loc>%s</url>" % (SITE, u, "<lastmod>%s</lastmod>" % d[:10] if d and re.match(r"\d{4}-\d{2}-\d{2}", d) else "") for u, d in urls))
    gravar("robots.txt", "User-agent: *\nAllow: /\nSitemap: %s/sitemap.xml\n" % SITE)
    print("Site gerado: %d edições, %d contextos, %d páginas no sitemap." % (len(eds), len(contextos), len(urls)))


if __name__ == "__main__":
    main()
