# Contexto

App de leitura do Contexto, newsletter sobre Mundo, Brasil e Inovação. Instala no celular como app (PWA) e é publicado pelo GitHub Pages.

- `edicoes/index.json`: lista das edições, a mais recente primeiro.
- `edicoes/<id>.json`: uma edição, com as notas e a Notícia completa de cada uma.
- `contextos/<tema>.json`: páginas de Contexto, reaproveitadas entre edições.
- Comentários ficam na tabela `contexto_comentarios` da Supabase.

Uma tarefa agendada grava a edição nova todo dia de madrugada.

## Minha edição (categorias por leitor)

O catálogo de categorias fica em `categorias.json` (Brasil, Mundo e Inovação de sempre, mais Economia, Política, Esportes, Ciência, Saúde, Meio ambiente, Cultura, Entretenimento, Celebridades, Educação e Trabalho e carreira). Cada seção de uma edição leva `"categoria"` com o id do catálogo. `categorias.js`, carregado pelo app e pelo site, deixa o leitor escolher quais categorias quer e em que ordem (botão "Personalizar"); a escolha fica no celular dele, e as seções que ele não escolheu ficam recolhidas em "Outras seções de hoje". Quem assina tem a escolha guardada também na coluna `categorias` de `contexto_inscritos` (parte "Minha edição" de `supabase/contexto_inscritos.sql`), para o e-mail personalizado. Para criar uma categoria, é só acrescentar um item em `categorias.json`.

O leitor também escolhe quantas notícias quer de cada categoria (1 a 5; padrão no campo `qtd` do catálogo). A edição traz as notas de cada seção da mais para a menos importante; o app mostra as N primeiras e guarda o resto em "Ver mais".

**O que você não pode deixar de saber hoje**: o campo `essencial` da edição (`[{"id": "b1", "porque": "..."}]`) lista as 2 a 4 notas que o editor-chefe destacou. Elas saem da seção delas e abrem a edição para todo leitor, com a frase "Por que não dá para perder".

## App Android

A pasta `android/` tem um app que abre o site do Contexto em tela cheia. A cada mudança nela, o GitHub Actions gera o `contexto.apk` e publica na página Releases do repositório.

### Lembrete diário

O sino no topo do app abre a folha "Lembrete diário": liga ou desliga, escolhe o horário, o que tocar ao abrir o aviso (resumo "Em 30 segundos" ou a edição inteira) e se o app deve deixar de cobrar quando a pessoa já ouviu no dia. Quem agenda é o Android (`Lembrete.java` e `LembreteReceiver.java`), então o aviso chega com o app fechado e volta a valer depois de reiniciar o celular. Tocar no aviso abre o app já tocando o áudio (`lembrete.js`); o botão "Lembrar em 30 min" adia. No navegador a folha só explica que o lembrete é do app Android. Em pull requests o APK de teste fica nos artefatos da execução do workflow.

## Site público e kit de redes

O app acima é o do editor: mostra o status de aprovação e o parecer da Ouvidoria. Para os leitores existe um site público gerado a partir dos mesmos arquivos, por `ferramentas/gerar_site.py`:

- `ler/`: a edição mais recente; `ler/<id>/` cada edição; `ler/<id>/<nota>/` a Notícia completa; `ler/contexto/<tema>/` as páginas de Contexto; `ler/arquivo/`.
- `assinar/`: página de inscrição, pensada para o link da bio. Os e-mails vão para a tabela `contexto_inscritos` da Supabase (`supabase/contexto_inscritos.sql`), com código de indicação por leitor.
- `feed.xml` e `sitemap.xml`, para leitores de RSS e para o Google.
- Cada nota tem botão de compartilhar (WhatsApp ou o menu do celular) e "Achei tendencioso", que cai nos comentários da redação marcado como `[leitor]`.

`ferramentas/instagram.js` gera em `social/<id>/` o carrossel do Instagram (1080x1350), o story do Número do dia, um vídeo vertical para Reels, TikTok e Shorts, a legenda e o texto do WhatsApp. O vídeo não fica no repositório: o workflow publica o do dia na versão `kit-redes` da página Releases. A página `social/` mostra o kit do dia para salvar e postar. `node ferramentas/instagram.js lancamento` gera os 3 posts fixos de abertura do perfil.

O workflow `.github/workflows/site.yml` roda os dois scripts a cada edição gravada na main e grava o resultado de volta. O endereço do site fica em `ferramentas/site.json`; quando houver domínio próprio, é só trocar ali.

## Ouvir as notícias

Toda nota, o "Em 30 segundos", a Notícia completa e cada página de Contexto têm um botão **Ouvir**, e o topo da edição tem **Ouvir a edição**, que narra tudo em sequência, como um podcast. O áudio lê exatamente o texto publicado (sem as fontes e os botões). O código fica em `ler/ouvir.js`, usado pelo app e pelo site público.

- No navegador e no app instalado pelo navegador, usa a voz em português do próprio aparelho.
- No app Android, usa o leitor de voz do celular por uma ponte nativa (`ContextoVoz` em `MainActivity.java`), que continua falando com a tela apagada.
- Se um trecho tiver `data-audio` com o endereço de um MP3, o app toca o MP3 no lugar da voz do aparelho.

A rotina diária não precisa fazer nada: o áudio é montado na hora a partir do texto da edição.
