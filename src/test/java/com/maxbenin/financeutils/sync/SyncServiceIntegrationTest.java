package com.maxbenin.financeutils.sync;

import com.maxbenin.financeutils.transaction.EntryKind;
import com.maxbenin.financeutils.transaction.LedgerEntryRepository;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

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
        for (String f : new String[]{"accounts.json", "transactions.json"}) {
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
    @Autowired LedgerEntryRepository entries;

    @Test
    void fullSyncAssignsProfilesMergesManualsAndSummarizes() throws IOException {
        SyncResult first = sync.sync();
        assertThat(first.fetched()).isEqualTo(18);     // projeção ignorada
        assertThat(first.created()).isEqualTo(18);
        assertThat(first.autoMerged()).isEqualTo(1);   // manual t12 -> real t13
        assertThat(first.unassigned()).isEqualTo(1);   // t15, cartão sem final

        assertThat(entries.findById("t12").orElseThrow().getMergedIntoId()).isEqualTo("t13");
        var real = entries.findById("t13").orElseThrow();
        assertThat(real.getSubprofileId()).as("herda a tag Pedro do manual").isNotNull();
        assertThat(entries.findById("t11").orElseThrow().getKind()).isEqualTo(EntryKind.BILL_PAYMENT);
        assertThat(entries.findById("t16").orElseThrow().getKind()).isEqualTo(EntryKind.IGNORED);

        Map<String, ProfileSummary> s = byName();
        ProfileSummary max = s.get("Max");
        assertThat(max.income()).isEqualByComparingTo("7000");
        assertThat(max.expenses()).isEqualByComparingTo("748.51");   // 92.97 + 655.54 + 98 - 98
        assertThat(max.fixed()).isEqualByComparingTo("655.54");
        assertThat(max.contributionsMade()).isEqualByComparingTo("2000");
        assertThat(max.balance()).isEqualByComparingTo("4251.49");

        ProfileSummary familia = s.get("Familia");
        assertThat(familia.contributionsReceived())
                .containsEntry("Mariana", new BigDecimal("1000.00"))
                .containsEntry("Max", new BigDecimal("2000.00"));
        assertThat(familia.expenses()).isEqualByComparingTo("573.29"); // 38.39 + 24.90 + 330 + 120 + 60
        assertThat(familia.fixed()).isEqualByComparingTo("24.90");
        assertThat(familia.bySubprofile()).containsEntry("Pedro", new BigDecimal("180.00"));
        assertThat(familia.balance()).isEqualByComparingTo("2426.71");

        // Segunda sincronização: valor alterado no banco e lançamento sumido do Dinzo
        Path tx = fixtures.resolve("transactions.json");
        String json = Files.readString(tx)
                .replace("\"amount\": 330", "\"amount\": 350")
                .replace("\"id\": \"t02\"", "\"id\": \"t02-gone\"");
        Files.writeString(tx, json);

        SyncResult second = sync.sync();
        assertThat(second.created()).isEqualTo(1);
        assertThat(second.updated()).isEqualTo(1);
        assertThat(second.removed()).isEqualTo(1);
        assertThat(second.autoMerged()).isZero();
        assertThat(entries.findById("t02").orElseThrow().getRemovedAt()).isNotNull();
        assertThat(entries.findById("t07").orElseThrow().getChangedAt()).isNotNull();
        assertThat(byName().get("Familia").expenses()).isEqualByComparingTo("593.29");
    }

    private Map<String, ProfileSummary> byName() {
        return dashboard.summaries(OCT).stream()
                .collect(java.util.stream.Collectors.toMap(ProfileSummary::profile, p -> p));
    }
}
