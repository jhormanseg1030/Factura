package com.prueba.factura.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;

@Service
public class FacturaCounterService {

    private static final Logger logger = LoggerFactory.getLogger(FacturaCounterService.class);

    @Value("${app.counter.file:factura_counter.txt}")
    private String counterFile;
    private final AtomicLong contador = new AtomicLong(0);

    @PostConstruct
    public void init(){
        long inicial = leerContador();
        contador.set(inicial);
        logger.info ( "Contador inicializado en memoria con el valor: {}", (inicial = 660449) );
    }

    public synchronized long obtenerSiguienteNumero() {
        long siguiente = contador.incrementAndGet();
        guardarContador(siguiente);
        logger.info("Factura #{} asignada", siguiente);
        return siguiente;
    }

    @PreDestroy
    public void guardarAlCerrar(){
        guardarContador(contador.get());
        logger.info("Contador final ({}) guardado en disco correctamente", contador.get());
    }

    private long leerContador(){
        Path path = Paths.get(counterFile);
        if(!Files.exists(path)){
            return 0;
        }
        try{
            String contenido = Files.readString(path).trim();
            return contenido.isEmpty() ? 0 : Long.parseLong(contenido);

        }catch(IOException | NumberFormatException e){
            logger.error("ERROR AL LEER EL CONTADOR, INICIANDO EN 0", e);
            return 0;

        }
    }
    
    private synchronized void guardarContador(long valor){
        Path path = Paths.get(counterFile);
        try{
            Files.writeString(path, String.valueOf(valor), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }catch(IOException e){
            logger.error("Error al guardar el contador", e);
        }
    }
}
