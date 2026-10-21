# FACTURA (MVP) — Cambios realizados

**Proyecto:** C:\Users\sopor\Desktop\Factura
**Verificación:** mvn compile (OK) · mvn test (8/8 OK)

> Versión en Word: `FACTURA - Cambios MVP (documentacion).docx`

## Resumen
- Fecha en las transacciones para poder diferenciarlas.
- Cheque reutilizado en otra fecha = factura nueva, con historial de usos.
- Reimpresión de un ticket sin recalcularlo (se guarda el JSON original).
- Modo manual: genera e imprime, pero no envía al portal.
- Panel de administración de solo lectura (configuración y estado).
- Notas de crédito: buscar la factura por CUFE.
- Cola de reintentos con ID único (ya no se procesa la factura equivocada).
- Seguridad: los endpoints solo leen archivos de carpetas autorizadas.
- Medición de rendimiento y retención de 30 días sin borrar nada.

---

## 1. Fecha en transacciones
- **Qué se hizo:** Se agregó la fecha a la factura y a la cola de pendientes.
- **Dónde:** `FacturaDto.java:17` · `TicketParserService.java:59-62` · `FacturaPendienteService.java:40`
- **Para qué:** Diferenciar transacciones del mismo día y agrupar por fecha.
- **Cómo funciona:** Si el ticket no trae fecha, el parser le pone la del día; al encolar una factura fallida se guarda su fecha de transacción.

```java
// FacturaDto.java
private String fecha;

// TicketParserService.java
if (factura.getFecha() == null || factura.getFecha().isBlank()) {
    factura.setFecha(java.time.LocalDate.now().toString());
}

// FacturaPendienteService.java
factura.put("fecha_transaccion", java.time.LocalDate.now().toString());
```

## 2. Reuso de cheque genera factura nueva
- **Qué se hizo:** El identificador de la factura ahora incluye la fecha y se lleva el conteo de usos de cada cheque.
- **Dónde:** `MiddlewareSimphony.java:888-899, 954-974, 865`
- **Para qué:** Que un cheque repetido en otra fecha genere factura nueva (no reusa la vieja) y quede trazabilidad.
- **Cómo funciona:** La llave se busca en `facturas_cufes.json`; como ahora lleva fecha, el cheque repetido no la encuentra y entra al flujo de factura nueva. El uso se suma en `facturas_historico.json`.

```java
// MiddlewareSimphony.java - construirIdentificadorFactura()
String checkId = datos.getOrDefault("check_id", "");
if (!checkId.isBlank()) {
    return "check_id:" + checkId + "|fecha:" + datos.getOrDefault("fecha_hora", "");
}

// actualizarHistoricoCheque()
entry.put("usos", usos + 1);
entry.put("ultima_factura", numeroFactura);
```

## 3. Notas de crédito: buscar CUFE
- **Qué se hizo:** Endpoint para localizar una factura por su CUFE.
- **Dónde:** `DianController.java:94-118` → `GET /api/dian/cufe?cufe=...`
- **Para qué:** Saber qué factura es la que se está nota de crédito.
- **Cómo funciona:** Lee `facturas_cufes.json`, compara el CUFE recibido y devuelve identificador y número de factura (404 si no existe).

```java
for (Map.Entry<String, Map<String, String>> e : registros.entrySet()) {
    String c = e.getValue().getOrDefault("cufe", "");
    if (c != null && c.equalsIgnoreCase(cufe)) {
        out.put("identificador", e.getKey());
        out.put("numero_factura", e.getValue().get("numero_factura_completo"));
    }
}
```

## 4. Reimpresión sin recalcular
- **Qué se hizo:** Se guarda el JSON original de cada factura y se agrega un endpoint para reimprimirla.
- **Dónde:** `MiddlewareSimphony.java:941-951, 864` · `ImpresionFactura.java:45-65`
- **Para qué:** Reimprimir un ticket ya emitido sin recalcular el CUFE ni generar otra factura.
- **Cómo funciona:** El payload se guarda en `facturas_impresas/{id}.json`; el endpoint lo lee y lo manda al servicio de impresión (socket a la impresora).

```java
// MiddlewareSimphony.java
java.nio.file.Files.writeString(dir.resolve(identificador + ".json"), payload);

// ImpresionFactura.java - POST /api/impresion/reimprimir
String nombre = identificador.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
java.nio.file.Path path = java.nio.file.Paths.get("facturas_impresas", nombre);
impresionService.imprimirFactura(objectMapper.readTree(
        java.nio.file.Files.readString(path)));
```

## 5. Modo manual (no envía al portal)
- **Qué se hizo:** Un flag que apaga el envío automático al portal.
- **Dónde:** `application.properties:12` · `MiddlewareSimphony.java:80, 867-872`
- **Para qué:** Generar, imprimir y guardar la factura, pero subirla después a mano.
- **Cómo funciona:** Todo se procesa igual; solo el envío HTTP queda pendiente y se loguea "Modo manual activo".

```java
// application.properties
app.envio.automatico=false

// MiddlewareSimphony.java
if (envioAutomatico) {
    enviarHttpPOST(jsonPayload);
} else {
    logger.info("Modo manual activo: NO se envió HTTP...");
}
```

## 6. Panel de administración (solo lectura)
- **Qué se hizo:** Endpoints para ver configuración y estado del sistema sin tocar código.
- **Dónde:** `ConfigController.java` (archivo nuevo) · `ImpresionFactura.java:67-71`
- **Para qué:** Que el jefe vea configuración, historial, rendimiento y retención sin entrar al proyecto.
- **Cómo funciona:** Lee las propiedades con `@Value` y las devuelve en JSON; la API key va enmascarada.
- **Endpoints:** `/api/admin/config` · `/api/admin/historico-checks` · `/api/admin/performance` · `/api/admin/raybag` · `/api/impresion/pendientes-total`

