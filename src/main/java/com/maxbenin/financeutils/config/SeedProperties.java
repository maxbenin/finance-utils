package com.maxbenin.financeutils.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Regras iniciais de perfis, carregadas de config/seed-local.yml (fora do git,
 * pois contém ids de contas e nomes). Modelo em config/seed.example.yml.
 */
@ConfigurationProperties(prefix = "finance.seed")
public record SeedProperties(
        List<ProfileSeed> profiles,
        List<CardFinalSeed> cardFinals,
        List<AccountSeed> accounts,
        List<TagSeed> tags,
        List<String> untrackedAccounts,
        List<ContributorSeed> contributors) {

    public record ProfileSeed(String name, String parent) {}
    public record CardFinalSeed(String cardFinal, String profile, String user) {}
    public record AccountSeed(String id, String profile) {}
    public record TagSeed(String tag, String profile) {}
    public record ContributorSeed(String contributor, String descriptionContains, String targetProfile) {}

    static <T> List<T> orEmpty(List<T> l) {
        return l == null ? List.of() : l;
    }
}
