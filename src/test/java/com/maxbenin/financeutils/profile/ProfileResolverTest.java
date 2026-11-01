package com.maxbenin.financeutils.profile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileResolverTest {

    static final ProfileRef MAX = new ProfileRef(1, "Max", null);
    static final ProfileRef FAMILIA = new ProfileRef(2, "Familia", null);
    static final ProfileRef PEDRO = new ProfileRef(3, "Pedro", 2L);

    final RuleSet rules = new RuleSet()
            .add(RuleType.CARD_FINAL, "1111", MAX)
            .add(RuleType.CARD_FINAL, "3333", FAMILIA)
            .add(RuleType.ACCOUNT, "acc-bb", FAMILIA)
            .add(RuleType.ACCOUNT, "acc-c6", MAX)
            .add(RuleType.TAG, "Max", MAX)
            .add(RuleType.TAG, "Familia", FAMILIA)
            .add(RuleType.TAG, "Pedro", PEDRO);

    final ProfileResolver resolver = new ProfileResolver();

    Assignment resolve(String account, String card, boolean manual, String... tags) {
        return resolver.resolve(new ProfileResolver.Facts(account, card, manual, List.of(tags)), rules);
    }

    @Test
    void cardFinalDecidesForOpenFinance() {
        assertThat(resolve("acc-card", "1111", false)).isEqualTo(new Assignment(1L, null, "CARD_FINAL"));
    }

    @Test
    void openFinanceDataWinsOverConflictingTag() {
        assertThat(resolve("acc-card", "1111", false, "Familia").profileId()).isEqualTo(1L);
        assertThat(resolve("acc-c6", null, false, "Familia").profileId()).isEqualTo(1L);
    }

    @Test
    void accountDecidesWhenNoCardFinal() {
        assertThat(resolve("acc-bb", null, false)).isEqualTo(new Assignment(2L, null, "ACCOUNT"));
    }

    @Test
    void manualUsesTagBeforeAccount() {
        assertThat(resolve("acc-c6", null, true, "Familia")).isEqualTo(new Assignment(2L, null, "TAG"));
    }

    @Test
    void manualWithoutTagFallsBackToAccount() {
        assertThat(resolve("acc-c6", null, true).profileId()).isEqualTo(1L);
    }

    @Test
    void subprofileTagIsAppliedUnderItsParent() {
        assertThat(resolve("acc-card", "3333", false, "Pedro")).isEqualTo(new Assignment(2L, 3L, "CARD_FINAL"));
    }

    @Test
    void subprofileTagIgnoredWhenParentDiffers() {
        assertThat(resolve("acc-card", "1111", false, "Pedro")).isEqualTo(new Assignment(1L, null, "CARD_FINAL"));
    }

    @Test
    void onlySubprofileTagClimbsToParent() {
        assertThat(resolve("acc-card", null, true, "Pedro")).isEqualTo(new Assignment(2L, 3L, "TAG"));
    }

    @Test
    void openFinanceWithoutDataUsesTagAsFallback() {
        assertThat(resolve("acc-card", null, false, "Max")).isEqualTo(new Assignment(1L, null, "TAG"));
    }

    @Test
    void nothingMatchesGoesToReview() {
        assertThat(resolve("acc-card", "0000", false)).isEqualTo(Assignment.UNASSIGNED);
    }
}
