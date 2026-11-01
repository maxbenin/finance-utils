package com.maxbenin.financeutils.profile;

import jakarta.persistence.*;

@Entity
@Table(name = "profile_rule")
public class ProfileRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType type;

    @Column(name = "match_value", nullable = false)
    private String matchValue;

    @ManyToOne(optional = false)
    @JoinColumn(name = "profile_id")
    private Profile profile;

    @Column(name = "card_user")
    private String cardUser;

    protected ProfileRule() {
    }

    public ProfileRule(RuleType type, String matchValue, Profile profile, String cardUser) {
        this.type = type;
        this.matchValue = matchValue;
        this.profile = profile;
        this.cardUser = cardUser;
    }

    public Long getId() { return id; }
    public RuleType getType() { return type; }
    public String getMatchValue() { return matchValue; }
    public Profile getProfile() { return profile; }
    public String getCardUser() { return cardUser; }
}
