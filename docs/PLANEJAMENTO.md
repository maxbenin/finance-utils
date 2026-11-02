# Planejamento

## Objetivo

Um gestor financeiro independente, com painel por perfil (pessoal e familiar) que mostra quanto
entra, quanto sai, os gastos fixos, variáveis e supérfluos e o que dá para reduzir, com foco em
sobrar dinheiro.

O **Dinzo** deixa de ser a fonte da verdade e vira uma das formas de trazer dados:
carga inicial, importação em lote, conferência e descoberta de contas e cartões novos. Os
lançamentos também podem vir de **arquivos** (OFX, CSV, XLSX, PDF) ou ser feitos **à mão no app**.
O Dinzo segue em uso até o app estar estável o bastante para trocar por uma ferramenta que fale
com o Open Finance diretamente.

## Decisões tomadas

| Tema | Decisão |
|---|---|
| Fonte da verdade | O banco de dados do app. Contas, cartões e lançamentos têm id próprio. O id do Dinzo (ou a linha do arquivo) fica guardado como **referência externa**. |
| Origens | Manual no app, Dinzo, OFX, CSV, XLSX, PDF. |
| Manuais | Lançados no app **e** no Dinzo, até abandonar o Dinzo. Os dois são consolidados com o lançamento real. |
| Duplicados entre origens | Lançamentos parecidos vindos de origens diferentes são **agrupados para o usuário decidir**. Nada é sobrescrito automaticamente. |
| Importação | Toda importação vira um **lote** revisado antes de gravar: novo, já existe, divergente ou descartar. |
| Carga inicial | 12 meses de histórico do Dinzo. |
| Categorias | Importar a árvore completa de categorias do Dinzo, inclusive as que ainda não têm lançamentos. Depois disso as categorias são do app, e o usuário pode refinar e reclassificar. |
| Período do painel | Mês calendário pela data da compra (competência). |
| Stack | Java 21, Spring Boot 4, Maven, PostgreSQL + Flyway, Thymeleaf (+ HTMX e Chart.js). |

## Regras de entradas e gastos

O cartão de crédito é tratado como um acumulador de pagamentos que só é quitado no fim do mês:
o que importa é quanto sai e quanto entra, não monitorar o cartão.

| Movimento | Como conta |
|---|---|
| Saída da conta corrente | **Gasto**, seja o que for |
| Compra no cartão | **Gasto** (estorno no cartão abate o gasto) |
| Crédito na conta corrente | **Entrada**, seja o que for |
| Pagamento de fatura | Fora (as compras do cartão já contaram uma a uma) |
| Transferência entre contas do **mesmo perfil** | Fora (transferência interna) |
| Transferência entre contas de **perfis diferentes** (ex.: Max → Família) | Gasto no perfil de origem e **entrada** no de destino, marcada como contribuição de quem enviou |
| Depósito de quem não tem conta no app (ex.: Mariana) | Entrada, marcada como contribuição pelo nome na descrição |

**Sobra do perfil no mês = Entradas − Gastos.**

Uma transferência é reconhecida pelo par débito/crédito de mesmo valor, em até 1 dia, entre contas
cadastradas no app (ou pelo grupo de transferência que o Dinzo informa). O perfil de cada lado
decide se é interna ou entre perfis.

## Regras de perfil

- Lançamento importado (Open Finance, arquivo): **final do cartão → conta → tag**.
- Lançamento manual ainda não consolidado: **tag → conta**.
- Subperfil pela tag (ex.: Pedro dentro de Família). O lançamento real herda as tags do manual.
- Sem regra: fila "Avaliar".
- Fixo: tag `Fixa`, recorrência ou parcela (só a parcela do mês entra). O resto é variável.
- Supérfluo: marcação manual.

## Usos do Dinzo

| Uso | O que faz |
|---|---|
| Carga inicial | Traz 12 meses de histórico, as categorias, as contas e os cartões, e sugere o perfil de cada um |
| Importação em lote | Traz um período como lote de importação, revisado como qualquer arquivo |
| Conferência | Compara um período do Dinzo com o app: falta no app, falta no Dinzo, valor ou data diferente |
| Descoberta | Avisa quando aparece conta nova ou final de cartão novo, para cadastrar e definir o perfil |

## Importação de arquivos

| Formato | Conta corrente | Cartão C6 | Leitura |
|---|---|---|---|
| OFX | sim | — | Leitor próprio (padrão aberto) |
| CSV | sim | sim | Mapeamento de colunas salvo por banco e tipo de arquivo |
| XLSX | sim | sim | Apache POI + o mesmo mapeamento de colunas |
| PDF | sim | sim | Conversão + extração por LLM (em discussão, abaixo) |

Os testes usam arquivos fictícios. Nenhum extrato real vai para o git (o repositório é público).

### PDF: proposta em discussão

```
PDF ─> Docling (local, docling-serve) ─> texto/tabelas ─> LLM via OpenRouter ─> JSON validado ─> lote
```

- **Docling** roda localmente como serviço separado (Docker) e converte o PDF em Markdown/JSON,
  inclusive tabelas e PDFs escaneados (OCR). O app Java chama a API REST dele.
- **LLM via OpenRouter** recebe o texto e devolve os lançamentos num formato JSON fixo (data,
  descrição, valor, direção, final do cartão, parcela). Modelo configurável.
- **Validação** antes do lote: datas dentro do período do extrato, valores positivos e, quando o
  documento traz, soma conferida com o total impresso (total da fatura, saldo final). Divergência
  vira aviso no lote, não bloqueio.
- **Privacidade:** o Docling não envia nada para fora. O texto do extrato vai para o OpenRouter e
  para o provedor do modelo. Mitigações: restringir a provedores sem retenção de dados, ou usar um
  modelo local.

## Fases

- [x] **F1, base:** schema, cliente Dinzo, sincronização, regras de perfil, contribuições,
      consolidação manual → real, painel inicial e testes.
- [x] Migração para Spring Boot 4.1 e diagramas em `docs/diagramas/`.
- [ ] **F2, modelo próprio:** contas, cartões, categorias e lançamentos com id próprio; referências
      externas; migração dos dados atuais; novas regras de entradas e gastos; lançamento manual no app.
- [ ] **F3, importação:** lote, revisão e agrupamento de parecidos; Dinzo adaptado aos quatro usos
      (carga de 12 meses e das categorias, lote, conferência, descoberta).
- [ ] **F4, arquivos:** OFX, CSV e XLSX com mapeamento salvo.
- [ ] **F5, PDF:** Docling + LLM, com validação.
- [ ] **F6, gestão:** telas de perfis, regras e categorias; reclassificação; fila "Avaliar".
- [ ] **F7, painel:** gráficos, evolução mês a mês, supérfluos, previsão a partir dos 12 meses e
      "onde reduzir".

## Pendências para decidir

1. **Fatura dos cartões da Família paga pela conta pessoal:** pelas regras acima, as compras nos
   finais da Família são gastos da Família, e o pagamento da fatura fica fora. Assim, o fato de a
   conta pessoal pagar essa parte não aparece em lugar nenhum. Ela deve contar como contribuição
   (gasto no perfil pessoal, entrada na Família)?
2. **PDF:** confirmar Docling local + OpenRouter, qual modelo e se o texto dos extratos pode sair
   da máquina.
3. **Exemplos de arquivo:** um de cada formato e banco, para escrever e testar os leitores.
