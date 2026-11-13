package com.prueba.factura.config;

import java.io.IOException;
import java.io.Reader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.Properties;

public final class HarmonyPuertoConfig {

    public static final int PUERTO_DEFECTO = 8080;
    public static final String ARCHIVO = "config/harmony.properties";
    public static final String CLAVE_PUERTO = "server.port";
    public static final String CLAVE_HOST = "app.harmony.advertised-host";
    public static final String CLAVE_TARGET = "app.target.url";
    public static final String CLAVE_WEBHOOK = "app.webhook.url";
    public static final String CLAVE_API_KEY = "app.api.key";
    public static final String CLAVE_EMISION = "app.emission.point.id";
    public static final String CLAVE_SQL_URL = "spring.datasource.url";
    public static final String CLAVE_SQL_USUARIO = "spring.datasource.username";
    public static final String CLAVE_SQL_CLAVE = "spring.datasource.password";
    public static final String CLAVE_SQL_INSTANCIA = "app.sql.instancia";
    public static final String BASE_DATOS = "Zonak";
    public static final String RUTA_PARTNER = "/api/harmony/col";

    private HarmonyPuertoConfig() {
    }

    public static void aplicar(String[] args) {
        boolean reconfigurar = tieneArgumento(args, "--configurar-puerto");
        Path archivo = Path.of(ARCHIVO);
        DatosPuerto datos;

        DatosPuerto previo = Files.exists(archivo)
                ? cargar(archivo)
                : new DatosPuerto(PUERTO_DEFECTO, hostPorDefecto(), "", "", "", "", "", "");

        if (!reconfigurar && Files.exists(archivo)) {
            datos = previo;
        } else if (System.console() != null) {
            datos = preguntar(previo);
            guardar(archivo, datos);
        } else {
            datos = new DatosPuerto(PUERTO_DEFECTO, hostPorDefecto(), "", "", "", "", "", "");
            System.out.println("No hay consola para elegir el puerto. Se uso " + datos.puerto()
                    + ". Ejecuta instalar.ps1 o inicia con --configurar-puerto para fijarlo.");
        }

        aplicarPropiedades(datos);
        System.out.println("En el EMC, PartnerCommunication/REST/URL debe quedar:");
        System.out.println(urlPartner(datos.host(), datos.puerto()));
        if (!datos.urlFirma().isBlank()) {
            System.out.println("La app que firma queda en:");
            System.out.println(datos.urlFirma());
        }
    }

    public static void aplicarPropiedades(DatosPuerto datos) {
        System.setProperty(CLAVE_PUERTO, Integer.toString(datos.puerto()));
        System.setProperty(CLAVE_HOST, datos.host());
        if (!datos.urlFirma().isBlank()) {
            System.setProperty(CLAVE_TARGET, datos.urlFirma());
            System.setProperty(CLAVE_WEBHOOK, datos.urlFirma());
        }
        if (!datos.apiKey().isBlank()) {
            System.setProperty(CLAVE_API_KEY, datos.apiKey());
        }
        System.setProperty(CLAVE_EMISION, datos.puntoEmision());
        if (!datos.instanciaSql().isBlank()) {
            System.setProperty(CLAVE_SQL_URL, urlJdbc(datos.instanciaSql(), BASE_DATOS));
            System.setProperty(CLAVE_SQL_USUARIO, datos.usuarioSql());
            System.setProperty(CLAVE_SQL_CLAVE, datos.claveSql());
        }
    }

    public static String urlPartner(String host, int puerto) {
        return "http://" + host + ":" + puerto + RUTA_PARTNER;
    }

