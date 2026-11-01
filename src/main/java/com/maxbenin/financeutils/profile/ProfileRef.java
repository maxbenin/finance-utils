package com.maxbenin.financeutils.profile;

/** Visão imutável de um perfil usada pelo motor de regras. */
public record ProfileRef(long id, String name, Long parentId) {

    public boolean isRoot() {
        return parentId == null;
    }

    public static ProfileRef of(Profile p) {
        return new ProfileRef(p.getId(), p.getName(), p.getParent() == null ? null : p.getParent().getId());
    }
}
