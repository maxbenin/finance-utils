package com.maxbenin.financeutils.profile;

import java.util.Collection;
import java.util.Objects;

/**
 * Decide o perfil de um lançamento.
 *
 * <ul>
 *   <li>Lançamento do Open Finance: o dado do banco decide (final do cartão, depois a conta).
 *       A tag só é usada se o banco não trouxer informação suficiente.</li>
 *   <li>Lançamento manual (ainda não consolidado): a tag decide, depois a conta.</li>
 *   <li>Subperfil (ex.: Pedro): tag cujo perfil é filho do perfil decidido.</li>
 * </ul>
 */
public final class ProfileResolver {

    public record Facts(String accountId, String cardFinal, boolean manual, Collection<String> tags) {
    }

    public Assignment resolve(Facts facts, RuleSet rules) {
        ProfileRef base = null;
        String rule = null;

        if (facts.manual()) {
            base = rootFromTags(facts.tags(), rules);
            rule = "TAG";
        }
        if (base == null && (base = rules.cardFinal(facts.cardFinal())) != null) {
            rule = "CARD_FINAL";
        }
        if (base == null && (base = rules.account(facts.accountId())) != null) {
            rule = "ACCOUNT";
        }
        if (base == null && !facts.manual() && (base = rootFromTags(facts.tags(), rules)) != null) {
            rule = "TAG";
        }
        if (base == null) {
            return Assignment.UNASSIGNED;
        }
        return new Assignment(base.id(), subprofile(base, facts.tags(), rules), rule);
    }

    private static ProfileRef rootFromTags(Collection<String> tags, RuleSet rules) {
        for (String tag : tags) {
            ProfileRef p = rules.tag(tag);
            if (p != null && p.isRoot()) {
                return p;
            }
        }
        // Só tag de subperfil (ex.: "Pedro"): sobe para o perfil pai.
        for (String tag : tags) {
            ProfileRef p = rules.tag(tag);
            if (p != null) {
                return new ProfileRef(p.parentId(), null, null);
            }
        }
        return null;
    }

    private static Long subprofile(ProfileRef base, Collection<String> tags, RuleSet rules) {
        for (String tag : tags) {
            ProfileRef p = rules.tag(tag);
            if (p != null && Objects.equals(p.parentId(), base.id())) {
                return p.id();
            }
        }
        return null;
    }
}
