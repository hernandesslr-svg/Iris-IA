# Iris IA — Android nativo

Este repositório agora é dedicado à **Iris**. O código Android Kotlin está preservado no arquivo `Iris-Android-preparado-para-GitHub.zip`; o workflow extrai o projeto e tenta compilar um APK de teste.

## Estado real
- Interface Android, memória SQLite, respostas locais e recursos de voz: código de protótipo disponível no ZIP.
- IRIS Neural Core: **estrutura preparada, sem inferência neural funcional**.
- Pesos Qwen3 1.7B GGUF: **não incluídos**.
- APK com IA neural offline: **ainda não disponível**.

## Compilação
Abra **Actions → Iris Android - compilar prototipo → Run workflow**. O artefato esperado é `iris-android-prototipo-sem-neural`. A compilação precisa ser confirmada no GitHub Actions antes de instalar.

O código antigo da Nara e seu workflow foram removidos da árvore atual. O histórico Git permanece disponível para recuperação. Não coloque modelos GGUF gigantes no Git.
