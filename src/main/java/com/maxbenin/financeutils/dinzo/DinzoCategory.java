package com.maxbenin.financeutils.dinzo;

/** Categoria como retornada pelo Dinzo (listar_categorias). */
public record DinzoCategory(String id, String title, String parentId, String colorHex, boolean archived) {
}
