package com.prueba.factura.Services;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.prueba.factura.dto.DianFacturaDto;
import com.prueba.factura.dto.DianFacturaDto.DetalleDian;
import com.prueba.factura.dto.DianFacturaDto.ImpuestoDian;
import com.prueba.factura.dto.DianFacturaDto.PagoDian;

@Service
public class DianXmlParserService {

    private static final Logger logger = LoggerFactory.getLogger(DianXmlParserService.class);

    public DianFacturaDto parsearXml(File xmlFile) {
        try {
            if (!xmlFile.exists() || !xmlFile.canRead()) {
                throw new IllegalArgumentException("El archivo XML DIAN no es accesible: " + xmlFile.getName());
            }

            if (xmlFile.length() > 10_000_000) {
                throw new IllegalArgumentException("El archivo XML DIAN es demasiado grande (max 10MB)");
            }

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
            Document doc = dbBuilder.parse(xmlFile);
            doc.getDocumentElement().normalize();

            DianFacturaDto dto = new DianFacturaDto();

            dto.setTipoDocumento(extraerCampoAdicional(doc, "Documento"));
            dto.setNumeroDocumento(getTexto(doc, "NumeroDocumento"));
            dto.setCufe(getTexto(doc, "Cufe"));
            dto.setFechaEmision(getTexto(doc, "FechaEmision"));
            dto.setRazonSocialEmisor(getTexto(doc, "RazonSocialEmisor"));
            dto.setRucEmisor(getTexto(doc, "RucEmisor"));
            dto.setEstadoComprobante(getTexto(doc, "EstadoComprobante"));

            dto.setNumeroAutorizacion(getTexto(doc, "NumeroAutorizacion"));
            dto.setClaveAcceso(getTexto(doc, "ClaveAcceso"));
            dto.setInformacionCufe(getTexto(doc, "InformacionCUFE"));
            dto.setResolucion(getResolucion(doc));
            dto.setPrefijo(getPrefijo(doc));

            Element cliente = getPrimerElemento(doc, "Cliente");
            if (cliente != null) {
                dto.setClienteNombre(getTextoDe(cliente, "RazonSocial"));
                dto.setClienteIdentificacion(getTextoDe(cliente, "NumeroIdentificacion"));
                dto.setClienteTipoIdentificacion(getTextoDe(cliente, "TipoIdentificacion"));
            }

            dto.setTotalSinImpuestos(getTexto(doc, "TotalSinImpuestos"));
            dto.setTotalConImpuestos(getTexto(doc, "TotalConImpuestos"));
            dto.setPropina(getTexto(doc, "Propina"));
            dto.setMoneda(getTexto(doc, "Moneda"));
            dto.setCondicionVenta(getTexto(doc, "CondicionVenta"));

            Element resumen = getPrimerElemento(doc, "ResumenComprobante");
            if (resumen != null) {
                dto.setTotalVenta(getTextoDe(resumen, "TotalVenta"));
                dto.setTotalImpuesto(getTextoDe(resumen, "TotalImpuesto"));
                dto.setTotalComprobante(getTextoDe(resumen, "TotalComprobante"));
            }

            dto.setDetalles(parsearDetalles(doc));
            dto.setImpuestos(parsearImpuestos(doc));
            dto.setPagos(parsearPagos(doc));

            logger.info("XML DIAN parseado exitosamente: {} - {} - Total: {}",
                    dto.getNumeroDocumento(), dto.getTipoDocumento(), dto.getTotalComprobante());

            return dto;

        } catch (Exception e) {
            throw new IllegalStateException("Error leyendo el XML DIAN: " + e.getMessage(), e);
        }
    }

    public java.util.Map<String, String> extraerDatosBasicos(File xmlFile) {
        DianFacturaDto dto = parsearXml(xmlFile);
        java.util.Map<String, String> datos = new java.util.LinkedHashMap<>();

        datos.put("tipo_documento", dto.getTipoDocumento());
        datos.put("numero_documento", dto.getNumeroDocumento());
        datos.put("cufe", dto.getCufe());
        datos.put("fecha_emision", dto.getFechaEmision());
        datos.put("razon_social_emisor", dto.getRazonSocialEmisor());
        datos.put("ruc_emisor", dto.getRucEmisor());
        datos.put("estado", dto.getEstadoComprobante());
        datos.put("cliente", dto.getClienteNombre());
        datos.put("identificacion_cliente", dto.getClienteIdentificacion());
        datos.put("total_sin_impuestos", dto.getTotalSinImpuestos());
        datos.put("total_con_impuestos", dto.getTotalConImpuestos());
        datos.put("total_venta", dto.getTotalVenta());
        datos.put("total_impuesto", dto.getTotalImpuesto());
        datos.put("total_comprobante", dto.getTotalComprobante());
        datos.put("propina", dto.getPropina());
        datos.put("moneda", dto.getMoneda());
        datos.put("condicion_venta", dto.getCondicionVenta());
        datos.put("resolucion", dto.getResolucion());
        datos.put("prefijo", dto.getPrefijo());

        if (dto.getDetalles() != null) {
            datos.put("cantidad_items", String.valueOf(dto.getDetalles().size()));
        }

        return datos;
    }

