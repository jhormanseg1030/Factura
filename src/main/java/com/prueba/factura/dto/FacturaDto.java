package com.prueba.factura.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacturaDto {
    
    private String checkId;
    private String cufe;
    private String total;
    private String qrCode;
    private List<String> items;

    public FacturaDto() {}

    public FacturaDto(String checkId, String cufe, String total, String qrCode, List<String> items){
        this.checkId = checkId;
        this.cufe = cufe;
        this.total = total;
        this.qrCode = qrCode;
        this.items = items;
    }
}