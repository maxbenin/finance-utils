package com.maxbenin.financeutils.profile;

import jakarta.persistence.*;

@Entity
@Table(name = "profile")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Profile parent;

    private boolean active = true;

    protected Profile() {
    }

    public Profile(String name, Profile parent) {
        this.name = name;
        this.parent = parent;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Profile getParent() { return parent; }
    public boolean isActive() { return active; }
    public boolean isRoot() { return parent == null; }
}
