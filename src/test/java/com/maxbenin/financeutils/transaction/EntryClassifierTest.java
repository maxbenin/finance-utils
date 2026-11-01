package com.maxbenin.financeutils.transaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntryClassifierTest {

    static final long MAX = 1, FAMILIA = 2;

    final EntryClassifier classifier = new EntryClassifier(new ContributorMatcher(List.of(
            new ContributorMatcher.Rule("Max", "MAX TESTE", FAMILIA),
            new ContributorMatcher.Rule("Mariana", "MARIANA TESTE", FAMILIA))));

    static EntryClassifier.Facts facts(boolean card, String dir, String desc, Long profile) {
        return new EntryClassifier.Facts(true, card, dir, null, null, false, desc, profile, null, null, List.of());
    }

    @Test
    void depositIntoFamilyAccountIsContribution() {
        var r = classifier.classify(facts(false, "CREDIT", "PIX - RECEBIDO  29/10 Mariana Teste", FAMILIA));
        assertThat(r.kind()).isEqualTo(EntryKind.CONTRIBUTION);
        assertThat(r.contributor()).isEqualTo("Mariana");
    }

    @Test
    void ownTransferIntoPersonalAccountIsNotContribution() {
        var f = new EntryClassifier.Facts(true, false, "CREDIT", null, null, true,
                "Pix recebido MAX TESTE", MAX, null, null, List.of());
        assertThat(classifier.classify(f).kind()).isEqualTo(EntryKind.IGNORED);
    }

    @Test
    void billPaymentAndTransfersAreExcluded() {
        var bill = new EntryClassifier.Facts(true, false, "DEBIT", "acc-card", null, false, "Fatura", MAX, null, null, List.of());
        var transfer = new EntryClassifier.Facts(true, false, "DEBIT", null, "g1", false, "Transf", MAX, null, null, List.of());
        assertThat(classifier.classify(bill).kind()).isEqualTo(EntryKind.BILL_PAYMENT);
        assertThat(classifier.classify(transfer).kind()).isEqualTo(EntryKind.TRANSFER);
    }

    @Test
    void creditOnCardIsRefundCountedAsExpense() {
        assertThat(classifier.classify(facts(true, "CREDIT", "Estorno", MAX)).kind()).isEqualTo(EntryKind.EXPENSE);
    }

    @Test
    void natureIsFixedForTagRecurrenceOrInstallment() {
        var tagged = new EntryClassifier.Facts(true, true, "DEBIT", null, null, false, "Escola", FAMILIA, null, null, List.of("Fixa"));
        var recurring = new EntryClassifier.Facts(true, true, "DEBIT", null, null, false, "Streaming", FAMILIA, "rec", null, List.of());
        var installment = new EntryClassifier.Facts(true, true, "DEBIT", null, null, false, "Loja", MAX, null, 3, List.of());
        assertThat(classifier.classify(tagged).nature()).isEqualTo(Nature.FIXED);
        assertThat(classifier.classify(recurring).nature()).isEqualTo(Nature.FIXED);
        assertThat(classifier.classify(installment).nature()).isEqualTo(Nature.FIXED);
        assertThat(classifier.classify(facts(true, "DEBIT", "Uber", MAX)).nature()).isEqualTo(Nature.VARIABLE);
    }

    @Test
    void untrackedAccountIsIgnored() {
        var f = new EntryClassifier.Facts(false, false, "DEBIT", null, null, false, "Cafe", null, null, null, List.of());
        assertThat(classifier.classify(f).kind()).isEqualTo(EntryKind.IGNORED);
    }
}
