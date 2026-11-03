package com.maxbenin.financeutils.transaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntryClassifierTest {

    static final long MAX = 1, FAMILIA = 2;

    final EntryClassifier classifier = new EntryClassifier(new ContributorMatcher(List.of(
            new ContributorMatcher.Rule("Mariana", "MARIANA TESTE", FAMILIA))));

    static EntryClassifier.Facts facts(boolean card, String dir, Long profile) {
        return new EntryClassifier.Facts(true, card, dir, false, false, null, "X", profile, null, null, List.of());
    }

    @Test
    void everyCheckingDebitAndCardPurchaseIsExpense() {
        assertThat(classifier.classify(facts(false, "DEBIT", MAX)).kind()).isEqualTo(EntryKind.EXPENSE);
        assertThat(classifier.classify(facts(true, "DEBIT", MAX)).kind()).isEqualTo(EntryKind.EXPENSE);
        assertThat(classifier.classify(facts(true, "CREDIT", MAX)).kind()).as("estorno abate o gasto").isEqualTo(EntryKind.EXPENSE);
    }

    @Test
    void everyCheckingCreditIsIncome() {
        assertThat(classifier.classify(facts(false, "CREDIT", MAX)).kind()).isEqualTo(EntryKind.INCOME);
    }

    @Test
    void depositFromSomeoneOutsideTheAppIsTaggedByName() {
        var f = new EntryClassifier.Facts(true, false, "CREDIT", false, false, null,
                "PIX - RECEBIDO 29/10 Mariana Teste", FAMILIA, null, null, List.of());
        var r = classifier.classify(f);
        assertThat(r.kind()).isEqualTo(EntryKind.INCOME);
        assertThat(r.contributor()).isEqualTo("Mariana");
    }

    @Test
    void transferBetweenProfilesIsIncomeFromTheOtherProfile() {
        var f = new EntryClassifier.Facts(true, false, "CREDIT", false, false,
                new EntryClassifier.Transfer(false, "Max"), "PIX MAX", FAMILIA, null, null, List.of());
        var r = classifier.classify(f);
        assertThat(r.kind()).isEqualTo(EntryKind.INCOME);
        assertThat(r.contributor()).isEqualTo("Max");

        var debit = new EntryClassifier.Facts(true, false, "DEBIT", false, false,
                new EntryClassifier.Transfer(false, "Familia"), "PIX", MAX, null, null, List.of());
        assertThat(classifier.classify(debit).kind()).isEqualTo(EntryKind.EXPENSE);
    }

    @Test
    void transferWithinSameProfileIsInternal() {
        var f = new EntryClassifier.Facts(true, false, "CREDIT", false, false,
                new EntryClassifier.Transfer(true, null), "PIX", MAX, null, null, List.of());
        assertThat(classifier.classify(f).kind()).isEqualTo(EntryKind.TRANSFER);
    }

    @Test
    void billPaymentAndExcludedAreOutOfTotals() {
        var bill = new EntryClassifier.Facts(true, false, "DEBIT", true, false, null, "Fatura", MAX, null, null, List.of());
        var excluded = new EntryClassifier.Facts(true, false, "CREDIT", false, true, null, "Acerto", MAX, null, null, List.of());
        assertThat(classifier.classify(bill).kind()).isEqualTo(EntryKind.BILL_PAYMENT);
        assertThat(classifier.classify(excluded).kind()).isEqualTo(EntryKind.NEUTRAL);
    }

    @Test
    void natureIsFixedForTagRecurrenceOrInstallment() {
        var tagged = new EntryClassifier.Facts(true, true, "DEBIT", false, false, null, "Escola", FAMILIA, null, null, List.of("Fixa"));
        var recurring = new EntryClassifier.Facts(true, true, "DEBIT", false, false, null, "Streaming", FAMILIA, "rec", null, List.of());
        var installment = new EntryClassifier.Facts(true, true, "DEBIT", false, false, null, "Loja", MAX, null, 3, List.of());
        assertThat(classifier.classify(tagged).nature()).isEqualTo(Nature.FIXED);
        assertThat(classifier.classify(recurring).nature()).isEqualTo(Nature.FIXED);
        assertThat(classifier.classify(installment).nature()).isEqualTo(Nature.FIXED);
        assertThat(classifier.classify(facts(true, "DEBIT", MAX)).nature()).isEqualTo(Nature.VARIABLE);
    }

    @Test
    void untrackedAccountIsIgnored() {
        var f = new EntryClassifier.Facts(false, false, "DEBIT", false, false, null, "Cafe", null, null, null, List.of());
        assertThat(classifier.classify(f).kind()).isEqualTo(EntryKind.IGNORED);
    }
}
