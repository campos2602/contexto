"""Temporário: grava amostras com vozes de podcast (Gemini e ElevenLabs) usando as chaves guardadas nos segredos do GitHub."""
import base64, json, os, subprocess, sys, urllib.request, urllib.error

TXT = open(os.path.join(os.path.dirname(__file__), "texto.txt"), encoding="utf-8").read()
OUT = os.path.dirname(os.path.abspath(__file__))
ESTILO = ("Você é a voz do Contexto, um podcast diário de notícias brasileiro. Leia o texto abaixo exatamente como está, palavra por palavra, "
          "em português do Brasil, com sotaque brasileiro neutro, ritmo de conversa, tom próximo e calmo de apresentador de podcast, "
          "sem soar robótico nem de locutor de rádio. Faça pausas naturais entre os parágrafos.\n\n")


def post(url, corpo, cab):
    req = urllib.request.Request(url, data=json.dumps(corpo).encode(), headers=dict({"Content-Type": "application/json"}, **cab))
    with urllib.request.urlopen(req, timeout=180) as r:
        return r.read()


def gemini(chave):
    modelos = ["gemini-2.5-pro-preview-tts", "gemini-2.5-flash-preview-tts"]
    vozes = ["Sulafat", "Achird", "Kore", "Charon", "Aoede", "Algieba"]
    for voz in vozes:
        for m in modelos:
            try:
                r = json.loads(post("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent" % m,
                                    {"contents": [{"parts": [{"text": ESTILO + TXT}]}],
                                     "generationConfig": {"responseModalities": ["AUDIO"],
                                                          "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": voz}}}}},
                                    {"x-goog-api-key": chave}))
                pcm = base64.b64decode(r["candidates"][0]["content"]["parts"][0]["inlineData"]["data"])
                saida = os.path.join(OUT, "gemini-%s.mp3" % voz.lower())
                subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "s16le", "-ar", "24000", "-ac", "1", "-i", "-", "-b:a", "96k", saida], input=pcm, check=True)
                print("ok", voz, m)
                break
            except Exception as e:
                msg = e.read()[:300] if isinstance(e, urllib.error.HTTPError) else e
                print("falhou", voz, m, msg)


def eleven(chave):
    # Vozes da biblioteca com sotaque brasileiro, se a conta tiver; senão, as vozes padrão no modelo multilíngue.
    try:
        vs = json.loads(urllib.request.urlopen(urllib.request.Request("https://api.elevenlabs.io/v1/shared-voices?language=pt&accent=brazilian&page_size=6&sort=usage_character_count_1y",
                                                                      headers={"xi-api-key": chave}), timeout=60).read())["voices"]
        ids = [(v["voice_id"], v["name"], v.get("public_owner_id")) for v in vs][:4]
    except Exception as e:
        print("lista de vozes falhou", e); ids = []
    for vid, nome, dono in ids:
        try:
            if dono:
                post("https://api.elevenlabs.io/v1/voices/add/%s/%s" % (dono, vid), {"new_name": "Contexto " + nome}, {"xi-api-key": chave})
        except Exception as e:
            print("não adicionou", nome, e)
        try:
            mp3 = post("https://api.elevenlabs.io/v1/text-to-speech/%s?output_format=mp3_44100_96" % vid,
                       {"text": TXT, "model_id": "eleven_multilingual_v2", "voice_settings": {"stability": 0.45, "similarity_boost": 0.8, "style": 0.2}},
                       {"xi-api-key": chave, "Accept": "audio/mpeg"})
            open(os.path.join(OUT, "elevenlabs-%s.mp3" % "".join(c for c in nome.lower() if c.isalnum())[:20]), "wb").write(mp3)
            print("ok", nome)
        except Exception as e:
            print("falhou", nome, e.read()[:300] if isinstance(e, urllib.error.HTTPError) else e)


if os.environ.get("GEMINI_API_KEY"):
    gemini(os.environ["GEMINI_API_KEY"])
if os.environ.get("ELEVENLABS_API_KEY"):
    eleven(os.environ["ELEVENLABS_API_KEY"])
if not os.environ.get("GEMINI_API_KEY") and not os.environ.get("ELEVENLABS_API_KEY"):
    print("Nenhuma chave nos segredos ainda."); sys.exit(0)
