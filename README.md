# Factura (Audit / Simphony -> Webhook)

Aplicación **Spring Boot (Java 21)** para:

- Vigilar una carpeta donde llegan tickets/facturas en **XML** (provenientes de Simphony).
- Parsear esos XML, construir el payload (incluye cálculos/estructura necesaria).
- Enviar el payload a un **endpoint webhook**.
- Mantener un **reintento** de envíos fallidos usando un archivo JSON local.
- Exponer endpoints de ayuda para parsear XML de **DIAN** y para simular el procesamiento.

> Nota: gran parte de la configuración apunta a rutas y recursos en **Windows** (hardcode/rutas en `application.properties` y algunos servicios).  

---

## Requisitos

1. **Java 21**
2. **Maven**
3. Acceso a los endpoints externos configurados (webhook, API de clientes).

---

## Cómo ejecutar

1. Ajusta `src/main/resources/application.properties`:
   - Rutas de `app.inbox.directory` y `app.processed.directory`
   - URL de `app.webhook.url`
   - Rutas/archivos locales (`app.counter.file`, `app.pendientes.file`, etc.)
2. Ejecuta:

```bash
mvn spring-boot:run
```

La app inicia:

- Un watcher de carpeta (componente `MiddlewareSimphony`) para detectar nuevos XML.
- Un job programado `ReintentarFacturasJob` para reintentar envíos fallidos.

---

## Configuración (application.properties)

Principales propiedades usadas:

- `app.inbox.directory`: carpeta donde caen los XML a procesar.
- `app.processed.directory`: carpeta donde se mueven los XML ya procesados.
- `app.webhook.url`: endpoint destino del payload (envíos principales y también reintentos).
- `app.counter.file`: archivo local para persistir el contador de numeración de factura.
- `app.pendientes.file`: archivo JSON con una cola de envíos pendientes (reintentos).
- `app.reintento.delay`: intervalo del scheduler para reintentar (por defecto 60s).
- `app.clientes.api.url`: API para consultar datos de clientes.
- `app.cliente.consumidor-final.identificacion`: identificador por defecto si el XML no trae cliente.
- `app.cufe.registry.file` *(opcional)*: archivo donde se guarda el registro de CUFEs (default: `facturas_cufes.json`).
- `app.dian.ambiente`: ambiente DIAN usado al generar CUFE (en el código aparece como default `2`).

---

## Endpoints REST

### Parseo DIAN

- `GET /api/dian/parsear?xmlPath=...`
  - Recibe una ruta a un XML DIAN y devuelve el JSON del documento.
- `GET /api/dian/parsear/basicos?xmlPath=...`
  - Extrae un subconjunto básico de campos desde un XML DIAN.

### Simulación / pruebas

- `GET /api/test/simular-pago?xmlPath=...`
  - Ejecuta el procesamiento (mismo flujo que el watcher) usando un XML de ejemplo.
  - Tiene **rate limit** (Resilience4j) configurado con instancia `simularPago`.
- `POST /api/test/mock-webhook`
  - Endpoint mock que responde `200 OK` (útil para pruebas manuales).

---

## Estructura del código (alto nivel)

- `com.prueba.factura.FacturaApplication`
  - App Spring Boot + `@EnableScheduling`
- `com.prueba.factura.Services.MiddlewareSimphony`
  - WatchService sobre `app.inbox.directory`
  - Parseo del XML de Simphony y envío al webhook
  - Movimiento del archivo a `app.processed.directory`
- `com.prueba.factura.Services.ReintentarFacturasJob`
  - `@Scheduled`: reintenta los registros en `app.pendientes.file`
- `com.prueba.factura.Services.FacturaPendienteService`
  - Persistencia (leer/escribir) de la cola JSON de reintentos
- `com.prueba.factura.Services.FacturaCounterService`
  - Persistencia del contador en `app.counter.file`
- `com.prueba.factura.Services.CufeServices`
  - Generación de CUFE (SHA-384)
- `com.prueba.factura.controller.DianController`
  - Endpoints REST de parseo DIAN
- `com.prueba.factura.controller.TestController`
  - Endpoints para simular y mockear webhook

---

## Tests

Los tests existentes cubren parseo (por ejemplo DIAN y Simphony) usando recursos en `src/test/resources`.

Ejecuta:

```bash
mvn test
```

---