    public static DatosPuerto cargar(Path archivo) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + archivo, e);
        }
        int puerto = validarPuerto(properties.getProperty(CLAVE_PUERTO, Integer.toString(PUERTO_DEFECTO)));
        String host = validarHost(properties.getProperty(CLAVE_HOST, "localhost"));
        String urlFirma = validarUrlFirma(properties.getProperty(CLAVE_TARGET, ""));
        String apiKey = validarApiKey(properties.getProperty(CLAVE_API_KEY, ""));
        String puntoEmision = validarPuntoEmision(properties.getProperty(CLAVE_EMISION, ""));
        String instanciaSql = properties.getProperty(CLAVE_SQL_INSTANCIA, "");
        String usuarioSql = properties.getProperty(CLAVE_SQL_USUARIO, "");
        String claveSql = properties.getProperty(CLAVE_SQL_CLAVE, "");
        if (!instanciaSql.isBlank()) {
            instanciaSql = validarInstancia(instanciaSql);
            usuarioSql = validarUsuarioSql(usuarioSql);
        }
        return new DatosPuerto(puerto, host, urlFirma, apiKey, puntoEmision, instanciaSql, usuarioSql, claveSql);
    }

    public static void guardar(Path archivo, DatosPuerto datos) {
        try {
            Path padre = archivo.getParent();
            if (padre != null) {
                Files.createDirectories(padre);
            }
            String contenido = CLAVE_PUERTO + "=" + datos.puerto() + "\n"
                    + CLAVE_HOST + "=" + escapar(datos.host()) + "\n"
                    + CLAVE_TARGET + "=" + escapar(datos.urlFirma()) + "\n"
                    + CLAVE_WEBHOOK + "=" + escapar(datos.urlFirma()) + "\n"
                    + CLAVE_API_KEY + "=" + escapar(datos.apiKey()) + "\n"
                    + CLAVE_EMISION + "=" + escapar(datos.puntoEmision()) + "\n"
                    + CLAVE_SQL_INSTANCIA + "=" + escapar(datos.instanciaSql()) + "\n"
                    + CLAVE_SQL_URL + "=" + escapar(datos.instanciaSql().isBlank()
                            ? ""
                            : urlJdbc(datos.instanciaSql(), BASE_DATOS)) + "\n"
                    + CLAVE_SQL_USUARIO + "=" + escapar(datos.usuarioSql()) + "\n"
                    + CLAVE_SQL_CLAVE + "=" + escapar(datos.claveSql()) + "\n";
            Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar " + archivo, e);
        }
    }

    public static int validarPuerto(String texto) {
        int puerto;
        try {
            puerto = Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El puerto debe ser un numero.");
        }
        if (puerto < 1 || puerto > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
        }
        return puerto;
    }

    public static String validarHost(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El host no puede estar vacio.");
        }
        String host = texto.trim();
        if (host.contains(" ") || host.contains("/") || host.contains(":")) {
            throw new IllegalArgumentException("Escribe solo la IP o el host, sin http ni puerto.");
        }
        return host;
    }

    public static String validarUrlFirma(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String url = texto.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("La URL de la app que firma debe empezar por http:// o https://");
        }
        if (url.contains(" ")) {
            throw new IllegalArgumentException("La URL de la app que firma no puede tener espacios.");
        }
        return url;
    }

    public static String validarApiKey(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String llave = texto.trim();
        if (llave.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("La API key no puede tener espacios.");
        }
        return llave;
    }

    public static String validarPuntoEmision(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String punto = texto.trim();
        if (punto.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El punto de emision no puede tener espacios.");
        }
        return punto;
    }

    public static boolean puertoLibre(int puerto) {
        try (ServerSocket socket = new ServerSocket(puerto)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static DatosPuerto preguntar(DatosPuerto previo) {
        String hostInicial = previo.host() == null || previo.host().isBlank() ? hostPorDefecto() : previo.host();
        while (true) {
            String puertoTexto = leer("Puerto de esta aplicacion [" + previo.puerto() + "]",
                    Integer.toString(previo.puerto()));
            int puerto;
            try {
                puerto = validarPuerto(puertoTexto);
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
                continue;
            }
            if (puerto != previo.puerto() && !puertoLibre(puerto)) {
                System.out.println("El puerto " + puerto + " ya esta en uso. Elige otro.");
                continue;
            }

            String hostTexto = leer("IP o host que Harmony usara [" + hostInicial + "]", hostInicial);
            String host;
            String urlFirma;
            String apiKey;
            String puntoEmision;
            String instanciaSql;
            String usuarioSql;
            String claveSql;
            try {
                host = validarHost(hostTexto);
                String urlInicial = previo.urlFirma().isBlank() ? "" : previo.urlFirma();
                String urlEtiqueta = urlInicial.isBlank()
                        ? "URL de la app que firma (Enter para omitir)"
                        : "URL de la app que firma [" + urlInicial + "]";
                urlFirma = validarUrlFirma(leer(urlEtiqueta, urlInicial));
                apiKey = validarApiKey(leer("API key de la app que firma (Enter para conservar la actual)", previo.apiKey()));
                String emisionInicial = previo.puntoEmision();
                puntoEmision = validarPuntoEmision(leer(
                        "Punto de emision [" + (emisionInicial.isBlank() ? "vacio" : emisionInicial) + "]",
                        emisionInicial));
                String instanciaInicial = previo.instanciaSql().isBlank() ? "localhost\\SQLEXPRESS" : previo.instanciaSql();
                instanciaSql = validarInstancia(leer("Instancia de SQL Server [" + instanciaInicial + "]", instanciaInicial));
                String usuarioInicial = previo.usuarioSql().isBlank() ? "sa" : previo.usuarioSql();
                usuarioSql = validarUsuarioSql(leer("Usuario de SQL Server [" + usuarioInicial + "]", usuarioInicial));
                claveSql = leerClave("Contrasena de SQL Server (Enter para conservar la actual)", previo.claveSql());
                if (claveSql.isBlank()) {
                    throw new IllegalArgumentException("La contrasena de SQL Server no puede estar vacia.");
                }
                asegurarBase(instanciaSql, usuarioSql, claveSql);
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
                continue;
            }
            return new DatosPuerto(puerto, host, urlFirma, apiKey, puntoEmision, instanciaSql, usuarioSql, claveSql);
        }
    }

    public static String urlJdbc(String instancia, String base) {
        String texto = validarInstancia(instancia);
        String servidor;
        String nombreInstancia = "";
        String puerto = "";
        int barra = texto.indexOf('\\');
        if (barra >= 0) {
            servidor = normalizarServidor(texto.substring(0, barra));
            nombreInstancia = texto.substring(barra + 1);
        } else {
            servidor = texto;
        }
        int coma = servidor.indexOf(',');
        if (coma >= 0) {
            puerto = servidor.substring(coma + 1);
            servidor = servidor.substring(0, coma);
        }
        servidor = normalizarServidor(servidor);
        String url = "jdbc:sqlserver://" + servidor;
        if (!puerto.isBlank()) {
            url += ":" + puerto;
        }
        if (!nombreInstancia.isBlank()) {
            url += ";instanceName=" + nombreInstancia;
        }
        return url + ";databaseName=" + base + ";encrypt=true;trustServerCertificate=true";
    }

    public static void asegurarBase(String instancia, String usuario, String clave) {
        String url = urlJdbc(instancia, "master");
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            try (Connection conexion = DriverManager.getConnection(url, usuario, clave);
                    Statement sentencia = conexion.createStatement()) {
                sentencia.execute("IF DB_ID(N'" + BASE_DATOS + "') IS NULL CREATE DATABASE [" + BASE_DATOS + "]");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo crear la base " + BASE_DATOS + " en " + instancia
                    + ". " + e.getMessage());
        }
        System.out.println("La base " + BASE_DATOS + " esta lista en " + instancia + ".");
    }

    public static String validarInstancia(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("La instancia de SQL Server no puede estar vacia.");
        }
        String instancia = texto.trim();
        if (instancia.chars().anyMatch(Character::isWhitespace) || instancia.indexOf(';') >= 0) {
            throw new IllegalArgumentException("Escribe la instancia sin espacios, por ejemplo localhost\\SQLEXPRESS.");
        }
        int barra = instancia.indexOf('\\');
        if (barra == 0 || barra == instancia.length() - 1) {
            throw new IllegalArgumentException("Escribe la instancia como servidor\\nombre, por ejemplo localhost\\SQLEXPRESS.");
        }
        return instancia;
    }

    public static String validarUsuarioSql(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El usuario de SQL Server no puede estar vacio.");
        }
        String usuario = texto.trim();
        if (usuario.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("El usuario de SQL Server no puede tener espacios.");
        }
        return usuario;
    }

    private static String leer(String etiqueta, String defecto) {
        String valor = System.console().readLine(etiqueta + ": ");
        if (valor == null || valor.isBlank()) {
            return defecto;
        }
        return valor.trim();
    }

    private static String leerClave(String etiqueta, String defecto) {
        char[] valor = System.console().readPassword(etiqueta + ": ");
        if (valor == null || valor.length == 0) {
            return defecto == null ? "" : defecto;
        }
        return new String(valor);
    }

    private static String normalizarServidor(String servidor) {
        if (".".equals(servidor) || "(local)".equalsIgnoreCase(servidor)) {
            return "localhost";
        }
        return servidor;
    }

    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\\", "\\\\");
    }

    public static String hostPorDefecto() {
        try {
            for (NetworkInterface interfaz : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!interfaz.isUp() || interfaz.isLoopback()) {
                    continue;
                }
                for (InetAddress direccion : Collections.list(interfaz.getInetAddresses())) {
                    if (direccion instanceof Inet4Address && !direccion.isLoopbackAddress()) {
                        return direccion.getHostAddress();
                    }
                }
            }
        } catch (IOException ignored) {
            // se usa localhost si no hay una IPv4 de red
        }
        return "localhost";
    }

    private static boolean tieneArgumento(String[] args, String esperado) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (esperado.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    public record DatosPuerto(int puerto, String host, String urlFirma, String apiKey, String puntoEmision,
            String instanciaSql, String usuarioSql, String claveSql) {
    }
}
