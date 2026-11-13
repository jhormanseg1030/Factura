package com.prueba.factura.dto;

public final class ResultadoFiscal {

    private final boolean success;
    private final String numeroFactura;
    private final String cufe;
    private final String qr;
    private final String mensaje;
    private final String cuerpo;

    private ResultadoFiscal(boolean success, String numeroFactura, String cufe, String qr, String mensaje, String cuerpo) {
        this.success = success;
        this.numeroFactura = numeroFactura == null ? "" : numeroFactura;
        this.cufe = cufe == null ? "" : cufe;
        this.qr = qr == null ? "" : qr;
        this.mensaje = mensaje == null ? "" : mensaje;
        this.cuerpo = cuerpo == null ? "" : cuerpo;
    }

    public static ResultadoFiscal ok(String numeroFactura, String cufe, String qr, String cuerpo) {
        return new ResultadoFiscal(true, numeroFactura, cufe, qr, "Documento generado", cuerpo);
    }

    public static ResultadoFiscal sinDocumentoFiscal(String cuerpo) {
        return new ResultadoFiscal(true, "", "", "",
                "La factura no requiere procesamiento de documento fiscal", cuerpo);
    }

    public static ResultadoFiscal error(String mensaje, String cuerpo) {
        return new ResultadoFiscal(false, "", "", "", mensaje, cuerpo);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getNumeroFactura() {
        return numeroFactura;
    }

    public String getCufe() {
        return cufe;
    }

    public String getQr() {
        return qr;
    }

    public String getMensaje() {
        return mensaje;
    }

    public String cuerpo() {
        return cuerpo;
    }
}
