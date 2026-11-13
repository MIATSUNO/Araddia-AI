# Araddia

Araddia é um projeto de inteligência artificial criado por [Miatsuno](https://github.com/MIATSUNO), com aplicativo Android para conversar com a personagem Araddia.

## Aplicativo Android

O app oferece modos local, por API e híbrido, além de recursos de voz, anexos, histórico, memória e busca na web. O modo local depende de um modelo compatível; o modo por API requer a configuração do provedor e do modelo.

## Projeto

- `app/`: aplicativo Android em Kotlin.
- `dataset/`: exemplos de diálogo da persona.
- `tools/`: ferramentas e orientações para preparar dados de treinamento.
- `.github/workflows/`: automação de compilação do aplicativo.

## Compilar

O projeto usa JDK 17, Android SDK 35 e Gradle 8.9. Com o Android SDK configurado:

```sh
gradle :app:assembleDebug
```
