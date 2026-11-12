#!/usr/bin/env python3
"""Gera diálogos da persona Araddia usando um modelo grande via API compatível com OpenAI.
Uso: API_KEY=... BASE=https://openrouter.ai/api/v1 MODEL=<id> python3 tools/gen_dataset.py 600 > dataset/gerado.jsonl
Depois REVISE à mão (descarte o que não soar como a Araddia) e junte ao araddia_persona.jsonl."""
import json, os, re, sys, random, urllib.request

N = int(sys.argv[1]) if len(sys.argv) > 1 else 200
BASE, KEY, MODEL = os.environ["BASE"], os.environ["API_KEY"], os.environ["MODEL"]
src = open(os.path.join(os.path.dirname(__file__), "..", "app/src/main/java/app/araddia/Persona.kt"), encoding="utf-8").read()
SYSTEM = re.search(r'SYSTEM = """(.*?)"""', src, re.S).group(1)
TOPICS = ["saúde e hospital", "trabalho", "estudos e provas", "amor e términos", "programação", "dinheiro", "família",
          "tédio", "medo e ansiedade", "conquistas", "pedidos de ajuda técnica", "curiosidades", "filosofia", "tarefas do dia a dia",
          "tristeza (responder SEM deboche, com acolhimento e CVV 188 se houver risco)"]

def ask(topic):
    body = {"model": MODEL, "temperature": 1.0, "messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": f"Invente uma mensagem realista de um usuário brasileiro sobre: {topic}. "
         "Responda SOMENTE com JSON: {\"user\": \"...\", \"araddia\": \"...\"} onde araddia é a sua resposta no tom do personagem."}]}
    req = urllib.request.Request(BASE.rstrip("/") + "/chat/completions", json.dumps(body).encode(),
                                 {"Content-Type": "application/json", "Authorization": "Bearer " + KEY})
    txt = json.load(urllib.request.urlopen(req, timeout=90))["choices"][0]["message"]["content"]
    return json.loads(re.search(r"\{.*\}", txt, re.S).group(0))

for _ in range(N):
    try:
        d = ask(random.choice(TOPICS))
        print(json.dumps({"messages": [{"role": "user", "content": d["user"]}, {"role": "assistant", "content": d["araddia"]}]}, ensure_ascii=False))
    except Exception as e:
        print("erro:", e, file=sys.stderr)
