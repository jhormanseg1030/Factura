package com.prueba.factura.Services;


import java.net.URL;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.prueba.factura.dto.FacturaDto;

@Service
public class ConeccionWeb {

    private static final Logger logger = LoggerFactory.getLogger(ConeccionWeb.class);
    
    @Value("${app.webhook.url}")
    private String url;
    
    private final RestTemplate restTemplate;

    public ConeccionWeb() {
        this.restTemplate = new RestTemplate(getClientHttpRequestFactory());
    }
    
    private ClientHttpRequestFactory getClientHttpRequestFactory() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectionRequestTimeout(5000);
        factory.setReadTimeout(10000);
        
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);
        connectionManager.setDefaultMaxPerRoute(10);
        
    HttpClient httpClient = HttpClientBuilder.create()
        .setConnectionManager(connectionManager)
        .build();

        factory.setHttpClient(httpClient);
        return factory;
    }

    public void enviarFactura(FacturaDto factura){
        try{
            if (factura == null || factura.getCheckId() == null) {
                logger.warn("Intento de enviar factura nula");
                return;
            }
            
            // Validar URL
            if (!isValidUrl(url)) {
                logger.error("URL de webhook inválida");
                return;
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("User-Agent", "FacturaApp/1.0");

            HttpEntity<FacturaDto> request = new HttpEntity<>(factura, headers);

            logger.debug("Enviando factura");

            String response = restTemplate.postForObject(url, request, String.class);
            logger.info("Factura enviada exitosamente");

        }catch(HttpClientErrorException e){
            logger.error("Error HTTP al enviar factura: {}", e.getStatusCode());
        }catch(Exception e){
            logger.error("Error al enviar la factura", e);
        }
    }
    
    private boolean isValidUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.getProtocol().equals("https") && !url.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
