# IRIS Neural — implementação inicial

Esta pasta contém o início do modelo causal Transformer próprio da IRIS. **Nenhum peso treinado é fornecido**. O treinamento deve utilizar apenas corpus autorizado.

## Testes

```bash
python iris-neural/test_model.py
```

## Uso (ambiente Python para treinamento)
```bash
python -m pip install torch
python iris-neural/train.py --corpus corpus.txt --out checkpoints --steps 100
python iris-neural/train.py --corpus corpus.txt --out checkpoints --steps 100 --resume
```

O corpus deve ser texto UTF-8 em português, com conteúdo autorizado. O tokenizer inicial é por bytes UTF-8 (256 tokens), reversível e independente de terceiros. A arquitetura padrão possui **10.000.000 parâmetros** (dimensão 368, 6 camadas, 8 cabeças, FFN 1479, contexto 256). O tokenizer de subpalavras será uma etapa posterior. O script imprime a contagem real de parâmetros.

**Importante:** o treinamento Python é uma etapa de desenvolvimento, não um runtime Android. A inferência Kotlin/Android e a exportação de pesos ainda não foram implementadas. Não gerar APK como se o modelo estivesse pronto.

O modelo usa um filtro temporal causal depthwise de dois passos em 331 canais (662 pesos treináveis), sem pesos externos. Esses pesos afetam efetivamente a geração e não são parâmetros decorativos.

## Experimento inicial de raciocinio

Corpus original de demonstracao: `dados/raciocinio_inicial.txt`. Contem apenas alguns exemplos de aritmetica, logica e verificacao, suficientes para testar o pipeline, **nao** para ensinar raciocinio geral. Execute `python iris-neural/train.py --corpus iris-neural/dados/raciocinio_inicial.txt --out checkpoints --steps 100`. Um checkpoint so deve ser chamado de treinado depois que essa execucao realmente terminar e as avaliacoes forem realizadas.

## Conversacao sem duplicatas

Os 40 dialogos originais estao em `dados/conversas_originais.json`. Prepare o texto de treino com:

```bash
python iris-neural/prepare_dialogues.py --source iris-neural/dados/conversas_originais.json --out iris-neural/dados/conversas_treino.txt
python iris-neural/train.py --corpus iris-neural/dados/conversas_treino.txt --out checkpoints-conversa --steps 100
```

O preparador remove perguntas identicas apos normalizacao e interrompe se houver respostas conflitantes. O treinamento grava o hash SHA-256 do corpus no checkpoint e rejeita `--resume` com corpus alterado. Isso evita repeticao acidental de exemplos no arquivo, mas nao impede que um mesmo exemplo seja amostrado varias vezes durante o treinamento por etapas (isso e normal em aprendizado de maquina). Os exemplos sao apenas demonstrativos; nao garantem conversacao competente.
