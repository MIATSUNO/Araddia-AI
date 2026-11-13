# Araddia

Araddia é um projeto oficial criado por [Vinicius Gabriel Menezes da Silva (Miatsuno)](https://github.com/Miatsuno). Já apresentado ao público, o projeto pode receber melhorias e atualizações. Este repositório reúne o código do aplicativo Android e materiais relacionados à sua persona.

## Aplicativo Android

O aplicativo oferece uma interface de conversa com a persona Araddia. O código inclui modos local, por API e híbrido, além de recursos de voz, anexos, histórico, memória e busca na web. O modo local depende de um modelo compatível, obtido separadamente; o modo por API exige configuração de provedor, modelo e chave. O funcionamento depende do aparelho, do modelo e dos serviços escolhidos.

O código prevê modelos locais em formatos `.litertlm` ou `.task` por meio do MediaPipe, com modelos de prompt Gemma e ChatML. A opção ChatML/Qwen no aplicativo não comprova, por si só, a compatibilidade de um modelo nem a existência de um APK Qwen validado.

Na configuração remota, há atalhos para serviços com API compatível com OpenAI, incluindo OpenRouter, Groq e Gemini. Esses atalhos pertencem ao aplicativo Android; este repositório não contém o código do chatbot do site para comprovar sua integração. Instruções de persona no Playground da Groq não treinam o modelo nem modificam automaticamente o aplicativo.

## Estrutura

- `app/`: código Kotlin, manifesto e recursos do aplicativo Android.
- `dataset/`: exemplos de diálogos da persona em JSONL.
- `tools/`: gerador de exemplos e orientações iniciais para treinamento.
- `.github/workflows/`: fluxo de compilação do APK de depuração no GitHub Actions.
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` e `gradle/`: configuração do projeto e do Gradle.

## Compilação

O projeto usa JDK 17, Android SDK 35 e Gradle 8.9. No Android Studio, abra a pasta do repositório e configure o SDK Android. Para compilar pela linha de comando com o Gradle 8.9 instalado:

```bash
gradle :app:assembleDebug
```

O fluxo **Build APK** no GitHub Actions executa o mesmo comando. Quando a execução termina com sucesso, o APK de depuração é disponibilizado como artefato dessa execução. Uma compilação bem-sucedida não substitui a instalação e os testes em um aparelho; a assinatura de depuração também pode impedir a atualização sobre uma instalação assinada com outra chave.

A configuração atual do aplicativo declara `versionName` 2.1, `versionCode` 3, Android mínimo 8.0 (API 26) e arquitetura `arm64-v8a`. Esses dados identificam a configuração de compilação, não garantem que um APK ou recurso específico tenha sido validado ou lançado.

## Persona e treinamento

A persona usada pelo aplicativo está definida no código. O arquivo `dataset/araddia_persona.jsonl` contém exemplos iniciais, e `tools/FINETUNE.md` apresenta um caminho proposto para treinamento. Os exemplos, as instruções e um eventual adaptador LoRA não constituem, por si só, um modelo treinado e validado para o aplicativo. Treinamento, conversão e compatibilidade precisam ser verificados separadamente, respeitando as licenças dos modelos utilizados.