## Notas de trabajo / consideraciones

- Varias rutas están pensadas para Windows y/o valores hardcodeados en servicios (ej. rutas de Journal).
- Algunos componentes realizan acceso a disco (JSON local) para cola de reintentos y registro de CUFEs.
- Si hay tráfico concurrente (watcher + scheduler), es importante considerar consistencia de la cola (ver código en `ReintentarFacturasJob` y `FacturaPendienteService`).

---

## Auditoría (hallazgos) y mejoras sugeridas

> Esta sección está pensada para que una persona en formación pueda revisar riesgos y proponer correcciones.
> No es “teoría”: cada punto referencia el código exacto que revisa.

### 1) Seguridad — CRÍTICO

1.1) Lectura arbitraria de archivos por `xmlPath` (Path Traversal / LFI)
- **Qué pasa:** los endpoints aceptan una ruta de archivo desde el request y la usan directamente para `new File(resolvedPath)`.
- **Por qué importa:** un atacante podría intentar leer archivos fuera de las carpetas esperadas (dependiendo del entorno/FS).
- **Dónde mirar:**
  - `src/main/java/com/prueba/factura/controller/DianController.java` (líneas 30-55) en `parsearXml(...)`.
  - `src/main/java/com/prueba/factura/controller/DianController.java` (líneas 60-75) en `parsearBasicos(...)`.
- **Mejora sugerida:**
  - Validar `xmlPath` contra una **allowlist** de directorios (ej. permitir solo subrutas de `app.inbox.directory` o una carpeta DIAN definida).
  - Validar con `getCanonicalFile()` y `startsWith(allowBasePath)` en vez de comparar strings.
  - Opcional: reemplazar `xmlPath` por un `id` o nombre de archivo, y mapearlo en servidor.

1.2) Exposición de mensajes internos al cliente
- **Qué pasa:** en el catch de `parsearXml` y `parsearBasicos` se devuelve `e.getMessage()` al frontend.
- **Por qué importa:** filtra detalles del sistema (estructura, errores de parsing, rutas).
- **Dónde mirar:**
  - `DianController.java` (líneas 50-55 y 70-75).
- **Mejora sugerida:**
  - Responder con mensaje genérico (ej. “Error procesando XML”) y dejar el detalle solo en logs.

1.3) RateLimiter: posible mismatch del método fallback (posible error en runtime)
- **Qué pasa:** `@RateLimiter` define `fallbackMethod = "rateLimitFallback"`, pero el método fallback no incluye los mismos parámetros que el método original.
- **Por qué importa:** puede fallar en la resolución del fallback o no comportarse como se espera al llegar `429`.
- **Dónde mirar:**
  - `src/main/java/com/prueba/factura/controller/DianController.java` (líneas 30 y 80).
  - `src/main/java/com/prueba/factura/controller/TestController.java` (líneas 30-70).
- **Mejora sugerida (idea):**
  - Ajustar la firma del fallback para que coincida con el método anotado (incluyendo los parámetros del endpoint y el `Exception`).

### 2) Correctitud / Concurrencia — ALTAS

2.1) Reintentos por índice (riesgo de eliminar/modificar el registro equivocado)
- **Qué pasa:** el job obtiene un snapshot `pendientes = leerPendientes()` y luego opera por **índice `i`**:
  - `incrementarIntento(i)`
  - `eliminarFacturaPendiente(i)`
- **Por qué importa:** si mientras el job corre el archivo cambia (por el watcher o múltiples ciclos), el índice del snapshot puede dejar de apuntar al mismo elemento en el archivo actual. Resultado: incrementar o eliminar el elemento equivocado.
- **Dónde mirar:**
  - `src/main/java/com/prueba/factura/Services/ReintentarFacturasJob.java` (líneas 30-75).
  - `src/main/java/com/prueba/factura/Services/FacturaPendienteService.java`:
    - `incrementarIntento(int index)` (líneas 80-85)
    - `eliminarFacturaPendiente(int index)` (líneas 90-95)
- **Mejora sugerida (idea):**
  - Persistir un `id` estable por entrada (UUID) y operar por `id`, no por índice.
  - O sincronizar/lockear a nivel de “ciclo completo” (menos recomendable si crece la cola).

2.2) Persistencia tipo `Map<String,Object>` + casteos frágiles
- **Qué pasa:** `FacturaPendienteService` hace casteos directos a `int`:
  - `int intentos = (int) factura.getOrDefault("intentos", 0);`
