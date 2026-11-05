# IRIS Neural — treinamento do zero no Galaxy A55

Objetivo: modelo causal Transformer de aproximadamente 10 milhões de parâmetros, sem pesos ou tokenizer Qwen.

## Regras do projeto
- Pesos inicializados aleatoriamente; tokenizer treinado apenas com corpus autorizado em português.
- Treinamento e inferência locais no Android ARM64; nenhum serviço remoto de IA.
- Treinamento incremental em blocos pequenos, checkpoint persistente e pausa por temperatura/bateria.
- Voz Inês é componente separado e deve passar em teste de síntese real.
- A qualidade linguística não é garantida pela contagem de parâmetros.

## Arquitetura inicial proposta
- Vocabulário: 8192 tokens
- Embedding: 256 dimensões
- 6 camadas Transformer decoder
- 8 cabeças de atenção
- Feed-forward: 1024 dimensões
- Contexto inicial: 256 tokens
- Pesos de embedding e saída compartilhados
- Aproximadamente 10 milhões de parâmetros (a contagem exata será aferida pelo código executável)

## Critérios para substituir o motor antigo
1. Inicializar pesos e tokenizer próprios no dispositivo.
2. Treinar em corpus português consentido e salvar/restaurar checkpoint.
3. Gerar texto por inferência sem Qwen, GGUF ou llama.cpp.
4. Medir RAM, temperatura e tokens/s no A55.
5. Remover todas as referências e dependências Qwen do APK e do workflow.
6. Compilar e testar a voz Inês separadamente.

**Status:** especificação inicial; o treinamento ainda não começou. Não apresentar APK como pronto até que os testes acima passem.
