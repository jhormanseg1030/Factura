package com.prueba.factura.Services;

public class ColumnasProducto {

    public record Producto(String cant, String codigo, String nombre, String precio, String total) {}

    private static final int ANCHO_CANTIDAD = 5;
    private static final int ANCHO_DESCRIPCION = 17;
    private static final int ANCHO_PRECIO = 10;
    private static final int ANCHO_TOTAL = 10;

    public static String ajustarTexto(String texto, int anchoMax, boolean alinearDerecha) {
        if (texto == null) texto = "";
        if (texto.length() > anchoMax) {
            return texto.substring(0, anchoMax);
        }
        int espaciosFaltantes = anchoMax - texto.length();
        String espacios = " ".repeat(espaciosFaltantes);
        return alinearDerecha ? espacios + texto : texto + espacios;
    }

    public static String formatearEncabezado() {
        String c = ajustarTexto("Cant", ANCHO_CANTIDAD, false);
        String d = ajustarTexto("Descripcion", ANCHO_DESCRIPCION, false);
        String p = ajustarTexto("Precio", ANCHO_PRECIO, true);
        String t = ajustarTexto("Total", ANCHO_TOTAL, true);
        return c + d + p + t;
    }

public static String formatoLinea1(Producto producto) {
        String c = ajustarTexto(producto.cant(), ANCHO_CANTIDAD, false);
        boolean tieneCodigo = producto.codigo() != null && !producto.codigo().trim().isEmpty();

        if (tieneCodigo) {
            return c + producto.codigo().trim();
        } else {
            String d = ajustarTexto(producto.nombre(), ANCHO_DESCRIPCION, false);
            String p = ajustarTexto(producto.precio(), ANCHO_PRECIO, true);
            String t = ajustarTexto(producto.total(), ANCHO_TOTAL, true);
            return c + d + p + t;
        }
    }

public static String formatoLinea2(Producto producto) {
        boolean tieneCodigo = producto.codigo() != null && !producto.codigo().trim().isEmpty();
        if (!tieneCodigo || producto.nombre() == null || producto.nombre().trim().isEmpty()) {
            return "";
        }

        String espacioCant = " ".repeat(ANCHO_CANTIDAD);
        String d = ajustarTexto(producto.nombre(), ANCHO_DESCRIPCION, false);
        String p = ajustarTexto(producto.precio(), ANCHO_PRECIO, true);
        String t = ajustarTexto(producto.total(), ANCHO_TOTAL, true);

        return espacioCant + d + p + t;
    }
}
