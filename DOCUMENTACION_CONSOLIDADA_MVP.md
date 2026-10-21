FACTURA (MVP) – DOCUMENTACIÓN DE CAMBIOS REALIZADOS
Proyecto: C:\Users\sopor\Desktop\Factura
Stack: Spring Boot 4 (Java 21) + SQL Server + impresión por socket TCP
Verificación: mvn -q -DskipTests compile (OK) y mvn test (8/8 OK)

======================================================================
1. RESUMEN EJECUTIVO
======================================================================
- Se agregó fecha a las transacciones para poder diferenciarlas.
- Un cheque (check) reutilizado en otra fecha ahora genera factura nueva, y queda registrado su historial de usos.
- Se puede reimprimir una factura sin recalcularla, usando el payload original guardado.
- Se agregó modo manual: el sistema genera y guarda todo, pero no envía al portal (subida manual).
- Panel de administración de solo lectura para ver la configuración sin tocar código.
- Notas de crédito: se puede buscar una factura por su CUFE.
- Cola de reintentos corregida: se opera por ID único, no por posición (evita procesar la factura equivocada).
- Seguridad: los endpoints que leen archivos XML solo aceptan rutas dentro de carpetas autorizadas.
- Medición de rendimiento y retención/archivado de 30 días sin borrar nada.

======================================================================
2. CAMBIO: FECHA EN TRANSACCIONES
======================================================================
Qué se hizo
Se agregó el campo "fecha" a la factura y a la cola de pendientes, para poder diferenciar transacciones del mismo día.

Dónde está
- src/main/java/com/prueba/factura/dto/FacturaDto.java, línea 17 → campo: private String fecha;
- src/main/java/com/prueba/factura/Services/TicketParserService.java, líneas 59-62 → si la fecha viene vacía se pone la del día (LocalDate.now(), formato yyyy-MM-dd).
- src/main/java/com/prueba/factura/Services/FacturaPendienteService.java, línea 40 → se guarda "fecha_transaccion" en cada factura pendiente (además de "fecha_procesamiento", que es el momento en que se encoló).

Para qué sirve
Para saber en qué día ocurrió cada transacción y poder filtrar/agrupar por fecha en los reportes y en la cola de reintentos.

Cómo funciona / cómo conecta
El DTO FacturaDto es el objeto que viaja entre el parser de tickets, la impresión y el envío al webhook. Al agregar el campo ahí, todas las capas lo reciben. Cuando una factura no se puede enviar, FacturaPendienteService la serializa a JSON (facturas_pendientes.json) y guarda también su fecha de transacción.

======================================================================
3. CAMBIO: REÚSO DE CHEQUE GENERA FACTURA NUEVA
======================================================================
Qué se hizo
Antes, si volvía el mismo check, el sistema reutilizaba la factura anterior. Ahora, si el mismo check llega en otra fecha/hora, se trata como una transacción nueva y se genera factura nueva (nuevo número y nuevo CUFE). Además se lleva el historial de cuántos veces se usó cada cheque.

Dónde está
- src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java, líneas 888-899 → función construirIdentificadorFactura(): el identificador ahora es "check_id:...|fecha:..." (antes era solo "check_id:...").
- Mismo archivo, líneas 954-974 → función actualizarHistoricoCheque(): suma un uso y guarda la última factura/fecha en facturas_historico.json.
- Mismo archivo, línea 865 → llamada a actualizarHistoricoCheque() dentro de procesarXML.

Para qué sirve
Porque en operación los cheques se reutilizan (años o meses después). Así cada venta genera su factura correctamente y queda trazabilidad de cuántas veces se reutilizó un cheque.

Cómo funciona / cómo conecta
El identificador de la factura es la llave que se busca en facturas_cufes.json. Si el identificador no existe, se genera número de factura (FacturaCounterService), se calcula el CUFE (CufeServices) y se imprime. Como ahora el identificador incluye la fecha, el cheque repetido no encuentra el registro viejo y entra por el flujo de "factura nueva". El histórico se guarda en facturas_historico.json (archivo local), y se consulta con GET /api/admin/historico-checks.

