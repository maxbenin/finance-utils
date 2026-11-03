package com.maxbenin.financeutils.transaction;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransferPairerTest {

    final TransferPairer pairer = new TransferPairer(1);
    static final LocalDate D = LocalDate.of(2026, 10, 10);

    static TransferPairer.Candidate c(String id, String acc, String dir, String amount, LocalDate date, String group) {
        return new TransferPairer.Candidate(id, acc, dir, new BigDecimal(amount), date, group);
    }

    @Test
    void pairsByGroupInformedBySource() {
        var pairs = pairer.pair(List.of(
                c("d", "mp", "DEBIT", "2000", D, "g1"),
                c("x", "c6", "CREDIT", "2000", D, null),
                c("k", "bb", "CREDIT", "2000", D.plusDays(1), "g1")));
        assertThat(pairs).containsExactly(new TransferPairer.Pair("d", "k"));
    }

    @Test
    void pairsSameAmountBetweenDifferentAccountsWithinOneDay() {
        var pairs = pairer.pair(List.of(
                c("d", "bb", "DEBIT", "1500.00", D, null),
                c("k", "c6", "CREDIT", "1500", D.plusDays(1), null)));
        assertThat(pairs).containsExactly(new TransferPairer.Pair("d", "k"));
    }

    @Test
    void doesNotPairSameAccountOrFarDatesOrDifferentAmounts() {
        assertThat(pairer.pair(List.of(c("d", "bb", "DEBIT", "10", D, null), c("k", "bb", "CREDIT", "10", D, null)))).isEmpty();
        assertThat(pairer.pair(List.of(c("d", "bb", "DEBIT", "10", D, null), c("k", "c6", "CREDIT", "10", D.plusDays(2), null)))).isEmpty();
        assertThat(pairer.pair(List.of(c("d", "bb", "DEBIT", "10", D, null), c("k", "c6", "CREDIT", "10.01", D, null)))).isEmpty();
    }

    @Test
    void prefersClosestDateAndSkipsAmbiguity() {
        var closest = pairer.pair(List.of(
                c("d", "bb", "DEBIT", "50", D, null),
                c("k1", "c6", "CREDIT", "50", D.plusDays(1), null),
                c("k2", "mp", "CREDIT", "50", D, null)));
        assertThat(closest).containsExactly(new TransferPairer.Pair("d", "k2"));

        var ambiguous = pairer.pair(List.of(
                c("d", "bb", "DEBIT", "50", D, null),
                c("k1", "c6", "CREDIT", "50", D, null),
                c("k2", "mp", "CREDIT", "50", D, null)));
        assertThat(ambiguous).isEmpty();
    }
}