```java
@Value("${app.envio.automatico:true}")
private boolean envioAutomatico;

@GetMapping("/config")
public ResponseEntity<Map<String, Object>> config() {
    Map<String, Object> cfg = new LinkedHashMap<>();
    cfg.put("app.envio.automatico", envioAutomatico);
    return ResponseEntity.ok(cfg);
}
```

## 7. Cola de reintentos con ID único
- **Qué se hizo:** Cada factura pendiente tiene id único y el job trabaja por id, no por posición.
- **Dónde:** `FacturaPendienteService.java:37, 66-75, 94-112, 114-128, 133-142` · `ReintentarFacturasJob.java:43-90`
- **Para qué:** Que el reintento nunca procese, incremente o borre la factura equivocada.
- **Cómo funciona:** El servicio genera el UUID al encolar (y se lo asigna a los registros antiguos); el job pide eliminar o incrementar por id.

```java
// FacturaPendienteService.java
factura.put("id", UUID.randomUUID().toString());

public synchronized void eliminarPorId(String id) {
    List<Map<String, Object>> pendientes = leerPendientes();
    boolean eliminada = pendientes.removeIf(f -> id.equals(f.get("id")));
    if (eliminada) guardarPendientes(pendientes);
}
```

## 8. Seguridad de rutas (path traversal)
- **Qué se hizo:** Los endpoints que leen XML solo aceptan archivos de carpetas autorizadas y ya no muestran errores internos.
- **Dónde:** `RutaArchivoService.java` (archivo nuevo) · `application.properties:24` · `DianController.java:43-92` · `TestController.java:37-56`
- **Para qué:** Evitar que alguien lea archivos ajenos del servidor y no filtrar datos internos.
- **Cómo funciona:** Se resuelve la ruta canónica y se compara con las carpetas base; si no está dentro responde 400 "Acceso Denegado". Para sumar una carpeta se agrega en `app.rutas.permitidas`.

```java
// RutaArchivoService.java - resolverPermitido()
File archivo = new File(ruta).getCanonicalFile();
Path destino = archivo.toPath().toAbsolutePath().normalize();
for (String base : basesPermitidas()) {
    if (destino.startsWith(Paths.get(base).toAbsolutePath().normalize())) {
        return archivo;   // permitido
    }
}
return null;   // fuera de las carpetas autorizadas
```

## 9. Medición de rendimiento
- **Qué se hizo:** Se mide cuánto demora cada operación clave y se expone por API.
- **Dónde:** `MetricasService.java` (archivo nuevo) · `MiddlewareSimphony.java:144, 884, 1064` · `ReintentarFacturasJob.java` · `DianController.java`
- **Para qué:** Saber con datos dónde está el cuello de botella (parseo, envío HTTP o reintentos).
- **Cómo funciona:** Cada punto medido registra el tiempo en memoria y el endpoint devuelve por operación: muestras, mínimo, promedio y máximo en ms.

```java
long inicio = System.nanoTime();
try {
    ... operacion ...
} finally {
    metricasService.registrar("procesarXML", System.nanoTime() - inicio);
}
```

## 10. Retención RayBag (30 días, no borra)
- **Qué se hizo:** Job diario que archiva lo que ya cumplió 30 días (lo mueve, nunca lo borra).
- **Dónde:** `RetencionArchivoService.java` (archivo nuevo) · `application.properties:27-30` · `ConfigController.java` (`/api/admin/raybag`)
- **Para qué:** Cumplir la retención de un mes sin perder información.
- **Cómo funciona:** Recorre las carpetas configuradas y mueve los archivos con más de 30 días a `archivo/yyyy-MM/`; corre a las 2:15 AM.

```java
long cutoff = System.currentTimeMillis() - (diasRetencion * 24L * 60L * 60L * 1000L);
if (Files.getLastModifiedTime(archivo).toMillis() < cutoff) {
    Files.move(archivo, destino, StandardCopyOption.REPLACE_EXISTING);  // archiva, no borra
}
```

## 11. Limpiezas menores
- **Qué se hizo:** Quitamos warnings y dejamos el código prolijo.
- **Dónde:** `ConeccionWeb.java` · `JournalConeccionServices.java` · `ImpresionFactura.java` · `MiddlewareSimphony.java` · `MiddlewareSimphonyTest.java` · `.gitignore`
- **Para qué:** Que no haya ruido en el editor ni se suban archivos generados al repositorio.
- **Cómo funciona:** Cambios puntuales de código y exclusiones en `.gitignore`.

```java
// ConeccionWeb.java (new URL está deprecado en Java 20)
java.net.URI uri = java.net.URI.create(urlString);
URL url = uri.toURL();

// JournalConeccionServices.java
@SuppressWarnings("deprecation")   // Tailer de commons-io
logger.info("Escuchando el simphony {}", file.getAbsolutePath());
```

---

## Pendientes
- Impresión con impresora física: no se pudo validar (no hay impresora en el entorno de pruebas).
- RayBag: falta confirmar con el jefe qué significa exactamente (hoy: retención de 30 días).
- Rendimiento: falta medir con carga real y ajustar el `Thread.sleep(500)` del watcher.
- Integración con la interfaz externa: esa aplicación no está en este repositorio.
- Panel admin: hoy es solo lectura (editar y usuario/rol admin queda a definir).
- Menores: warnings del pom (org.json duplicado), deprecations del Tailer y `System.out` en ProbadorCufe.
