# Pendientes MVP (Factura)

## Hecho
- [x] Campo `fecha` en `FacturaDto` + asignación por defecto en `TicketParserService`.
- [x] Campo `fecha_transaccion` en cola pendiente (`FacturaPendienteService`).
- [x] Reúso de cheque diferenciado por fecha/hora (`construirIdentificadorFactura`).
- [x] Reimpresión: guardar payload original (`facturas_impresas/*.json`) y endpoint `POST /api/impresion/reimprimir`.
- [x] Modo manual: `app.envio.automatico=false` (sin envío HTTP) + guardado payload/registro.
- [x] Histórico por cheque: `facturas_historico.json` (usos, última factura/fecha).
- [x] Notas de crédito: endpoint `GET /api/dian/cufe?cufe=...` (lookup por CUFE).
- [x] Panel admin/config (solo lectura): `GET /api/admin/config` + `GET /api/admin/historico-checks`.
- [x] Cola de pendientes con `id` UUID + reintentos que operan **por ID** (no por índice).
- [x] Seguridad de rutas: `RutaArchivoService` (allowlist) en endpoints de XML.
- [x] Rendimiento: `MetricasService` + `GET /api/admin/performance`.
- [x] Retención RayBag 30 días: `RetencionArchivoService` (archiva, no borra) + `GET /api/admin/raybag`.

## Pendiente
- [ ] Proceso manual completo (si se quiere además: botón de descarga/subida desde interfaz externa).
- [ ] Histórico avanzado (movimientos detallados) si el jefe lo exige.
- [ ] Revisión de warnings/code cleanup (Pom, deprecations, logs).
- [ ] Panel admin con edición/clave WS (si se necesita).
- [x] Rendimiento: medición de tiempos implementada (`/api/admin/performance`);
      falta carga real para analizar resultados.
- [x] RayBag: retención de 30 días con archivado (nunca borra) + estado en `/api/admin/raybag`.
      **Pendiente confirmar con el jefe qué es exactamente "RayBag".**

## Sin completar / bloqueos
- [ ] Integración real con la interfaz externa (no está conectada en este repo).
- [ ] Prueba real de impresión con impresora física (bloqueado: no hay impresora disponible).
- [ ] Validación en ambiente real de reúso de cheque (usar datos reales).

## Notas
- Proyecto activo: `C:\Users\sopor\Desktop\Factura`.
- Verificación recomendada: `mvn -q -DskipTests compile` y `mvn test` (8/8 OK).
- Detalle funcional en `DOCUMENTACION_CONSOLIDADA_MVP.md`.
