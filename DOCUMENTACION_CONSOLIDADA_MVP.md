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

## Pendiente (funcionalidad)
- Botón de descarga/subida en la interfaz externa (frontend) → usar `POST /api/impresion/reimprimir`.
- Histórico avanzado (movimientos detallados) si se requiere.
- Panel admin con edición/clave WS (si el jefe lo pide).
- Integración real con la interfaz externa (no está conectada en este repo).

## Pendiente (calidad/hardening)
- Warnings de pom/dependencias (org.json duplicado, etc.).
- Deprecations (ConeccionWeb, Journal tailer).
- Logging centralizado (sustituir algunos `System.out`).
- Revisión de seguridad (path traversal en DianController, LFI reportada).

## Sin completar / depende de ambiente
- Prueba real de impresión física (impresora IP en `impresora.ip`).
- Validación end-to-end con tickets reales (watcher de Simphony).

## Endpoints principales (MVP)
- `GET /api/dian/parsear?xmlPath=...`
- `GET /api/dian/parsear/basicos?xmlPath=...`
- `GET /api/dian/cufe?cufe=...`
- `POST /api/test/simular-pago?xmlPath=...`
- `POST /api/impresion`
- `POST /api/impresion/reimprimir`
- `GET /api/admin/config`

## How to verify
```bash
mvn -q -DskipTests compile
mvn test
```

## Archivos clave
- `src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java`
- `src/main/java/com/prueba/factura/controller/DianController.java`
- `src/main/java/com/prueba/factura/controller/ImpresionFactura.java`
- `src/main/java/com/prueba/factura/controller/ConfigController.java`
- `src/main/java/com/prueba/factura/dto/FacturaDto.java`
- `src/main/java/com/prueba/factura/Services/TicketParserService.java`
- `src/main/java/com/prueba/factura/Services/FacturaPendienteService.java`
- `src/main/resources/application.properties`
- `pendientes por revisar: PENDIENTES_MVP.md`
