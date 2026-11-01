package com.maxbenin.financeutils.profile;

import jakarta.persistence.*;

/** Identifica quem fez um depósito numa conta familiar pela descrição do crédito. */
@Entity
@Table(name = "contributor_rule")
public class ContributorRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String contributor;

    @Column(name = "description_contains", nullable = false)
    private String descriptionContains;

    @ManyToOne(optional = false)
    @JoinColumn(name = "target_profile_id")
    private Profile targetProfile;

    protected ContributorRule() {
    }

    public ContributorRule(String contributor, String descriptionContains, Profile targetProfile) {
        this.contributor = contributor;
        this.descriptionContains = descriptionContains;
        this.targetProfile = targetProfile;
    }

    public String getContributor() { return contributor; }
    public String getDescriptionContains() { return descriptionContains; }
    public Profile getTargetProfile() { return targetProfile; }
}
