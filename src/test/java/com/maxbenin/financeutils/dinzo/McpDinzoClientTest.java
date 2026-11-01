package com.maxbenin.financeutils.dinzo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class McpDinzoClientTest {

    final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void handshakeThenPaginatesTransactionsOverSse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        McpDinzoClient client = new McpDinzoClient(builder,
                new DinzoProperties("mcp", "https://dinzo.test/mcp", "tok", null), mapper);

        HttpHeaders session = new HttpHeaders();
        session.add("Mcp-Session-Id", "s-1");
        server.expect(requestTo("https://dinzo.test/mcp"))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(jsonPath("$.method").value("initialize"))
                .andRespond(withSuccess("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}", MediaType.APPLICATION_JSON).headers(session));
        server.expect(jsonPath("$.method").value("notifications/initialized"))
                .andExpect(header("Mcp-Session-Id", "s-1"))
                .andRespond(withSuccess());
        server.expect(jsonPath("$.params.name").value("listar_lancamentos"))
                .andExpect(jsonPath("$.params.arguments.view").value("ledger"))
                .andExpect(jsonPath("$.params.arguments.page").value(1))
                .andRespond(withSuccess(sse(2, page("t1", 101)), MediaType.TEXT_EVENT_STREAM));
        server.expect(jsonPath("$.params.arguments.page").value(2))
                .andRespond(withSuccess(sse(3, page("t2", 101)), MediaType.TEXT_EVENT_STREAM));

        var list = client.listTransactions(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(list).extracting(DinzoTransaction::id).containsExactly("t1", "t2");
        server.verify();
    }

    @Test
    void parsesPlainJsonResponse() {
        McpDinzoClient client = new McpDinzoClient(RestClient.builder(),
                new DinzoProperties("mcp", "https://x", null, null), mapper);
        assertThat(client.parseBody("{\"id\":7,\"result\":{}}", 7).path("id").asInt()).isEqualTo(7);
    }

    private String page(String id, int total) {
        String tx = "{\"id\":\"%s\",\"projected\":false,\"date\":\"2026-10-10\",\"type\":\"DEBIT\",\"status\":\"POSTED\",\"amount\":10,\"account_id\":\"a\",\"tags\":[],\"source\":\"OPEN_FINANCE\"}".formatted(id);
        return "{\"transactions\":[" + tx + "],\"total\":" + total + "}";
    }

    private String sse(int id, String toolJson) {
        try {
            String text = mapper.writeValueAsString(toolJson);
            return "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":" + id
                    + ",\"result\":{\"content\":[{\"type\":\"text\",\"text\":" + text + "}]}}\n\n";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