======================================================================
4. CAMBIO: NOTAS DE CRÉDITO – BUSCAR CUFE
======================================================================
Qué se hizo
Se agregó la consulta de una factura a partir de su CUFE, necesario para el flujo de nota de crédito (ubicar la factura que se va a nota).

Dónde está
- src/main/java/com/prueba/factura/controller/DianController.java, líneas 94-118 → endpoint GET /api/dian/cufe?cufe=...
  (usa la propiedad app.cufe.registry.file, por defecto facturas_cufes.json).

Para qué sirve
Para que el área de notas de crédito pueda localizar una factura por CUFE y devolver su número de factura y el identificador de la transacción.

Cómo funciona / cómo conecta
Lee facturas_cufes.json (mapa: identificador → {numero_factura_completo, cufe}), recorre las entradas y devuelve la que coincida con el CUFE (sin distinguir mayúsculas/minúsculas). Si no existe devuelve 404 "CUFE no encontrado". El registro ese archivo lo escribe MiddlewareSimphony cuando genera la factura.

======================================================================
5. CAMBIO: BOTÓN / ENDPOINT DE REIMPRESIÓN
======================================================================
Qué se hizo
Cada factura procesada guarda su payload original (el JSON exacto que se envió). Con ese payload se puede reimprimir sin recalcular CUFE, sin volver a consultar el XML y sin generar una factura nueva.

Dónde está
- src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java, línea 864 → llamada guardarPayloadImpresion().
- Mismo archivo, líneas 941-951 → método guardarPayloadImpresion(): crea la carpeta facturas_impresas y escribe {identificador}.json.
- src/main/java/com/prueba/factura/controller/ImpresionFactura.java, líneas 45-65 → endpoint POST /api/impresion/reimprimir.

Para qué sirve
Para reimprimir un ticket ya emitido (por ejemplo si se damage o se pierde), haciendo uso del payload tal como se generó.

Cómo funciona / cómo conecta
1) Al procesar el XML, el payload se guarda en facturas_impresas/{identificador}.json (el identificador se sanea, solo letras, números, punto, guion y guion bajo).
2) El endpoint recibe {"identificador": "..."}, arma el nombre del archivo, lo lee y lo pasa a Impresion.imprimirFactura(datos), que es el mismo servicio que usa la impresión normal (socket TCP a la impresora configurada en impresora.ip / impresora.puerto).
3) Si el archivo no existe responde 404; si todo va bien responde "Factura reimpresa: ...".

Prueba
POST http://localhost:8080/api/impresion/reimprimir
Body: { "identificador": "check_id:200|fecha:2026-10-14T10:00:00" }

======================================================================
6. CAMBIO: MODO MANUAL (PROCESO MANUAL)
======================================================================
Qué se hizo
Se puede apagar el envío automático al portal: el sistema procesa, imprime, guarda el payload y el registro del CUFE, pero NO envía el POST al webhook. Queda listo para que alguien lo suba manualmente.

Dónde está
- src/main/resources/application.properties, línea 12 → app.envio.automatico=true (true = automático, false = manual).
- src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java, líneas 867-872 → if (envioAutomatico) { enviarHttpPOST(...) } else { log "Modo manual activo: NO se envió HTTP" }.

Para qué sirve
Para trabajar con un proceso físico/manual: generar el documento, revisarlo y subirlo después al portal, en vez de mandarlo automático.

Cómo funciona / cómo conecta
El flag se inyecta con @Value en MiddlewareSimphony (línea 80). Al terminar de armar el payload, el flujo siempre guarda payload (facturas_impresas) y registro CUFE (facturas_cufes.json); lo único que depende del flag es el envío HTTP. Para activar modo manual se cambia la propiedad a false y se reinicia la aplicación.

======================================================================
7. CAMBIO: PANEL DE ADMINISTRACIÓN (SOLO LECTURA)
======================================================================
Qué se hizo
Se creó un controlador que muestra la configuración del sistema y el estado de las cosechas, sin permitir modificar nada y sin exponer secretos completos.

