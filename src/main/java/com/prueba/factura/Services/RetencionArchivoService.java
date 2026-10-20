package com.prueba.factura.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Retención de archivos (RayBag): los archivos con más de N días (30 por defecto)
 * se ARCHIVAN (se mueven, nunca se borran) para no perder información
 * antes de cumplir el mes de retención.
 */
@Service
public class RetencionArchivoService {

    private static final Logger logger = LoggerFactory.getLogger(RetencionArchivoService.class);
    private static final DateTimeFormatter CARPETA_MES = DateTimeFormatter.ofPattern("yyyy-MM");

    @Value("${app.raybag.directorio:raybag}")
    private String directorioRayBag;

    @Value("${app.raybag.dias:30}")
    private int diasRetencion;

    @Value("${app.raybag.directorios:raybag,facturas_impresas}")
    private String directoriosVigilados;

    /** Se ejecuta a diario (2:15 AM) y archiva lo que ya cumplió la retención. */
    @Scheduled(cron = "${app.raybag.cron:0 15 2 * * *}")
    public void archivarVencidos() {
        Map<String, Object> resultado = archivar();
        logger.info("Retención RayBag ejecutada: {}", resultado);
    }
    public Map<String, Object> archivar() {
        Map<String, Object> resultado = new LinkedHashMap<>();
        List<String> movidos = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - (diasRetencion * 24L * 60L * 60L * 1000L);

        for (String dir : directoriosVigilados().split(",")) {
            String origen = dir.trim();
            if (origen.isBlank()) {
                continue;
            }
            Path path = Paths.get(origen);
            if (!Files.isDirectory(path)) {
                continue;
            }
            try (java.util.stream.Stream<Path> archivos = Files.list(path)) {
                for (Path archivo : archivos.toList()) {
                    if (!Files.isRegularFile(archivo)) {
                        continue;
                    }
                    long modificado = Files.getLastModifiedTime(archivo).toMillis();
                    if (modificado < cutoff) {
                        Path destino = carpetaArchivo(archivo);
                        Files.createDirectories(destino.getParent());
                        Files.move(archivo, destino, StandardCopyOption.REPLACE_EXISTING);
                        movidos.add(archivo.getFileName().toString());
                    }
                }
            } catch (IOException e) {
                logger.warn("No se pudo archivar la carpeta {}: {}", origen, e.getMessage());
            }
        }

        resultado.put("diasRetencion", diasRetencion);
        resultado.put("archivosArchivados", movidos.size());
        resultado.put("detalle", movidos);
        return resultado;
    }

    /** Estado actual de la retención: cuántos archivos hay y cuántos ya fueron archivados. */
    public Map<String, Object> estado() {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("directorioRayBag", directorioRayBag);
        estado.put("diasRetencion", diasRetencion);
        estado.put("directoriosVigilados", directoriosVigilados());

        List<String> activos = new ArrayList<>();
        List<String> archivados = new ArrayList<>();
        for (String dir : directoriosVigilados().split(",")) {
            String base = dir.trim();
            if (base.isBlank()) {
                continue;
            }
            Path path = Paths.get(base);
            if (Files.isDirectory(path)) {
                try (java.util.stream.Stream<Path> archivos = Files.list(path)) {
                    archivos.filter(Files::isRegularFile).forEach(p -> activos.add(p.getFileName().toString()));
                } catch (IOException e) {
                    logger.warn("No se pudo listar {}: {}", base, e.getMessage());
                }
            }
            Path carpetaArchivos = Paths.get(base, "archivo");
            if (Files.isDirectory(carpetaArchivos)) {
                try (java.util.stream.Stream<Path> archivos = Files.walk(carpetaArchivos)) {
                    archivos.filter(Files::isRegularFile).forEach(p -> archivados.add(p.getFileName().toString()));
                } catch (IOException e) {
                    logger.warn("No se pudo listar archivados de {}: {}", base, e.getMessage());
                }
            }
        }
        estado.put("archivosActivos", activos.size());
        estado.put("archivosArchivados", archivados.size());
        return estado;
    }

    private Path carpetaArchivo(Path archivo) {
        LocalDate mes = LocalDate.now();
        return Paths.get(archivo.getParent().toString(), "archivo", mes.format(CARPETA_MES), archivo.getFileName().toString());
    }

    private String directoriosVigilados() {
        return (directoriosVigilados == null || directoriosVigilados.isBlank())
                ? directorioRayBag
                : directoriosVigilados;
    }
}
