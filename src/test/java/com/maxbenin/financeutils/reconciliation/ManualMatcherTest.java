package com.maxbenin.financeutils.reconciliation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManualMatcherTest {

    final ManualMatcher matcher = ManualMatcher.defaults();
    static final LocalDate D = LocalDate.of(2026, 10, 20);

    static ManualMatcher.Entry e(String id, String account, String amount, LocalDate date, String desc) {
        return new ManualMatcher.Entry(id, account, "DEBIT", new BigDecimal(amount), date, desc);
    }

    @Test
    void matchesSameAmountTwoDaysLaterAutomatically() {
        var manual = e("m1", "card", "120.00", D, "Restaurante Galo");
        var real = e("r1", "card", "120.00", D.plusDays(2), "RESTAURANTE GALO LTDA");
        var match = matcher.findMatch(real, List.of(manual)).orElseThrow();
        assertThat(match.manualId()).isEqualTo("m1");
        assertThat(match.automatic()).isTrue();
    }

    @Test
    void toleratesSmallAmountDifference() {
        var manual = e("m1", "card", "100.00", D, "Mercado");
        var real = e("r1", "card", "100.40", D.plusDays(1), "MERCADO EXEMPLO");
        assertThat(matcher.findMatch(real, List.of(manual))).isPresent();
    }

    @Test
    void rejectsDifferentAccountAmountOrDate() {
        var real = e("r1", "card", "50.00", D, "X");
        assertThat(matcher.findMatch(real, List.of(e("m1", "other", "50.00", D, "X")))).isEmpty();
        assertThat(matcher.findMatch(real, List.of(e("m2", "card", "55.00", D, "X")))).isEmpty();
        assertThat(matcher.findMatch(real, List.of(e("m3", "card", "50.00", D.minusDays(4), "X")))).isEmpty();
        assertThat(matcher.findMatch(real, List.of(e("m4", "card", "50.00", D.plusDays(2), "X")))).isEmpty();
    }

    @Test
    void ambiguousCandidatesNeedConfirmation() {
        var real = e("r1", "card", "30.00", D.plusDays(1), "UBER");
        var match = matcher.findMatch(real, List.of(
                e("m1", "card", "30.00", D, "Uber"),
                e("m2", "card", "30.00", D, "Uber"))).orElseThrow();
        assertThat(match.automatic()).isFalse();
    }

    @Test
    void descriptionSimilarityIgnoresAccentsAndCase() {
        assertThat(ManualMatcher.similarity("Padaria São João", "PADARIA SAO JOAO")).isEqualTo(1.0);
    }
}