Dónde está
- src/main/java/com/prueba/factura/controller/ConfigController.java (archivo nuevo), líneas 56-97:
  • GET /api/admin/config → configuración actual (rutas, webhook, archivos, delay de reintentos, impresora, ambiente DIAN, modo de envío). La API key se devuelve enmascarada (ej. In****26*).
  • GET /api/admin/historico-checks → contenido de facturas_historico.json.
  • GET /api/admin/performance → métricas de rendimiento (ver punto 10).
  • GET /api/admin/raybag → estado de la retención/archivado (ver punto 11).
- src/main/java/com/prueba/factura/controller/ImpresionFactura.java, líneas 67-71 → GET /api/impresion/pendientes-total (cuántas facturas hay en la cola de reintentos).

Para qué sirve
Para que el jefe o el administrador vea la configuración y el estado del sistema sin entrar al código ni tocar archivos.

Cómo funciona / cómo conecta
Es un @RestController en /api/admin. Cada propiedad se lee con @Value desde application.properties, y el método arma un Map y lo devuelve como JSON. No hay lógica de escritura: es puramente informativo.

======================================================================
8. CAMBIO: COLA DE REINTENTOS CON ID ÚNICO (corrección de bug)
======================================================================
Qué se hizo
Antes, el job de reintentos eliminaba e incrementaba las facturas por POSICIÓN (índice). Si la cola cambiaba mientras el job corría, podía modificar o borrar la factura equivocada. Ahora cada factura tiene un ID único y el job trabaja por ese ID.

Dónde está
- src/main/java/com/prueba/factura/Services/FacturaPendienteService.java:
  • línea 37 → se genera el id: factura.put("id", UUID.randomUUID().toString());
  • líneas 66-75 → al leer la cola, a los registros antiguos que no tienen id se les asigna uno (migración automática).
  • líneas 94-112 → método incrementarIntentoPorId(String id).
  • líneas 114-128 → método eliminarPorId(String id).
  • líneas 133-142 → leerIntentos(): lee el contador como Number (evita ClassCastException).
- src/main/java/com/prueba/factura/Services/ReintentarFacturasJob.java, líneas 43-58 y 60-90 → el ciclo collects idsAEliminar y llama a eliminarPorId / incrementarIntentoPorId.

Para qué sirve
Para que el reintento automático nunca procese, incremente o borre la factura que no es.

Cómo funciona / cómo conecta
El servicio (FacturaPendienteService) es la única capa que escribe la cola (facturas_pendientes.json). El job (ReintentarFacturasJob) lee una foto de la cola, intenta enviar cada factura y luego, en vez de borrar por índice, pide borrar/incrementar por id. Cada operación vuelve a leer el archivo, busca el id y escribe; si el id ya no existe, solo lo avisa en el log.

======================================================================
9. CAMBIO: SEGURIDAD DE RUTAS (path traversal / LFI)
======================================================================
Qué se hizo
Los endpoints que reciben una ruta de archivo (xmlPath) now solo aceptan archivos que estén dentro de carpetas autorizadas. Antes, se podía pedir cualquier archivo del disco. También se dejó de mostrar el detalle interno de los errores al cliente.

Dónde está
- src/main/java/com/prueba/factura/Services/RutaArchivoService.java (archivo nuevo): resolverPermitido(ruta) resuelve la ruta canónica y la compara (Path.startsWith) con las bases permitidas.
  • Bases permitidas: app.inbox.directory, app.processed.directory y app.rutas.permitidas.
- src/main/resources/application.properties, línea 24 → app.rutas.permitidas=C:/SimphonyTest/inbox,C:/SimphonyTest/outbox
- src/main/java/com/prueba/factura/controller/DianController.java: se usa en /parsear (líneas 43-69) y /parsear/basicos (líneas 71-92).
- src/main/java/com/prueba/factura/controller/TestController.java, líneas 37-56 → /simular-pago también valida.
- Errores: en DianController y TestController el mensaje al cliente ahora es genérico ("Error al procesar la solicitud. Contacte con el administrador.") y el detalle queda solo en el log.

