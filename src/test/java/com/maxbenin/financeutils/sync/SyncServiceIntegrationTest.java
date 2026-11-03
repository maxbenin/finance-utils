package com.maxbenin.financeutils.sync;

import com.maxbenin.financeutils.account.AccountRepository;
import com.maxbenin.financeutils.category.CategoryRepository;
import com.maxbenin.financeutils.profile.ProfileRepository;
import com.maxbenin.financeutils.transaction.*;
import com.maxbenin.financeutils.web.DashboardService;
import com.maxbenin.financeutils.web.ProfileSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
class SyncServiceIntegrationTest {

    static final YearMonth OCT = YearMonth.of(2026, 10);

    @TempDir
    static Path fixtures;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws IOException {
        for (String f : new String[]{"accounts.json", "categories.json", "transactions.json"}) {
            Files.copy(Path.of("src/test/resources/fixtures", f), fixtures.resolve(f));
        }
        r.add("dinzo.fixture-dir", fixtures::toString);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @Autowired SyncService sync;
    @Autowired DashboardService dashboard;
    @Autowired LedgerService ledger;
    @Autowired LedgerEntryRepository entries;
    @Autowired TxnRefRepository refs;
    @Autowired AccountRepository accounts;
    @Autowired CategoryRepository categories;
    @Autowired ProfileRepository profiles;

    LedgerEntry e(String id) {
        return entries.findById(id).orElseThrow();
    }

    @Test
    void dinzoFeedsTheAppAndTheNewRulesApply() throws IOException {
        // 1. Primeira sincronização
        SyncResult first = sync.sync();
        assertThat(first.fetched()).isEqualTo(22);       // projeção ignorada
        assertThat(first.created()).isEqualTo(22);
        assertThat(first.autoMerged()).isEqualTo(1);     // manual t17 -> real t18
        assertThat(first.unassigned()).isEqualTo(1);     // t20, cartão sem final
        assertThat(first.newCategories()).isEqualTo(6);  // árvore inteira, mesmo sem uso
        assertThat(first.newAccounts()).isEqualTo(4);    // a carteira já existia (seed)

        // Dados próprios do app, com o Dinzo como referência externa
        assertThat(refs.findBySourceAndExternalId("DINZO", "t01").orElseThrow().getEntry().getId()).isEqualTo("t01");
        assertThat(categories.findById("cat-rest").orElseThrow().getParentId()).isEqualTo("cat-alim");
        assertThat(e("t01").getCategoryId()).isEqualTo("cat-uber");
        assertThat(accounts.findById("acc-card").orElseThrow().getBillPaidFromAccountId()).isEqualTo("acc-c6");
        assertThat(accounts.findById("acc-wallet").orElseThrow().getName()).isEqualTo("Carteira");

        // Novas regras
        assertThat(e("t09").getKind()).as("Max -> Família: gasto do Max").isEqualTo(EntryKind.EXPENSE);
        assertThat(e("t10").getKind()).as("Max -> Família: entrada da Família").isEqualTo(EntryKind.INCOME);
        assertThat(e("t10").getContributor()).isEqualTo("Max");
        assertThat(e("t11").getKind()).as("BB -> C6 é acerto de fatura").isEqualTo(EntryKind.NEUTRAL);
        assertThat(e("t12").getEffectiveExcludeReason()).isEqualTo(EntryProcessor.BILL_SETTLEMENT);
        assertThat(e("t13").getKind()).as("MP -> C6, mesmo perfil").isEqualTo(EntryKind.TRANSFER);
        assertThat(e("t14").getKind()).isEqualTo(EntryKind.TRANSFER);
        assertThat(e("t15").getKind()).isEqualTo(EntryKind.BILL_PAYMENT);
        assertThat(e("t16").getKind()).isEqualTo(EntryKind.INCOME);
        assertThat(e("t17").getMergedIntoId()).isEqualTo("t18");
        assertThat(e("t18").getSubprofileId()).as("herda a tag Pedro do manual").isNotNull();
        assertThat(e("t21").getKind()).isEqualTo(EntryKind.IGNORED);

        Map<String, ProfileSummary> s = byName();
        ProfileSummary max = s.get("Max");
        assertThat(max.income()).isEqualByComparingTo("7085.58");      // 7000 + 85,58
        assertThat(max.expenses()).isEqualByComparingTo("2748.51");    // 92,97 + 655,54 + 98 - 98 + 2000
        assertThat(max.fixed()).isEqualByComparingTo("655.54");
        assertThat(max.contributionsMade()).containsEntry("Familia", new BigDecimal("2000.00"));
        assertThat(max.neutral()).isEqualByComparingTo("1500");
        assertThat(max.balance()).isEqualByComparingTo("4337.07");

        ProfileSummary familia = s.get("Familia");
        assertThat(familia.income()).isEqualByComparingTo("3000");
        assertThat(familia.contributionsReceived())
                .containsEntry("Mariana", new BigDecimal("1000.00"))
                .containsEntry("Max", new BigDecimal("2000.00"));
        assertThat(familia.expenses()).isEqualByComparingTo("573.29"); // 38,39 + 24,90 + 330 + 120 + 60
        assertThat(familia.fixed()).isEqualByComparingTo("24.90");
        assertThat(familia.bySubprofile()).containsEntry("Pedro", new BigDecimal("180.00"));
        assertThat(familia.neutral()).isEqualByComparingTo("1500");
        assertThat(familia.balance()).isEqualByComparingTo("2426.71");

        // 2. Decisões do usuário: o PIX da Mariana na conta pessoal foi um acerto de contas
        ledger.setTotalsOverride("t16", TotalsOverride.EXCLUDE, "Acerto de contas");
        assertThat(e("t16").getKind()).isEqualTo(EntryKind.NEUTRAL);
        assertThat(byName().get("Max").income()).isEqualByComparingTo("7000");

        // ...e um acerto de fatura pode voltar a contar
        ledger.setTotalsOverride("t11", TotalsOverride.INCLUDE, null);
        assertThat(e("t11").getKind()).isEqualTo(EntryKind.EXPENSE);
        ledger.setTotalsOverride("t11", null, null);
        assertThat(e("t11").getKind()).isEqualTo(EntryKind.NEUTRAL);

        // 3. Lançamento manual no app, antes de o banco trazer a compra
        LedgerEntry manual = ledger.createManual(new LedgerService.ManualInput("acc-card", LocalDate.of(2026, 10, 25),
                "DEBIT", new BigDecimal("50.00"), "Padaria", null, "1111", List.of(), null));
        assertThat(entries.findById(manual.getId()).orElseThrow().getProfileId()).isEqualTo(profileId("Max"));
        assertThat(byName().get("Max").expenses()).isEqualByComparingTo("2798.51");

        // 4. Segunda sincronização: valor alterado, lançamento sumido e a compra real da padaria
        Path tx = fixtures.resolve("transactions.json");
        String json = Files.readString(tx)
                .replace("\"amount\": 330", "\"amount\": 350")
                .replace("\"id\": \"t02\"", "\"id\": \"t02-gone\"")
                .replace("\"transactions\": [", """
                        "transactions": [{"id": "t24", "projected": false, "date": "2026-10-26", "type": "DEBIT",
                         "status": "POSTED", "amount": 50.0, "description": "PADARIA EXEMPLO", "account_id": "acc-card",
                         "tags": [], "source": "OPEN_FINANCE", "card_number": "1111"},""");
        Files.writeString(tx, json);

        SyncResult second = sync.sync();
        assertThat(second.created()).isEqualTo(2);
        assertThat(second.updated()).isEqualTo(1);
        assertThat(second.removed()).isEqualTo(1);
        assertThat(second.autoMerged()).isEqualTo(1);
        assertThat(second.newCategories()).isZero();
        assertThat(entries.findById(manual.getId()).orElseThrow().getMergedIntoId()).isEqualTo("t24");
        assertThat(e("t02").getRemovedAt()).isNotNull();
        assertThat(refs.findBySourceAndExternalId("DINZO", "t07").orElseThrow().getChangedAt()).isNotNull();
        assertThat(e("t16").getKind()).as("decisão do usuário sobrevive à sincronização").isEqualTo(EntryKind.NEUTRAL);
        assertThat(byName().get("Max").expenses()).isEqualByComparingTo("2798.51");
        assertThat(byName().get("Familia").expenses()).isEqualByComparingTo("593.29");

        // 5. Perfil escolhido à mão tira o lançamento da fila "Avaliar"
        ledger.setProfileOverride("t20", profileId("Max"));
        assertThat(dashboard.unassigned(OCT)).isEmpty();
        assertThat(byName().get("Max").expenses()).isEqualByComparingTo("2813.51");
    }

    private Long profileId(String name) {
        return profiles.findByName(name).orElseThrow().getId();
    }

    private Map<String, ProfileSummary> byName() {
        return dashboard.summaries(OCT).stream().collect(Collectors.toMap(ProfileSummary::profile, p -> p));
    }
}
