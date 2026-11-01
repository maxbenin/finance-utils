package com.maxbenin.financeutils.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    /** Datas do Dinzo são dias de calendário no fuso de Brasília. */
    @Bean
    Clock clock() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
