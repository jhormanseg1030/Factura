package com.prueba.factura.Services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Medición de tiempos en memoria para detectar cuellos de botella
 * (parseo XML, envío HTTP, ciclo de reintentos, watcher).
 */
@Service
public class MetricasService {

    private static final int MAX_MUESTRAS = 200;

    private final Map<String, List<Long>> muestras = new ConcurrentHashMap<>();

    public void registrar(String operacion, long nanos) {
        List<Long> lista = muestras.computeIfAbsent(operacion, k -> new ArrayList<>());
        synchronized (lista) {
            lista.add(nanos);
            while (lista.size() > MAX_MUESTRAS) {
                lista.remove(0);
            }
        }
    }

    public <T> T medir(String operacion, java.util.function.Supplier<T> accion) {
        long inicio = System.nanoTime();
        try {
            return accion.get();
        } finally {
            registrar(operacion, System.nanoTime() - inicio);
        }
    }

    public void medir(String operacion, Runnable accion) {
        long inicio = System.nanoTime();
        try {
            accion.run();
        } finally {
            registrar(operacion, System.nanoTime() - inicio);
        }
    }

    public Map<String, Object> resumen() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> entry : muestras.entrySet()) {
            List<Long> lista;
            synchronized (entry.getValue()) {
                lista = new ArrayList<>(entry.getValue());
            }
            if (lista.isEmpty()) {
                continue;
            }
            long min = lista.get(0);
            long max = lista.get(0);
            long suma = 0;
            for (Long n : lista) {
                min = Math.min(min, n);
                max = Math.max(max, n);
                suma += n;
            }
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("muestras", lista.size());
            datos.put("minMs", aMs(min));
            datos.put("promMs", aMs(suma / lista.size()));
            datos.put("maxMs", aMs(max));
            out.put(entry.getKey(), datos);
        }
        return out;
    }

    private long aMs(long nanos) {
        return nanos / 1_000_000L;
    }
}
