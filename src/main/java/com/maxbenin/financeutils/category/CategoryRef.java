package com.maxbenin.financeutils.category;

import jakarta.persistence.*;

@Entity
@Table(name = "category_ref")
public class CategoryRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    protected CategoryRef() {
    }

    public CategoryRef(String categoryId, String source, String externalId) {
        this.categoryId = categoryId;
        this.source = source;
        this.externalId = externalId;
    }

    public String getCategoryId() { return categoryId; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
}
