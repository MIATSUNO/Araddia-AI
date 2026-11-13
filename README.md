# Araddia

Araddia é um projeto oficial criado por [Vinicius Gabriel Menezes da Silva (Miatsuno)](https://github.com/Miatsuno). O projeto já foi publicado e pode receber melhorias e atualizações. Este repositório reúne o código do aplicativo Android e materiais relacionados à persona.

## Aplicativo Android

O app oferece conversa com a persona Araddia, com modos local, por API e híbrido, além de recursos de voz, anexos, histórico, memória e busca na web. O modo local depende de um modelo compatível, obtido separadamente; o modo por API requer configuração de provedor, modelo e chave. O comportamento depende do aparelho, do modelo e dos serviços configurados.

O código prevê modelos locais `.litertlm` ou `.task` via MediaPipe e templates de prompt Gemma e ChatML. A opção ChatML/Qwen não comprova compatibilidade de um modelo Qwen nem a existência de APK Qwen validado. Os atalhos para OpenRouter, Groq e Gemini pertencem ao app Android; este repositório não contém o código do chatbot do site para confirmar qual serviço ele usa. Instruções de persona no Playground da Groq não treinam os pesos do modelo nem modificam automaticamente o app.

## Estrutura

- `app/`: código Kotlin, manifesto, dependências e recursos do app Android.
- `dataset/`: exemplos de diálogos da persona em JSONL.
- `tools/`: gerador de exemplos e orientações iniciais para treinamento.
- `.github/workflows/`: compilação e verificações do APK de depuração no GitHub Actions.
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` e `gradle/`: configuração do projeto e do Gradle.

## Compilação e instalação de teste

O projeto usa JDK 17, Android SDK 35 e Gradle 8.9. No Android Studio, abra a pasta do repositório e configure o SDK Android. Pela linha de comando, com Gradle 8.9 instalado:

```bash
gradle :app:assembleDebug
```

O APK local é gerado em `app/build/outputs/apk/debug/app-debug.apk`. O workflow **Build APK** compila o mesmo app e verifica o alinhamento ZIP e a assinatura antes de enviar o artefato.

**Download do Actions:** baixe o artefato `araddia-debug-apk` e extraia o arquivo `app-debug.apk` de dentro do ZIP. Não renomeie o ZIP para `.apk` nem tente instalar o ZIP. Confira se o download terminou por completo.

O build `debug` usa o sufixo de pacote `.debug` e nomeia a versão como `-debug`. Isso permite instalá-lo ao lado do app normal e evita apresentar uma assinatura de depuração variável como atualização do app normal. Cada build do GitHub Actions pode usar uma chave de depuração diferente; portanto, não se deve contar com a atualização de uma instalação debug anterior. A versão de teste não é uma release e não foi validada em todos os aparelhos.

A configuração atual declara Android mínimo 8.0 (API 26), arquitetura `arm64-v8a`, `versionName` 2.2 e `versionCode` 4. Esses valores descrevem a configuração do código, não certificam que um APK específico tenha sido testado ou lançado.

## Persona e treinamento

A persona enviada ao modelo está em `app/src/main/java/app/araddia/Persona.kt`. O arquivo `dataset/araddia_persona.jsonl` contém exemplos iniciais; `tools/FINETUNE.md` e `tools/gen_dataset.py` documentam um caminho proposto para preparação de dados e treinamento. Instruções e exemplos não treinam automaticamente um modelo. Treinamento, conversão para formato compatível e validação no app são etapas separadas, que devem respeitar as licenças dos modelos.