- **Por qué importa:** si Jackson persiste/lee el número como otro tipo (`Long`, etc.), puede aparecer `ClassCastException`.
- **Dónde mirar:**
  - `FacturaPendienteService.java` (líneas 80-100).
- **Mejora sugerida (idea):**
  - Leer como `Number` y convertir con `intValue()`.

### 3) Funcional / Validaciones — ALTAS o MEDIAS

3.1) Validación de CUFE hex: posible fallo por mayúsculas
- **Qué pasa:** el regex es case-insensitive, pero luego valida con:
  - `capturaCufe.matches("^[a-f0-9]+$")`
- **Por qué importa:** si el CUFE trae letras `A-F`, el `matches` falla (solo acepta minúsculas).
- **Dónde mirar:**
  - `src/main/java/com/prueba/factura/Services/TicketParserService.java` (líneas 20-35).
- **Mejora sugerida:**
  - Normalizar el valor a minúsculas antes de validar.

### 4) Operación / Robustez — MEDIAS

4.1) Watcher + procesamiento: `Thread.sleep(500)` puede ser insuficiente (archivo aún no completo)
- **Qué pasa:** al detectar `ENTRY_CREATE`, el watcher espera `500ms` y procesa.
- **Por qué importa:** si el archivo tarda más en escribirse, el parser puede leer XML incompleto.
- **Dónde mirar:**
  - `src/main/java/com/prueba/factura/Services/MiddlewareSimphony.java` (líneas 100-130).
- **Mejora sugerida:**
  - Esperar estabilidad del tamaño/fecha de modificación (ej. “si no cambia por N segundos”).
  - O usar `ENTRY_MODIFY`/estrategia de reintento de parsing local.

4.2) Journal tailer: `StringBuilder` sin límite (riesgo de crecimiento de memoria)
- **Qué pasa:** el `StringBuilder contenido` acumula líneas hasta encontrar marcadores:
  - `=============` o `GRACIAS POR SU COMPRA`
- **Por qué importa:** si el archivo no trae el delimitador esperado, puede crecer sin control.
- **Dónde mirar:**
  - `src/main/java/com/prueba/factura/Services/JournalConeccionServices.java` (líneas 20-45).
- **Mejora sugerida:**
  - Limitar tamaño máximo del buffer (ej. si supera X chars, reiniciar o cortar).
  - Externalizar la ruta a propiedades.

### 5) Calidad / Performance — MEDIAS o BAJAS

5.1) Performance: creación repetida de `HttpClient` en reintentos
- **Dónde mirar:**
  - `ReintentarFacturasJob.java` (líneas 80-95) en `enviarHTTP(...)`.
- **Mejora sugerida:**
  - Reutilizar un `HttpClient` compartido (pool).

5.2) Logging vs `System.out.println`
- **Qué pasa:** hay `System.out.println(...)` en varios servicios.
- **Por qué importa:** ensucia logs, dificulta correlación y control centralizado.
- **Dónde mirar (ejemplos):**
  - `MiddlewareSimphony.java` (líneas 760-770) usa `System.out.println(...)`.
  - `ComunicadorBase.java` también imprime en consola en métodos de utilidad (`listar`, `crear`, `main`).

5.3) Consistencia de dependencias (Spring Boot parent vs libs “boot3”)
- **Qué pasa:** `pom.xml` usa `spring-boot-starter-parent` con versión `4.1.0`, pero el proyecto depende de artefactos explícitos para Spring Boot 3 (`resilience4j-spring-boot3`).
- **Por qué importa:** puede causar incompatibilidades de versión (compilación/arranque).
- **Dónde mirar:**
  - `pom.xml` (líneas 10 y 60-70) para ver el parent y la dependencia `resilience4j-spring-boot3`.

---

### Cómo usar esta auditoría (para el aprendiz)

1. Empieza por los **CRÍTICOS** (seguridad de archivos y fallback de rate limit).
2. Luego revisa **ALTAS** (reintentos por índice y tipos de datos/casteos).
3. Después valida escenarios de operación:
   - watcher leyendo XML incompleto
   - tailer creciendo buffer
4. Finalmente optimiza calidad/observabilidad (logging, reutilizar clientes HTTP, etc.).
5.cambio
