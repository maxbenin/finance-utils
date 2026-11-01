package com.maxbenin.financeutils.dinzo;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Lê accounts.json e transactions.json de um diretório, no mesmo formato
 * devolvido pelas ferramentas listar_contas / listar_lancamentos do Dinzo.
 * Útil para desenvolvimento e testes sem acesso à conta real.
 */
public class FixtureDinzoClient implements DinzoClient {

    private final Path dir;
    private final JsonMapper mapper;

    public FixtureDinzoClient(Path dir, JsonMapper mapper) {
        this.dir = dir;
        this.mapper = mapper;
    }

    @Override
    public List<DinzoAccount> listAccounts() {
        return DinzoJson.accounts(read("accounts.json"));
    }

    @Override
    public List<DinzoTransaction> listTransactions(LocalDate from, LocalDate to) {
        return DinzoJson.transactions(read("transactions.json")).stream()
                .filter(t -> !t.projected())
                .filter(t -> !t.date().isBefore(from) && !t.date().isAfter(to))
                .toList();
    }

    private JsonNode read(String file) {
        try {
            return mapper.readTree(dir.resolve(file).toFile());
        } catch (JacksonException e) {
            throw new DinzoException("Falha ao ler fixture " + file, e);
        }
    }
}
