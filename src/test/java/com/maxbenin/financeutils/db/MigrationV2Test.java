package com.maxbenin.financeutils.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/** A migração V2 leva os dados da fase 1 (espelho do Dinzo) para o modelo próprio. */
class MigrationV2Test {

    /** H2 por padrão; para validar no PostgreSQL: -Dmigration.test.url=jdbc:postgresql://... (banco vazio). */
    static final String URL = System.getProperty("migration.test.url",
            "jdbc:h2:mem:migration-v2;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    static final String USER = System.getProperty("migration.test.user", "sa");

    @Test
    void copiesPhaseOneDataIntoTheOwnModel() throws Exception {
        Flyway.configure().dataSource(URL, USER, "").target("1").load().migrate();
        try (Connection c = DriverManager.getConnection(URL, USER, ""); Statement st = c.createStatement()) {
            st.execute("INSERT INTO dinzo_account (dinzo_id, title, account_type, sub_type, tracked) VALUES "
                    + "('acc-card', 'Cartao', 'CREDIT', 'CREDIT_CARD', TRUE), ('acc-c6', 'Conta', 'BANK', 'CHECKING_ACCOUNT', TRUE)");
            st.execute("INSERT INTO ledger_entry (dinzo_id, account_id, card_final, entry_date, direction, status, amount, "
                    + "description, source, raw_json, content_hash, first_seen_at, last_seen_at, superfluous) VALUES "
                    + "('t1', 'acc-card', '1111', DATE '2026-10-01', 'DEBIT', 'POSTED', 10.50, 'Uber', 'OPEN_FINANCE', '{}', 'h1', "
                    + "TIMESTAMP '2026-10-02 10:00:00', TIMESTAMP '2026-10-02 10:00:00', TRUE), "
                    + "('t2', 'acc-card', NULL, DATE '2026-09-30', 'DEBIT', 'POSTED', 10.50, 'Manual', 'APP_MANUAL', '{}', 'h2', "
                    + "TIMESTAMP '2026-10-01 10:00:00', TIMESTAMP '2026-10-01 10:00:00', NULL)");
            st.execute("UPDATE ledger_entry SET merged_into_id = 't1' WHERE dinzo_id = 't2'");
            st.execute("INSERT INTO ledger_entry_tag (dinzo_id, tag) VALUES ('t2', 'Familia')");
            st.execute("INSERT INTO reconciliation (manual_id, real_id, score, status, created_at) "
                    + "VALUES ('t2', 't1', 0.9, 'AUTO', TIMESTAMP '2026-10-02 10:00:00')");
        }

        Flyway.configure().dataSource(URL, USER, "").load().migrate();

        try (Connection c = DriverManager.getConnection(URL, USER, ""); Statement st = c.createStatement()) {
            assertThat(scalar(st, "SELECT kind FROM account WHERE id = 'acc-card'")).isEqualTo("CREDIT_CARD");
            assertThat(scalar(st, "SELECT COUNT(*) FROM account_ref WHERE source = 'DINZO'")).isEqualTo("2");
            assertThat(scalar(st, "SELECT origin || '/' || manual || '/' || superfluous FROM txn WHERE id = 't1'"))
                    .isEqualToIgnoringCase("DINZO/false/true");
            assertThat(scalar(st, "SELECT manual || '/' || merged_into_id FROM txn WHERE id = 't2'")).isEqualToIgnoringCase("true/t1");
            assertThat(scalar(st, "SELECT tag FROM txn_tag WHERE txn_id = 't2'")).isEqualTo("Familia");
            assertThat(scalar(st, "SELECT external_id FROM txn_ref WHERE txn_id = 't1' AND source = 'DINZO'")).isEqualTo("t1");
            assertThat(scalar(st, "SELECT status FROM txn_reconciliation WHERE manual_id = 't2'")).isEqualTo("AUTO");
            assertThat(scalar(st, "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'ledger_entry'")).isEqualTo("0");
        }
    }

    private static String scalar(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
