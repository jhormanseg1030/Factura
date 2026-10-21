FACTURA (MVP) - CAMBIOS REALIZADOS
Proyecto: C:\Users\sopor\Desktop\Factura
Verificacion: mvn compile (OK) y mvn test (8/8 OK)

======================================================================
1) FECHA EN TRANSACCIONES
======================================================================
Que hicimos: Agregamos la fecha a la factura y a la cola de pendientes.
Donde: FacturaDto.java linea 17 | TicketParserService.java lineas 59-62 | FacturaPendienteService.java linea 40
Para que sirve: Poder diferenciar transacciones del mismo dia y agrupar por fecha.
Como funciona: Si el ticket no trae fecha, el parser le pone la del dia; al encolar una factura fallida se guarda su fecha de transaccion.
Codigo:
  // FacturaDto.java
  private String fecha;

  // TicketParserService.java
  if (factura.getFecha() == null || factura.getFecha().isBlank()) {
      factura.setFecha(java.time.LocalDate.now().toString());
  }

  // FacturaPendienteService.java
  factura.put("fecha_transaccion", java.time.LocalDate.now().toString());

======================================================================
2) REUSO DE CHEQUE GENERA FACTURA NUEVA
======================================================================
Que hicimos: El identificador de la factura ahora incluye la fecha, y llevamos el conteo de usos de cada cheque.
Donde: MiddlewareSimphony.java lineas 888-899 (construirIdentificadorFactura), 954-974 (actualizarHistoricoCheque) y 865 (llamada)
Para que sirve: Que un cheque repetido en otra fecha genere factura nueva (no reusa la vieja) y quede trazabilidad.
Como funciona: La llave se busca en facturas_cufes.json; como ahora lleva fecha, el cheque repetido no la encuentra y entra al flujo de factura nueva; el uso se suma en facturas_historico.json.
Codigo:
  // MiddlewareSimphony.java - construirIdentificadorFactura()
  String checkId = datos.getOrDefault("check_id", "");
  if (!checkId.isBlank()) {
      return "check_id:" + checkId + "|fecha:" + datos.getOrDefault("fecha_hora", "");
  }

  // actualizarHistoricoCheque()
  entry.put("usos", usos + 1);
  entry.put("ultima_factura", numeroFactura);
  entry.put("ultima_fecha_hora", fechaHora);

======================================================================
3) NOTAS DE CREDITO - BUSCAR CUFE
======================================================================
Que hicimos: Endpoint para localizar una factura por su CUFE.
Donde: DianController.java lineas 94-118
Para que sirve: Saber que factura se esta nota de credito.
Como funciona: Lee facturas_cufes.json, compara el CUFE recibido y devuelve identificador y numero de factura (404 si no existe).
Codigo:
  // DianController.java - GET /api/dian/cufe?cufe=...
  for (Map.Entry<String, Map<String, String>> e : registros.entrySet()) {
      String c = e.getValue().getOrDefault("cufe", "");
      if (c != null && c.equalsIgnoreCase(cufe)) {
          out.put("identificador", e.getKey());
          out.put("numero_factura", e.getValue().get("numero_factura_completo"));
          return ResponseEntity.ok(...);
      }
  }

======================================================================
4) REIMPRESION SIN RECALCULAR
======================================================================
Que hicimos: Guardamos el JSON original de cada factura y agregamos un endpoint para reimprimirla.
Donde: MiddlewareSimphony.java lineas 941-951 (guardarPayloadImpresion) y 864 (llamada) | ImpresionFactura.java lineas 45-65 (POST /api/impresion/reimprimir)
Para que sirve: Reimprimir un ticket ya emitido sin recalcular CUFE ni generar otra factura.
Como funciona: El payload se guarda en facturas_impresas/{id}.json; el endpoint lo lee y lo manda al servicio de impresion (socket a la impresora).
Codigo:
  // MiddlewareSimphony.java - guardarPayloadImpresion()
  java.nio.file.Files.writeString(dir.resolve(identificador + ".json"), payload);

  // ImpresionFactura.java - POST /api/impresion/reimprimir
  String nombre = identificador.replaceAll("[^a-zA-Z0-9._-]", "_") + ".json";
  java.nio.file.Path path = java.nio.file.Paths.get("facturas_impresas", nombre);
  String payload = java.nio.file.Files.readString(path);
  impresionService.imprimirFactura(objectMapper.readTree(payload));

