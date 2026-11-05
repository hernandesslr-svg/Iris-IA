# IRIS Neural — modelo próprio de linguagem

## Objetivo
Desenvolver um modelo causal Transformer próprio da IRIS, com aproximadamente 10 milhões de parâmetros, para inferência offline no Android.

## Arquitetura implementada no código inicial
- Tokenizador inicial: bytes UTF-8, 256 símbolos
- Embedding: 368 dimensões
- 6 camadas Transformer causais
- 8 cabeças de atenção
- Feed-forward: 1472 dimensões
- Contexto máximo inicial: 256 tokens
- Embedding e projeção de saída com pesos compartilhados
- Contagem calculada da arquitetura padrão: 9.968.384 parâmetros

A arquitetura está definida em `iris-neural/model.py`. A configuração pode evoluir após os testes de treinamento e desempenho.

## Componentes preparados
- Arquitetura de rede neural e geração de tokens
- Script de treinamento com salvamento e retomada de checkpoints
- Testes básicos do modelo
- Contrato Android `IrisLanguageEngine` para futura inferência
- Mecanismo local provisório de conversação até o modelo neural funcionar

## Etapas pendentes
1. Validar os testes automatizados em ambiente com PyTorch.
2. Preparar corpus autorizado de português brasileiro e melhorar o tokenizer.
3. Executar treinamento real, medir perda e avaliar respostas.
4. Exportar pesos e implementar inferência nativa no Android.
5. Medir memória, temperatura e velocidade no Galaxy A55.
6. Testar separadamente a síntese da voz Inês.
7. Somente depois da validação completa, liberar compilação do APK final.

## Status real
**Modelo em desenvolvimento. Não treinado. Sem pesos neurais finais e sem runtime de inferência Android funcional.** Não apresentar o aplicativo como concluído.
