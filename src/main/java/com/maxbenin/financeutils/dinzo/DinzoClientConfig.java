package com.maxbenin.financeutils.dinzo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

@Configuration
public class DinzoClientConfig {

    @Bean
    DinzoClient dinzoClient(DinzoProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        return switch (props.mode() == null ? "fixture" : props.mode()) {
            case "mcp" -> new McpDinzoClient(builder, props, mapper);
            case "fixture" -> new FixtureDinzoClient(Path.of(props.fixtureDir()), mapper);
            default -> throw new IllegalStateException("dinzo.mode inválido: " + props.mode());
        };
    }
}