    private List<DetalleDian> parsearDetalles(Document doc) {
        List<DetalleDian> detalles = new ArrayList<>();
        NodeList detalleNodes = doc.getElementsByTagName("Detalle");

        for (int i = 0; i < detalleNodes.getLength(); i++) {
            Element detalle = (Element) detalleNodes.item(i);
            DetalleDian d = new DetalleDian();

            d.setCantidad(getTextoDe(detalle, "Cantidad"));
            d.setSubTotal(getTextoDe(detalle, "SubTotal"));
            d.setTotal(getTextoDe(detalle, "Total"));
            d.setDescuento(getTextoDe(detalle, "Descuento"));
            d.setTipo(getTextoDe(detalle, "Tipo"));

            Element producto = getPrimerElemento(detalle, "Producto");
            if (producto != null) {
                d.setCodigoProducto(getTextoDe(producto, "Codigo"));
                d.setDescripcionProducto(getTextoDe(producto, "Descripcion"));
                d.setValorUnitario(getTextoDe(producto, "ValorUnitario"));
            }

            detalles.add(d);
        }

        return detalles;
    }

    private List<ImpuestoDian> parsearImpuestos(Document doc) {
        List<ImpuestoDian> impuestos = new ArrayList<>();

        NodeList impuestosNodes = doc.getElementsByTagName("Impuestos");
        if (impuestosNodes.getLength() > 0) {
            Element impuestosRoot = (Element) impuestosNodes.item(0);
            NodeList impuestoNodes = impuestosRoot.getChildNodes();

            for (int i = 0; i < impuestoNodes.getLength(); i++) {
                if (impuestoNodes.item(i) instanceof Element && "Impuesto".equals(impuestoNodes.item(i).getNodeName())) {
                    Element impuesto = (Element) impuestoNodes.item(i);
                    ImpuestoDian imp = new ImpuestoDian();

                    imp.setCodigoImpuesto(getTextoDe(impuesto, "CodigoImpuesto"));
                    imp.setNombreImpuesto(getTextoDe(impuesto, "Impuesto"));
                    imp.setCodigoPorcentaje(getTextoDe(impuesto, "CodigoPorcentaje"));
                    imp.setPorcentaje(getTextoDe(impuesto, "Porcentaje"));
                    imp.setBaseImponible(getTextoDe(impuesto, "BaseImponible"));
                    imp.setValor(getTextoDe(impuesto, "Valor"));

                    impuestos.add(imp);
                }
            }
        }

        return impuestos;
    }

    private List<PagoDian> parsearPagos(Document doc) {
        List<PagoDian> pagos = new ArrayList<>();
        NodeList pagoNodes = doc.getElementsByTagName("Pago");

        for (int i = 0; i < pagoNodes.getLength(); i++) {
            Element pago = (Element) pagoNodes.item(i);
            PagoDian p = new PagoDian();

            p.setFormaPago(getTextoDe(pago, "FormaPagoColombia"));
            p.setCodigoFormaPago(getTextoDe(pago, "CodigoFormaPago"));
            p.setTotal(getTextoDe(pago, "Total"));

            pagos.add(p);
        }

        return pagos;
    }

    private String extraerCampoAdicional(Document doc, String descripcion) {
        NodeList campos = doc.getElementsByTagName("Campo");
        for (int i = 0; i < campos.getLength(); i++) {
            Element campo = (Element) campos.item(i);
            String desc = getTextoDe(campo, "Descripcion");
            if (descripcion.equals(desc)) {
                return getTextoDe(campo, "Valor");
            }
        }
        return "";
    }

    private String getTexto(Document doc, String tagName) {
        NodeList nodeList = doc.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String texto = nodeList.item(0).getTextContent();
            return texto != null ? texto.trim() : "";
        }
        return "";
    }

    private String getTextoDe(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            String texto = nodeList.item(0).getTextContent();
            return texto != null ? texto.trim() : "";
        }
        return "";
    }

    private Element getPrimerElemento(Document doc, String tagName) {
        NodeList nodeList = doc.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0 && nodeList.item(0) instanceof Element) {
            return (Element) nodeList.item(0);
        }
        return null;
    }

    private Element getPrimerElemento(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0 && nodeList.item(0) instanceof Element) {
            return (Element) nodeList.item(0);
        }
        return null;
    }

    private String getResolucion(Document doc) {
        Element resolucion = getPrimerElemento(doc, "ResolucionFiscal");
        if (resolucion != null) {
            return getTextoDe(resolucion, "Resolucion");
        }
        return "";
    }

    private String getPrefijo(Document doc) {
        Element resolucion = getPrimerElemento(doc, "ResolucionFiscal");
        if (resolucion != null) {
            return getTextoDe(resolucion, "Prefijo");
        }
        return "";
    }
}