Para qué sirve
Para evitar que alguien lea archivos ajenos del servidor (informes de clientes, configuración, etc.) y para no filtrar detalles internos del sistema.

Cómo funciona / cómo conecta
Se compara la ruta CANÓNICA (resuelve .. y enlaces) contra la lista de carpetas base. Si el archivo no está dentro de una de ellas, o no existe, el servicio devuelve null y el endpoint responde 400 "Acceso Denegado: ruta no permitida o archivo inexistente". Para agregar una carpeta nueva basta con añadirla en app.rutas.permitidas (separadas por coma).

======================================================================
10. CAMBIO: MEDICIÓN DE RENDIMIENTO
======================================================================
Qué se hizo
Se instrumentó el sistema para medir cuánto demora cada operación clave, y se exponen esas métricas por API para detectar cuellos de botella con carga real.

Dónde está
- src/main/java/com/prueba/factura/Services/MetricasService.java (archivo nuevo): guarda en memoria las últimas 200 muestras de cada operación (min / promedio / max).
- Instrumentado en:
  • MiddlewareSimphony.java línea 884 → procesarXML
  • MiddlewareSimphony.java línea 144 → watcher.procesarXML (detección del archivo hasta fin del proceso)
  • MiddlewareSimphony.java línea 1064 → envioHttpPOST
  • ReintentarFacturasJob → reintento.ciclo
  • DianController → api.dian.parsear y api.dian.parsearBasicos
- Consulta: GET /api/admin/performance

Para qué sirve
Para responder con datos "cuánto tarda el sistema" y detectar si el cuello de botella es el parseo, el envío HTTP o la cola de reintentos.

Cómo funciona / cómo conecta
MetricasService es un @Service en memoria (no toca base de datos). Cada punto medido llama registrar(nombre, nanosegundos). El endpoint devuelve un JSON con, por operación: número de muestras, mínimo, promedio y máximo en milisegundos. La métrica se reinicia si se reinicia la aplicación.

======================================================================
11. CAMBIO: RETENCIÓN RAYBAG (30 DÍAS, SIN BORRAR)
======================================================================
Qué se hizo
Se agregó una política de retención: los archivos que ya cumplen 30 días se ARCHIVAN (se mueven a una carpeta de archivo por mes). No se borra nada antes del mes, de modo que no se pierde información.

Dónde está
- src/main/java/com/prueba/factura/Services/RetencionArchivoService.java (archivo nuevo):
  • archivar() → mueve los archivos con más de 30 días a {carpeta}/archivo/yyyy-MM/.
  • estado() → devuelve cuántos archivos hay activos y cuántos archivados.
  • Job programado diario (2:15 AM) que ejecuta el archivado.
- src/main/resources/application.properties, líneas 27-30:
  app.raybag.directorio=raybag
  app.raybag.dias=30
  app.raybag.directorios=raybag,facturas_impresas
  app.raybag.cron=0 15 2 * * *
- Consulta: GET /api/admin/raybag

Para qué sirve
Para cumplir la retención de 1 mes: nada se elimina antes de los 30 días, y lo vencido queda ordenado en archivo/mes para consulta.

Cómo funciona / cómo conecta
El job (@Scheduled) recorre las carpetas configuradas; cada archivo cuya fecha de modificación sea mayor a 30 días se mueve (Files.move) a la subcarpeta archivo del mes correspondiente. Es idempotente y seguro: si algo falla se registra en el log y se sigue. Nota: falta confirmar con el jefe qué es exactamente "RayBag"; si es otra cosa, se ajusta solo en app.raybag.*.

