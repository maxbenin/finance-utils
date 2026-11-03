# finance-utils

Gestor financeiro independente, com painel por perfil (pessoal × familiar). Os dados são do
próprio app; o Dinzo (Open Finance) é uma das origens, e depois virão arquivos OFX, CSV, XLSX e PDF.

- Contas, cartões, categorias e lançamentos com **id próprio**. A origem de cada registro (id do
  Dinzo, por exemplo) fica guardada como **referência externa**, e mudanças na origem são detectadas.
- Separa lançamentos por **perfil** usando o final do cartão, a conta corrente e as tags.
- **Lançamento manual no app**, consolidado com o lançamento real quando o banco o traz.
- **Entradas e gastos**: toda saída de conta corrente e compra no cartão é gasto; todo crédito na
  conta corrente é entrada. Ficam fora o pagamento de fatura, as transferências entre contas do
  mesmo perfil e o que estiver marcado **"não conta"** (ex.: acerto de fatura, acerto de contas).
- Transferência entre perfis vira gasto de um e entrada do outro, com o registro de **quem contribuiu**.
- Mostra gastos **fixos, variáveis e supérfluos** e a **sobra** (entradas − gastos) de cada perfil no mês.

Planejamento completo: [docs/PLANEJAMENTO.md](docs/PLANEJAMENTO.md).
Diagramas do funcionamento: [docs/diagramas](docs/diagramas/README.md).

## Stack

Java 21, Spring Boot 4.1, Maven, PostgreSQL + Flyway, Thymeleaf.

## Como rodar

### Desenvolvimento (sem Postgres e sem Dinzo)

Usa H2 em memória e arquivos JSON no formato das ferramentas do Dinzo
(`accounts.json` = saída de `listar_contas`, `categories.json` = saída de `listar_categorias`,
`transactions.json` = saída de `listar_lancamentos`):

```bash
cp config/seed.example.yml config/seed-local.yml   # preencha com seus ids
mkdir -p data/dinzo                                # coloque os três arquivos JSON
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Com Postgres e acesso ao Dinzo

```bash
docker compose up -d
export DINZO_MODE=mcp DINZO_MCP_URL=<endpoint> DINZO_TOKEN=<token>
mvn spring-boot:run
```

Abra http://localhost:8080. Na primeira vez, use **Carga inicial (12 meses)**: traz do Dinzo as
categorias, as contas e um ano de lançamentos. Depois, **Sincronizar com o Dinzo** reprocessa os
últimos 60 dias, e a sincronização automática roda a cada 6 horas (`finance.sync.cron`).

Em **Lançamentos** ficam a lista do mês e as ações de cada lançamento: "não conta", supérfluo,
perfil escolhido à mão e categoria. Em **Novo lançamento** você registra uma compra antes de o
banco trazê-la.

### Testes

```bash
mvn test
```

A migração do banco roda em H2 nos testes. Para validá-la também num PostgreSQL vazio:

```bash
mvn test -Dtest=MigrationV2Test -Dmigration.test.url=jdbc:postgresql://localhost:5432/vazio -Dmigration.test.user=finance
```

## Configuração de perfis

As regras reais (ids de contas, finais de cartão, nomes de quem deposita) ficam em
`config/seed-local.yml`, que **não vai para o git** porque este repositório é público. O modelo
está em [`config/seed.example.yml`](config/seed.example.yml). O arquivo é lido só na primeira
inicialização, quando o banco ainda não tem perfis.

## Estrutura

| Pacote | Responsabilidade |
|---|---|
| `account` | Contas correntes e cartões, com referências externas |
| `category` | Categorias do app (começam como cópia das do Dinzo) |
| `transaction` | Lançamentos, referências externas, classificação (`EntryClassifier`), pareamento de transferências (`TransferPairer`), processamento (`EntryProcessor`) e ações do usuário (`LedgerService`) |
| `profile` | Perfis, regras e `ProfileResolver` (qual perfil é dono do lançamento) |
| `reconciliation` | Consolidação manual → real (`ManualMatcher`) |
| `dinzo` | Acesso ao Dinzo (`DinzoClient`: MCP ou arquivos JSON) |
| `sync` | Sincronização com o Dinzo: carga inicial, periódica e sob demanda |
| `web` | Painel, lista de lançamentos e lançamento manual |
