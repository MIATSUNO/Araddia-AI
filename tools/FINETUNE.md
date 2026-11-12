# Treinar a Araddia (LoRA)
1. Gere 500–1000 diálogos: `tools/gen_dataset.py` (usa um modelo grande via API). Revise à mão e junte ao `dataset/araddia_persona.jsonl`.
2. No Google Colab (GPU), rode LoRA com Unsloth no `gemma-3n-E4B-it` (ou outro Gemma instruct), formato `messages` do jsonl, 2–3 épocas, lr 2e-4.
3. Faça o merge dos pesos e converta para `.litertlm`/`.task` com a ferramenta de conversão LiteRT-LM/AI Edge **da documentação atual do Google** (o processo muda entre versões).
4. No app: ⚙ > Local > cole a URL do seu modelo convertido (ou copie o arquivo para o aparelho e hospede em qualquer link direto).
Caminho sem conversão: faça o merge e sirva o modelo treinado numa API própria; use o modo API do app.
