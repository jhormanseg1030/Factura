package com.prueba.factura.Services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class FacturaEnviadaService {

    private static final Logger logger = LoggerFactory.getLogger(FacturaEnviadaService.class);
    private static final int MAXIMO = 2000;

    private final Object estadosLock = new Object();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private RepositorioFacturas repositorio;

    @Value("${app.enviadas.file:facturas_enviadas.json}")
    private String archivo;

    public record Resumen(
            String id,
            String numero,
            String fecha,
            String cliente,
            String tipo,
            String cheque,
            long total,
            String estadoEnvio,
            String mensajeEnvio,
            String estadoDian,
            String detalleDian,
            String consultadoEn,
            boolean reimprimible,
            String cufe) {
    }

    void usarArchivo(Path ruta) {
        this.archivo = ruta.toString();
    }

    public synchronized void registrar(String id, String numero, String prefijo, long consecutivo, String cufe,
            String fecha, String cliente, String tipo, long total, String ticket, String cheque,
            String estadoEnvio, String mensajeEnvio, String payload) {
        String clave = id == null || id.isBlank() ? numero : id.trim();
        if (clave == null || clave.isBlank()) {
            return;
        }
        if (sql()) {
            try {
                Map<String, Object> fila = new LinkedHashMap<>();
                fila.put("id", clave);
                fila.put("numero", numero == null ? "" : numero);
                fila.put("prefijo", prefijo == null ? "" : prefijo);
                fila.put("consecutivo", consecutivo);
                fila.put("cufe", cufe == null ? "" : cufe);
                fila.put("fecha", fecha == null ? "" : fecha);
                fila.put("cliente", cliente == null || cliente.isBlank() ? "Consumidor Final" : cliente);
                fila.put("tipo", tipo == null || tipo.isBlank() ? "FV" : tipo);
                fila.put("total", total);
                fila.put("cheque", cheque == null ? "" : cheque);
                fila.put("ticket", quitarSecreto(ticket));
                fila.put("payload", payload == null ? "" : payload);
                fila.put("estadoEnvio", estadoEnvio == null ? "pendiente" : estadoEnvio);
                fila.put("mensajeEnvio", acotar(mensajeEnvio));
                repositorio.guardarEmitida(fila);
                return;
            } catch (RuntimeException e) {
                logger.error("No se pudo guardar la factura en SQL Server. Se usa el archivo.", e);
            }
        }
        List<Map<String, Object>> todas = leer();
        Map<String, Object> previa = null;
        for (Map<String, Object> fila : todas) {
            if (clave.equals(fila.get("id"))) {
                previa = fila;
                break;
            }
        }
        Map<String, Object> fila = previa == null ? new LinkedHashMap<>() : previa;
        fila.put("id", clave);
        fila.put("numero", numero == null ? "" : numero);
        fila.put("prefijo", prefijo == null ? "" : prefijo);
        fila.put("consecutivo", consecutivo);
        fila.put("cufe", cufe == null ? "" : cufe);
        fila.put("fecha", fecha == null ? "" : fecha);
        fila.put("cliente", cliente == null || cliente.isBlank() ? "Consumidor Final" : cliente);
        fila.put("tipo", tipo == null || tipo.isBlank() ? "FV" : tipo);
        fila.put("total", total);
        fila.put("cheque", cheque == null ? "" : cheque);
        fila.put("ticket", quitarSecreto(ticket));
        if (payload != null && !payload.isBlank()) {
            fila.put("payload", payload);
        }
        fila.put("estadoEnvio", estadoEnvio == null ? "pendiente" : estadoEnvio);
        fila.put("mensajeEnvio", acotar(mensajeEnvio));
        if (previa == null) {
            fila.put("estadoDian", "pendiente");
            fila.put("detalleDian", "En espera de la DIAN");
            fila.put("consultadoEn", "");
            todas.add(0, fila);
        } else {
            todas.remove(previa);
            todas.add(0, fila);
        }
        while (todas.size() > MAXIMO) {
            todas.remove(todas.size() - 1);
        }
        guardar(todas);
        asegurarEstado(clave, numero, cufe, fecha);
    }

    public synchronized void marcarEnvio(String jsonPayload, boolean aceptado, String mensaje) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return;
        }
        String cufe = "";
        String numero = "";
        try {
            JsonNode nodo = objectMapper.readTree(jsonPayload);
            cufe = nodo.path("cufe").asText("");
            String prefijo = nodo.path("Prefijo").asText("");
            JsonNode consecutivo = nodo.get("numero_factura");
            if (consecutivo != null && consecutivo.isNumber()) {
                numero = prefijo + consecutivo.asLong();
            } else if (consecutivo != null) {
                numero = prefijo + consecutivo.asText("");
            }
        } catch (Exception e) {
            logger.warn("No se pudo leer la factura reenviada: {}", e.getMessage());
            return;
        }
        if (sql()) {
            try {
                marcarEnvioSql(cufe, numero, aceptado, mensaje);
                return;
            } catch (RuntimeException e) {
                logger.error("No se pudo actualizar el envío en SQL Server. Se usa el archivo.", e);
            }
        }
        List<Map<String, Object>> todas = leer();
        for (Map<String, Object> fila : todas) {
            boolean coincide = !cufe.isBlank() && cufe.equals(fila.get("cufe"));
            if (!coincide && !numero.isBlank() && numero.equals(fila.get("numero"))) {
                coincide = true;
            }
            if (coincide) {
                fila.put("estadoEnvio", aceptado ? "enviada" : "error");
                fila.put("mensajeEnvio", acotar(mensaje));
                guardar(todas);
                return;
            }
        }
    }

    private void marcarEnvioSql(String cufe, String numero, boolean aceptado, String mensaje) {
        repositorio.marcarEnvio(cufe, numero, aceptado ? "enviada" : "error", acotar(mensaje));
    }

    public synchronized String cufeDeNumero(String numero) {
        if (numero == null || numero.isBlank()) {
            return "";
        }
        String buscado = numero.trim();
        if (sql()) {
            try {
                return repositorio.cufeDeNumero(buscado);
            } catch (RuntimeException e) {
                logger.error("No se pudo buscar el CUFE en SQL Server. Se usa el archivo.", e);
            }
        }
        String cualquiera = "";
        for (Map<String, Object> fila : leer()) {
            if (!buscado.equalsIgnoreCase(texto(fila.get("numero")))) {
                continue;
            }
            String cufe = texto(fila.get("cufe"));
            if (cufe.isBlank()) {
                continue;
            }
            if ("FV".equalsIgnoreCase(texto(fila.get("tipo")))) {
                return cufe;
            }
            if (cualquiera.isBlank()) {
                cualquiera = cufe;
            }
        }
        return cualquiera;
    }

    public synchronized List<Resumen> listar() {
        if (sql()) {
            try {
                List<Resumen> salida = new ArrayList<>();
                for (Map<String, Object> fila : repositorio.leerEmitidas()) {
                    salida.add(resumen(fila));
                }
                return salida;
            } catch (RuntimeException e) {
                logger.error("No se pudo leer el reporte en SQL Server. Se usa el archivo.", e);
            }
        }
        Map<String, Map<String, Object>> estados = indiceEstados();
        List<Resumen> salida = new ArrayList<>();
        for (Map<String, Object> fila : leer()) {
            Resumen resumen = resumen(fila);
            Map<String, Object> estado = estados.get(resumen.id());
            salida.add(estado == null ? resumen : conEstado(resumen, estado));
        }
        return salida;
    }

    public record Consulta(String id, String numero, String cufe) {
    }

    public Consulta siguienteConsulta() {
        if (sql()) {
            try {
                return elegirConsulta(repositorio.leerParaConsulta());
            } catch (RuntimeException e) {
                logger.error("No se pudo elegir la consulta en SQL Server. Se usa el archivo.", e);
            }
        }
        synchronized (estadosLock) {
            return elegirConsulta(leerEstados());
        }
    }

    private Consulta elegirConsulta(List<Map<String, Object>> filas) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate hoy = ahora.toLocalDate();
        Map<String, Object> elegido = null;
        for (Map<String, Object> fila : filas) {
            if (!sePuedeConsultar(fila, ahora, hoy)) {
                continue;
            }
            if (elegido == null || vaAntes(fila, elegido)) {
                elegido = fila;
            }
        }
        if (elegido == null) {
            return null;
        }
        return new Consulta(texto(elegido.get("id")), texto(elegido.get("numero")), texto(elegido.get("cufe")));
    }

    public void anotarConsulta(String id, String estado, String detalle, boolean falloPortal) {
        if (sql()) {
            try {
                repositorio.anotarConsulta(id,
                        estado == null || estado.isBlank() ? "pendiente" : estado,
                        acotar(detalle), falloPortal, LocalDateTime.now().toString());
                return;
            } catch (RuntimeException e) {
                logger.error("No se pudo guardar el estado DIAN en SQL Server. Se usa el archivo.", e);
            }
        }
        synchronized (estadosLock) {
            List<Map<String, Object>> todas = leerEstados();
            Map<String, Object> fila = buscarEstado(todas, id);
            if (fila == null) {
                return;
            }
            fila.put("estadoDian", estado == null || estado.isBlank() ? "pendiente" : estado);
            fila.put("detalleDian", acotar(detalle));
            fila.put("consultadoEn", LocalDateTime.now().toString());
            fila.put("intentos", entero(fila.get("intentos")) + 1);
            fila.put("falloPortal", falloPortal);
            guardarEstados(todas);
        }
    }

    public void guardarEstadoDian(String id, String estado, String detalle) {
        anotarConsulta(id, estado, detalle, "error".equals(estado));
    }

    static long esperaMillis(int intentos, boolean falloPortal) {
        if (falloPortal) {
            return Duration.ofMinutes(10).toMillis();
        }
        return switch (intentos) {
            case 0 -> 0L;
            case 1 -> Duration.ofMinutes(1).toMillis();
            case 2 -> Duration.ofMinutes(3).toMillis();
            case 3 -> Duration.ofMinutes(10).toMillis();
            default -> Duration.ofMinutes(30).toMillis();
        };
    }

    public synchronized Resumen resumenDe(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (Map<String, Object> fila : filas()) {
            if (id.equals(fila.get("id"))) {
                return resumen(fila);
            }
        }
        return null;
    }

    public synchronized String payloadDe(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        for (Map<String, Object> fila : filas()) {
            if (id.equals(fila.get("id"))) {
                return texto(fila.get("payload"));
            }
        }
        return "";
    }

    public synchronized void marcarPorId(String id, boolean aceptado, String mensaje) {
        if (sql()) {
            try {
                repositorio.marcarPorId(id, aceptado ? "enviada" : "error", acotar(mensaje), aceptado);
                return;
            } catch (RuntimeException e) {
                logger.error("No se pudo marcar la factura en SQL Server. Se usa el archivo.", e);
            }
        }
        List<Map<String, Object>> todas = leer();
        for (Map<String, Object> fila : todas) {
            if (id != null && id.equals(fila.get("id"))) {
                fila.put("estadoEnvio", aceptado ? "enviada" : "error");
                fila.put("mensajeEnvio", acotar(mensaje));
                if (aceptado) {
                    fila.put("estadoDian", "pendiente");
                    fila.put("detalleDian", "En espera de la DIAN");
                }
                guardar(todas);
                if (aceptado) {
                    reiniciarConsulta(id);
                }
                return;
            }
        }
    }

    public synchronized JsonNode ticket(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (Map<String, Object> fila : filas()) {
            if (id.equals(fila.get("id"))) {
                Object ticket = fila.get("ticket");
                if (ticket == null || ticket.toString().isBlank()) {
                    return null;
                }
                try {
                    return objectMapper.readTree(ticket.toString());
                } catch (Exception e) {
                    logger.warn("La copia de la factura {} no se puede reimprimir: {}", id, e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    private void asegurarEstado(String id, String numero, String cufe, String fecha) {
        synchronized (estadosLock) {
            List<Map<String, Object>> todas = leerEstados();
            if (buscarEstado(todas, id) != null) {
                return;
            }
            Map<String, Object> fila = estadoNuevo(id, numero, cufe, fecha);
            todas.add(0, fila);
            guardarEstados(todas);
        }
    }

    private void reiniciarConsulta(String id) {
        synchronized (estadosLock) {
            List<Map<String, Object>> todas = leerEstados();
            Map<String, Object> fila = buscarEstado(todas, id);
            if (fila == null) {
                return;
            }
            fila.put("estadoDian", "pendiente");
            fila.put("detalleDian", "En espera de la DIAN");
            fila.put("consultadoEn", "");
            fila.put("intentos", 0);
            fila.put("falloPortal", false);
            guardarEstados(todas);
        }
    }

    private Map<String, Map<String, Object>> indiceEstados() {
        synchronized (estadosLock) {
            Map<String, Map<String, Object>> indice = new LinkedHashMap<>();
            for (Map<String, Object> fila : leerEstados()) {
                indice.put(texto(fila.get("id")), fila);
            }
            return indice;
        }
    }

    private List<Map<String, Object>> leerEstados() {
        Path path = rutaEstado();
        if (!Files.exists(path)) {
            return importarEstados();
        }
        try {
            String contenido = Files.readString(path).trim();
            if (contenido.isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(contenido, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            logger.error("No se pudo leer el estado de las facturas: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> importarEstados() {
        List<Map<String, Object>> estados = new ArrayList<>();
        Path facturas = Paths.get(archivo);
        if (!Files.exists(facturas)) {
            return estados;
        }
        try {
            String contenido = Files.readString(facturas).trim();
            if (!contenido.isEmpty()) {
                List<Map<String, Object>> todas = objectMapper.readValue(
                        contenido, new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> fila : todas) {
                    Map<String, Object> estado = estadoNuevo(
                            texto(fila.get("id")),
                            texto(fila.get("numero")),
                            texto(fila.get("cufe")),
                            texto(fila.get("fecha")));
                    String estadoDian = texto(fila.get("estadoDian"));
                    if (!estadoDian.isBlank()) {
                        estado.put("estadoDian", estadoDian);
                    }
                    String detalle = texto(fila.get("detalleDian"));
                    if (!detalle.isBlank()) {
                        estado.put("detalleDian", detalle);
                    }
                    estado.put("consultadoEn", texto(fila.get("consultadoEn")));
                    estados.add(estado);
                }
            }
        } catch (IOException e) {
            logger.warn("No se pudo preparar el estado de facturas ya guardadas: {}", e.getMessage());
        }
        guardarEstados(estados);
        return estados;
    }

    private void guardarEstados(List<Map<String, Object>> filas) {
        Path path = rutaEstado().toAbsolutePath();
        try {
            Path padre = path.getParent();
            if (padre != null) {
                Files.createDirectories(padre);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), filas);
        } catch (IOException e) {
            logger.error("No se pudo guardar el estado de las facturas en {}: {}", path, e.getMessage());
        }
    }

    private Path rutaEstado() {
        Path facturas = Paths.get(archivo);
        String nombre = facturas.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        String base = punto > 0 ? nombre.substring(0, punto) : nombre;
        Path estado = Path.of(base + "_estado.json");
        Path padre = facturas.getParent();
        return padre == null ? estado : padre.resolve(estado);
    }

    private static Map<String, Object> estadoNuevo(String id, String numero, String cufe, String fecha) {
        Map<String, Object> fila = new LinkedHashMap<>();
        fila.put("id", id == null ? "" : id);
        fila.put("numero", numero == null ? "" : numero);
        fila.put("cufe", cufe == null ? "" : cufe);
        fila.put("fecha", fecha == null ? "" : fecha);
        fila.put("estadoDian", "pendiente");
        fila.put("detalleDian", "En espera de la DIAN");
        fila.put("consultadoEn", "");
        fila.put("intentos", 0);
        fila.put("falloPortal", false);
        return fila;
    }

    private static Map<String, Object> buscarEstado(List<Map<String, Object>> todas, String id) {
        for (Map<String, Object> fila : todas) {
            if (id != null && id.equals(fila.get("id"))) {
                return fila;
            }
        }
        return null;
    }

    private static boolean sePuedeConsultar(Map<String, Object> fila, LocalDateTime ahora, LocalDate hoy) {
        if (texto(fila.get("numero")).isBlank() || !esDeHoy(texto(fila.get("fecha")), hoy)) {
            return false;
        }
        String estado = texto(fila.get("estadoDian"));
        if ("aprobada".equals(estado) || "rechazada".equals(estado) || "no_encontrado".equals(estado)) {
            return false;
        }
        int intentos = entero(fila.get("intentos"));
        if (intentos == 0 || texto(fila.get("consultadoEn")).isBlank()) {
            return true;
        }
        LocalDateTime consultado = LocalDateTime.parse(texto(fila.get("consultadoEn")));
        long espera = esperaMillis(intentos, bandera(fila.get("falloPortal")));
        return Duration.between(consultado, ahora).toMillis() >= espera;
    }

    private static boolean vaAntes(Map<String, Object> candidata, Map<String, Object> actual) {
        boolean candidataNueva = entero(candidata.get("intentos")) == 0;
        boolean actualNueva = entero(actual.get("intentos")) == 0;
        if (candidataNueva != actualNueva) {
            return candidataNueva;
        }
        if (candidataNueva) {
            return texto(candidata.get("fecha")).compareTo(texto(actual.get("fecha"))) > 0;
        }
        return texto(candidata.get("consultadoEn")).compareTo(texto(actual.get("consultadoEn"))) < 0;
    }

    private static boolean esDeHoy(String fecha, LocalDate hoy) {
        if (fecha == null || fecha.isBlank()) {
            return true;
        }
        String dia = fecha.length() >= 10 ? fecha.substring(0, 10) : fecha;
        try {
            return LocalDate.parse(dia).equals(hoy);
        } catch (Exception e) {
            return true;
        }
    }

    private static Resumen conEstado(Resumen resumen, Map<String, Object> estado) {
        return new Resumen(
                resumen.id(),
                resumen.numero(),
                resumen.fecha(),
                resumen.cliente(),
                resumen.tipo(),
                resumen.cheque(),
                resumen.total(),
                resumen.estadoEnvio(),
                resumen.mensajeEnvio(),
                texto(estado.get("estadoDian")),
                texto(estado.get("detalleDian")),
                texto(estado.get("consultadoEn")),
                resumen.reimprimible(),
                resumen.cufe());
    }

    private Resumen resumen(Map<String, Object> fila) {
        Object ticket = fila.get("ticket");
        boolean reimprimible = ticket != null && !ticket.toString().isBlank() && !"{}".equals(ticket.toString());
        return new Resumen(
                texto(fila.get("id")),
                texto(fila.get("numero")),
                texto(fila.get("fecha")),
                texto(fila.get("cliente")),
                texto(fila.get("tipo")),
                texto(fila.get("cheque")),
                numero(fila.get("total")),
                texto(fila.get("estadoEnvio")),
                texto(fila.get("mensajeEnvio")),
                texto(fila.get("estadoDian")),
                texto(fila.get("detalleDian")),
                texto(fila.get("consultadoEn")),
                reimprimible,
                texto(fila.get("cufe")));
    }

    private boolean sql() {
        return repositorio != null && repositorio.lista();
    }

    private List<Map<String, Object>> filas() {
        if (sql()) {
            try {
                return repositorio.leerEmitidas();
            } catch (RuntimeException e) {
                logger.error("No se pudo leer la factura en SQL Server. Se usa el archivo.", e);
            }
        }
        return leer();
    }

    private List<Map<String, Object>> leer() {
        Path path = Paths.get(archivo);
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            String contenido = Files.readString(path).trim();
            if (contenido.isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(contenido, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            logger.error("No se pudo leer el reporte de facturas: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void guardar(List<Map<String, Object>> filas) {
        Path path = Paths.get(archivo).toAbsolutePath();
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), filas);
        } catch (IOException e) {
            logger.error("No se pudo guardar el reporte de facturas en {}: {}", path, e.getMessage());
        }
    }

    private String quitarSecreto(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return "";
        }
        try {
            JsonNode nodo = objectMapper.readTree(ticket);
            if (nodo instanceof ObjectNode objeto) {
                objeto.remove("clave_tecnica_xml");
                objeto.remove("ClaveTecnica");
                return objectMapper.writeValueAsString(objeto);
            }
            return ticket;
        } catch (Exception e) {
            return ticket.replaceAll("\"clave_tecnica_xml\"\\s*:\\s*\"[^\"]*\"", "\"clave_tecnica_xml\":\"\"");
        }
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static boolean bandera(Object valor) {
        if (valor instanceof Boolean activo) {
            return activo;
        }
        return "true".equalsIgnoreCase(texto(valor));
    }

    private static int entero(Object valor) {
        if (valor instanceof Number numero) {
            return numero.intValue();
        }
        try {
            return Integer.parseInt(texto(valor));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long numero(Object valor) {
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        if (valor == null) {
            return 0L;
        }
        try {
            return Long.parseLong(valor.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String acotar(String texto) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.replaceAll("\\s+", " ").trim();
        return limpio.length() <= 400 ? limpio : limpio.substring(0, 400);
    }
}