======================================================================
5) MODO MANUAL (NO ENVIA AL PORTAL)
======================================================================
Que hicimos: Un flag que apaga el envio automatico al portal.
Donde: application.properties linea 12 (app.envio.automatico) | MiddlewareSimphony.java lineas 80 y 867-872
Para que sirve: Generar, imprimir y guardar la factura, pero subirla despues a mano.
Como funciona: Todo se procesa igual; solo el envio HTTP queda pendiente y se loguea "Modo manual activo".
Codigo:
  // application.properties
  app.envio.automatico=false

  // MiddlewareSimphony.java
  if (envioAutomatico) {
      enviarHttpPOST(jsonPayload);
  } else {
      logger.info("Modo manual activo: NO se envio HTTP. Payload listo en facturas_impresas...");
  }

======================================================================
6) PANEL DE ADMINISTRACION (SOLO LECTURA)
======================================================================
Que hicimos: Endpoints para ver configuracion y estado del sistema sin tocar codigo.
Donde: ConfigController.java (archivo nuevo) | ImpresionFactura.java lineas 67-71
Para que sirve: Que el jefe vea la configuracion, el historial, el rendimiento y la retencion sin entrar al proyecto.
Como funciona: Lee las propiedades con @Value y las devuelve en JSON; la API key va enmascarada.
Codigo:
  // ConfigController.java
  @Value("${app.envio.automatico:true}")
  private boolean envioAutomatico;

  @GetMapping("/config")
  public ResponseEntity<Map<String, Object>> config() {
      Map<String, Object> cfg = new LinkedHashMap<>();
      cfg.put("app.envio.automatico", envioAutomatico);
      return ResponseEntity.ok(cfg);
  }
  Endpoints: /api/admin/config, /api/admin/historico-checks, /api/admin/performance,
             /api/admin/raybag, /api/impresion/pendientes-total

======================================================================
7) COLA DE REINTENTOS CON ID UNICO
======================================================================
Que hicimos: Cada factura pendiente tiene id unico y el job trabaja por id, no por posicion.
Donde: FacturaPendienteService.java lineas 37, 66-75, 94-112, 114-128, 133-142 | ReintentarFacturasJob.java lineas 43-90
Para que sirve: Que el reintento nunca procese, incremente o borre la factura equivocada.
Como funciona: El servicio genera el UUID al encolar (y se lo asigna a los registros antiguos); el job pide eliminar o incrementar por id, y cada operacion relee el archivo y busca ese id.
Codigo:
  // FacturaPendienteService.java
  factura.put("id", UUID.randomUUID().toString());

  public synchronized void eliminarPorId(String id) {
      List<Map<String, Object>> pendientes = leerPendientes();
      boolean eliminada = pendientes.removeIf(f -> id.equals(f.get("id")));
      if (eliminada) guardarPendientes(pendientes);
  }

======================================================================
8) SEGURIDAD DE RUTAS (PATH TRAVERSAL)
======================================================================
Que hicimos: Los endpoints que leen XML solo aceptan archivos de carpetas autorizadas y ya no muestran errores internos.
Donde: RutaArchivoService.java (archivo nuevo) | application.properties linea 24 (app.rutas.permitidas) | DianController.java lineas 43-92 | TestController.java lineas 37-56
Para que sirve: Evitar que alguien lea archivos ajenos del servidor y no filtrar datos internos.
Como funciona: Se resuelve la ruta canonica y se compara con las carpetas base (inbox, processed y app.rutas.permitidas); si no esta dentro, responde 400 Acceso Denegado. Para sumar una carpeta, se agrega en app.rutas.permitidas.
Codigo:
  // RutaArchivoService.java - resolverPermitido()
  File archivo = new File(ruta).getCanonicalFile();
  Path destino = archivo.toPath().toAbsolutePath().normalize();
  for (String base : basesPermitidas()) {
      if (destino.startsWith(Paths.get(base).toAbsolutePath().normalize())) {
          return archivo;   // permitido
      }
  }
  return null;   // fuera de las carpetas autorizadas

