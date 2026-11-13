package com.prueba.factura.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class SecuenciaService {

    public static final String FACTURA = "FACTURA";
    public static final String NOTA_CREDITO = "NOTA_CREDITO";

    private static final Logger logger = LoggerFactory.getLogger(SecuenciaService.class);

    private final JdbcTemplate jdbc;
    private volatile boolean lista;

    @Value("${app.acceso.usuario:admin}")
    private String usuarioInicial;

    @Value("${app.acceso.clave:admin}")
    private String claveInicial;

    public SecuenciaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record NumeroSecuencia(String prefijo, long numero) {
    }

    public record ConfiguracionSecuencia(String tipo, String prefijo, long proximoNumero) {
    }

    public record DatosEmisor(String razonSocial, String nit, String direccion, String telefono,
            String textosFiscales, String pieFactura) {

        public static DatosEmisor predeterminado() {
            return new DatosEmisor("", "", "", "", TextosFactura.FISCALES, TextosFactura.PIE);
        }
    }

    @PostConstruct
    public void iniciar() {
        try {
            preparar();
        } catch (RuntimeException e) {
            lista = false;
            logger.error("No se pudo conectar a SQL Server. El sitio de secuencias queda abierto con el aviso.", e);
        }
    }

    private void preparar() {
        jdbc.execute("""
                IF OBJECT_ID('dbo.SecuenciaDocumento', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.SecuenciaDocumento (
                        tipo varchar(20) NOT NULL PRIMARY KEY,
                        prefijo varchar(20) NOT NULL,
                        ultimo_numero bigint NOT NULL,
                        actualizado_en datetime2 NOT NULL CONSTRAINT DF_SecuenciaDocumento_fecha DEFAULT SYSUTCDATETIME()
                    )
                END
                """);
        jdbc.execute("""
                IF OBJECT_ID('dbo.UsuarioAcceso', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.UsuarioAcceso (
                        id int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                        usuario varchar(50) NOT NULL CONSTRAINT UQ_UsuarioAcceso UNIQUE,
                        clave_hash varchar(200) NOT NULL,
                        activo bit NOT NULL CONSTRAINT DF_UsuarioAcceso_activo DEFAULT 1
                    )
                END
                """);
        EsquemaFactura.crear(jdbc);
        asegurarSecuencia(FACTURA, "", 0);
        asegurarSecuencia(NOTA_CREDITO, "NC", 0);
        asegurarEmisor();
        asegurarUsuarioInicial();
        lista = true;
    }

    public boolean lista() {
        return lista;
    }

    public NumeroSecuencia siguiente(String tipo) {
        String tipoSeguro = tipoValido(tipo);
        NumeroSecuencia asignado = jdbc.queryForObject("""
                UPDATE dbo.SecuenciaDocumento
                SET ultimo_numero = ultimo_numero + 1,
                    actualizado_en = SYSUTCDATETIME()
                OUTPUT inserted.prefijo, inserted.ultimo_numero
                WHERE tipo = ?
                """, (rs, row) -> new NumeroSecuencia(rs.getString("prefijo"), rs.getLong("ultimo_numero")), tipoSeguro);
        if (asignado == null) {
            throw new IllegalStateException("No existe la secuencia " + tipoSeguro);
        }
        logger.info("Secuencia {} asigno {}{}", tipoSeguro, asignado.prefijo(), asignado.numero());
        return asignado;
    }

    public ConfiguracionSecuencia consultar(String tipo) {
        String tipoSeguro = tipoValido(tipo);
        ConfiguracionSecuencia encontrada = jdbc.queryForObject("""
                SELECT prefijo, ultimo_numero
                FROM dbo.SecuenciaDocumento
                WHERE tipo = ?
                """, (rs, row) -> new ConfiguracionSecuencia(
                tipoSeguro, rs.getString("prefijo"), rs.getLong("ultimo_numero") + 1), tipoSeguro);
        if (encontrada == null) {
            throw new IllegalStateException("No existe la secuencia " + tipoSeguro);
        }
        return encontrada;
    }

    public void guardar(String tipo, String prefijo, long proximoNumero) {
        String tipoSeguro = tipoValido(tipo);
        String prefijoSeguro = validarPrefijo(prefijo);
        if (proximoNumero < 1) {
            throw new IllegalArgumentException("El proximo numero debe ser 1 o mayor.");
        }
        int filas = jdbc.update("""
                UPDATE dbo.SecuenciaDocumento
                SET prefijo = ?, ultimo_numero = ?, actualizado_en = SYSUTCDATETIME()
                WHERE tipo = ?
                """, prefijoSeguro, proximoNumero - 1, tipoSeguro);
        if (filas == 0) {
            throw new IllegalStateException("No existe la secuencia " + tipoSeguro);
        }
    }

    public boolean entrar(String usuario, String clave) {
        if (usuario == null || usuario.isBlank() || clave == null) {
            return false;
        }
        String hash = jdbc.query("""
                SELECT clave_hash
                FROM dbo.UsuarioAcceso
                WHERE usuario = ? AND activo = 1
                """, rs -> rs.next() ? rs.getString(1) : null, usuario.trim());
        return ClaveAcceso.coincide(clave, hash);
    }

    public void cambiarClave(String usuario, String claveNueva) {
        if (claveNueva == null || claveNueva.length() < 4) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 4 caracteres.");
        }
        jdbc.update("""
                UPDATE dbo.UsuarioAcceso
                SET clave_hash = ?
                WHERE usuario = ?
                """, ClaveAcceso.hash(claveNueva), usuario);
    }

    public DatosEmisor emisor() {
        DatosEmisor encontrado = jdbc.query("""
                SELECT razon_social, nit, direccion, telefono, textos_fiscales, pie_factura
                FROM dbo.DatosEmisor
                WHERE id = 1
                """, rs -> rs.next()
                ? new DatosEmisor(
                        rs.getString("razon_social"),
                        rs.getString("nit"),
                        rs.getString("direccion"),
                        rs.getString("telefono"),
                        rs.getString("textos_fiscales"),
                        rs.getString("pie_factura"))
                : null);
        return encontrado == null ? DatosEmisor.predeterminado() : encontrado;
    }

    public void guardarEmisor(DatosEmisor entrada) {
        DatosEmisor limpio = validarEmisor(entrada);
        int filas = jdbc.update("""
                UPDATE dbo.DatosEmisor
                SET razon_social = ?, nit = ?, direccion = ?, telefono = ?,
                    textos_fiscales = ?, pie_factura = ?
                WHERE id = 1
                """, limpio.razonSocial(), limpio.nit(), limpio.direccion(), limpio.telefono(),
                limpio.textosFiscales(), limpio.pieFactura());
        if (filas == 0) {
            jdbc.update("""
                    INSERT INTO dbo.DatosEmisor
                        (id, razon_social, nit, direccion, telefono, textos_fiscales, pie_factura)
                    VALUES (1, ?, ?, ?, ?, ?, ?)
                    """, limpio.razonSocial(), limpio.nit(), limpio.direccion(), limpio.telefono(),
                    limpio.textosFiscales(), limpio.pieFactura());
        }
    }

    public static DatosEmisor validarEmisor(DatosEmisor entrada) {
        if (entrada == null) {
            throw new IllegalArgumentException("Faltan los datos de la empresa.");
        }
        String razon = recortar(entrada.razonSocial(), 200, "La razón social admite hasta 200 caracteres.");
        String nit = entrada.nit() == null ? "" : entrada.nit().trim();
        if (!nit.isEmpty() && !nit.matches("\\d{1,15}")) {
            throw new IllegalArgumentException("El NIT solo lleva dígitos, sin dígito de verificación.");
        }
        String direccion = recortar(entrada.direccion(), 300, "La dirección admite hasta 300 caracteres.");
        String telefono = entrada.telefono() == null ? "" : entrada.telefono().trim();
        if (telefono.length() > 40 || !telefono.matches("[0-9+()\\- ]*")) {
            throw new IllegalArgumentException("El teléfono solo admite números, espacios, + y guiones.");
        }
        String fiscales = recortar(normalizar(entrada.textosFiscales()), 2000,
                "Los textos fiscales admiten hasta 2000 caracteres.");
        String pie = recortar(normalizar(entrada.pieFactura()), 2000,
                "El pie de factura admite hasta 2000 caracteres.");
        return new DatosEmisor(razon, nit, direccion, telefono, fiscales, pie);
    }

    public static String validarPrefijo(String prefijo) {
        String valor = prefijo == null ? "" : prefijo.trim();
        if (valor.length() > 10 || !valor.matches("[A-Za-z0-9]*")) {
            throw new IllegalArgumentException("El prefijo solo puede tener letras y numeros, hasta 10 caracteres.");
        }
        return valor;
    }

    private void asegurarSecuencia(String tipo, String prefijo, long ultimo) {
        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.SecuenciaDocumento WHERE tipo = ?", Integer.class, tipo);
        if (existe != null && existe == 0) {
            jdbc.update("""
                    INSERT INTO dbo.SecuenciaDocumento (tipo, prefijo, ultimo_numero)
                    VALUES (?, ?, ?)
                    """, tipo, prefijo, ultimo);
        }
    }

    private void asegurarEmisor() {
        jdbc.execute("""
                IF OBJECT_ID('dbo.DatosEmisor', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.DatosEmisor (
                        id int NOT NULL PRIMARY KEY,
                        razon_social nvarchar(200) NOT NULL,
                        nit varchar(20) NOT NULL,
                        direccion nvarchar(300) NOT NULL,
                        telefono varchar(40) NOT NULL,
                        textos_fiscales nvarchar(2000) NOT NULL,
                        pie_factura nvarchar(2000) NOT NULL
                    )
                END
                """);
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.DatosEmisor WHERE id = 1", Integer.class);
        if (existe != null && existe == 0) {
            DatosEmisor inicial = DatosEmisor.predeterminado();
            jdbc.update("""
                    INSERT INTO dbo.DatosEmisor
                        (id, razon_social, nit, direccion, telefono, textos_fiscales, pie_factura)
                    VALUES (1, ?, ?, ?, ?, ?, ?)
                    """, inicial.razonSocial(), inicial.nit(), inicial.direccion(), inicial.telefono(),
                    inicial.textosFiscales(), inicial.pieFactura());
        }
    }

    private static String recortar(String valor, int maximo, String mensaje) {
        String texto = valor == null ? "" : valor.trim();
        if (texto.length() > maximo) {
            throw new IllegalArgumentException(mensaje);
        }
        return texto;
    }

    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private void asegurarUsuarioInicial() {
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.UsuarioAcceso", Integer.class);
        if (existe != null && existe == 0) {
            jdbc.update("""
                    INSERT INTO dbo.UsuarioAcceso (usuario, clave_hash, activo)
                    VALUES (?, ?, 1)
                    """, usuarioInicial.trim(), ClaveAcceso.hash(claveInicial));
            logger.warn("Se creo el usuario de secuencias '{}'. Cambie la contraseña en /secuencias.", usuarioInicial);
        }
    }

    private static String tipoValido(String tipo) {
        if (FACTURA.equals(tipo) || NOTA_CREDITO.equals(tipo)) {
            return tipo;
        }
        throw new IllegalArgumentException("Tipo de secuencia no reconocido.");
    }
}
