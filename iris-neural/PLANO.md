# IRIS Neural — modelo próprio de linguagem

## Objetivo
Desenvolver um modelo causal Transformer próprio da IRIS, com aproximadamente 10 milhões de parâmetros, para inferência offline no Android.

## Arquitetura implementada no código inicial
- Tokenizador inicial: bytes UTF-8, 256 símbolos
- Embedding: 368 dimensões
- 6 camadas Transformer causais
- 8 cabeças de atenção
- Feed-forward: 1479 dimensões
- Contexto máximo inicial: 256 tokens
- Embedding e projeção de saída com pesos compartilhados
- Contagem calculada da arquitetura padrão: 10.000.000 parâmetros

A arquitetura está definida em `iris-neural/model.py`. A configuração pode evoluir após os testes de treinamento e desempenho.

- Filtro temporal causal local de 662 pesos, complementar à atenção

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

## Prioridade de treinamento: conversacao cotidiana

A IRIS continua com **10.000.000 de parametros compartilhados**. Nao ha divisao fixa de 1 milhao de pesos para um tema: o treinamento de conversacao ajusta a rede inteira.

Meta de dados, **nao de parametros**: construir gradualmente ate 1.000.000 de dialogos de alta qualidade, originais/licenciados, com perguntas, respostas e turnos encadeados. Essa quantidade e uma meta futura, nao dados ja existentes.

Categorias planejadas: cumprimentos, rotina, familia, amizades, trabalho, escola, estudos, tecnologia, compras, transporte, alimentacao, lazer, sentimentos, planejamento, explicacoes, duvidas, correcoes, despedidas e mudanca de assunto. Separar treino/validacao/teste por conversa e por grupo de semelhanca para reduzir vazamento.

Qualidade: rejeitar duplicatas exatas; revisar parafrases e intencoes redundantes; nao multiplicar frases artificialmente para inflar a contagem; evitar respostas inventadas; medir capacidade de continuar conversas de varios turnos. Os exemplos atuais sao pares curtos e **ainda nao** ensinam memoria conversacional longa. O contexto atual de 256 bytes limita fortemente dialogos longos; ampliar contexto exige avaliar memoria e desempenho.

Proxima etapa tecnica: corpus estruturado por dialogo, verificador de duplicatas semanticas, particionamento treino/validacao/teste e avaliacao em conversas ineditas antes de qualquer afirmacao de aprendizado.