======================================================================
9) MEDICION DE RENDIMIENTO
======================================================================
Que hicimos: Medimos cuanto demora cada operacion clave y lo exponemos por API.
Donde: MetricasService.java (archivo nuevo) | MiddlewareSimphony.java lineas 144, 884, 1064 | ReintentarFacturasJob.java (reintento.ciclo) | DianController.java (parseo DIAN) | ConfigController.java (/api/admin/performance)
Para que sirve: Saber con datos donde esta el cuello de botella (parseo, envio HTTP o reintentos).
Como funciona: Cada punto medido registra el tiempo en memoria y el endpoint devuelve por operacion: muestras, minimo, promedio y maximo en ms.
Codigo:
  // instrumentacion (MiddlewareSimphony / DianController / ReintentarFacturasJob)
  long inicio = System.nanoTime();
  try {
      ... operacion ...
  } finally {
      metricasService.registrar("procesarXML", System.nanoTime() - inicio);
  }

======================================================================
10) RETENCION RAYBAG (30 DIAS, NO BORRA)
======================================================================
Que hicimos: Job diario que archiva lo que ya cumplio 30 dias (lo mueve, nunca lo borra).
Donde: RetencionArchivoService.java (archivo nuevo) | application.properties lineas 27-30 | ConfigController.java (/api/admin/raybag)
Para que sirve: Cumplir la retencion de un mes sin perder informacion.
Como funciona: Recorre las carpetas de app.raybag.directorios y mueve los archivos con mas de 30 dias a archivo/yyyy-MM/; corre a las 2:15 AM.
Codigo:
  // RetencionArchivoService.java
  long cutoff = System.currentTimeMillis() - (diasRetencion * 24L * 60L * 60L * 1000L);
  if (Files.getLastModifiedTime(archivo).toMillis() < cutoff) {
      Files.move(archivo, destino, StandardCopyOption.REPLACE_EXISTING);  // archiva, no borra
  }

======================================================================
11) LIMPIEZAS MENORES
======================================================================
Que hicimos: Quitamos warnings y dejamos el codigo prolijo.
Donde: ConeccionWeb.java | JournalConeccionServices.java | ImpresionFactura.java | MiddlewareSimphony.java | MiddlewareSimphonyTest.java | .gitignore
Para que sirve: Que no haya ruido en el editor ni se suban archivos generados al repositorio.
Como funciona: Cambios puntuales de codigo y exclusiones en .gitignore.
Codigo:
  // ConeccionWeb.java (new URL esta deprecado en Java 20)
  java.net.URI uri = java.net.URI.create(urlString);
  URL url = uri.toURL();

  // JournalConeccionServices.java
  @SuppressWarnings("deprecation")   // Tailer de commons-io
  logger.info("Escuchando el simphony {}", file.getAbsolutePath());

======================================================================
PENDIENTES
======================================================================
- Impresion con impresora fisica: no se pudo validar (no hay impresora en el entorno de pruebas).
- RayBag: falta confirmar con el jefe que significa exactamente (hoy: retencion de 30 dias).
- Rendimiento: falta medir con carga real y ajustar el Thread.sleep(500) del watcher.
- Integracion con la interfaz externa: esa aplicacion no esta en este repositorio.
- Panel admin: hoy es solo lectura (editar y usuario/rol admin queda a definir).
- Menores: warnings del pom (org.json duplicado), deprecations del Tailer y System.out en ProbadorCufe.
