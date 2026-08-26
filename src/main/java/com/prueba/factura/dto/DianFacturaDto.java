package com.prueba.factura.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DianFacturaDto {

    private String tipoDocumento;
    private String numeroDocumento;
    private String cufe;
    private String fechaEmision;
    private String razonSocialEmisor;
    private String rucEmisor;
    private String estadoComprobante;

    private String clienteNombre;
    private String clienteIdentificacion;
    private String clienteTipoIdentificacion;

    private String totalSinImpuestos;
    private String totalConImpuestos;
    private String totalVenta;
    private String totalImpuesto;
    private String totalComprobante;
    private String propina;
    private String moneda;
    private String condicionVenta;

    private List<DetalleDian> detalles;
    private List<ImpuestoDian> impuestos;
    private List<PagoDian> pagos;

    private String numeroAutorizacion;
    private String claveAcceso;
    private String informacionCufe;
    private String resolucion;
    private String prefijo;

    public DianFacturaDto() {}

    @Getter
    @Setter
    public static class DetalleDian {
        private String cantidad;
        private String subTotal;
        private String total;
        private String descuento;
        private String tipo;
        private String codigoProducto;
        private String descripcionProducto;
        private String valorUnitario;
    }

    @Getter
    @Setter
    public static class ImpuestoDian {
        private String codigoImpuesto;
        private String nombreImpuesto;
        private String codigoPorcentaje;
        private String porcentaje;
        private String baseImponible;
        private String valor;
    }

    @Getter
    @Setter
    public static class PagoDian {
        private String formaPago;
        private String codigoFormaPago;
        private String total;
    }
}
