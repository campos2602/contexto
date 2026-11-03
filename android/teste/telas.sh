#!/bin/bash
# Abre o app no emulador, passa pelas telas principais e guarda capturas e o log em ./telas.
set -x
mkdir -p telas
adb install -r contexto.apk
adb logcat -c
adb shell pm grant br.contexto.app android.permission.POST_NOTIFICATIONS || true
adb shell am start -W -n br.contexto.app/.MainActivity
sleep 15
cap() { adb exec-out screencap -p > "telas/$1.png"; }
toque() {
  # Toca no primeiro elemento cujo texto contém $1.
  adb shell uiautomator dump /sdcard/t.xml >/dev/null
  adb pull /sdcard/t.xml telas/ui.xml >/dev/null
  b=$(python3 - "$1" <<'PY'
import re,sys
x=open('telas/ui.xml',encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>', x):
    n=m.group(0)
    t=re.search(r' text="([^"]*)"',n); d=re.search(r'content-desc="([^"]*)"',n)
    if (t and sys.argv[1] in t.group(1)) or (d and sys.argv[1] in d.group(1)):
        a=list(map(int,re.findall(r'\d+',re.search(r'bounds="([^"]*)"',n).group(1))))
        print((a[0]+a[2])//2,(a[1]+a[3])//2); break
PY
)
  [ -n "$b" ] && adb shell input tap $b
  sleep 4
}
cap 01-hoje
adb shell input swipe 540 1800 540 600 300; sleep 2; cap 02-hoje-rolado
adb shell input swipe 540 1800 540 400 300; sleep 2; cap 03-hoje-rolado2
toque "Notícia completa"; sleep 3; cap 04-noticia
toque "Voltar"; toque "Arquivo"; cap 05-arquivo
toque "Ajustes"; cap 06-ajustes
adb shell input swipe 540 1800 540 500 300; sleep 2; cap 07-ajustes-rolado
toque "Hoje"; toque "Ouvir o resumo"; sleep 3; cap 08-ouvindo
adb shell am start -W -n br.contexto.app/.MainActivity
adb logcat -d > telas/logcat.txt
grep -E "FATAL|AndroidRuntime" -A20 telas/logcat.txt > telas/erros.txt || echo "Nenhum erro fatal." > telas/erros.txt
cat telas/erros.txt
