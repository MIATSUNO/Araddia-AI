# Araddia 2.1

## Compilar
- **Android Studio:** File > Open nesta pasta > Run (JDK 17).
- **Sem PC:** suba a pasta para um repositório GitHub; Actions > "Build APK" > Artifacts. (O APK debug já sai assinado com a chave de debug e instala normalmente.)
- Se o Gradle não achar `tasks-genai:0.10.26`, use a versão mais recente em `app/build.gradle.kts`.

## Novidades
Vídeo de fundo em loop (res/raw/bg_araddia.mp4, comprimido de 35 MB para 11 MB), visual preto/laranja/creme, várias conversas, markdown e blocos de código com copiar, anexos (imagem/PDF/texto com OCR), voz (entrada e saída), busca web (Tavily ou Wikipédia), modo Híbrido (API com fallback local), template Gemma/ChatML, histórico por orçamento, memória automática e editável, tudo criptografado no Keystore.

## Treino
Veja `tools/FINETUNE.md`.
