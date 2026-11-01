package com.maxbenin.financeutils.dinzo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração de acesso ao Dinzo.
 *
 * @param mode       {@code mcp} (servidor MCP do Dinzo via HTTP) ou {@code fixture} (arquivos JSON locais)
 * @param mcpUrl     endpoint MCP (Streamable HTTP) do Dinzo
 * @param token      token Bearer de acesso (definir via variável de ambiente DINZO_TOKEN)
 * @param fixtureDir diretório com accounts.json e transactions.json no formato das ferramentas do Dinzo
 */
@ConfigurationProperties(prefix = "dinzo")
public record DinzoProperties(String mode, String mcpUrl, String token, String fixtureDir) {
}
