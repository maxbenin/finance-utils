package com.maxbenin.financeutils.category;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Categoria do app. Começa como cópia das categorias do Dinzo; depois é refinada no app. */
@Entity
@Table(name = "category")
public class Category {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "parent_id")
    private String parentId;

    @Column(name = "color_hex")
    private String colorHex;

    private boolean archived;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Category() {
    }

    public Category(String id, String name, String colorHex, boolean archived, Instant now) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.name = name;
        this.colorHex = colorHex;
        this.archived = archived;
        this.createdAt = now;
    }

    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getParentId() { return parentId; }
    public String getColorHex() { return colorHex; }
    public boolean isArchived() { return archived; }
}
