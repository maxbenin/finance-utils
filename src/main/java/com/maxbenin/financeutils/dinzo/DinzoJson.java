package com.maxbenin.financeutils.dinzo;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Converte o JSON das ferramentas do Dinzo nos modelos da aplicação. */
public final class DinzoJson {

    private DinzoJson() {
    }

    public static List<DinzoAccount> accounts(JsonNode root) {
        List<DinzoAccount> result = new ArrayList<>();
        for (JsonNode a : root.path("accounts")) {
            result.add(new DinzoAccount(
                    text(a, "id"), text(a, "title"), text(a, "type"), text(a, "sub_type"),
                    a.path("archived").asBoolean(false)));
        }
        return result;
    }

    public static List<DinzoTransaction> transactions(JsonNode root) {
        List<DinzoTransaction> result = new ArrayList<>();
        for (JsonNode t : root.path("transactions")) {
            result.add(transaction(t));
        }
        return result;
    }

    public static DinzoTransaction transaction(JsonNode t) {
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : t.path("tags")) {
            tags.add(tag.path("title").asText());
        }
        JsonNode inst = t.path("installment");
        return new DinzoTransaction(
                text(t, "id"),
                t.path("projected").asBoolean(false),
                LocalDate.parse(t.path("date").asText()),
                text(t, "type"),
                text(t, "status"),
                new BigDecimal(t.path("amount").asText("0")),
                text(t, "description"),
                text(t, "original_description"),
                text(t, "notes"),
                text(t, "account_id"),
                text(t, "bill_to_account_id"),
                text(t, "category_id"),
                tags,
                text(t, "recurrence_id"),
                inst.isObject() ? inst.path("current").asInt() : null,
                inst.isObject() ? inst.path("total").asInt() : null,
                text(t, "transfer_group_id"),
                text(t, "source"),
                t.path("hide_from_results").asBoolean(false),
                text(t, "card_number"),
                t.toString());
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isEmpty() ? null : v.asText();
    }
}
