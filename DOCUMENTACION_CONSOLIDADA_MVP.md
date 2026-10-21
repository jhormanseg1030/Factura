FACTURA (MVP) - CAMBIOS REALIZADOS
Proyecto: C:\Users\sopor\Desktop\Factura
Verificacion: mvn compile (OK) y mvn test (8/8 OK)

======================================================================
1. FECHA EN TRANSACCIONES
======================================================================
src/main/java/com/prueba/factura/dto/FacturaDto.java - linea 17
  Campo "fecha" en la factura.
src/main/java/com/prueba/factura/Services/TicketParserService.java - lineas 59-62
  Si la fecha viene vacia, se pone la del dia.
src/main/java/com/prueba/factura/Services/FacturaPendienteService.java - linea 40
  Guarda "fecha_transaccion" en la cola de pendientes.

======================================================================
2. REUSO DE CHEQUE GENERA FACTURA NUEVA
======================================================================
src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java - lineas 888-899
  construirIdentificadorFactura(): el id ahora es check_id + fecha, para que el
  cheque repetido en otra fecha genere factura nueva.
src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java - lineas 954-974
  actualizarHistoricoCheque(): cuenta los usos de cada cheque en facturas_historico.json.
src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java - linea 865
  Llamada a actualizarHistoricoCheque() dentro de procesarXML.

======================================================================
3. NOTAS DE CREDITO - BUSCAR CUFE
======================================================================
src/main/java/com/prueba/factura/controller/DianController.java - lineas 94-118
  GET /api/dian/cufe?cufe=...  devuelve la factura de ese CUFE.

======================================================================
4. REIMPRESION (NO RECALCULA)
======================================================================
src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java - lineas 941-951
  guardarPayloadImpresion(): guarda el JSON original en facturas_impresas/{id}.json.
src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java - linea 864
  Llamada que guarda el payload.
src/main/java/com/prueba/factura/controller/ImpresionFactura.java - lineas 45-65
  POST /api/impresion/reimprimir  con body {"identificador": "..."}.

======================================================================
5. MODO MANUAL (NO ENVIA AL PORTAL)
======================================================================
src/main/resources/application.properties - linea 12
  app.envio.automatico=true   (false = modo manual).
src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java - lineas 80 y 867-872
  Solo envia al webhook si envioAutomatico es true; si no, deja todo listo y lo loguea.

======================================================================
6. PANEL DE ADMINISTRACION (SOLO LECTURA)
======================================================================
src/main/java/com/prueba/factura/controller/ConfigController.java - archivo nuevo
  GET /api/admin/config           configuracion del sistema (API key enmascarada).
  GET /api/admin/historico-checks  historial de uso de los cheques.
  GET /api/admin/performance       metricas de rendimiento.
  GET /api/admin/raybag            estado de la retencion/archivado.
src/main/java/com/prueba/factura/controller/ImpresionFactura.java - lineas 67-71
  GET /api/impresion/pendientes-total  facturas en cola de reintentos.

======================================================================
7. COLA DE REINTENTOS CON ID UNICO
======================================================================
src/main/java/com/prueba/factura/Services/FacturaPendienteService.java
  linea 37    genera el id unico de cada factura pendiente.
  lineas 66-75  a los registros antiguos sin id se les asigna uno.
  lineas 94-112  incrementarIntentoPorId(id).
  lineas 114-128 eliminarPorId(id).
  lineas 133-142 leerIntentos() sin riesgo de ClassCastException.
src/main/java/com/prueba/factura/Services/ReintentarFacturasJob.java - lineas 43-90
  El job reintenta y borra por id, no por posicion (no toca la factura equivocada).

======================================================================
8. SEGURIDAD DE RUTAS (PATH TRAVERSAL)
======================================================================
src/main/java/com/prueba/factura/Services/RutaArchivoService.java - archivo nuevo
  resolverPermitido(): solo deja leer archivos dentro de las carpetas autorizadas.
src/main/resources/application.properties - linea 24
  app.rutas.permitidas=C:/SimphonyTest/inbox,C:/SimphonyTest/outbox
src/main/java/com/prueba/factura/controller/DianController.java - lineas 43-92
  /parsear y /parsear/basicos validan la ruta y no muestran errores internos.
src/main/java/com/prueba/factura/controller/TestController.java - lineas 37-56
  /simular-pago tambien valida la ruta.

======================================================================
9. MEDICION DE RENDIMIENTO
======================================================================
src/main/java/com/prueba/factura/Services/MetricasService.java - archivo nuevo
  Guarda los tiempos (min/prom/max) de cada operacion.
src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java - lineas 144, 884, 1064
  Mide procesarXML, watcher.procesarXML y envioHttpPOST.
src/main/java/com/prueba/factura/Services/ReintentarFacturasJob.java - reintento.ciclo
src/main/java/com/prueba/factura/controller/DianController.java - parseo de endpoints DIAN.
src/main/java/com/prueba/factura/controller/ConfigController.java - GET /api/admin/performance

======================================================================
10. RETENCION RAYBAG (30 DIAS, NO BORRA)
======================================================================
src/main/java/com/prueba/factura/Services/RetencionArchivoService.java - archivo nuevo
  Archiva (mueve) a archivo/yyyy-MM los archivos con mas de 30 dias. Nunca borra.
  Job diario 2:15 AM. Estado: GET /api/admin/raybag.
src/main/resources/application.properties - lineas 27-30
  app.raybag.directorio / dias / directorios / cron.

======================================================================
11. LIMPIEZAS MENORES
======================================================================
src/main/java/com/prueba/factura/Services/ConeccionWeb.java
  Se quito una variable sin usar y se cambio new URL (deprecado) por URI.toURL().
src/main/java/com/prueba/factura/Services/JournalConeccionServices.java
  Se marco Tailer como deprecado y se cambio System.out por logger.
src/main/java/com/prueba/factura/controller/ImpresionFactura.java
src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java
  System.out.println cambiados por logger.
src/test/java/com/prueba/factura/MiddlewareSimphonyTest.java
  Llamadas estaticas corregidas.
.gitignore
  Se ignoran los archivos generados (facturas_impresas, facturas_*.json, contador).

======================================================================
PENDIENTES
======================================================================
- Impresion con impresora fisica: no se pudo validar (no hay impresora en el entorno).
- RayBag: falta confirmar con el jefe que es exactamente (hoy: retencion de 30 dias).
- Rendimiento: falta medir con carga real y ajustar el Thread.sleep(500) del watcher.
- Integracion con la interfaz externa: esa app no esta en este repositorio.
- Panel admin: hoy es solo lectura (editar y usuario/rol admin queda a definir).
- Menores: warnings del pom (org.json duplicado), deprecations del Tailer y System.out
  restantes en ProbadorCufe.
