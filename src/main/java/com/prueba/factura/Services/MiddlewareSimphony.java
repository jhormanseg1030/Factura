package com.prueba.factura.Services;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prueba.factura.dto.ResultadoFiscal;


@Service
public class MiddlewareSimphony {

    private static final Logger logger = LoggerFactory.getLogger(MiddlewareSimphony.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    private SecuenciaService secuenciaService;

    @Autowired
    private FacturaPendienteService facturaPendienteService;

    @Autowired
    private FacturaEnviadaService facturaEnviadaService;

    @Autowired
    private PortalEnvioService portalEnvio;

    @Autowired
    private Impresion impresion;

    @Autowired
    private ComunicadorBase comunicadorBase;

    private final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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
            String codigo = itemFields.getOrDefault("MiObjNum", "N/A");
            String cantidadStr = itemFields.getOrDefault("SalesCount", "1");
            String totalItemStr = itemFields.getOrDefault("Total", "0.00");

            String codImpuesto = itemFields.getOrDefault("DE_SATCOM_CodigoImpuesto", "01");
            String porcImpuesto = itemFields.getOrDefault("DE_SATCOM_Porc_Impuestos", "0.00");
            String nombreImpuesto = itemFields.getOrDefault("DE_SATCOM_NombreImpuesto", "IVA");

            long totalItem = parseDineroEntero(totalItemStr);
            double cantidad = parseDoubleSafe(cantidadStr.replace(',', '.'), 0);
            if(cantidad <= 0) cantidad = 1.0;
            double porcentajeImp = parseDoubleSafe(porcImpuesto.replace(',', '.'), 0.0);

            long baseImponible;
            long valorImpuesto;
            long[] impuestoMaterial = impuestoDelMaterial(itemElement);
            if (impuestoMaterial != null) {
                baseImponible = impuestoMaterial[0];
                valorImpuesto = impuestoMaterial[1];
            } else if (porcentajeImp > 0) {
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
            if (cantidad == Math.rint(cantidad)) {
                itemMap.put("cantidad", Math.round(cantidad));
            } else {
                itemMap.put("cantidad", cantidad);
            }
            itemMap.put("Total", totalItem);
            itemMap.put("total", totalItem);
            itemMap.put("precio", precioUnitarioSinImpuesto);
            itemMap.put("precio_unitario", precioUnitarioSinImpuesto);
            itemMap.put("descuento", 0);
            itemMap.put("subtotal", baseImponible);

            List<Map<String, String>> impuestosItem = new ArrayList<>();
            if(!codImpuesto.isBlank() && (porcentajeImp > 0 || valorImpuesto > 0)){
                String porcentajeTexto = String.format(Locale.US, "%.2f", porcentajeImp);
                String baseTexto = formatoImpuesto(baseImponible);
                String valorTexto = formatoImpuesto(valorImpuesto);
                String nombre = nombreImpuesto.isBlank() ? "IVA" : nombreImpuesto;
                Map<String, String> impMap = new LinkedHashMap<>();
                impMap.put("codigo", codImpuesto);
                impMap.put("nombre", nombre);
                impMap.put("numero_impuesto", "1");
                impMap.put("porcentaje", porcentajeTexto);
                impMap.put("base", baseTexto);
                impMap.put("valor", valorTexto);
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
            impuestoFinal.put("base_imponible", formatoImpuesto((long) valores.get("base")));
            impuestoFinal.put("monto_impuesto", formatoImpuesto((long) valores.get("monto")));
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
                total = parseDineroEntero(totalItemStr);
            } catch (NumberFormatException e) {
                continue;
            }

            long baseImponibleItem;
            long montoImpuestoItem;
            if (!taxableStr.isBlank() && !taxAmountStr.isBlank()) {
                baseImponibleItem = parseDineroEntero(taxableStr);
                montoImpuestoItem = parseDineroEntero(taxAmountStr);
                if (total > 0 && porcentaje > 0 && baseImponibleItem + montoImpuestoItem != total) {
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
                long total = parseDineroEntero(subtotalFacturaStr);
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
            impuestoFinal.put("base_imponible", formatoImpuesto(redondearEntero(valores.get("base"))));
            impuestoFinal.put("monto_impuesto", formatoImpuesto(redondearEntero(valores.get("monto"))));

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
            long tenderAmount = parseDineroEntero(montoStr);
            long tipAmount = parseDineroEntero(tipStr);

            pagoMap.put("tenderMediaId", tenderMediaId);
            pagoMap.put("tenderName", nombrePago);
            pagoMap.put("medio_pago", nombrePago);
            pagoMap.put("tenderAmount", tenderAmount);
            pagoMap.put("valor", tenderAmount);
            pagoMap.put("tipAmount", tipAmount);
            String formaPago = campos.getOrDefault("DE_SATCOM_CodigoFiscal", "").trim();
            if (!formaPago.isBlank()) {
                pagoMap.put("forma_pago", formaPago);
            }

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

            return extraerDocumento(nuevoDocumentBuilder().parse(xmlFile));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo el XML de factura: " + e.getMessage(), e);
        }
    }

    public static Map<String, String> extraerDatosFactura(String xml) {
        try {
            if (xml == null || xml.isBlank()) {
                throw new IllegalArgumentException("XML vacio");
            }
            xml = prepararXml(xml);
            if (xml.length() > 10_000_000) {
                throw new IllegalArgumentException("El XML es demasiado grande");
            }
            return extraerDocumento(nuevoDocumentBuilder().parse(new InputSource(new StringReader(xml))));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo el XML de factura: " + e.getMessage(), e);
        }
    }

    public static String prepararXml(String xml) {
        if (xml == null) {
            return "";
        }
        String texto = quitarMarca(xml);
        int inicio = texto.indexOf('<');
        if (inicio < 0 && texto.contains("%3C")) {
            texto = quitarMarca(decodificar(texto));
            inicio = texto.indexOf('<');
        }
        if (inicio > 0) {
            texto = texto.substring(inicio);
        }
        return texto;
    }

    private static String quitarMarca(String texto) {
        int i = 0;
        while (i < texto.length() && texto.charAt(i) == '\uFEFF') {
            i++;
        }
        return i == 0 ? texto : texto.substring(i);
    }

    private static String decodificar(String valor) {
        try {
            return URLDecoder.decode(valor, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return valor;
        }
    }

    private static String numeroCheque(String xml) {
        if (xml == null) {
            return "";
        }
        Matcher marca = Pattern.compile("CheckNum\\s*=\\s*\"([^\"]*)\"").matcher(xml);
        return marca.find() ? marca.group(1) : "";
    }

    static boolean esNotaCredito(String numeroReferencia) {
        return numeroReferencia != null
                && !numeroReferencia.isBlank()
                && !"N/A".equalsIgnoreCase(numeroReferencia.trim());
    }

    /**
     * Nota crédito DIAN: el documento es 91, la razón de anulación es 2 y la
     * operación 20 indica que referencia una factura electrónica.
     */
    static void marcarDocumento(Map<String, Object> jsonMap, String numeroReferencia,
            String fechaReferencia, String motivo, String cufeReferencia) {
        if (!esNotaCredito(numeroReferencia)) {
            jsonMap.put("DocumentType", "01");
            jsonMap.put("tipo_documento", "FV");
            return;
        }
        jsonMap.put("DocumentType", "91");
        jsonMap.put("tipo_documento", "91");
        jsonMap.put("credit_note_type_code", "2");
        jsonMap.put("tipo_operacion", "20");
        jsonMap.put("CreditNoteNumber", numeroReferencia.trim());
        ponerSiHay(jsonMap, "CreditNoteInvoiceDate", fechaReferencia);
        ponerSiHay(jsonMap, "CreditNoteReason", motivo);
        String prefijo = prefijoDe(numeroReferencia);
        long numero = numeroDe(numeroReferencia);
        if (!prefijo.isBlank()) {
            jsonMap.put("prefijo_referencia", prefijo);
        }
        if (numero > 0) {
            jsonMap.put("numero_referencia", numero);
        }
        Map<String, String> referencia = new LinkedHashMap<>();
        referencia.put("numeroDocumento", numeroReferencia.trim());
        referencia.put("cufe", cufeReferencia == null ? "" : cufeReferencia.trim());
        jsonMap.put("factura_referencia", referencia);
    }

    static String cufeDeNumero(Map<String, Map<String, String>> registros, String numero) {
        if (registros == null || numero == null || numero.isBlank()) {
            return "";
        }
        String buscado = numero.trim();
        for (Map<String, String> registro : registros.values()) {
            if (registro == null) {
                continue;
            }
            String guardado = registro.getOrDefault("numero_factura_completo", "");
            if (buscado.equalsIgnoreCase(guardado)) {
                String cufe = registro.getOrDefault("cufe", "");
                if (cufe != null && !cufe.isBlank()) {
                    return cufe.trim();
                }
            }
        }
        return "";
    }

    private void completarEmisor(Map<String, String> datos) {
        if (secuenciaService == null) {
            return;
        }
        SecuenciaService.DatosEmisor emisor = secuenciaService.emisor();
        aplicarEmisor(datos, emisor);
        datos.put("textos_fiscales", emisor.textosFiscales() == null ? "" : emisor.textosFiscales());
        datos.put("pie_factura", emisor.pieFactura() == null ? "" : emisor.pieFactura());
    }

    static void aplicarEmisor(Map<String, String> datos, SecuenciaService.DatosEmisor emisor) {
        if (datos == null || emisor == null) {
            return;
        }
        ponerTexto(datos, "RazonSocial", emisor.razonSocial());
        ponerTexto(datos, "RucEmisor", emisor.nit());
        ponerTexto(datos, "Direccion", emisor.direccion());
        ponerTexto(datos, "Telefono", emisor.telefono());
    }

    private static void ponerTexto(Map<String, String> datos, String clave, String valor) {
        if (valor != null && !valor.isBlank()) {
            datos.put(clave, valor.trim());
        }
    }

    private static void ponerSiHay(Map<String, Object> jsonMap, String clave, String valor) {
        if (valor == null || valor.isBlank() || "N/A".equalsIgnoreCase(valor.trim())) {
            return;
        }
        jsonMap.put(clave, valor.trim());
    }

    static String prefijoDe(String completo) {
        if (completo == null) {
            return "";
        }
        return completo.replaceAll("\\d", "");
    }

    static long numeroDe(String completo) {
        if (completo == null) {
            return 0L;
        }
        String digitos = completo.replaceAll("\\D", "");
        if (digitos.isBlank()) {
            return 0L;
        }
        return Long.parseLong(digitos);
    }

    static Map<String, String> clienteParaPortal(JsonNode cliente, String identificacionRespaldo) {
        if (cliente == null || cliente.isNull() || cliente.isMissingNode()
                || cliente.path("identificacion").asText("").isBlank()) {
            return objetoCliente("13", identificacionRespaldo, "CONSUMIDOR FINAL");
        }
        String nombre = cliente.path("nombre").asText("").trim();
        String apellido = cliente.path("apellido").asText("").trim();
        String razon = (nombre + " " + apellido).trim();
        if (razon.isBlank()) {
            razon = "CONSUMIDOR FINAL";
        }
        return objetoCliente("13", cliente.path("identificacion").asText("").trim(), razon);
    }

    private static Map<String, String> objetoCliente(String tipo, String numero, String razon) {
        if (numero == null || numero.isBlank()) {
            return null;
        }
        Map<String, String> objeto = new LinkedHashMap<>();
        objeto.put("tipo_identificacion", tipo);
        objeto.put("numero_identificacion", numero.trim());
        objeto.put("razon_social", razon);
        return objeto;
    }

    private static DocumentBuilder nuevoDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return dbFactory.newDocumentBuilder();
    }

    private static Map<String, String> extraerDocumento(Document doc) {
        try {
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
                if("Direccion".equals(name)){
                    result.putIfAbsent("Direccion", value == null ? "" : value); 
                }
                if("Telefono".equals(name)){
                    result.putIfAbsent("Telefono", value == null ? "" : value);
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
                if("RazonSocial".equals(name)){
                    result.putIfAbsent("RazonSocial", value == null ? "" : value);
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
            long propinaNum = parseDineroEntero(propinaRaw);
            result.put("propina", formatoDineroEntero(propinaNum));

            if (result.containsKey("total")) {
                result.put("total", formatoDineroEntero(parseDineroEntero(result.get("total"))));
            }
            if (result.containsKey("subtotal")) {
                result.put("subtotal", formatoDineroEntero(parseDineroEntero(result.get("subtotal"))));
            }

            List<Map<String, String>> impuestos = consolidarImpuestosDesdeProductos(productos);
            if (impuestos.isEmpty()) {
                impuestos = extraerImpuestos(doc, result.getOrDefault("subtotal", "0.00"));
            }

            if (!productos.isEmpty()) {
                Map<String, Object> primero = productos.get(0);
                result.putIfAbsent("producto", String.valueOf(primero.getOrDefault("nombre", "")));
                String cantidadResumen = String.valueOf(primero.getOrDefault("cantidad", "1"));
                if (cantidadResumen.endsWith(" ")) {
                    cantidadResumen = cantidadResumen.substring(0, cantidadResumen.length() - 3);
                }
                result.putIfAbsent("cantidad", cantidadResumen);
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
            ResultadoFiscal resultado = procesarDatos(extraerDatosFactura(xmlFile), true);
            Path origen = xmlFile.toPath();
            Path destino = Paths.get(dirProcessed, xmlFile.getName());
            Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Archivo movido a carpeta de procesados /processed. {}", resultado.getMensaje());
        } catch(Exception e){
            logger.error("ERROR procesando el archivo XML {}: {}", xmlFile.getName(), e.getMessage(), e);
        }
    }

    public ResultadoFiscal procesarPayload(String xml) {
        try {
            return procesarDatos(extraerDatosFactura(xml), false);
        } catch (Exception e) {
            logger.error("ERROR procesando payload de Harmony: {}", e.getMessage(), e);
            return ResultadoFiscal.error("Error al procesar el payload",
                    FormatoFacturaHarmony.rechazo("Error al procesar el payload", numeroCheque(xml)));
        }
    }

    private ResultadoFiscal procesarDatos(Map<String, String> datos, boolean imprimirLocal) throws Exception {
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
            completarEmisor(datos);

            double valFacNum = redondearEntero(Double.parseDouble(datos.getOrDefault("base_imponible_total", "0.00").replace(',', '.')));
            double ivaNum = 0.00;
            double incNum = redondearEntero(Double.parseDouble(datos.getOrDefault("total_impuestos", "0.00").replace(',', '.')));
            double icaNum = 0.00;
            double totalFiscalNum = valFacNum + ivaNum + incNum + icaNum;

            String claveTecnicaXml = datos.getOrDefault("ClaveTecnica", "");
            String nitEmisor = datos.getOrDefault("RucEmisor", "N/A");
            String direccion = datos.getOrDefault("Direccion", "N/A");
            String telefono = datos.getOrDefault("Telefono", "N/A");
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

            if (cliente != null) {
                String identificacionClienteEncontrado = cliente.path("identificacion").asText("").trim();
                if (!identificacionClienteEncontrado.isBlank()) {
                    identificacionCliente = identificacionClienteEncontrado;
                }
            }

            if (identificacionCliente.isBlank()) {
                identificacionCliente = identificacionConsumidorFinal;
                logger.info("Venta sin cliente identificado; se usará consumidor final: {}",
                    identificacionConsumidorFinal);
            }

            String identificadorFactura = construirIdentificadorFactura(datos);
            Map<String, String> registroExistente = leerRegistroCufe(identificadorFactura);
            String numeroFacturaCompleto = registroExistente == null ? null
                : registroExistente.get("numero_factura_completo");
            String cufeGenerado = registroExistente == null ? null : registroExistente.get("cufe");
            String prefijoPortal = numeroFacturaCompleto == null ? "" : prefijoDe(numeroFacturaCompleto);
            long numeroPortal = numeroFacturaCompleto == null ? 0L : numeroDe(numeroFacturaCompleto);

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
                    String tipoSecuencia = esNotaCredito(datos.get("CreditNoteNumber"))
                            ? SecuenciaService.NOTA_CREDITO
                            : SecuenciaService.FACTURA;
                    SecuenciaService.NumeroSecuencia asignado = secuenciaService.siguiente(tipoSecuencia);
                    String prefijoSecuencia = asignado.prefijo() == null || asignado.prefijo().isBlank()
                            ? prefijoFac
                            : asignado.prefijo();
                    numeroFacturaCompleto = prefijoSecuencia + asignado.numero();
                    prefijoPortal = prefijoSecuencia;
                    numeroPortal = asignado.numero();
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
                jsonMap.put("RucEmisor", nitEmisor);
                jsonMap.put("Direccion", direccion);
                jsonMap.put("Tel", telefono);
                jsonMap.put("restaurante", datos.getOrDefault("restaurante", "N/A"));
                jsonMap.put("RazonSocial", datos.getOrDefault("RazonSocial", "N/A"));
                jsonMap.put("fecha_procesamiento", LocalDate.now().toString());
                jsonMap.put("numero_ticket", datos.get("numero_ticket"));
                jsonMap.put("check_id", checkId);
                jsonMap.put("harmony_id", harmonyId);
                jsonMap.put("caja_wsid", wsId);
                jsonMap.put("hora_hora", horaFac);
                jsonMap.put("fecha_hora", fechaFac + "T" + horaFac);
                jsonMap.put("Fecha de Generacion", fechaFac + " " + horaFac);
                jsonMap.put("condicion_venta", condicionVenta);
                jsonMap.put("codigo_fiscal", codigoFiscal);
                jsonMap.put("guid_transaccion", guid);
                jsonMap.put("tipo_ambiente", tipoAmbiente);
                jsonMap.put("clave_tecnica_xml", claveTecnicaXml);
                
                jsonMap.put("Resolucion", datos.getOrDefault("Resolucion", "N/A"));
                jsonMap.put("Fecha Resolucion", datos.getOrDefault("FechaResolucion", "N/A"));
                jsonMap.put("Fecha Inicial", datos.getOrDefault("ResolucionIni", "N/A"));
                jsonMap.put("Fecha Final", datos.getOrDefault("ResolucionFin", "N/A"));
                jsonMap.put("Rango Inicial", datos.getOrDefault("RangoIni", "N/A"));
                jsonMap.put("Rango Final", datos.getOrDefault("RangoFin", "N/A"));
                
                long propinaNumero = parseDineroEntero(datos.getOrDefault("propina", "0"));
                long totalNumero = parseDineroEntero(datos.getOrDefault("total", "0"));
                jsonMap.put("Base", redondearEntero(valFacNum));
                jsonMap.put("INC", redondearEntero(incNum));
                jsonMap.put("Subtotal", redondearEntero(totalFiscalNum));
                jsonMap.put("Propina", propinaNumero);
                jsonMap.put("propina", propinaNumero);
                jsonMap.put("Total", totalNumero);
                
                jsonMap.put("Cajero", datos.getOrDefault("empleado", "N/A"));
                jsonMap.put("Mesa", "N/A");
                jsonMap.put("identificacion_cliente", identificacionCliente);
                jsonMap.put("cliente", cliente != null ? cliente.path("nombre").asText("Consumidor Final") : "Consumidor Final");
                jsonMap.put("cliente_encontrado", cliente != null);
                jsonMap.put("Dirección", (cliente != null) ? cliente.path("direccion").asText(" ") : " ");
                jsonMap.put("Telefono", (cliente != null) ? cliente.path("telefono").asText(" ") : "");

                jsonMap.put("impuestos", objectMapper.readTree(datos.getOrDefault("impuestos_json", "[]")));
                jsonMap.put("items", objectMapper.readTree(datos.getOrDefault("items_json", "[]")));
                jsonMap.put("pagos", objectMapper.readTree(datos.getOrDefault("pagos_json", "[]")));
                jsonMap.put("cufeGenerado", cufeGenerado);
                jsonMap.put("cufe", cufeGenerado);
                jsonMap.put("textos_fiscales", datos.getOrDefault("textos_fiscales", ""));
                jsonMap.put("pie_factura", datos.getOrDefault("pie_factura", ""));
                jsonMap.put("qr", urlQr);
                jsonMap.put("qr_url", urlQr);

                JsonNode nodoFacturaCompleta = objectMapper.valueToTree(jsonMap);
                String ticketImpresion = objectMapper.writeValueAsString(nodoFacturaCompleta);
                String clienteTexto = jsonMap.get("cliente") instanceof String texto ? texto : "Consumidor Final";
                String fechaDoc = String.valueOf(jsonMap.getOrDefault("fecha_hora", ""));
                String cheque = String.valueOf(jsonMap.getOrDefault("numero_ticket", ""));
                long totalDoc = numeroValor(jsonMap.get("Total"));
                boolean nota = esNotaCredito(datos.get("CreditNoteNumber"));
                String respuestaHarmony = FormatoFacturaHarmony.exito(nodoFacturaCompleta);

                if (imprimirLocal) {
                    try {
                        impresion.imprimirFactura(nodoFacturaCompleta);
                    } catch (Exception e) {
                        logger.error("No se pudo imprimir la factura en local. Harmony imprime con la respuesta.", e);
                    }
                }
                
            if (esNotaCredito(datos.get("CreditNoteNumber"))) {
                logger.info("=== Iniciando Flujo de Nota de Crédito ===");
            } else {
                logger.info("=== Iniciando Flujo de Factura ===");
            }
            String cufeOriginal = "";
            if (esNotaCredito(datos.get("CreditNoteNumber"))) {
                cufeOriginal = cufeDeFacturaReferenciada(datos.get("CreditNoteNumber"));
                if (cufeOriginal.isBlank()) {
                    logger.warn("La nota crédito no tiene el CUFE de la factura {}", datos.get("CreditNoteNumber"));
                }
            }
            marcarDocumento(jsonMap,
                    datos.get("CreditNoteNumber"),
                    datos.get("CreditNoteInvoiceDate"),
                    datos.get("CreditNoteReason"),
                    cufeOriginal);
                jsonMap.put("numero_factura", numeroPortal);
                jsonMap.put("Prefijo", prefijoPortal);
                jsonMap.put("cliente", clienteParaPortal(cliente, identificacionCliente));
                aplicarFormaPortal(jsonMap);
                cuadrarNotaCredito(jsonMap);
                String jsonPayload = objectMapper.writeValueAsString(jsonMap);
                guardarRegistroCufe(identificadorFactura, numeroFacturaCompleto, cufeGenerado);
                ResultadoEnvio envio = enviarHttpPOST(jsonPayload);
                registrarEnviada(identificadorFactura, numeroFacturaCompleto, prefijoPortal, numeroPortal,
                        cufeGenerado, fechaDoc, clienteTexto, nota ? "NC" : "FV", totalDoc, ticketImpresion,
                        cheque, envio, jsonPayload);
                return ResultadoFiscal.ok(numeroFacturaCompleto, cufeGenerado, urlQr, respuestaHarmony);
            }

            logger.info("La factura no requiere procesamiento de documento fiscal");
            Map<String, String> identidad = new LinkedHashMap<>();
            identidad.put("caja_wsid", wsId);
            identidad.put("harmony_id", harmonyId);
            identidad.put("numero_ticket", checkNum);
            identidad.put("fecha_hora", datos.getOrDefault("fecha_hora", ""));
            return ResultadoFiscal.sinDocumentoFiscal(
                    FormatoFacturaHarmony.sinDocumento(objectMapper.valueToTree(identidad)));
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

    private String cufeDeFacturaReferenciada(String numero) {
        String cufe = cufeEnArchivo(numero);
        if (!cufe.isBlank()) {
            return cufe;
        }
        if (facturaEnviadaService == null) {
            return "";
        }
        String desdeReporte = facturaEnviadaService.cufeDeNumero(numero);
        return desdeReporte == null ? "" : desdeReporte;
    }

    private synchronized String cufeEnArchivo(String numero) {
        Path path = Paths.get(cufeRegistryFile);
        if (!Files.exists(path)) {
            return "";
        }
        try {
            Map<String, Map<String, String>> registros = objectMapper.readValue(
                    path.toFile(), new TypeReference<Map<String, Map<String, String>>>() {});
            return cufeDeNumero(registros, numero);
        } catch (Exception e) {
            logger.warn("No se pudo buscar el CUFE de {}: {}", numero, e.getMessage());
            return "";
        }
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


    private ResultadoEnvio enviarHttpPOST(String jsonPayload){
        String destino = (targetUrl != null && !targetUrl.isBlank()) ? targetUrl : apiUrl;
        logger.info("Enviando datos a la API: {}", destino);
        logger.info("Cuerpo JSON: {}", jsonPayload);
        if (portalEnvio == null) {
            facturaPendienteService.guardarFacturaPendiente(jsonPayload, "No se pudo contactar el portal");
            return new ResultadoEnvio(false, "No se pudo contactar el portal");
        }
        PortalEnvioService.Resultado respuesta = portalEnvio.enviar(jsonPayload);
        if (!respuesta.aceptado()) {
            facturaPendienteService.guardarFacturaPendiente(jsonPayload, respuesta.mensaje());
        }
        return new ResultadoEnvio(respuesta.aceptado(), respuesta.mensaje());
    }

    private void registrarEnviada(String id, String numero, String prefijo, long consecutivo, String cufe,
            String fecha, String cliente, String tipo, long total, String ticket, String cheque,
            ResultadoEnvio envio, String payload) {
        if (facturaEnviadaService == null) {
            return;
        }
        try {
            facturaEnviadaService.registrar(id, numero, prefijo, consecutivo, cufe, fecha, cliente, tipo, total,
                    ticket, cheque, envio.aceptado() ? "enviada" : "error", envio.mensaje(), payload);
        } catch (Exception e) {
            logger.error("No se pudo registrar la factura en el reporte", e);
        }
    }

    private record ResultadoEnvio(boolean aceptado, String mensaje) {
    }

    private String generarUrlQr(String cufe){
        if(cufe == null || cufe.isBlank()){
            throw new IllegalArgumentException("El CUFE no puede estar vacio");
        }
        return "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=" + cufe;
    }

    static long parseDineroEntero(String value) {
        return redondearEntero(parseDinero(value));
    }

    static double parseDinero(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        String texto = value.trim().replace(" ", "");
        boolean tienePunto = texto.indexOf('.') >= 0;
        boolean tieneComa = texto.indexOf(',') >= 0;
        if (tienePunto && tieneComa) {
            boolean puntoEsDecimal = texto.lastIndexOf('.') > texto.lastIndexOf(',');
            char miles = puntoEsDecimal ? ',' : '.';
            texto = texto.replace(String.valueOf(miles), "");
            if (!puntoEsDecimal) {
                texto = texto.replace(',', '.');
            }
            return parseDoubleSafe(texto, 0.0);
        }
        if (tienePunto || tieneComa) {
            char separador = tienePunto ? '.' : ',';
            String[] partes = texto.split(separador == '.' ? "\\." : ",", -1);
            boolean gruposDeMil = partes.length > 1 && !partes[0].isEmpty() && partes[0].length() <= 3;
            for (int i = 1; gruposDeMil && i < partes.length; i++) {
                gruposDeMil = partes[i].length() == 3;
            }
            if (gruposDeMil) {
                return parseDoubleSafe(String.join("", partes), 0.0);
            }
            if (separador == ',') {
                texto = texto.replace(',', '.');
            }
            return parseDoubleSafe(texto, 0.0);
        }
        return parseDoubleSafe(texto, 0.0);
    }

    private static long[] impuestoDelMaterial(Element item) {
        NodeList campos = item.getElementsByTagName("OraPayloadEntityField");
        String base = "";
        String valor = "";
        for (int i = 0; i < campos.getLength(); i++) {
            Element campo = (Element) campos.item(i);
            if (!"TaxData".equals(campo.getAttribute("field"))) {
                continue;
            }
            NodeList valores = campo.getElementsByTagName("OraPayloadEntityFieldGenericParameter");
            for (int k = 0; k < valores.getLength(); k++) {
                Element valorCampo = (Element) valores.item(k);
                String nombre = valorCampo.getAttribute("field");
                if ("Taxable".equals(nombre)) {
                    base = valorCampo.getAttribute("value");
                } else if ("Tax".equals(nombre)) {
                    valor = valorCampo.getAttribute("value");
                }
            }
        }
        if (base.isBlank() || valor.isBlank()) {
            return null;
        }
        return new long[] { parseDineroEntero(base), parseDineroEntero(valor) };
    }

    static void cuadrarNotaCredito(Map<String, Object> jsonMap) {
        String tipo = String.valueOf(jsonMap.get("tipo_documento"));
        if (!"91".equals(tipo) && !"NC".equals(tipo)) {
            return;
        }
        long base = 0L;
        long impuesto = 0L;
        Object items = jsonMap.get("items");
        if (items instanceof List<?> lista) {
            for (Object raw : lista) {
                if (!(raw instanceof Map<?, ?> item)) {
                    continue;
                }
                long baseLinea = numeroValor(item.get("subtotal"));
                if (baseLinea == 0L) {
                    baseLinea = numeroValor(item.get("precio"));
                }
                base += baseLinea;
                Object impuestosItem = item.get("impuestos");
                if (!(impuestosItem instanceof List<?> impuestosLinea)) {
                    continue;
                }
                for (Object impRaw : impuestosLinea) {
                    if (impRaw instanceof Map<?, ?> imp) {
                        impuesto += numeroValor(imp.get("valor"));
                    }
                }
            }
        }
        if (base == 0L) {
            base = numeroValor(jsonMap.get("Base"));
        }
        if (impuesto == 0L) {
            impuesto = numeroValor(jsonMap.get("INC"));
        }
        jsonMap.put("Base", base);
        jsonMap.put("INC", impuesto);
        jsonMap.put("Subtotal", base);
        Object impuestos = jsonMap.get("impuestos");
        if (!(impuestos instanceof List<?> cabeceras)) {
            return;
        }
        for (Object raw : cabeceras) {
            if (raw instanceof Map<?, ?> mapa) {
                @SuppressWarnings("unchecked")
                Map<String, String> cabecera = (Map<String, String>) mapa;
                cabecera.put("base", formatoImpuesto(base));
                cabecera.put("valor", formatoImpuesto(impuesto));
            }
        }
    }

    static void aplicarFormaPortal(Map<String, Object> jsonMap) {
        long totalFactura = numeroValor(jsonMap.get("Total"));
        long propinaFactura = numeroValor(jsonMap.get("propina"));
        if (propinaFactura == 0L) {
            propinaFactura = numeroValor(jsonMap.get("Propina"));
        }
        jsonMap.put("items", itemsParaPortal(jsonMap.get("items")));
        jsonMap.put("impuestos", impuestosCabeceraParaPortal(jsonMap.get("impuestos")));
        jsonMap.put("pagos", pagosParaPortal(jsonMap.get("pagos"), totalFactura, propinaFactura));
        jsonMap.put("propina", propinaFactura);
        jsonMap.remove("Propina");
        for (String clave : List.of(
                "qr_url", "cufeGenerado", "Mesa", "Cajero", "Telefono", "Dirección",
                "hora_hora", "clave_tecnica_xml", "Fecha de Generacion", "Fecha Resolucion",
                "Fecha Inicial", "Fecha Final", "Rango Inicial", "Rango Final", "restaurante",
                "fecha_procesamiento", "identificacion_cliente", "cliente_encontrado",
                "textos_fiscales", "pie_factura")) {
            jsonMap.remove(clave);
        }
        for (String clave : List.of("RucEmisor", "RazonSocial", "Direccion", "Tel")) {
            Object valor = jsonMap.get(clave);
            if (valor == null || valor.toString().isBlank() || "N/A".equalsIgnoreCase(valor.toString().trim())) {
                jsonMap.remove(clave);
            }
        }
    }

    static List<Map<String, Object>> itemsParaPortal(Object items) {
        List<Map<String, Object>> lista = new ArrayList<>();
        JsonNode nodo = comoArreglo(items);
        if (nodo == null) {
            return lista;
        }
        for (JsonNode item : nodo) {
            long totalLinea = numeroNodo(item, "total", "Total");
            long precioUnitario = numeroNodo(item, "precio_unitario", "precio");
            long baseLinea = baseDelItem(item);
            if (baseLinea == 0L) {
                baseLinea = precioUnitario;
            }
            Map<String, Object> limpio = new LinkedHashMap<>();
            limpio.put("codigo", textoNodo(item, "codigo"));
            limpio.put("descripcion", textoNodo(item, "descripcion", "nombre"));
            limpio.put("unidad_medida", textoNodo(item, "unidad_medida").isBlank() ? "94" : textoNodo(item, "unidad_medida"));
            JsonNode cantidad = item.get("cantidad");
            if (cantidad != null && cantidad.isNumber()) {
                limpio.put("cantidad", cantidad.numberValue());
            } else {
                limpio.put("cantidad", numeroNodo(item, "cantidad"));
            }
            limpio.put("precio", precioUnitario);
            limpio.put("precio_unitario", precioUnitario);
            limpio.put("descuento", numeroNodo(item, "descuento"));
            limpio.put("subtotal", baseLinea);
            limpio.put("total", totalLinea);
            limpio.put("impuestos", impuestosDeItem(item.get("impuestos")));
            lista.add(limpio);
        }
        return lista;
    }

    private static long baseDelItem(JsonNode item) {
        JsonNode impuestos = item.get("impuestos");
        if (impuestos == null || !impuestos.isArray() || impuestos.isEmpty()) {
            return 0L;
        }
        long base = 0L;
        for (JsonNode impuesto : impuestos) {
            base += numeroNodo(impuesto, "base", "base_imponible");
        }
        return base;
    }

    static List<Map<String, String>> impuestosCabeceraParaPortal(Object impuestos) {
        List<Map<String, String>> lista = new ArrayList<>();
        JsonNode nodo = comoArreglo(impuestos);
        if (nodo == null) {
            return lista;
        }
        for (JsonNode impuesto : nodo) {
            lista.add(impuestoCorto(impuesto, true));
        }
        return lista;
    }

    static List<Map<String, Object>> pagosParaPortal(Object pagos, long totalFactura, long propinaFactura) {
        List<Map<String, Object>> lista = new ArrayList<>();
        JsonNode nodo = comoArreglo(pagos);
        if (nodo == null || nodo.isEmpty()) {
            return lista;
        }
        long restante = totalFactura;
        int indice = 0;
        for (JsonNode pago : nodo) {
            long ofrecido = numeroNodo(pago, "valor", "tenderAmount");
            long aplicado = Math.min(Math.max(ofrecido, 0L), Math.max(restante, 0L));
            restante -= aplicado;
            Map<String, Object> limpio = new LinkedHashMap<>();
            if (pago.hasNonNull("tenderMediaId")) {
                limpio.put("tenderMediaId", pago.get("tenderMediaId").numberValue());
            }
            String medio = textoNodo(pago, "medio_pago", "tenderName");
            if (!medio.isBlank()) {
                limpio.put("medio_pago", medio);
            }
            String forma = textoNodo(pago, "forma_pago");
            if (!forma.isBlank()) {
                limpio.put("forma_pago", forma);
            }
            limpio.put("valor", aplicado);
            limpio.put("propina", indice == 0 ? propinaFactura : 0L);
            lista.add(limpio);
            indice++;
        }
        return lista;
    }

    private static List<Map<String, String>> impuestosDeItem(JsonNode impuestos) {
        List<Map<String, String>> lista = new ArrayList<>();
        if (impuestos == null || !impuestos.isArray()) {
            return lista;
        }
        for (JsonNode impuesto : impuestos) {
            lista.add(impuestoCorto(impuesto, false));
        }
        return lista;
    }

    private static Map<String, String> impuestoCorto(JsonNode impuesto, boolean conNumero) {
        Map<String, String> limpio = new LinkedHashMap<>();
        limpio.put("codigo", textoNodo(impuesto, "codigo", "codigo_impuesto"));
        limpio.put("nombre", textoNodo(impuesto, "nombre", "nombre_impuesto"));
        if (conNumero) {
            String numero = textoNodo(impuesto, "numero_impuesto");
            if (!numero.isBlank()) {
                limpio.put("numero_impuesto", numero);
            }
        }
        limpio.put("porcentaje", textoNodo(impuesto, "porcentaje", "porcentaje_impuesto"));
        limpio.put("base", textoNodo(impuesto, "base", "base_imponible"));
        limpio.put("valor", textoNodo(impuesto, "valor", "monto_impuesto"));
        return limpio;
    }

    private static JsonNode comoArreglo(Object valor) {
        if (valor instanceof JsonNode nodo && nodo.isArray()) {
            return nodo;
        }
        return null;
    }

    private static long numeroValor(Object valor) {
        if (valor instanceof Number numero) {
            return numero.longValue();
        }
        if (valor instanceof JsonNode nodo) {
            return numeroNodo(nodo);
        }
        return parseDineroEntero(valor == null ? "0" : valor.toString());
    }

    private static long numeroNodo(JsonNode nodo, String... campos) {
        if (nodo == null) {
            return 0L;
        }
        if (campos.length == 0) {
            return nodo.isNumber() ? nodo.longValue() : parseDineroEntero(nodo.asText("0"));
        }
        for (String campo : campos) {
            JsonNode valor = nodo.get(campo);
            if (valor == null || valor.isNull()) {
                continue;
            }
            if (valor.isNumber()) {
                return valor.longValue();
            }
            String texto = valor.asText("");
            if (!texto.isBlank()) {
                return parseDineroEntero(texto);
            }
        }
        return 0L;
    }

    private static String textoNodo(JsonNode nodo, String... campos) {
        if (nodo == null) {
            return "";
        }
        for (String campo : campos) {
            JsonNode valor = nodo.get(campo);
            if (valor != null && !valor.isNull() && !valor.asText("").isBlank()) {
                return valor.asText();
            }
        }
        return "";
    }

    private static long redondearEntero(double valor) {
        return Math.round(valor);
    }

    private static String formatoDineroEntero(long valor) {
        return Long.toString(valor);
    }

    private static String formatoImpuesto(long valor) {
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
