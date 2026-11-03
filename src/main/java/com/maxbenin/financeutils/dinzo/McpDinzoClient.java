package com.maxbenin.financeutils.dinzo;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cliente mínimo do servidor MCP do Dinzo (JSON-RPC sobre Streamable HTTP).
 * Faz o handshake initialize e chama as ferramentas via tools/call.
 *
 * <p>URL e forma de autenticação ainda precisam ser confirmadas com o Dinzo.
 */
public class McpDinzoClient implements DinzoClient {

    static final String PROTOCOL_VERSION = "2025-06-18";
    private static final int PAGE_SIZE = 100;

    private final RestClient http;
    private final JsonMapper mapper;
    private final AtomicLong ids = new AtomicLong();
    private volatile String sessionId;

    public McpDinzoClient(RestClient.Builder builder, DinzoProperties props, JsonMapper mapper) {
        RestClient.Builder b = builder.baseUrl(props.mcpUrl())
                .defaultHeader("Accept", "application/json, text/event-stream");
        if (props.token() != null && !props.token().isBlank()) {
            b.defaultHeader("Authorization", "Bearer " + props.token());
        }
        this.http = b.build();
        this.mapper = mapper;
    }

    @Override
    public List<DinzoAccount> listAccounts() {
        return DinzoJson.accounts(callTool("listar_contas", Map.of("include_archived", false)));
    }

    @Override
    public List<DinzoCategory> listCategories() {
        return DinzoJson.categories(callTool("listar_categorias", Map.of()));
    }

    @Override
    public List<DinzoTransaction> listTransactions(LocalDate from, LocalDate to) {
        List<DinzoTransaction> all = new ArrayList<>();
        int page = 1;
        while (true) {
            JsonNode result = callTool("listar_lancamentos", Map.of(
                    "from", from.toString(),
                    "to", to.toString(),
                    "view", "ledger",
                    "sort_order", "asc",
                    "page", page,
                    "page_size", PAGE_SIZE));
            List<DinzoTransaction> batch = DinzoJson.transactions(result);
            batch.stream().filter(t -> !t.projected()).forEach(all::add);
            int total = result.path("total").asInt(0);
            if (batch.isEmpty() || page * PAGE_SIZE >= total) {
                return all;
            }
            page++;
        }
    }

    JsonNode callTool(String name, Map<String, Object> arguments) {
        ensureSession();
        ObjectNode params = mapper.createObjectNode();
        params.put("name", name);
        params.set("arguments", mapper.valueToTree(arguments));
        JsonNode result = rpc("tools/call", params).path("result");
        if (result.path("isError").asBoolean(false)) {
            throw new DinzoException("Ferramenta " + name + " falhou: " + result.path("content"));
        }
        for (JsonNode c : result.path("content")) {
            if ("text".equals(c.path("type").asString())) {
                return readJson(c.path("text").asString());
            }
        }
        throw new DinzoException("Resposta sem conteúdo de texto para " + name);
    }

    private synchronized void ensureSession() {
        if (sessionId != null) {
            return;
        }
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", mapper.createObjectNode());
        params.set("clientInfo", mapper.createObjectNode().put("name", "finance-utils").put("version", "0.1.0"));
        ResponseEntity<String> resp = post(request("initialize", params));
        sessionId = resp.getHeaders().getFirst("Mcp-Session-Id");
        if (sessionId == null) {
            sessionId = ""; // servidor sem sessão (stateless)
        }
        ObjectNode notification = mapper.createObjectNode().put("jsonrpc", "2.0").put("method", "notifications/initialized");
        post(notification);
    }

    private JsonNode rpc(String method, JsonNode params) {
        ObjectNode req = request(method, params);
        JsonNode response = parseBody(post(req).getBody(), req.path("id").asLong());
        if (response.has("error")) {
            throw new DinzoException("Erro MCP em " + method + ": " + response.path("error"));
        }
        return response;
    }

    private ObjectNode request(String method, JsonNode params) {
        ObjectNode req = mapper.createObjectNode();
        req.put("jsonrpc", "2.0");
        req.put("id", ids.incrementAndGet());
        req.put("method", method);
        req.set("params", params);
        return req;
    }

    private ResponseEntity<String> post(JsonNode body) {
        RestClient.RequestBodySpec spec = http.post().contentType(MediaType.APPLICATION_JSON);
        if (sessionId != null && !sessionId.isEmpty()) {
            spec.header("Mcp-Session-Id", sessionId);
        }
        return spec.body(body.toString()).retrieve().toEntity(String.class);
    }

    /** Aceita resposta JSON direta ou em Server-Sent Events (linhas "data:"). */
    JsonNode parseBody(String body, long expectedId) {
        if (body == null || body.isBlank()) {
            throw new DinzoException("Resposta MCP vazia");
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            return readJson(trimmed);
        }
        for (String line : trimmed.split("\\R")) {
            if (line.startsWith("data:")) {
                JsonNode msg = readJson(line.substring(5).trim());
                if (msg.path("id").asLong(-1) == expectedId) {
                    return msg;
                }
            }
        }
        throw new DinzoException("Resposta MCP sem mensagem para o id " + expectedId);
    }

    private JsonNode readJson(String s) {
        return mapper.readTree(s); // Jackson 3: JacksonException é unchecked
    }
}
