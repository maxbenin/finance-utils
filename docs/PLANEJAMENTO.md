# Planejamento

## Objetivo

Painel por perfil (pessoal e familiar) com gastos fixos, variáveis e supérfluos e o que dá
para reduzir, com foco em sobrar dinheiro. O Dinzo é usado só como fonte dos dados do Open Finance.

## Decisões tomadas

| Tema | Decisão |
|---|---|
| Acesso ao Dinzo | Interface `DinzoClient`. Implementado: cliente MCP (JSON-RPC sobre HTTP) e cliente de arquivos JSON. REST entra se o Dinzo confirmar a API. |
| Stack | Java 21, Spring Boot 4, Maven, PostgreSQL + Flyway, Thymeleaf (+ HTMX e Chart.js nas próximas fases). |
| Período do painel | **Mês calendário pela data da compra** (competência). Cartão e conta corrente usam a data do lançamento. |
| Prioridade das regras | O dado do Open Finance decide: **final do cartão → conta → tag**. Em lançamento manual ainda não consolidado, a **tag** decide primeiro. |
| Subperfil | Subperfis dentro de Família (ex.: Pedro), definidos pela tag. |
| Contribuição | Depósito nas contas da Família reconhecido pela descrição (nome de quem enviou). Entra como contribuição para a Família e sai da sobra de quem contribuiu. |
| Parcelamento | Só a parcela do mês entra, como gasto fixo. |
| Manual → real | Consolidação só do nosso lado. No Dinzo nada é alterado. O real herda as tags do manual (ex.: subperfil). |
| Contas fora dos cálculos | Ex.: Carteira, por enquanto. |
| Supérfluos | Marcação manual pelo usuário. |

## Regras

Os valores reais (finais, contas, nomes) ficam em `config/seed-local.yml`, fora do git.

- Cada final de cartão aponta para um perfil e guarda quem usa o cartão.
- Conta corrente familiar → Família; demais contas correntes → perfil pessoal.
- Tags de perfil e de subperfil nos lançamentos manuais.
- Fixo: tag `Fixa`, recorrência ou parcelamento. O resto é variável.

## Fora dos totais

- Pagamento de fatura (os gastos do cartão já contam um a um).
- Transferências entre contas próprias (`transfer_group_id`) e lançamentos ocultos no Dinzo.
- Contas não acompanhadas.
- Estornos no cartão abatem o gasto.

## Sincronização

1. Atualiza as contas.
2. Busca os lançamentos dos últimos 60 dias (visão `ledger`, sem projeções).
3. Grava cada um com o id do Dinzo, o JSON original e um hash. Mudança de conteúdo → `changed_at`;
   sumiu do Dinzo → `removed_at`.
4. Para cada lançamento novo do Open Finance, procura um manual em aberto: mesma conta e
   direção, valor com tolerância (R$ 0,50 ou 2%), data de 1 dia antes a 3 dias depois e descrição
   parecida. Candidato claro → consolida; ambíguo → fila "Consolidações para confirmar".
5. Recalcula perfil, subperfil, tipo e natureza de todos os lançamentos da janela.

## Fases

- [x] **F1, base:** schema, cliente Dinzo, sincronização, regras de perfil, contribuições,
      consolidação manual → real, painel inicial e testes.
- [ ] **F2, conectar:** confirmar endpoint e autenticação do Dinzo (REST ou MCP) e rodar com dados reais.
- [ ] **F3, gestão:** telas para criar e editar perfis e regras, ajuste manual de perfil por
      lançamento, ação na fila "Avaliar".
- [ ] **F4, supérfluos:** marcar lançamento ou estabelecimento como supérfluo e aplicar aos próximos.
- [ ] **F5, painel:** categorias do Dinzo, gráficos (Chart.js), evolução mês a mês, quadro da
      fatura corrente, filtro por quem usa o cartão.
- [ ] **F6, onde reduzir:** assinaturas repetidas entre perfis, categorias acima da média e
      simulação "quanto sobra se cortar X".

## Pendências para decidir

1. **Fatura paga pela conta pessoal:** a fatura do cartão é paga por uma conta pessoal,
   inclusive os gastos dos cartões da Família. Contar essa parte como contribuição?
2. **Gasto da Família pago pela conta pessoal:** ex.: lançamento na conta pessoal com tag
   Familia. Hoje fica no perfil pessoal (o Open Finance decide). Deve contar como contribuição?
3. **Outros contribuintes:** alguém além dos dois já cadastrados deposita na conta familiar?
