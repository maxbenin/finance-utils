package com.maxbenin.financeutils.profile;

/** Resultado da atribuição de perfil; profileId nulo = vai para a fila "Avaliar". */
public record Assignment(Long profileId, Long subprofileId, String rule) {

    public static final Assignment UNASSIGNED = new Assignment(null, null, "NONE");

    public boolean isAssigned() {
        return profileId != null;
    }
}