======================================================================
12. LIMPIEZAS MENORES (higiene de código)
======================================================================
- src/main/java/com/prueba/factura/Services/ConeccionWeb.java: se eliminó una variable sin usar y se reemplazó new URL(...) (deprecado en Java 20) por URI.toURL().
- src/main/java/com/prueba/factura/Services/JournalConeccionServices.java: se marcó el uso de Tailer como @SuppressWarnings("deprecation") y se cambió System.out.println por logger.
- src/main/java/com/prueba/factura/controller/ImpresionFactura.java y MiddlewareSimphony.java: se reemplazaron System.out.println por logger (logs centralizados).
- src/test/java/com/prueba/factura/MiddlewareSimphonyTest.java: llamadas estáticas corregidas (MiddlewareSimphony.extraerDatosFactura).
- Se eliminaron los archivos con marca BOM (que rompían la compilación) y espacios al final de línea.
- .gitignore: se agregaron los archivos generados (facturas_impresas/, facturas_historico.json, facturas_pendientes.json, facturas_cufes.json, factura_counter.txt) para no subirlos al repositorio.
- Documentos de control: PENDIENTES_MVP.md (listo / pendiente / bloqueos) y este documento.

======================================================================
13. ENDPOINTS DISPONIBLES
======================================================================
GET  /api/dian/parsear?xmlPath=...            Parsea un XML DIAN (ruta validada)
GET  /api/dian/parsear/basicos?xmlPath=...    Extrae campos básicos (ruta validada)
GET  /api/dian/cufe?cufe=...                  Busca factura por CUFE
GET  /api/test/simular-pago?xmlPath=...       Simula el procesamiento (ruta validada)
POST /api/test/mock-webhook                   Webhook de pruebas (200 OK)
POST /api/impresion                           Imprime una factura enviada por JSON
POST /api/impresion/reimprimir                Reimprime desde el payload guardado
GET  /api/impresion/pendientes-total          Facturas en cola de reintentos
GET  /api/admin/config                        Configuración del sistema (lectura)
GET  /api/admin/historico-checks              Historial de uso de cheques
GET  /api/admin/performance                   Métricas de rendimiento
GET  /api/admin/raybag                        Estado de la retención/archivado

======================================================================
14. ARCHIVOS NUEVOS
======================================================================
src/main/java/com/prueba/factura/Services/RutaArchivoService.java
src/main/java/com/prueba/factura/Services/MetricasService.java
src/main/java/com/prueba/factura/Services/RetencionArchivoService.java
src/main/java/com/prueba/factura/controller/ConfigController.java
PENDIENTES_MVP.md
DOCUMENTACION_CONSOLIDADA_MVP.md (este documento)

======================================================================
15. ARCHIVOS DE DATOS GENERADOS
======================================================================
facturas_cufes.json            Registro CUFE → factura (para búsqueda y control)
facturas_historico.json        Veces que se ha usado cada cheque
facturas_impresas/*.json       Payload original de cada factura (base de la reimpresión)
facturas_pendientes.json       Cola de reintentos (con id único, intentos y fecha)

======================================================================
16. PENDIENTES / LO QUE NO SE PUDO TERMINAR
======================================================================
- Impresión con impresora física: no se pudo validar (no hay impresora disponible en el entorno de pruebas).
- RayBag: la retención de 30 días está implementada; falta confirmar con el jefe qué significa exactamente "RayBag".
- Rendimiento: la medición está lista; falta correr carga real para analizar y ajustar (por ejemplo el Thread.sleep(500) del watcher).
- Integración con la interfaz externa: esa aplicación no está en este repositorio; el backend ya expone los endpoints para que los consuma.
- Panel admin: hoy es solo lectura; falta decidir si se habilita edición y manejo de usuario/rol admin.
- Pendientes técnicos menores: warnings del pom (org.json duplicado en el classpath), deprecations del Tailer, System.out restantes en ProbadorCufe y la firma del método fallback del RateLimiter.

======================================================================
17. CÓMO VERIFICAR
======================================================================
cd C:\Users\sopor\Desktop\Factura
mvn -q -DskipTests compile     -> compila
mvn test                       -> 8 pruebas, 0 fallos
mvn spring-boot:run            -> levanta la app en el puerto 8080
