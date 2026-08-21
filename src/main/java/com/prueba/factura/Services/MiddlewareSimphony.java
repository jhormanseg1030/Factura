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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Autowired
    private FacturaCounterService facturaCounterService;

    @Autowired
    private FacturaPendienteService facturaPendienteService;

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

    private static List<Map<String, String>> extraerImpuestos(Document doc){
        List<Map<String, String>> listaImpuestos = new ArrayList<>();

        Set<String> impuestosProcesados = new HashSet<>();

        NodeList buscadorDeImpuestos = doc.getElementsByTagName("GenericParameterList");

        for(int i = 0; i < buscadorDeImpuestos.getLength(); i++){
            Element listElement = (Element) buscadorDeImpuestos.item(i);
            NodeList fields = listElement.getElementsByTagName("OraPayloadEntityFieldGenericParameter");
            Map<String, String> rawTax = new HashMap<>();

            for(int j = 0; j < fields.getLength(); j++){
                Element field = (Element) fields.item(j);
                String name = field.getAttribute("field");
                String value = field.getAttribute("value");

                if(name != null && !name.isBlank()){
                    rawTax.put(name, value == null ? "" : value);
                }
            }
                if(rawTax.containsKey("NombreImpuesto") || rawTax.containsKey("CodigoImpuesto")
                    || rawTax.containsKey("Porc_Impuestos") || rawTax.containsKey("DE_SATCOM_NombreImpuesto")
                    || rawTax.containsKey("DE_SATCOM_CodigoImpuesto") || rawTax.containsKey("DE_SATCOM_Porc_Impuestos")){

                String codigo = rawTax.getOrDefault("DE_SATCOM_CodigoImpuesto", rawTax.getOrDefault("CodigoImpuesto", ""));
                String nombre = rawTax.getOrDefault("DE_SATCOM_NombreImpuesto", rawTax.getOrDefault("NombreImpuesto", ""));
                String numero = rawTax.getOrDefault("DE_SATCOM_Numero_Impuestos", rawTax.getOrDefault("Numero_Impuestos", ""));
                String porcentaje = rawTax.getOrDefault("DE_SATCOM_Porc_Impuestos", rawTax.getOrDefault("Porc_Impuestos", ""));
                
                String claveUnica = codigo + "__" + porcentaje;

                if(!impuestosProcesados.contains(claveUnica) && (!codigo.isEmpty() || !nombre.isEmpty())){
                    impuestosProcesados.add(claveUnica);

                    Map<String, String> impuestoLimpio = new LinkedHashMap<>();
                    impuestoLimpio.put("codigo_impuesto", codigo);
                    impuestoLimpio.put("nombre_impuesto", nombre);
                    impuestoLimpio.put("numero_impuesto", numero);
                    impuestoLimpio.put("porcentaje_impuesto", porcentaje);

                    listaImpuestos.add(impuestoLimpio);
                }
            }
        }
        return listaImpuestos;
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

            List<Map<String, String>> productos = extraerProductos(doc);
            List<Map<String, String>> impuestos = extraerImpuestos(doc);

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
            }

            result.putIfAbsent("genera_documento", "FALSE");
            result.putIfAbsent("condicion_venta", "N/A");
            result.putIfAbsent("codigo_fiscal", "N/A");
            result.putIfAbsent("guid_transaccion", "N/A");
            result.putIfAbsent("producto", "");
            result.putIfAbsent("cantidad", "");
            result.putIfAbsent("precio_unitario", "");
            result.putIfAbsent("subtotal", "");
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

            System.out.println("Datos Extraidos del XML:");
            System.out.println("• Ticket (CheckNum):" + checkNum);
            System.out.println("• Estación (WsID):" + wsId);
            System.out.println("• Fecha/Hora: "+ timestamp);
            System.out.println("• Condición de Venta: "+ condicionVenta);
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

            if("TRUE".equalsIgnoreCase(generaDoc)){
                Map<String, Object> jsonMap = new LinkedHashMap<>();
                jsonMap.put("numero_factura", facturaCounterService.obtenerSiguienteNumero());
                jsonMap.put("fecha_procesamiento", LocalDateTime.now().toString());
                jsonMap.put("numero_ticket", checkNum);
                jsonMap.put("check_id", checkId);
                jsonMap.put("harmony_id", harmonyId);
                jsonMap.put("caja_wsid", wsId);
                jsonMap.put("fecha_hora", timestamp);
                jsonMap.put("condicion_venta", condicionVenta);
                jsonMap.put("codigo_fiscal", codigoFiscal);
                jsonMap.put("guid_transaccion", guid);
                jsonMap.put("Resolucion", datos.getOrDefault("Resolucion", "N/A"));
                jsonMap.put("ResolucionIni", datos.getOrDefault("ResolucionIni", "N/A"));
                jsonMap.put("ResolucionFin", datos.getOrDefault("ResolucionFin", "N/A"));
                jsonMap.put("FechaResolucion", datos.getOrDefault("FechaResolucion", "N/A"));
                jsonMap.put("propina", datos.getOrDefault("propina", "N/A"));
                jsonMap.put("total", datos.getOrDefault("total", "N/A"));
                jsonMap.put("restaurante", datos.getOrDefault("restaurante", "N/A"));
                jsonMap.put("workstation", datos.getOrDefault("workstation_nombre", "N/A"));
                jsonMap.put("empleado", datos.getOrDefault("empleado", "N/A"));
                jsonMap.put("impuestos", objectMapper.readTree(datos.getOrDefault("impuestos_json", "[]")));
                jsonMap.put("items", objectMapper.readTree(datos.getOrDefault("items_json", "[]")));
                
                String jsonPayload = objectMapper.writeValueAsString(jsonMap);

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
}
