package com.maxbenin.financeutils.profile;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Fotografia das regras de perfil, carregada uma vez por sincronização. */
public final class RuleSet {

    private final Map<String, ProfileRef> byCardFinal = new HashMap<>();
    private final Map<String, ProfileRef> byAccount = new HashMap<>();
    private final Map<String, ProfileRef> byTag = new HashMap<>();

    public RuleSet add(RuleType type, String value, ProfileRef profile) {
        switch (type) {
            case CARD_FINAL -> byCardFinal.put(value, profile);
            case ACCOUNT -> byAccount.put(value, profile);
            case TAG -> byTag.put(normalizeTag(value), profile);
        }
        return this;
    }

    public static RuleSet from(List<ProfileRule> rules) {
        RuleSet set = new RuleSet();
        rules.forEach(r -> set.add(r.getType(), r.getMatchValue(), ProfileRef.of(r.getProfile())));
        return set;
    }

    ProfileRef cardFinal(String finalDigits) { return finalDigits == null ? null : byCardFinal.get(finalDigits); }
    ProfileRef account(String accountId) { return accountId == null ? null : byAccount.get(accountId); }
    ProfileRef tag(String tag) { return tag == null ? null : byTag.get(normalizeTag(tag)); }

    private static String normalizeTag(String tag) {
        return tag.trim().toLowerCase(Locale.ROOT);
    }
}
