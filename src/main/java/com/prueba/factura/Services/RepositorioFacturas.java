package com.prueba.factura.Services;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class RepositorioFacturas {

    private static final Logger logger = LoggerFactory.getLogger(RepositorioFacturas.class);
    private static final int MAXIMO = 2000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile boolean lista;

    @Value("${app.enviadas.file:facturas_enviadas.json}")
    private String archivoEnviadas;

    @Value("${app.cufe.registry.file:facturas_cufes.json}")
    private String archivoCufes;

    @Value("${app.pendientes.file:facturas_pendientes.json}")
    private String archivoPendientes;

    public RepositorioFacturas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    void iniciar() {
        try {
            EsquemaFactura.crear(jdbc);
            importarSiEstanVacias();
            lista = true;
            logger.info("Las facturas se guardan en SQL Server");
        } catch (RuntimeException e) {
            lista = false;
            logger.error("No se pudieron crear las tablas de facturas. Se siguen usando los archivos JSON.", e);
        }
    }

    public boolean lista() {
        return lista;
    }

    public void guardarEmitida(Map<String, Object> fila) {
        String id = texto(fila.get("id"));
        String payload = texto(fila.get("payload"));
        int actualizadas = jdbc.update("""
                UPDATE dbo.FacturaEmitida
                SET numero = ?, prefijo = ?, consecutivo = ?, cufe = ?, fecha = ?, cliente = ?, tipo = ?,
                    total = ?, cheque = ?, ticket = ?, estado_envio = ?, mensaje_envio = ?,
                    payload = CASE WHEN ? = '' THEN payload ELSE ? END
                WHERE id = ?
                """,
                texto(fila.get("numero")), texto(fila.get("prefijo")), numero(fila.get("consecutivo")),
                texto(fila.get("cufe")), texto(fila.get("fecha")), texto(fila.get("cliente")),
                texto(fila.get("tipo")), numero(fila.get("total")), texto(fila.get("cheque")),
                texto(fila.get("ticket")), texto(fila.get("estadoEnvio")), texto(fila.get("mensajeEnvio")),
                payload, payload, id);
        if (actualizadas == 0) {
            jdbc.update("""
                    INSERT INTO dbo.FacturaEmitida (
                        id, numero, prefijo, consecutivo, cufe, fecha, cliente, tipo, total, cheque, ticket, payload,
                        estado_envio, mensaje_envio, estado_dian, detalle_dian, consultado_en, intentos, fallo_portal)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pendiente', 'En espera de la DIAN', '', 0, 0)
                    """,
                    id, texto(fila.get("numero")), texto(fila.get("prefijo")), numero(fila.get("consecutivo")),
                    texto(fila.get("cufe")), texto(fila.get("fecha")), texto(fila.get("cliente")),
                    texto(fila.get("tipo")), numero(fila.get("total")), texto(fila.get("cheque")),
                    texto(fila.get("ticket")), payload, texto(fila.get("estadoEnvio")), texto(fila.get("mensajeEnvio")));
        }
        jdbc.update("""
                ;WITH sobra AS (
                    SELECT id, ROW_NUMBER() OVER (ORDER BY creado_en DESC, id DESC) AS n
                    FROM dbo.FacturaEmitida
                )
                DELETE FROM sobra WHERE n > ?
                """, MAXIMO);
    }

    public List<Map<String, Object>> leerEmitidas() {
        return jdbc.query("""
                SELECT TOP (?) id, numero, prefijo, consecutivo, cufe, fecha, cliente, tipo, total, cheque, ticket,
                       payload, estado_envio, mensaje_envio, estado_dian, detalle_dian, consultado_en
                FROM dbo.FacturaEmitida
                ORDER BY creado_en DESC, id DESC
                """, (rs, row) -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("id", rs.getString("id"));
                    fila.put("numero", rs.getString("numero"));
                    fila.put("prefijo", rs.getString("prefijo"));
                    fila.put("consecutivo", rs.getLong("consecutivo"));
                    fila.put("cufe", rs.getString("cufe"));
                    fila.put("fecha", rs.getString("fecha"));
                    fila.put("cliente", rs.getString("cliente"));
                    fila.put("tipo", rs.getString("tipo"));
                    fila.put("total", rs.getLong("total"));
                    fila.put("cheque", rs.getString("cheque"));
                    fila.put("ticket", rs.getString("ticket"));
                    fila.put("payload", rs.getString("payload"));
                    fila.put("estadoEnvio", rs.getString("estado_envio"));
                    fila.put("mensajeEnvio", rs.getString("mensaje_envio"));
                    fila.put("estadoDian", rs.getString("estado_dian"));
                    fila.put("detalleDian", rs.getString("detalle_dian"));
                    fila.put("consultadoEn", rs.getString("consultado_en"));
                    return fila;
                }, MAXIMO);
    }

    public List<Map<String, Object>> leerParaConsulta() {
        return jdbc.query("""
                SELECT id, numero, cufe, fecha, estado_dian, detalle_dian, consultado_en, intentos, fallo_portal
                FROM dbo.FacturaEmitida
                WHERE estado_dian NOT IN ('aprobada', 'rechazada', 'no_encontrado')
                """, (rs, row) -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("id", rs.getString("id"));
                    fila.put("numero", rs.getString("numero"));
                    fila.put("cufe", rs.getString("cufe"));
                    fila.put("fecha", rs.getString("fecha"));
                    fila.put("estadoDian", rs.getString("estado_dian"));
                    fila.put("detalleDian", rs.getString("detalle_dian"));
                    fila.put("consultadoEn", rs.getString("consultado_en"));
                    fila.put("intentos", rs.getInt("intentos"));
                    fila.put("falloPortal", rs.getBoolean("fallo_portal"));
                    return fila;
                });
    }

    public void anotarConsulta(String id, String estado, String detalle, boolean falloPortal, String consultadoEn) {
        jdbc.update("""
                UPDATE dbo.FacturaEmitida
                SET estado_dian = ?, detalle_dian = ?, consultado_en = ?, intentos = intentos + 1, fallo_portal = ?
                WHERE id = ?
                """, estado, detalle, consultadoEn, falloPortal, id);
    }

    public void marcarEnvio(String cufe, String numero, String estado, String mensaje) {
        int filas = 0;
        if (cufe != null && !cufe.isBlank()) {
            filas = jdbc.update("""
                    UPDATE dbo.FacturaEmitida SET estado_envio = ?, mensaje_envio = ? WHERE cufe = ?
                    """, estado, mensaje, cufe);
        }
        if (filas == 0 && numero != null && !numero.isBlank()) {
            jdbc.update("""
                    UPDATE dbo.FacturaEmitida SET estado_envio = ?, mensaje_envio = ? WHERE numero = ?
                    """, estado, mensaje, numero);
        }
    }

    public void marcarPorId(String id, String estado, String mensaje, boolean reiniciarDian) {
        if (reiniciarDian) {
            jdbc.update("""
                    UPDATE dbo.FacturaEmitida
                    SET estado_envio = ?, mensaje_envio = ?, estado_dian = 'pendiente',
                        detalle_dian = 'En espera de la DIAN', consultado_en = '', intentos = 0, fallo_portal = 0
                    WHERE id = ?
                    """, estado, mensaje, id);
            return;
        }
        jdbc.update("""
                UPDATE dbo.FacturaEmitida SET estado_envio = ?, mensaje_envio = ? WHERE id = ?
                """, estado, mensaje, id);
    }

    public String cufeDeNumero(String numero) {
        List<String> factura = jdbc.query("""
                SELECT TOP 1 cufe FROM dbo.FacturaEmitida
                WHERE numero = ? AND tipo = 'FV' AND cufe <> ''
                """, (rs, row) -> rs.getString(1), numero);
        if (!factura.isEmpty()) {
            return factura.get(0);
        }
        List<String> cualquiera = jdbc.query("""
                SELECT TOP 1 cufe FROM dbo.FacturaEmitida WHERE numero = ? AND cufe <> ''
                """, (rs, row) -> rs.getString(1), numero);
        return cualquiera.isEmpty() ? "" : cualquiera.get(0);
    }

    public Map<String, String> leerCufe(String identificador) {
        List<Map<String, String>> filas = jdbc.query("""
                SELECT numero, cufe FROM dbo.FacturaCufe WHERE identificador = ?
                """, (rs, row) -> {
                    Map<String, String> registro = new LinkedHashMap<>();
                    registro.put("numero_factura_completo", rs.getString("numero"));
                    registro.put("cufe", rs.getString("cufe"));
                    return registro;
                }, identificador);
        return filas.isEmpty() ? null : filas.get(0);
    }

    public void guardarCufe(String identificador, String numero, String cufe) {
        int actualizadas = jdbc.update("""
                UPDATE dbo.FacturaCufe SET numero = ?, cufe = ? WHERE identificador = ?
                """, numero, cufe, identificador);
        if (actualizadas == 0) {
            jdbc.update("""
                    INSERT INTO dbo.FacturaCufe (identificador, numero, cufe) VALUES (?, ?, ?)
                    """, identificador, numero, cufe);
        }
    }

    public String cufePorNumero(String numero) {
        List<String> filas = jdbc.query("""
                SELECT TOP 1 cufe FROM dbo.FacturaCufe WHERE numero = ? AND cufe <> ''
                """, (rs, row) -> rs.getString(1), numero);
        return filas.isEmpty() ? "" : filas.get(0);
    }

    public void guardarPendiente(String jsonPayload, String error, String fecha) {
        jdbc.update("""
                INSERT INTO dbo.FacturaPendiente (json_payload, fecha_procesamiento, intentos, ultimo_error)
                VALUES (?, ?, 1, ?)
                """, jsonPayload, fecha, error);
    }

    public List<Map<String, Object>> leerPendientes() {
        return jdbc.query("""
                SELECT id, json_payload, fecha_procesamiento, intentos, ultimo_error, ultimo_intento
                FROM dbo.FacturaPendiente
                ORDER BY id
                """, (rs, row) -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("id", rs.getInt("id"));
                    fila.put("json_payload", rs.getString("json_payload"));
                    fila.put("fecha_procesamiento", rs.getString("fecha_procesamiento"));
                    fila.put("intentos", rs.getInt("intentos"));
                    fila.put("ultimo_error", rs.getString("ultimo_error") == null ? "" : rs.getString("ultimo_error"));
                    fila.put("ultimo_intento", rs.getString("ultimo_intento") == null ? "" : rs.getString("ultimo_intento"));
                    return fila;
                });
    }

    public void incrementarPendiente(int id, String momento) {
        jdbc.update("""
                UPDATE dbo.FacturaPendiente SET intentos = intentos + 1, ultimo_intento = ? WHERE id = ?
                """, momento, id);
    }

    public void eliminarPendiente(int id) {
        jdbc.update("DELETE FROM dbo.FacturaPendiente WHERE id = ?", id);
    }

    private void importarSiEstanVacias() {
        Integer emitidas = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.FacturaEmitida", Integer.class);
        if (emitidas != null && emitidas == 0) {
            importarEmitidas();
        }
        Integer cufes = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.FacturaCufe", Integer.class);
        if (cufes != null && cufes == 0) {
            importarCufes();
        }
        Integer pendientes = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.FacturaPendiente", Integer.class);
        if (pendientes != null && pendientes == 0) {
            importarPendientes();
        }
    }

    private void importarEmitidas() {
        List<Map<String, Object>> filas = leerJsonLista(Paths.get(archivoEnviadas));
        if (filas.isEmpty()) {
            return;
        }
        Map<String, Map<String, Object>> estados = indiceEstados(archivoEnviadas);
        for (int i = filas.size() - 1; i >= 0; i--) {
            Map<String, Object> fila = filas.get(i);
            Map<String, Object> estado = estados.get(texto(fila.get("id")));
            if (estado != null) {
                copiarSiHay(fila, estado, "estadoDian");
                copiarSiHay(fila, estado, "detalleDian");
                copiarSiHay(fila, estado, "consultadoEn");
                fila.put("intentos", estado.getOrDefault("intentos", 0));
                fila.put("falloPortal", estado.getOrDefault("falloPortal", false));
            }
            insertarImportada(fila);
        }
        logger.info("Se copiaron {} facturas desde {}", filas.size(), archivoEnviadas);
    }

    private void insertarImportada(Map<String, Object> fila) {
        String id = texto(fila.get("id"));
        if (id.isBlank()) {
            return;
        }
        String estado = texto(fila.get("estadoDian"));
        jdbc.update("""
                INSERT INTO dbo.FacturaEmitida (
                    id, numero, prefijo, consecutivo, cufe, fecha, cliente, tipo, total, cheque, ticket, payload,
                    estado_envio, mensaje_envio, estado_dian, detalle_dian, consultado_en, intentos, fallo_portal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, texto(fila.get("numero")), texto(fila.get("prefijo")), numero(fila.get("consecutivo")),
                texto(fila.get("cufe")), texto(fila.get("fecha")),
                texto(fila.get("cliente")).isBlank() ? "Consumidor Final" : texto(fila.get("cliente")),
                texto(fila.get("tipo")).isBlank() ? "FV" : texto(fila.get("tipo")),
                numero(fila.get("total")), texto(fila.get("cheque")), texto(fila.get("ticket")), texto(fila.get("payload")),
                texto(fila.get("estadoEnvio")).isBlank() ? "pendiente" : texto(fila.get("estadoEnvio")),
                texto(fila.get("mensajeEnvio")),
                estado.isBlank() ? "pendiente" : estado,
                texto(fila.get("detalleDian")).isBlank() ? "En espera de la DIAN" : texto(fila.get("detalleDian")),
                texto(fila.get("consultadoEn")), entero(fila.get("intentos")), bandera(fila.get("falloPortal")));
    }

    private void importarCufes() {
        Path path = Paths.get(archivoCufes);
        if (!Files.exists(path)) {
            return;
        }
        try {
            String contenido = Files.readString(path).trim();
            if (contenido.isEmpty()) {
                return;
            }
            Map<String, Map<String, String>> registros = objectMapper.readValue(
                    contenido, new TypeReference<Map<String, Map<String, String>>>() {});
            for (Map.Entry<String, Map<String, String>> entrada : registros.entrySet()) {
                Map<String, String> valor = entrada.getValue();
                if (valor == null) {
                    continue;
                }
                guardarCufe(entrada.getKey(),
                        valor.getOrDefault("numero_factura_completo", ""),
                        valor.getOrDefault("cufe", ""));
            }
            logger.info("Se copiaron {} CUFE desde {}", registros.size(), archivoCufes);
        } catch (Exception e) {
            logger.warn("No se pudieron copiar los CUFE guardados: {}", e.getMessage());
        }
    }

    private void importarPendientes() {
        List<Map<String, Object>> filas = leerJsonLista(Paths.get(archivoPendientes));
        for (Map<String, Object> fila : filas) {
            jdbc.update("""
                    INSERT INTO dbo.FacturaPendiente (json_payload, fecha_procesamiento, intentos, ultimo_error, ultimo_intento)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    texto(fila.get("json_payload")),
                    texto(fila.get("fecha_procesamiento")),
                    Math.max(entero(fila.get("intentos")), 1),
                    texto(fila.get("ultimo_error")),
                    texto(fila.get("ultimo_intento")));
        }
        if (!filas.isEmpty()) {
            logger.info("Se copiaron {} facturas pendientes desde {}", filas.size(), archivoPendientes);
        }
    }

    private Map<String, Map<String, Object>> indiceEstados(String archivo) {
        Map<String, Map<String, Object>> indice = new LinkedHashMap<>();
        Path facturas = Paths.get(archivo);
        String nombre = facturas.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        String base = punto > 0 ? nombre.substring(0, punto) : nombre;
        Path padre = facturas.getParent();
        Path estado = Path.of(base + "_estado.json");
        if (padre != null) {
            estado = padre.resolve(estado);
        }
        for (Map<String, Object> fila : leerJsonLista(estado)) {
            indice.put(texto(fila.get("id")), fila);
        }
        return indice;
    }

    private List<Map<String, Object>> leerJsonLista(Path path) {
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            String contenido = Files.readString(path).trim();
            if (contenido.isEmpty() || contenido.startsWith("{")) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(contenido, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            logger.warn("No se pudo leer {}: {}", path, e.getMessage());
            return new ArrayList<>();
        }
    }

    private static void copiarSiHay(Map<String, Object> destino, Map<String, Object> origen, String clave) {
        String valor = texto(origen.get(clave));
        if (!valor.isBlank()) {
            destino.put(clave, valor);
        }
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private static long numero(Object valor) {
        if (valor instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(texto(valor));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int entero(Object valor) {
        if (valor instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(texto(valor));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean bandera(Object valor) {
        if (valor instanceof Boolean activo) {
            return activo;
        }
        return "true".equalsIgnoreCase(texto(valor));
    }
}
