package com.maxbenin.financeutils.config;

import com.maxbenin.financeutils.profile.*;
import com.maxbenin.financeutils.account.Account;
import com.maxbenin.financeutils.account.AccountKind;
import com.maxbenin.financeutils.account.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static com.maxbenin.financeutils.config.SeedProperties.orEmpty;

/** Carrega perfis e regras iniciais apenas se o banco ainda não tiver nenhum perfil. */
@Component
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final SeedProperties seed;
    private final ProfileRepository profiles;
    private final ProfileRuleRepository rules;
    private final ContributorRuleRepository contributors;
    private final AccountRepository accounts;

    public SeedLoader(SeedProperties seed, ProfileRepository profiles, ProfileRuleRepository rules,
                      ContributorRuleRepository contributors, AccountRepository accounts) {
        this.seed = seed;
        this.profiles = profiles;
        this.rules = rules;
        this.contributors = contributors;
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (profiles.count() > 0 || orEmpty(seed.profiles()).isEmpty()) {
            return;
        }
        for (SeedProperties.ProfileSeed p : seed.profiles()) {
            Profile parent = p.parent() == null ? null : profile(p.parent());
            profiles.save(new Profile(p.name(), parent));
        }
        orEmpty(seed.cardFinals()).forEach(c ->
                rules.save(new ProfileRule(RuleType.CARD_FINAL, c.cardFinal(), profile(c.profile()), c.user())));
        orEmpty(seed.accounts()).forEach(a ->
                rules.save(new ProfileRule(RuleType.ACCOUNT, a.id(), profile(a.profile()), null)));
        orEmpty(seed.tags()).forEach(t ->
                rules.save(new ProfileRule(RuleType.TAG, t.tag(), profile(t.profile()), null)));
        orEmpty(seed.contributors()).forEach(c ->
                contributors.save(new ContributorRule(c.contributor(), c.descriptionContains(), profile(c.targetProfile()))));
        orEmpty(seed.untrackedAccounts()).forEach(id ->
                accounts.save(new Account(id, "(aguardando sincronização)", AccountKind.CHECKING, false, Instant.now())));
        log.info("Regras iniciais carregadas: {} perfis", profiles.count());
    }

    private Profile profile(String name) {
        return profiles.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Perfil não declarado no seed: " + name));
    }
}
