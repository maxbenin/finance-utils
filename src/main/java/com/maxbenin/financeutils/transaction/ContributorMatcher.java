package com.maxbenin.financeutils.transaction;

import com.maxbenin.financeutils.profile.ContributorRule;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Reconhece o contribuinte de um crédito pela descrição, restrito ao perfil de destino. */
public final class ContributorMatcher {

    public record Rule(String contributor, String descriptionContains, long targetProfileId) {
        public static Rule of(ContributorRule r) {
            return new Rule(r.getContributor(), r.getDescriptionContains(), r.getTargetProfile().getId());
        }
    }

    private final List<Rule> rules;

    public ContributorMatcher(List<Rule> rules) {
        this.rules = rules;
    }

    public Optional<String> match(String description, Long entryProfileId) {
        if (description == null || entryProfileId == null) {
            return Optional.empty();
        }
        String text = normalize(description);
        return rules.stream()
                .filter(r -> Objects.equals(r.targetProfileId(), entryProfileId))
                .filter(r -> text.contains(normalize(r.descriptionContains())))
                .map(Rule::contributor)
                .findFirst();
    }

    static String normalize(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT)
                .trim();
    }
}
