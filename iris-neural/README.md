# IRIS Neural — implementação inicial

Esta pasta contém o início do modelo causal Transformer próprio da IRIS. **Nenhum peso treinado é fornecido**. O treinamento deve utilizar apenas corpus autorizado.

## Uso (ambiente Python para treinamento)
```bash
python -m pip install torch
python iris-neural/train.py --corpus corpus.txt --out checkpoints --steps 100
python iris-neural/train.py --corpus corpus.txt --out checkpoints --steps 100 --resume
```

O corpus deve ser texto UTF-8 em português, com conteúdo autorizado. O tokenizer inicial é por bytes UTF-8 (256 tokens), reversível e independente de terceiros. A configuração inicial é deliberadamente pequena para permitir testar o pipeline; o alvo de ~10M parâmetros e um tokenizer de subpalavras serão etapas posteriores. O script imprime a contagem real de parâmetros.

**Importante:** o treinamento Python é uma etapa de desenvolvimento, não um runtime Android. A inferência Kotlin/Android e a exportação de pesos ainda não foram implementadas. Não gerar APK como se o modelo estivesse pronto.
