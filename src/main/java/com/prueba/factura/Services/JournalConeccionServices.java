package com.prueba.factura.Services;

import java.io.File;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JournalConeccionServices implements CommandLineRunner {

    private final TicketParserService parceService;
    private final ConeccionWeb coneccion;
    private static final Logger logger = LoggerFactory.getLogger(JournalConeccionServices.class);

    public JournalConeccionServices(TicketParserService parceService, ConeccionWeb coneccion){
        this.parceService = parceService;
        this.coneccion = coneccion;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void run(String... args){
        File file = new File("C:\\Journal\\Journal.txt");

        logger.info("Escuchando el simphony {}", file.getAbsolutePath());

        StringBuilder contenido = new StringBuilder();

        TailerListenerAdapter escucha = new TailerListenerAdapter() {

            @Override
            public void handle(String line){

                contenido.append(line).append("\n");

                if(line.contains("=============") || line.contains("GRACIAS POR SU COMPRA")){ 
                    String completo = contenido.toString();

                    var factura = parceService.parseTicket(completo);

                    if(factura.getCheckId() != null || factura.getCufe() != null){
                        coneccion.enviarFactura(factura);
                    }
                    contenido.setLength(0);
                }
            }
        };
        Tailer tailer = new Tailer(file, escucha, 1000, true);
        Thread thread = new Thread(tailer);
        thread.setDaemon(true);
        thread.start();
    }

}
