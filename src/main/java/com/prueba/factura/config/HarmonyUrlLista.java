package com.prueba.factura.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class HarmonyUrlLista {

    private static final Logger logger = LoggerFactory.getLogger(HarmonyUrlLista.class);

    private final Environment environment;

    public HarmonyUrlLista(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publicarUrl() {
        String host = environment.getProperty(HarmonyPuertoConfig.CLAVE_HOST, "localhost");
        String puerto = environment.getProperty(HarmonyPuertoConfig.CLAVE_PUERTO, "8080");
        logger.info("En el EMC, PartnerCommunication/REST/URL debe quedar: {}",
                HarmonyPuertoConfig.urlPartner(host, Integer.parseInt(puerto)));
    }
}
