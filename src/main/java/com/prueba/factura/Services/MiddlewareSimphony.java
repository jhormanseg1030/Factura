package com.prueba.factura.Services;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class MiddlewareSimphony implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MiddlewareSimphony.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.inbox.directory}")
    private String dirInbox;

    @Value("${app.processed.directory}")
    private String dirProcessed;

    @Value("${app.webhook.url}")
    private String apiUrl;

    @Value("${app.dian.ambiente:1}")
    private String tipoAmbiente;

    @Value("${app.cliente.consumidor-final.identificacion:2222222222}")
    private String identificacionConsumidorFinal;   

    @Value("${app.cufe.registry.file:facturas_cufes.json}")
    private String cufeRegistryFile;

    @Value ("${app.api.key}")
    private String apiKey;

    @Value ("${app.target.url}")
    private String targetUrl;

    @Value ("${app.emission.point.id}")
    private String emissionPointId;

    @Autowired
    private FacturaCounterService facturaCounterService;

    @Autowired
    private FacturaPendienteService facturaPendienteService;

    @Autowired
    private ComunicadorBase comunicadorBase;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public void run(String... args) throws Exception{
        logger.info("Iniciando Vigilante de la carpeta de Simphony...");
        try{
            Files.createDirectories(Paths.get(dirInbox));
            Files.createDirectories(Paths.get(dirProcessed));
        }
        catch(Exception e){
            logger.error("Error al crear los directorios inbox/processed", e);
        }
        Thread threadWatcher = new Thread(this::iniciarWatchService);
        threadWatcher.setDaemon(true);
        threadWatcher.start();
    }

    private void iniciarWatchService(){
        try{
            WatchService watchService = FileSystems.getDefault().newWatchService();
                Path pathInbox = Paths.get(dirInbox);
                pathInbox.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

                logger.info("Vigilando la carpeta: {}", dirInbox);

                while(true){
                    WatchKey key = watchService.take(); 
                    //esto espera a que llegue la informacion

                    for(WatchEvent<?> event : key.pollEvents()){
                        WatchEvent.Kind<?> kind = event.kind();

                        if(kind == StandardWatchEventKinds.ENTRY_CREATE){
                            Path fileName = (Path) event.context();
                            String nombreArchivo = fileName.toString();

                            if(nombreArchivo.endsWith(".xml")){
                                logger.info("Nuevo XML detectado!!: {}", nombreArchivo);

                                File xmlFile = new File(dirInbox, nombreArchivo);

                                Thread.sleep(500);
                                procesarXML(xmlFile);
                            }
                        }
                    }
                    boolean reset = key.reset();
                    if(!reset) break;
                }
        }catch(Exception e){
            logger.error("Error en el WatchService:", e);
        }
    }

    private static List<Map<String, Object>> extraerProductos(Document doc){
        List<Map<String, Object>> productos = new ArrayList<>();
        NodeList menuItems = doc.getElementsByTagName("OraPayloadEntityMI");

        for(int i = 0; i < menuItems.getLength(); i++){
            Element itemElement = (Element) menuItems.item(i);
            NodeList fields = itemElement.getElementsByTagName("OraPayloadEntityField");
            Map<String, String> itemFields = new HashMap<>();

            for(int j = 0; j < fields.getLength(); j++){
                Element field = (Element) fields.item(j);
                String name = field.getAttribute("field");
                String value = field.getAttribute("value");
                if(name != null && !name.isBlank()){
                    itemFields.put(name, value == null ? "" : value);
                }
            }
            String nombreProducto = itemFields.getOrDefault("Name", "");
            if(nombreProducto.isBlank()){
                continue;
            }
            String codigo = itemFields.getOrDefault("MenuItemNumber", itemFields.getOrDefault("ObjectNumber", "PLT-" + (i + 1)));
            String cantidadStr = itemFields.getOrDefault("SalesCount", "1");
            String totalItemStr = itemFields.getOrDefault("Total", "0.00");

            String codImpuesto = itemFields.getOrDefault("DE_SATCOM_CodigoImpuesto", "01");
            String porcImpuesto = itemFields.getOrDefault("DE_SATCOM_Porc_Impuestos", "0.00");
            String nombreImpuesto = itemFields.getOrDefault("DE_SATCOM_NombreImpuesto", "IVA");

            long totalItem = redondearEntero(parseDoubleSafe(totalItemStr.replace(',', '.'), 0.0));
            double cantidad = parseDoubleSafe(cantidadStr.replace(',', '.'), 0.0);
            if(cantidad <= 0) cantidad = 1.0;
            double porcentajeImp = parseDoubleSafe(porcImpuesto.replace(',', '.'), 0.0);

            long baseImponible;
            long valorImpuesto;
            if (porcentajeImp > 0) {
                baseImponible = redondearEntero(totalItem / (1.0 + (porcentajeImp / 100.0)));
                valorImpuesto = totalItem - baseImponible;
            } else {
                baseImponible = totalItem;
                valorImpuesto = 0L;
            }
            long precioUnitarioSinImpuesto = redondearEntero(baseImponible / cantidad);

            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("codigo", codigo);
            itemMap.put("nombre", nombreProducto);
            itemMap.put("descripcion", nombreProducto);
            itemMap.put("unidad_medida", "94");
            itemMap.put("cantidad", String.format(Locale.US, "%.2f", cantidad));
            itemMap.put("Total", formatoDineroEntero(totalItem));
            itemMap.put("precio", formatoDineroEntero(precioUnitarioSinImpuesto));
            itemMap.put("descuento", "0.00");
            itemMap.put("subtotal", formatoDineroEntero(baseImponible));

            List<Map<String, String>> impuestosItem = new ArrayList<>();
            if(!codImpuesto.isBlank() && porcentajeImp > 0){
                Map<String, String> impMap = new LinkedHashMap<>();
                impMap.put("codigo", codImpuesto);
                impMap.put("nombre", nombreImpuesto.isBlank() ? "IVA" : nombreImpuesto);
                impMap.put("porcentaje", String.format(Locale.US, "%.2f", porcentajeImp));
                impMap.put("base", formatoDineroEntero(baseImponible));
                impMap.put("valor", formatoDineroEntero(valorImpuesto));
                impuestosItem.add(impMap);
            }
            itemMap.put("impuestos", impuestosItem);

            productos.add(itemMap);
        }
        return productos;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> consolidarImpuestosDesdeProductos(List<Map<String, Object>> productos) {
        Map<String, Map<String, Object>> agrupado = new LinkedHashMap<>();

        for (Map<String, Object> producto : productos) {
            Object impuestosObj = producto.get("impuestos");
            if (!(impuestosObj instanceof List<?> impuestosList)) {
                continue;
            }
            for (Object raw : impuestosList) {
                if (!(raw instanceof Map<?, ?> impRaw)) {
                    continue;
                }
                Map<String, String> imp = (Map<String, String>) impRaw;
                String codigo = imp.getOrDefault("codigo", "");
                String porcentaje = imp.getOrDefault("porcentaje", "0.00");
                if (codigo.isBlank()) {
                    continue;
                }
                String clave = codigo + "_" + porcentaje;
                Map<String, Object> acumulado = agrupado.computeIfAbsent(clave, k -> {
                    Map<String, Object> nuevo = new LinkedHashMap<>();
                    nuevo.put("codigo", codigo);
                    nuevo.put("nombre", imp.getOrDefault("nombre", ""));
                    nuevo.put("porcentaje", porcentaje);
                    nuevo.put("base", 0L);
                    nuevo.put("monto", 0L);
                    return nuevo;
                });
                long base = redondearEntero(parseDoubleSafe(imp.getOrDefault("base", "0"), 0.0));
                long valor = redondearEntero(parseDoubleSafe(imp.getOrDefault("valor", "0"), 0.0));
                acumulado.put("base", (long) acumulado.get("base") + base);
                acumulado.put("monto", (long) acumulado.get("monto") + valor);
            }
        }

        List<Map<String, String>> lista = new ArrayList<>();
        int contador = 1;
        for (Map<String, Object> valores : agrupado.values()) {
            Map<String, String> impuestoFinal = new LinkedHashMap<>();
            impuestoFinal.put("codigo_impuesto", String.valueOf(valores.get("codigo")));
            impuestoFinal.put("nombre_impuesto", String.valueOf(valores.get("nombre")));
            impuestoFinal.put("numero_impuesto", String.valueOf(contador++));
            impuestoFinal.put("porcentaje_impuesto", String.valueOf(valores.get("porcentaje")));
            impuestoFinal.put("base_imponible", formatoDineroEntero((long) valores.get("base")));
            impuestoFinal.put("monto_impuesto", formatoDineroEntero((long) valores.get("monto")));
            lista.add(impuestoFinal);
        }
        return lista;
    }

    private static List<Map<String, String>> extraerImpuestos(Document doc, String subtotalFacturaStr){
        
        Map<String, Map<String, Double>> mapaAgrupado = new HashMap<>();
        Map<String, String> nombreImpuestos = new HashMap<>();

        NodeList menuItems = doc.getElementsByTagName("OraPayloadEntityMI");

        for(int i = 0; i < menuItems.getLength(); i++){
            Element itemsElement = (Element) menuItems.item(i);
            NodeList fields = itemsElement.getElementsByTagName("OraPayloadEntityField");

            Map<String, String> itemFields = new HashMap<>();
            for(int j = 0; j < fields.getLength(); j++){
                Element field = (Element) fields.item(j);
                String name = field.getAttribute("field");
                String value = field.getAttribute("value");

                if(name != null && !name.isBlank()){
                    itemFields.put(name, value == null ? "": value);
                }
            }

            String codigo = itemFields.getOrDefault("DE_SATCOM_CodigoImpuesto", "");
            String porcentajeStr = itemFields.getOrDefault("DE_SATCOM_Porc_Impuestos", "0.00");
            String nombre = itemFields.getOrDefault("DE_SATCOM_NombreImpuesto", "");
            String totalItemStr = itemFields.getOrDefault("Total","0.00" );

            if(codigo.isEmpty() || totalItemStr.equals("0.00")){
                continue;
            }

            long total = 0L;
            double porcentaje = 0.0;
            String taxableStr = "";
            String taxAmountStr = "";

            NodeList taxDataFields = itemsElement.getElementsByTagName("OraPayloadEntityField");
            for (int j = 0; j < taxDataFields.getLength(); j++) {
                Element taxData = (Element) taxDataFields.item(j);
                if (!"TaxData".equals(taxData.getAttribute("field"))) {
                    continue;
                }

                NodeList taxValues = taxData.getElementsByTagName("OraPayloadEntityFieldGenericParameter");
                for (int k = 0; k < taxValues.getLength(); k++) {
                    Element taxValue = (Element) taxValues.item(k);
                    String field = taxValue.getAttribute("field");
                    if ("Taxable".equals(field)) {
                        taxableStr = taxValue.getAttribute("value");
                    } else if ("Tax".equals(field)) {
                        taxAmountStr = taxValue.getAttribute("value");
                    }
                }
            }

            try{
                porcentaje = Double.parseDouble(porcentajeStr.replace(',', '.'));
                total = redondearEntero(Double.parseDouble(totalItemStr.replace(',', '.')));
            } catch (NumberFormatException e) {
                continue;
            }

            long baseImponibleItem;
            long montoImpuestoItem;
            if (!taxableStr.isBlank() && !taxAmountStr.isBlank()) {
                baseImponibleItem = redondearEntero(Double.parseDouble(taxableStr.replace(',', '.')));
                montoImpuestoItem = redondearEntero(Double.parseDouble(taxAmountStr.replace(',', '.')));
                // Preferir coherencia total = base + impuesto cuando el Total del ítem es confiable
                if (total > 0 && porcentaje > 0) {
                    baseImponibleItem = redondearEntero(total / (1.0 + (porcentaje / 100.0)));
                    montoImpuestoItem = total - baseImponibleItem;
                }
            } else if (porcentaje > 0) {
                baseImponibleItem = redondearEntero(total / (1.0 + (porcentaje / 100.0)));
                montoImpuestoItem = total - baseImponibleItem;
            } else {
                baseImponibleItem = total;
                montoImpuestoItem = 0L;
            }

            String claveUnica = codigo + "_" + porcentajeStr;
            nombreImpuestos.putIfAbsent(claveUnica, nombre);

            mapaAgrupado.putIfAbsent(claveUnica, new HashMap<>());

            Map<String, Double> valoresActuales = mapaAgrupado.get(claveUnica);
            mapaAgrupado.get(claveUnica).put("base", valoresActuales.getOrDefault("base", 0.0) + baseImponibleItem);
            mapaAgrupado.get(claveUnica).put("monto", valoresActuales.getOrDefault("monto", 0.0) + montoImpuestoItem);
            mapaAgrupado.get(claveUnica).put("porcentaje", porcentaje);
        }

        if (mapaAgrupado.isEmpty()) {
            NodeList impuestoFields = doc.getElementsByTagName("OraPayloadEntityFieldGenericParameter");
            Map<String, String> impuestoGlobal = new HashMap<>();

            for (int i = 0; i < impuestoFields.getLength(); i++) {
                Element field = (Element) impuestoFields.item(i);
                String name = field.getAttribute("field");
                String value = field.getAttribute("value");
                if (name != null && !name.isBlank()) {
                    impuestoGlobal.put(name, value == null ? "" : value);
                }
            }

            String codigo = impuestoGlobal.getOrDefault("DE_SATCOM_CodigoImpuesto", "");
            String porcentajeStr = impuestoGlobal.getOrDefault("DE_SATCOM_Porc_Impuestos", "0.00");
            String nombre = impuestoGlobal.getOrDefault("DE_SATCOM_NombreImpuesto", "");

            try {
                double porcentaje = Double.parseDouble(porcentajeStr.replace(',', '.'));
                long total = redondearEntero(Double.parseDouble(subtotalFacturaStr.replace(',', '.')));
                if (!codigo.isEmpty() && porcentaje > 0 && total > 0) {
                    long baseImponible = redondearEntero(total / (1.0 + (porcentaje / 100.0)));
                    long montoImpuesto = total - baseImponible;
                    String claveUnica = codigo + "_" + porcentajeStr;
                    Map<String, Double> valores = new HashMap<>();
                    valores.put("base", (double) baseImponible);
                    valores.put("monto", (double) montoImpuesto);
                    valores.put("porcentaje", porcentaje);
                    mapaAgrupado.put(claveUnica, valores);
                    nombreImpuestos.put(claveUnica, nombre);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        List<Map<String, String>> listaImpuestosLimpia = new ArrayList<>();
        int contador = 1;

        for(Map.Entry<String, Map<String, Double>> entrada : mapaAgrupado.entrySet()){
            String clave = entrada.getKey();
            String codigoImpuesto = clave.split("_")[0];
            Map<String, Double> valores = entrada.getValue();
            String nombreImpuesto = nombreImpuestos.get(clave);

            Map<String, String > impuestoFinal = new LinkedHashMap<>();
            impuestoFinal.put("codigo_impuesto", codigoImpuesto);
            impuestoFinal.put("nombre_impuesto", nombreImpuesto);
            impuestoFinal.put("numero_impuesto", String.valueOf(contador++));
            impuestoFinal.put("porcentaje_impuesto", String.format(Locale.US ,"%.2f", valores.get("porcentaje")));
            impuestoFinal.put("base_imponible", formatoDineroEntero(redondearEntero(valores.get("base"))));
            impuestoFinal.put("monto_impuesto", formatoDineroEntero(redondearEntero(valores.get("monto"))));

            listaImpuestosLimpia.add(impuestoFinal);
        }
        return listaImpuestosLimpia;
    }
private static List<Map<String, Object>> extraerTenderMediaList(Document doc) {
    List<Map<String, Object>> tenderMediaList = new ArrayList<>();
    
    NodeList tenders = doc.getElementsByTagName("OraPayloadEntityTmed");
    logger.info("TenderMedia encontrados: {}", tenders.getLength());

    for (int i = 0; i < tenders.getLength(); i++) {
        Element tenderElement = (Element) tenders.item(i);
        NodeList fields = tenderElement.getElementsByTagName("OraPayloadEntityField");
        Map<String, String> campos = new HashMap<>();

        for (int j = 0; j < fields.getLength(); j++) {
            Element field = (Element) fields.item(j);
            String name = field.getAttribute("field");
            String value = field.getAttribute("value");
            if (name != null && !name.isBlank()) {
                campos.put(name, value == null ? "" : value);
            }
        }

        String objectNumberStr = campos.getOrDefault("ObjectNumber", "0");
        String montoStr = campos.getOrDefault("CurrencyAmount", campos.getOrDefault("Total", "0.00"));
        String tipStr = campos.getOrDefault("ChargeTip", campos.getOrDefault("Tip", "0.00"));
        String nombrePago = campos.getOrDefault("Name", "Pago");
        String refNum = campos.getOrDefault("ReferenceNumber", campos.getOrDefault("AuthCode", ""));

        logger.debug("TenderMedia [{}] - ObjectNumber: {}, Monto: {}, Tip: {}, Nombre: {}", 
            i, objectNumberStr, montoStr, tipStr, nombrePago);

        if (!montoStr.isBlank() && !montoStr.equals("0.00")) {
            Map<String, Object> pagoMap = new LinkedHashMap<>();

            int tenderMediaId = parseIntSafe(objectNumberStr, 0);
            long tenderAmount = redondearEntero(parseDoubleSafe(montoStr, 0.00));
            long tipAmount = redondearEntero(parseDoubleSafe(tipStr, 0.00));

            pagoMap.put("tenderMediaId", tenderMediaId);
            pagoMap.put("tenderName", nombrePago);
            pagoMap.put("tenderAmount", tenderAmount);
            pagoMap.put("tipAmount", tipAmount);

            if (!refNum.isBlank()) {
                pagoMap.put("referenceNumber", refNum);
            }

            tenderMediaList.add(pagoMap);
            logger.info("Pago agregado: {}", pagoMap);
        }
    }
    
    logger.info("Total pagos extraídos: {}", tenderMediaList.size());
    return tenderMediaList;
}

    public static Map<String, String> extraerDatosFactura(File xmlFile) {
        try {
            if(!xmlFile.exists() || !xmlFile.canRead()){    
                throw new IllegalArgumentException("El archivo XML no es accesible");
            }

            if(xmlFile.length() > 10_000_000){
                throw new IllegalArgumentException("El archivo XML es demasiado grande");
            }

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
            Document doc = dbBuilder.parse(xmlFile);

            Element rootElement = doc.getDocumentElement();
            if(rootElement == null || rootElement.getElementsByTagName("Check").getLength() == 0){
                throw new IllegalArgumentException("Estructura XML invalida");
            }

            doc.getDocumentElement().normalize();
            Map<String, String> result = new HashMap<>();

            Element checkElement = (Element) doc.getElementsByTagName("Check").item(0);
            result.put("numero_ticket", checkElement.getAttribute("CheckNum"));
            result.put("check_id", checkElement.getAttribute("Id"));
            result.put("harmony_id", checkElement.getAttribute("HarmonyId"));
            result.put("workstation_id", checkElement.getAttribute("WsID"));
            result.put("fecha_hora", checkElement.getAttribute("Timestamp"));

            NodeList fieldList = doc.getElementsByTagName("OraPayloadEntityField");
            for (int i = 0; i < fieldList.getLength(); i++) {
                Element field = (Element) fieldList.item(i);
                String name = field.getAttribute("field");
                String value = field.getAttribute("value");
                if (name == null || name.isBlank()) {
                    continue;
                }

                if ("CreditNoteNumber".equals(name)) {
                    result.putIfAbsent("CreditNoteNumber", value == null ? "": value);
                }

                if("CreditNoteInvoiceDate".equals(name)){
                    result.putIfAbsent("CreditNoteInvoiceDate", value == null ? "": value);
                }

                if("CreditNoteReason".equals(name)){
                    result.putIfAbsent("CreditNoteReason", value ==null ? "": value);
                }

                if("DocumentType".equals(name)){
                    result.putIfAbsent("DocumentType", value == null ? "": value);
                }

                if ("CheckSubtotal".equals(name)) {
                    result.putIfAbsent("subtotal", value == null ? "" : value);
                }
                if ("CheckAutoServiceCharge".equals(name)) {
                    result.putIfAbsent("propina", value == null ? "" : value);
                }
                if ("CheckTotalDue".equals(name)) {
                    result.putIfAbsent("total", value == null ? "" : value);
                }
                if ("PropertyName".equals(name)) {
                    result.putIfAbsent("restaurante", value == null ? "" : value);
                }
                if ("WorkstationName".equals(name)) {
                    result.putIfAbsent("workstation_nombre", value == null ? "" : value);
                }
                if ("TransEmployeeFullName".equals(name)) {
                    result.putIfAbsent("empleado", value == null ? "" : value);
                }
                if ("DE_SATCOM_Genera_Documento".equals(name)) {
                    result.putIfAbsent("genera_documento", value == null ? "FALSE" : value);
                }
                if ("DE_SATCOM_CondicionVenta".equals(name)) {
                    result.putIfAbsent("condicion_venta", value == null ? "N/A" : value);
                }
                if ("DE_SATCOM_CodigoFiscal".equals(name)) {
                    result.putIfAbsent("codigo_fiscal", value == null ? "N/A" : value);
                }
                if ("Guid".equals(name)) {
                    result.putIfAbsent("guid_transaccion", value == null ? "N/A" : value);
                }
                if ("Resolucion".equals(name)){
                    result.putIfAbsent("Resolucion", value == null ? "" : value);
                }
                if ("ResolucionIni".equals(name)){
                    result.putIfAbsent("ResolucionIni", value == null ? "" : value);
                }
                if ("ResolucionFin".equals(name)){
                    result.putIfAbsent("ResolucionFin", value == null ? "": value);
                }
                if ("FechaResolucion".equals(name)){
                    result.putIfAbsent("FechaResolucion", value == null ? "" : value);
                }
                if("RucEmisor".equals(name)){
                    result.putIfAbsent("RucEmisor", value == null ? "": value);  
                }
                if("ClaveTecnica".equals(name)){
                    result.putIfAbsent("ClaveTecnica", value == null ? "": value);
                }
                if("RangoIni".equals(name)){
                    result.putIfAbsent("RangoIni", value == null ? "": value);
                }
                if("RangoFin".equals(name)){
                    result.putIfAbsent("RangoFin", value == null ? "": value);
                }
                if ("CustomerIdentification".equals(name)
                    || "CustomerIDNumber".equals(name)
                    || "CustomerDocumentNumber".equals(name)
                    || "CustomerIdentificationNumber".equals(name)
                    || "DE_SATCOM_IdentificacionCliente".equals(name)) {
                    result.putIfAbsent("identificacion_cliente", value == null ? "" : value);
                }
            }

            //Propina 
            List<Map<String, Object>> productos = extraerProductos(doc);
            String propinaRaw = result.getOrDefault("propina", "0.00");
            long propinaNum = redondearEntero(parseDoubleSafe(propinaRaw.replace(',', '.'), 0.0));
            result.put("propina", formatoDineroEntero(propinaNum));

            if (result.containsKey("total")) {
                result.put("total", formatoDineroEntero(redondearEntero(
                    parseDoubleSafe(result.get("total").replace(',', '.'), 0.0))));
            }
            if (result.containsKey("subtotal")) {
                result.put("subtotal", formatoDineroEntero(redondearEntero(
                    parseDoubleSafe(result.get("subtotal").replace(',', '.'), 0.0))));
            }

            List<Map<String, String>> impuestos = consolidarImpuestosDesdeProductos(productos);
            if (impuestos.isEmpty()) {
                impuestos = extraerImpuestos(doc, result.getOrDefault("subtotal", "0.00"));
            }

            if (!productos.isEmpty()) {
                Map<String, Object> primero = productos.get(0);
                result.putIfAbsent("producto", String.valueOf(primero.getOrDefault("nombre", "")));
                String cantidadResumen = String.valueOf(primero.getOrDefault("cantidad", "1"));
                if (cantidadResumen.endsWith(".00")) {
                    cantidadResumen = cantidadResumen.substring(0, cantidadResumen.length() - 3);
                }
                result.putIfAbsent("cantidad", cantidadResumen);
                // Precio unitario de resumen: Total del ítem (como en Simphony UnitPrice/Total)
                result.putIfAbsent("precio_unitario", String.valueOf(primero.getOrDefault("Total",
                    primero.getOrDefault("precio", ""))));
                result.putIfAbsent("subtotal", String.valueOf(primero.getOrDefault("Total", "0.00")));
            }

            long totalImpuestos = 0L;
            long totalBaseImponible = 0L;
            for(Map<String, String> impuesto : impuestos){
                totalImpuestos += redondearEntero(parseDoubleSafe(impuesto.getOrDefault("monto_impuesto", "0.00"), 0.0));
                totalBaseImponible += redondearEntero(parseDoubleSafe(impuesto.getOrDefault("base_imponible", "0.00"), 0.0));
            }

            result.putIfAbsent("genera_documento", "FALSE");
            result.putIfAbsent("condicion_venta", "N/A");
            result.putIfAbsent("codigo_fiscal", "N/A");
            result.putIfAbsent("guid_transaccion", "N/A");
            result.putIfAbsent("producto", "");
            result.putIfAbsent("cantidad", "");
            result.putIfAbsent("precio_unitario", "");
            result.putIfAbsent("subtotal", "");
            result.put("total_impuestos", formatoDineroEntero(totalImpuestos));
            result.put("base_imponible_total", formatoDineroEntero(totalBaseImponible));
            result.putIfAbsent("propina", "");
            result.putIfAbsent("total", "");
            result.putIfAbsent("restaurante", "");
            result.putIfAbsent("workstation_nombre", "");
            result.putIfAbsent("empleado", "");
            result.putIfAbsent("impuestos_json", null);
            result.putIfAbsent("RangoIni", "");
            result.putIfAbsent("RangoFin", "");
            result.putIfAbsent("CreditNoteNumber", "");
            result.putIfAbsent("CreditNoteInvoiceDate", "");
            result.putIfAbsent("CreditNoteReason", "");
            result.putIfAbsent("DocumentType", "");

            ObjectMapper mapper = new ObjectMapper();
            result.put("total_impuestos", formatoDineroEntero(totalImpuestos));
            result.put("base_imponible_total", formatoDineroEntero(totalBaseImponible));
            result.put("impuestos_json", mapper.writeValueAsString(impuestos));
            result.put("items_json", mapper.writeValueAsString(productos));

            List<Map<String, Object>> pagos = extraerTenderMediaList(doc);
            result.put("pagos_json", mapper.writeValueAsString(pagos));

            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo el XML de factura: " + e.getMessage(), e);
        }
    }

    public void procesarXML(File xmlFile){
        try{
            Map<String, String> datos = extraerDatosFactura(xmlFile);

            String checkNum = datos.getOrDefault("numero_ticket", "");
            String checkId = datos.getOrDefault("check_id", "");
            String harmonyId = datos.getOrDefault("harmony_id", "");
            String wsId = datos.getOrDefault("workstation_id", "");
            String timestamp = datos.getOrDefault("fecha_hora", "");
            String generaDoc = datos.getOrDefault("genera_documento", "FALSE");
            String condicionVenta = datos.getOrDefault("condicion_venta", "N/A");
            String codigoFiscal = datos.getOrDefault("codigo_fiscal", "N/A");
            String guid = datos.getOrDefault("guid_transaccion", "N/A");
            String fechaFac = obtenerFechaCufe(timestamp);
            String horaFac = obtenerHoraCufe(timestamp);

            double valFacNum = redondearEntero(Double.parseDouble(datos.getOrDefault("base_imponible_total", "0.00").replace(',', '.')));
            double ivaNum = 0.00;
            double incNum = redondearEntero(Double.parseDouble(datos.getOrDefault("total_impuestos", "0.00").replace(',', '.')));
            double icaNum = 0.00;
            double totalFiscalNum = valFacNum + ivaNum + incNum + icaNum;

            String claveTecnicaXml = datos.getOrDefault("ClaveTecnica", "");
            String nitEmisor = datos.getOrDefault("RucEmisor", "8605108638");
            String prefijoFac = datos.getOrDefault("RangoIni", "SETT").replaceAll("[0-9]", "");
            String identificacionCliente = datos.getOrDefault("identificacion_cliente", "");
            
            JsonNode cliente = null;
            
            if (!identificacionCliente.isBlank()) {
                cliente = comunicadorBase.buscarPorIdentificacion(identificacionCliente);
                if (cliente != null) {
                    logger.info("Cliente encontrado por identificacion: {}", identificacionCliente);
                }
            }
            
            if (cliente == null && !checkId.isBlank()) {
                cliente = comunicadorBase.buscarPorCheckId(checkId);
                if (cliente != null) {
                    logger.info("Cliente encontrado por check_id: {}", checkId);
                } else {
                    logger.warn("No se encontró cliente con check_id: {}", checkId);
                }
            }
            
            if (cliente == null) {
                if (identificacionCliente.isBlank() && checkId.isBlank()) {
                    logger.warn("El XML no trae identificacion_cliente ni check_id; no se puede consultar la base");
                } else {
                    logger.warn("No se encontro el cliente con identificacion: {} o check_id: {}", 
                        identificacionCliente, checkId);
                }
            }

            if (cliente == null && identificacionCliente.isBlank()) {
                identificacionCliente = identificacionConsumidorFinal;
                logger.info("Venta sin cliente identificado; se usará consumidor final: {}",
                    identificacionConsumidorFinal);
            }

            String identificadorFactura = construirIdentificadorFactura(datos);
            Map<String, String> registroExistente = leerRegistroCufe(identificadorFactura);
            String numeroFacturaCompleto = registroExistente == null ? null
                : registroExistente.get("numero_factura_completo");
            String cufeGenerado = registroExistente == null ? null : registroExistente.get("cufe");

            logger.info("=== Datos Extraídos del XML ===");
            logger.info("• Ticket (CheckNum): {}", checkNum);
            logger.info("• Estación (WsID): {}", wsId);
            logger.info("• Fecha/Hora: {}", timestamp);
            logger.info("• Condición de Venta: {}", condicionVenta);
            logger.info("• Identificación Cliente: {}", identificacionCliente);
            logger.info("• Cliente Encontrado: {}", (cliente != null));
            logger.info("• Código Fiscal: {}", codigoFiscal);
            logger.info("• GUID Transaccion: {}", guid);
            logger.info("• Genera Documento: {}", generaDoc);
            logger.info("• Propina: {}", datos.getOrDefault("propina", "N/A"));
            logger.info("• Total: {}", datos.getOrDefault("total", "N/A"));
            logger.info("• Restaurante: {}", datos.getOrDefault("restaurante", "N/A"));
            logger.info("• CUFE Generado: {}", cufeGenerado);
            logger.info("• Prefijo Factura: {}", prefijoFac);

            if("TRUE".equalsIgnoreCase(generaDoc)){
                if (registroExistente == null) {
                    numeroFacturaCompleto = prefijoFac + facturaCounterService.obtenerSiguienteNumero();
                    String numAdquiriente = cliente == null
                        ? identificacionConsumidorFinal
                        : cliente.path("identificacion").asText(identificacionConsumidorFinal);

                    cufeGenerado = CufeServices.generarCufe(numeroFacturaCompleto, fechaFac, horaFac, valFacNum, "01", ivaNum,
                        "04", incNum, "03", icaNum, totalFiscalNum, nitEmisor, numAdquiriente, claveTecnicaXml, tipoAmbiente);
                }
                String urlQr = generarUrlQr(cufeGenerado);
                logger.info("• Url Qr: {}", urlQr);

                Map<String, Object> jsonMap = new LinkedHashMap<>();
                jsonMap.put("numero_factura", numeroFacturaCompleto);
                jsonMap.put("fecha_procesamiento", LocalDate.now().toString());
                jsonMap.put("numero_ticket", datos.get("numero_ticket"));
                jsonMap.put("check_id", checkId);
                jsonMap.put("harmony_id", harmonyId);
                jsonMap.put("caja_wsid", wsId);
                jsonMap.put("hora_hora", horaFac);
                jsonMap.put("fecha_hora", fechaFac+ "T" + horaFac);
                jsonMap.put("condicion_venta", condicionVenta);
                jsonMap.put("codigo_fiscal", codigoFiscal);
                jsonMap.put("guid_transaccion", guid);
                jsonMap.put("tipo_ambiente", tipoAmbiente);
                jsonMap.put("numero_factura_completo", numeroFacturaCompleto);
                jsonMap.put("clave_tecnica_xml", claveTecnicaXml);
                jsonMap.put("Resolucion", datos.getOrDefault("Resolucion", "N/A"));
                jsonMap.put("ResolucionIni", datos.getOrDefault("ResolucionIni", "N/A"));
                jsonMap.put("ResolucionFin", datos.getOrDefault("ResolucionFin", "N/A"));
                jsonMap.put("FechaResolucion", datos.getOrDefault("FechaResolucion", "N/A"));
                jsonMap.put("RangoIni", datos.getOrDefault("RangoIni", "N/A"));
                jsonMap.put("RangoFin", datos.getOrDefault("RangoFin", "N/A"));
                jsonMap.put("subtotal_base", formatoDineroEntero(redondearEntero(valFacNum)));
                jsonMap.put("total_impuesto", formatoDineroEntero(redondearEntero(incNum)));
                jsonMap.put("total_fiscal", formatoDineroEntero(redondearEntero(totalFiscalNum)));
                jsonMap.put("propina", datos.getOrDefault("propina", "0.00"));
                jsonMap.put("total", datos.getOrDefault("total", "0.00"));
                jsonMap.put("restaurante", datos.getOrDefault("restaurante", "N/A"));
                jsonMap.put("workstation", datos.getOrDefault("workstation_nombre", "N/A"));
                jsonMap.put("empleado", datos.getOrDefault("empleado", "N/A"));
                jsonMap.put("identificacion_cliente", identificacionCliente);
                jsonMap.put("cliente", cliente);
                jsonMap.put("cliente_encontrado", cliente != null);
                jsonMap.put("impuestos", objectMapper.readTree(datos.getOrDefault("impuestos_json", "[]")));
                jsonMap.put("items", objectMapper.readTree(datos.getOrDefault("items_json", "[]")));
                jsonMap.put("pagos", objectMapper.readTree(datos.getOrDefault("pagos_json", "[]")));
                jsonMap.put("cufe", cufeGenerado);
                jsonMap.put("qr", urlQr);
                jsonMap.put("qr_url", urlQr);

            String crediterNoteNumber = datos.getOrDefault("CreditNoteNumber", "N/A");
            boolean esNotaCredito = !crediterNoteNumber.isBlank() && !crediterNoteNumber.equals("N/A");
            if(esNotaCredito){  
                logger.info("=== Iniciando Flujo de Nota de Crédito ===");
                jsonMap.put("DocumentType", "91");
                jsonMap.put("CreditNoteNumber", datos.getOrDefault("CreditNoteNumber", "N/A"));
                jsonMap.put("CreditNoteInvoiceDate", datos.getOrDefault("CreditNoteInvoiceDate", "N/A"));
                jsonMap.put("CreditNoteReason", datos.getOrDefault("CreditNoteReason", "N/A"));
            } else{
                logger.info("=== Iniciando Flujo de Factura ===");
                jsonMap.put("DocumentType", "01");
            }
                String jsonPayload = objectMapper.writeValueAsString(jsonMap);
                guardarRegistroCufe(identificadorFactura, numeroFacturaCompleto, cufeGenerado);
                enviarHttpPOST(jsonPayload);
            } else {
                logger.info("La factura no requiere procesamiento de documento fiscal");
            }

            Path origen = xmlFile.toPath();
            Path destino = Paths.get(dirProcessed, xmlFile.getName());
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Archivo movido a carpeta de procesados /processed\n");

        } catch(Exception e){
            logger.error("ERROR procesando el archivo XML {}: {}", xmlFile.getName(), e.getMessage(), e);
        }
    }

    private String construirIdentificadorFactura(Map<String, String> datos) {
        String guid = datos.getOrDefault("guid_transaccion", "");
        if (!guid.isBlank() && !"N/A".equalsIgnoreCase(guid)) {
            return "guid:" + guid;
        }

        String checkId = datos.getOrDefault("check_id", "");
        if (!checkId.isBlank()) {
            return "check_id:" + checkId;
        }

        return "ticket:" + datos.getOrDefault("numero_ticket", "")
            + "|fecha:" + datos.getOrDefault("fecha_hora", "");
    }

    private synchronized Map<String, String> leerRegistroCufe(String identificador) {
        Path path = Paths.get(cufeRegistryFile);
        if (!Files.exists(path)) {
            return null;
        }

        try {
            Map<String, Map<String, String>> registros = objectMapper.readValue(
                path.toFile(), new TypeReference<Map<String, Map<String, String>>>() {});
            return registros.get(identificador);
        } catch (Exception e) {
            logger.warn("No se pudo leer el registro de CUFEs: {}", e.getMessage());
            return null;
        }
    }

    private synchronized void guardarRegistroCufe(String identificador, String numeroFactura, String cufe) {
        try {
            Path path = Paths.get(cufeRegistryFile);
            Map<String, Map<String, String>> registros = new LinkedHashMap<>();
            if (Files.exists(path)) {
                registros = objectMapper.readValue(
                    path.toFile(), new TypeReference<Map<String, Map<String, String>>>() {});
            }

            Map<String, String> registro = new LinkedHashMap<>();
            registro.put("numero_factura_completo", numeroFactura);
            registro.put("cufe", cufe);
            registros.put(identificador, registro);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), registros);
        } catch (Exception e) {
            logger.error("No se pudo guardar el registro de CUFE", e);
        }
    }

    private String obtenerFechaCufe(String timestamp) {
        if(timestamp == null || timestamp.isBlank()){
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
        }
        try{
            if (timestamp.contains("T")) {
                timestamp = timestamp.split("T")[0];
            }

            if(timestamp.contains("/")){
                String fechaParte = timestamp.split(" ")[0];
                String [] partes = fechaParte.split("/");
                if(partes.length == 3){
                    return String.format("%s-%02d-%02d", partes[2], Integer.parseInt(partes[0]), Integer.parseInt(partes[1]));
                }
            }
            if (timestamp.length() >= 10) {
                return timestamp.substring(0, 10); 
            }
        } catch(Exception e){
            logger.warn("Error al extraer fecha del timestamp: {}", timestamp);
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy"));
    }

    private String obtenerHoraCufe(String timestamp){
        if(timestamp == null || timestamp.isBlank()){
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss-05:00"));
        }
        try{
            String horaExtraida = "";

            if(timestamp.contains("T")){
                horaExtraida = timestamp.split("T")[1];
            }else if( timestamp.contains(" ")){
                horaExtraida = timestamp.split(" ")[1];
            }else{
                horaExtraida = timestamp;
            }

            if(horaExtraida.length() >= 8){
                horaExtraida = horaExtraida.substring(0, 8);
            }

            if(horaExtraida.matches("\\d{2}:\\d{2}:\\d{2}")){
                return horaExtraida + "-05:00";
            }
        } catch(Exception e){
            logger.warn("Error al extraer hora del timestamp: {}", timestamp, e);
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss-05:00"));
    }


    private void enviarHttpPOST(String jsonPayload){
        try{
            String destino = (targetUrl != null && !targetUrl.isBlank()) ? targetUrl : apiUrl;
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(destino))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("User-Agent", "FacturaApp/1.0")
            .header("X-API-KEY", apiKey != null ? apiKey : "");

            if (emissionPointId != null && !emissionPointId.isBlank()) {
                requestBuilder.header("X-Emission-Point-ID", emissionPointId);
            }

            HttpRequest request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();

            logger.info("Enviando datos a la API: {}", destino);
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Respuesta exitosa: {}", response.statusCode());
            } else {
                logger.warn("Respuesta con estado: {} body: {}", response.statusCode(), response.body());
                facturaPendienteService.guardarFacturaPendiente(jsonPayload, "Status code:" + response.statusCode());
            }
            System.out.println("Respuesta del servidor - Codigo Status" + response.statusCode());
        }catch(HttpStatusCodeException e){
            logger.error("Error HTTP al enviar datos a la API: {}", e.getStatusCode(), e.getResponseBodyAsString());
            facturaPendienteService.guardarFacturaPendiente(jsonPayload, "HTTP Status code:" + e.getStatusCode());
        }catch(Exception e){
            logger.error("Error al enviar la solicitud Http POST guardando en cola pendiente", e);
            facturaPendienteService.guardarFacturaPendiente(jsonPayload, e.getMessage());
        }
    }

    private String generarUrlQr(String cufe){
        if(cufe == null || cufe.isBlank()){
            throw new IllegalArgumentException("El CUFE no puede estar vacio");
        }
        return "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=" + cufe;
    }

    private static long redondearEntero(double valor) {
        return Math.round(valor);
    }

    private static String formatoDineroEntero(long valor) {
        return String.format(Locale.US, "%.2f", (double) valor);
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("No se pudo parsear '{}' como entero, usando default: {}", value, defaultValue);
            return defaultValue;
        }
    }

    private static double parseDoubleSafe(String value, double defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            logger.warn("No se pudo parsear '{}' como double, usando default: {}", value, defaultValue);
            return defaultValue;
        }
    }

}
