package com.distribuidora.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

// "Hoje" do negocio (vencimento de lote, titulos) e no fuso da empresa, nao
// no do servidor (Render roda em UTC: 21h em Fortaleza ja seria "amanha").
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:America/Fortaleza}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
