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
import java.time.LocalDateTime;
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

    @Value("${app.dian.ambiente:2}")
    private String tipoAmbiente;

    @Value("${app.cufe.registry.file:facturas_cufes.json}")
    private String cufeRegistryFile;

    @Value("${app.cliente.prueba.identificacion}")
    private String identificacionPrueba;

    @Autowired
    private FacturaCounterService facturaCounterService;

    @Autowired
    private FacturaPendienteService facturaPendienteService;

    @Autowired
    private ComunicadorBase comunicadorBase;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
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
                    WatchKey key = watchService.take(); //esto espera a que llegue la informacion

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

    private static List<Map<String, String>> extraerProductos(Document doc) {
        List<Map<String, String>> productos = new ArrayList<>();
        NodeList menuItems = doc.getElementsByTagName("OraPayloadEntityMI");

        for (int i = 0; i < menuItems.getLength(); i++) {
            Element itemElement = (Element) menuItems.item(i);
            NodeList fields = itemElement.getElementsByTagName("OraPayloadEntityField");
            Map<String, String> item = new HashMap<>();

            for (int j = 0; j < fields.getLength(); j++) {
                Element field = (Element) fields.item(j);
                String name = field.getAttribute("field");
                String value = field.getAttribute("value");
                if (name != null && !name.isBlank()) {
                    item.put(name, value == null ? "" : value);
                }
            }

            String nombreProducto = item.get("Name");
            if (nombreProducto != null && !nombreProducto.isBlank()) {
                productos.add(item);
            }
        }

        return productos;
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

            double porcentaje = 0.0;
            double total = 0.0;
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
                total = Double.parseDouble(totalItemStr.replace(',', '.'));
            } catch (NumberFormatException e) {
                continue;
            }

            double baseImponibleItem;
            double montoImpuestoItem;
            if (!taxableStr.isBlank() && !taxAmountStr.isBlank()) {
                baseImponibleItem = Double.parseDouble(taxableStr.replace(',', '.'));
                montoImpuestoItem = Double.parseDouble(taxAmountStr.replace(',', '.'));
            } else {
                baseImponibleItem = total /(1.0 + (porcentaje / 100.0));
                montoImpuestoItem = total - baseImponibleItem;
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
                double total = Double.parseDouble(subtotalFacturaStr.replace(',', '.'));
                if (!codigo.isEmpty() && porcentaje > 0 && total > 0) {
                    double baseImponible = total / (1.0 + (porcentaje / 100.0));
                    double montoImpuesto = total - baseImponible;
                    String claveUnica = codigo + "_" + porcentajeStr;
                    Map<String, Double> valores = new HashMap<>();
                    valores.put("base", baseImponible);
                    valores.put("monto", montoImpuesto);
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
            impuestoFinal.put("base_imponible", String.format(Locale.US, "%.2f", valores.get("base")));
            impuestoFinal.put("monto_impuesto", String.format(Locale.US, "%.2f", valores.get("monto")));

            listaImpuestosLimpia.add(impuestoFinal);
        }
        return listaImpuestosLimpia;
    }
    
    private static String generarJsonItems(List<Map<String, String>> productos) {
        StringBuilder json = new StringBuilder();
        json.append("[");

        for (int i = 0; i < productos.size(); i++) {
            Map<String, String> item = productos.get(i);
            String nombre = item.getOrDefault("Name", "");
            String cantidad = item.getOrDefault("SalesCount", "");
            String precio = item.getOrDefault("UnitPrice", "");
            String subtotal = item.getOrDefault("Total", precio);

            if (i > 0) {
                json.append(",");
            }

            json.append("{\"nombre\":\"")
                .append(nombre.replace("\"", "\\\""))
                .append("\",\"cantidad\":\"")
                .append(cantidad.replace("\"", "\\\""))
                .append("\",\"precio\":\"")
                .append(precio.replace("\"", "\\\""))
                .append("\",\"subtotal\":\"")
                .append(subtotal.replace("\"", "\\\""))
                .append("\"}");
        }

        json.append("]");
        return json.toString();
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

            List<Map<String, String>> productos = extraerProductos(doc);

            if (!productos.isEmpty()) {
                Map<String, String> primerProducto = productos.get(0);
                result.putIfAbsent("producto", primerProducto.getOrDefault("Name", ""));
                result.putIfAbsent("cantidad", primerProducto.getOrDefault("SalesCount", ""));
                result.putIfAbsent("precio_unitario", primerProducto.getOrDefault("UnitPrice", ""));
                result.putIfAbsent("subtotal", primerProducto.getOrDefault("Total", ""));
            }

            List<Map<String, String>> impuestos = extraerImpuestos(doc, result.getOrDefault("subtotal", "0.00"));
            double totalImpuestos = 0.0;
            double totalBaseImponible = 0.0;
            for (Map<String, String> impuesto : impuestos) {
                totalImpuestos += Double.parseDouble(impuesto.getOrDefault("monto_impuesto", "0.00"));
                totalBaseImponible += Double.parseDouble(impuesto.getOrDefault("base_imponible", "0.00"));
            }

            result.putIfAbsent("genera_documento", "FALSE");
            result.putIfAbsent("condicion_venta", "N/A");
            result.putIfAbsent("codigo_fiscal", "N/A");
            result.putIfAbsent("guid_transaccion", "N/A");
            result.putIfAbsent("producto", "");
            result.putIfAbsent("cantidad", "");
            result.putIfAbsent("precio_unitario", "");
            result.putIfAbsent("subtotal", "");
            result.put("total_impuestos", String.format(Locale.US, "%.2f", totalImpuestos));
            result.put("base_imponible_total", String.format(Locale.US, "%.2f", totalBaseImponible));
            result.putIfAbsent("propina", "");
            result.putIfAbsent("total", "");
            result.putIfAbsent("restaurante", "");
            result.putIfAbsent("workstation_nombre", "");
            result.putIfAbsent("empleado", "");
            result.putIfAbsent("impuestos_json", null);
            ObjectMapper mapper = new ObjectMapper();
            result.put("impuestos_json", mapper.writeValueAsString(impuestos));
            result.put("items_json", generarJsonItems(productos));

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

            double valFacNum = Double.parseDouble(datos.getOrDefault("base_imponible_total", "0.00").replace(',', '.'));
            double totalNum = Double.parseDouble(datos.getOrDefault("total", "0.00").replace(',', '.'));

            double ivaNum = 0.00;
            double incNum = Double.parseDouble(datos.getOrDefault("total_impuestos", "0.00").replace(',', '.'));
            double icaNum = 0.00;

            String claveTecnicaXml = datos.getOrDefault("ClaveTecnica", "");
            String nitEmisor = datos.getOrDefault("RucEmisor", "8605108638");
            String prefijoFac = datos.getOrDefault("RangoIni", "SETT").replaceAll("[0-9]", "");
            String identificacionCliente = datos.getOrDefault("identificacion_cliente", "");
            JsonNode cliente = comunicadorBase.buscarPorIdentificacion(identificacionCliente);
            if (identificacionCliente.isBlank()) {
                identificacionCliente = identificacionPrueba;
                logger.warn("El XML no trae identificacion del cliente; no se puede consultar la base");
            } else if (cliente == null) {
                logger.warn("No se encontro el cliente con identificacion {}", identificacionCliente);
            }
            

            String identificadorFactura = construirIdentificadorFactura(datos);
            Map<String, String> registroExistente = leerRegistroCufe(identificadorFactura);
            String numeroFacturaCompleto = registroExistente == null ? null
                : registroExistente.get("numero_factura_completo");
            String cufeGenerado = registroExistente == null ? null : registroExistente.get("cufe");

            System.out.println("Datos Extraidos del XML:");
            System.out.println("• Ticket (CheckNum):" + checkNum);
            System.out.println("• Estación (WsID):" + wsId);
            System.out.println("• Fecha/Hora: "+ timestamp);
            System.out.println("• Condición de Venta: "+ condicionVenta);
            System.out.println("• Identificación Cliente: "+ identificacionCliente);
            System.out.println("• Cliente Encontrado: "+ (cliente != null));
            System.out.println("• Código Fiscal: "+ codigoFiscal);
            System.out.println("• GUID Transaccion: "+ guid);
            System.out.println("• Genera Documento: "+ generaDoc);
            System.out.println("• Resolucion: "+ datos.getOrDefault("Resolucion", "N/A"));
            System.out.println("• ResolucionIni: "+ datos.getOrDefault("ResolucionIni", "N/A"));
            System.out.println("• ResolucionFin: "+ datos.getOrDefault("ResolucionFin", "N/A"));
            System.out.println("• FechaResolucion: " + datos.getOrDefault("FechaResolucion", "N/A"));
            System.out.println("• Propina: "+ datos.getOrDefault("propina", "N/A"));
            System.out.println("• Total: "+ datos.getOrDefault("total", "N/A"));
            System.out.println("• Restaurante: "+ datos.getOrDefault("restaurante", "N/A"));
            System.out.println("• Workstation: "+ datos.getOrDefault("workstation_nombre", "N/A"));
            System.out.println("• Empleado: "+ datos.getOrDefault("empleado", "N/A"));
            System.out.println("• Impuestos: "+ datos.getOrDefault("impuestos_json", "[]"));
            System.out.println("• Items: "+ datos.getOrDefault("items_json", "[]"));
            System.out.println("• CUFE Generado: "+ cufeGenerado);
            System.out.println("• Prefijo Factura: "+ prefijoFac);

            if("TRUE".equalsIgnoreCase(generaDoc)){
                if (registroExistente == null) {
                    numeroFacturaCompleto = prefijoFac + facturaCounterService.obtenerSiguienteNumero();
                    String fechaFac = timestamp.substring(0, 10);
                    String horaFac = timestamp.substring(11);
                    String numAdquiriente = cliente == null
                        ? "222222222"
                        : cliente.path("identificacion").asText("222222222");
                    cufeGenerado = CufeServices.generarCufe(numeroFacturaCompleto, fechaFac, horaFac, valFacNum, "01", ivaNum,
                        "04", incNum, "00", icaNum, totalNum, nitEmisor, numAdquiriente, claveTecnicaXml, tipoAmbiente);
                }
                String urlQr = generarUrlQr(cufeGenerado);
                System.out.println("• Url Qr: " + urlQr);

                Map<String, Object> jsonMap = new LinkedHashMap<>();
                jsonMap.put("numero_factura", numeroFacturaCompleto);
                jsonMap.put("fecha_procesamiento", LocalDateTime.now().toString());
                jsonMap.put("numero_ticket", datos.get("numero_ticket"));
                jsonMap.put("check_id", checkId);
                jsonMap.put("harmony_id", harmonyId);
                jsonMap.put("caja_wsid", wsId);
                jsonMap.put("fecha_hora", timestamp);
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
                jsonMap.put("propina", datos.getOrDefault("propina", "N/A"));
                jsonMap.put("total", datos.getOrDefault("total", "N/A"));
                jsonMap.put("restaurante", datos.getOrDefault("restaurante", "N/A"));
                jsonMap.put("workstation", datos.getOrDefault("workstation_nombre", "N/A"));
                jsonMap.put("empleado", datos.getOrDefault("empleado", "N/A"));
                jsonMap.put("identificacion_cliente", identificacionCliente);
                jsonMap.put("cliente", cliente);
                jsonMap.put("cliente_encontrado", cliente != null);
                jsonMap.put("impuestos", objectMapper.readTree(datos.getOrDefault("impuestos_json", "[]")));
                jsonMap.put("items", objectMapper.readTree(datos.getOrDefault("items_json", "[]")));
                jsonMap.put("cufe", cufeGenerado);
                jsonMap.put("qr", urlQr);
                jsonMap.put("qr_url", urlQr);
                String jsonPayload = objectMapper.writeValueAsString(jsonMap);
                guardarRegistroCufe(identificadorFactura, numeroFacturaCompleto, cufeGenerado);
                enviarHttpPOST(jsonPayload);
            } else {
                System.out.println("La factura no requiere procesamiento de documento fiscal");
            }

            Path origen = xmlFile.toPath();
            Path destino = Paths.get(dirProcessed, xmlFile.getName());
        Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Archivo movido a carpeta de procesados /processed\n");

        }catch(Exception e){
            System.err.println("ERROR procesando el archivo xml:" + e.getMessage());
            e.printStackTrace();
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

    private void enviarHttpPOST(String jsonPayload){
        try{
            HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("User-Agent", "FacturaApp/1.0")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();

            logger.info("Enviando datos a la API");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if(response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Respuesta exitosa");
            } else {
                logger.warn("Respuesta con estado: {}", response.statusCode());
                facturaPendienteService.guardarFacturaPendiente(jsonPayload, "Status code:" + response.statusCode());
            }
            System.out.println("Respuesta del servidor - Codigo Status" + response.statusCode());
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

}
