# Contexto

App de leitura do Contexto, newsletter sobre Mundo, Brasil e Inovação. Instala no celular como app (PWA) e é publicado pelo GitHub Pages.

- `edicoes/index.json`: lista das edições, a mais recente primeiro.
- `edicoes/<id>.json`: uma edição, com as notas e a Notícia completa de cada uma.
- `contextos/<tema>.json`: páginas de Contexto, reaproveitadas entre edições.
- Comentários ficam na tabela `contexto_comentarios` da Supabase.

Uma tarefa agendada grava a edição nova todo dia de madrugada.
