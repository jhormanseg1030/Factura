# Factura (MVP) – Estado y cambios consolidados

## Proyecto activo
`C:\Users\sopor\Desktop\Factura` (compila y tests OK, 8/8)

## Listo (implementado)
- **Campo fecha** en `FacturaDto` para diferenciar transacciones.
- **Fecha de transacción** en cola pendiente (`facturas_pendientes.json`).
- **Reúso de cheque** por `check_id + fecha/hora` (genera factura nueva por reúso).
- **Reimpresión**: guarda payload original en `facturas_impresas/*.json`
  y endpoint `POST /api/impresion/reimprimir`.
- **Modo manual** (`app.envio.automatico=false`): no envía HTTP; deja payload/registro listos.
- **Histórico por cheque** (`facturas_historico.json`): usos, última factura/fecha.
- **Notas de crédito**: endpoint `GET /api/dian/cufe?cufe=...` (lookup en registro).
- **Panel admin/config** (solo lectura): `GET /api/admin/config` (secretos enmascarados).
- **Reintentos por ID estable** (h-hardening): la cola usa `id` UUID y el job
  `ReintentarFacturasJob` ya no opera por índice.
- **Seguridad de rutas (anti path traversal)**: `RutaArchivoService` valida que todo
  XML leído por los endpoints esté dentro de carpetas autorizadas.

## Pendiente (funcionalidad)
- [ ] Botón de descarga/subida en la interfaz externa (frontend) → usar `POST /api/impresion/reimprimir`.
- [ ] Histórico avanzado (movimientos detallados) si se requiere.
- [ ] Panel admin con edición/clave WS (si el jefe lo pide).
- [ ] Integración real con la interfaz externa (no está conectada en este repo).
- [ ] **RayBag**: falta definir qué es + política de retención 1 mes.

## Pendiente (calidad/hardening)
- [ ] Rendimiento: medir endpoints/watcher con carga real (watcher usa `Thread.sleep(500)`).
- [ ] Warnings de pom/dependencias (org.json duplicado en classpath).
- [ ] Deprecations restantes (Tailer de commons-io).
- [ ] Logging centralizado (quedan `System.out` en `ProbadorCufe`).
- [ ] RateLimiter: la firma del método `fallback` no coincide con la del método original.

## Sin completar / depende de ambiente
- [ ] Prueba real de impresión física (impresora IP en `impresora.ip`).
- [ ] Validación end-to-end con tickets reales (watcher de Simphony).

## Endpoints principales (MVP)
- `GET /api/dian/parsear?xmlPath=...` (ruta validada)
- `GET /api/dian/parsear/basicos?xmlPath=...` (ruta validada)
- `GET /api/dian/cufe?cufe=...`
- `GET /api/test/simular-pago?xmlPath=...` (ruta validada)
- `POST /api/impresion`
- `POST /api/impresion/reimprimir`
- `GET /api/impresion/pendientes-total`
- `GET /api/admin/config`
- `GET /api/admin/historico-checks`

## How to verify
```bash
mvn -q -DskipTests compile
mvn test
```

## Archivos clave
- `src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java`
- `src/main/java/com/prueba/factura/Services/FacturaPendienteService.java`
- `src/main/java/com/prueba/factura/Services/ReintentarFacturasJob.java`
- `src/main/java/com/prueba/factura/Services/RutaArchivoService.java`
- `src/main/java/com/prueba/factura/controller/DianController.java`
- `src/main/java/com/prueba/factura/controller/ImpresionFactura.java`
- `src/main/java/com/prueba/factura/controller/ConfigController.java`
- `src/main/java/com/prueba/factura/controller/TestController.java`
- `src/main/java/com/prueba/factura/dto/FacturaDto.java`
- `src/main/java/com/prueba/factura/Services/TicketParserService.java`
- `src/main/resources/application.properties`
- `PENDIENTES_MVP.md`
