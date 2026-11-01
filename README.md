# finance-utils

Automatizações de tarefas financeiras. Painel por perfil (pessoal × familiar) a partir dos
dados que o Dinzo traz via Open Finance.

- Separa lançamentos por **perfil** usando o final do cartão, a conta corrente e as tags.
- Guarda o **id do Dinzo** de cada lançamento e detecta quando ele muda ou some.
- **Consolida lançamentos manuais** com o lançamento real quando o Open Finance o traz.
- Registra **contribuições** de cada pessoa para as contas da família.
- Mostra gastos **fixos, variáveis e supérfluos** e a **sobra** de cada perfil no mês.

Planejamento completo: [docs/PLANEJAMENTO.md](docs/PLANEJAMENTO.md).

## Stack

Java 21, Spring Boot 3.5, Maven, PostgreSQL + Flyway, Thymeleaf.

## Como rodar

### Desenvolvimento (sem Postgres e sem Dinzo)

Usa H2 em memória e arquivos JSON no formato das ferramentas do Dinzo
(`accounts.json` = saída de `listar_contas`, `transactions.json` = saída de `listar_lancamentos`):

```bash
cp config/seed.example.yml config/seed-local.yml   # preencha com seus ids
mkdir -p data/dinzo                                # coloque accounts.json e transactions.json
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Com Postgres e acesso ao Dinzo

```bash
docker compose up -d
export DINZO_MODE=mcp DINZO_MCP_URL=<endpoint> DINZO_TOKEN=<token>
mvn spring-boot:run
```

Abra http://localhost:8080 e clique em **Sincronizar agora**. A sincronização automática roda a
cada 6 horas (`finance.sync.cron`).

### Testes

```bash
mvn test
```

## Configuração de perfis

As regras reais (ids de contas, finais de cartão, nomes de quem deposita) ficam em
`config/seed-local.yml`, que **não vai para o git** porque este repositório é público. O modelo
está em [`config/seed.example.yml`](config/seed.example.yml). O arquivo é lido só na primeira
inicialização, quando o banco ainda não tem perfis.

## Estrutura

| Pacote | Responsabilidade |
|---|---|
| `dinzo` | Acesso ao Dinzo (`DinzoClient`: MCP ou arquivos JSON) |
| `profile` | Perfis, regras e `ProfileResolver` (qual perfil é dono do lançamento) |
| `transaction` | Lançamentos espelhados, classificação (gasto, entrada, contribuição...) |
| `reconciliation` | Consolidação manual → real (`ManualMatcher`) |
| `sync` | Sincronização agendada e sob demanda |
| `web` | Painel |
