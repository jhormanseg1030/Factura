package com.prueba.factura.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.prueba.factura.Services.LogoFactura;
import com.prueba.factura.Services.FacturaEnviadaService;
import com.prueba.factura.Services.FacturaEnviadaService.Resumen;
import com.prueba.factura.Services.FacturaPendienteService;
import com.prueba.factura.Services.Impresion;
import com.prueba.factura.Services.PortalEnvioService;
import com.prueba.factura.Services.SecuenciaService;
import com.prueba.factura.Services.SecuenciaService.ConfiguracionSecuencia;
import com.prueba.factura.Services.SecuenciaService.DatosEmisor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class SecuenciaController {

    static final String SESION = "usuarioSecuencia";
    static final String CSRF = "csrfSecuencia";

    private final SecuenciaService secuencias;
    private final FacturaEnviadaService enviadas;
    private final FacturaPendienteService pendientes;
    private final PortalEnvioService portal;
    private final Impresion impresion;
    private final LogoFactura logoFactura;

    public SecuenciaController(SecuenciaService secuencias, FacturaEnviadaService enviadas,
            FacturaPendienteService pendientes, PortalEnvioService portal, Impresion impresion,
            LogoFactura logoFactura) {
        this.secuencias = secuencias;
        this.enviadas = enviadas;
        this.pendientes = pendientes;
        this.portal = portal;
        this.impresion = impresion;
        this.logoFactura = logoFactura;
    }

    @GetMapping(value = "/secuencias", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pagina(HttpSession session,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "ok", required = false) String ok) {
        if (!secuencias.lista()) {
            return html(sinBase());
        }
        if (session.getAttribute(SESION) == null) {
            return html(login(error));
        }
        String token = csrf(session);
        ConfiguracionSecuencia factura = secuencias.consultar(SecuenciaService.FACTURA);
        ConfiguracionSecuencia nota = secuencias.consultar(SecuenciaService.NOTA_CREDITO);
        return html(panel(session.getAttribute(SESION).toString(), factura, nota, token, error, ok));
    }

    @GetMapping("/secuencias/facturas")
    public String facturas() {
        return "redirect:/secuencias/estado";
    }

    @GetMapping(value = "/secuencias/empresa", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> empresa(HttpSession session,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "ok", required = false) String ok) {
        if (!secuencias.lista()) {
            return html(sinBase());
        }
        if (!sesionActiva(session)) {
            return html(login(null));
        }
        return html(VistaPortal.empresa(usuario(session), secuencias.emisor(), logoFactura.visible(),
                csrf(session), error, ok));
    }

    @GetMapping(value = "/secuencias/estado", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> estado(HttpSession session,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "ok", required = false) String ok,
            @RequestParam(value = "id", required = false) String id) {
        if (!sesionActiva(session)) {
            return html(login(null));
        }
        return html(VistaPortal.estado(usuario(session), csrf(session), ok, error, textoAviso(ok, error, id),
                enviadas.listar()));
    }

    @GetMapping(value = "/secuencias/estado/datos", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> datosEstado(HttpSession session) {
        if (!sesionActiva(session)) {
            return ResponseEntity.status(401).body(Map.of("facturas", List.of()));
        }
        List<Map<String, Object>> filas = new ArrayList<>();
        for (Resumen factura : enviadas.listar()) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("id", factura.id());
            fila.put("numero", factura.numero());
            fila.put("fecha", VistaPortal.fechaCorta(factura.fecha()));
            fila.put("cliente", factura.cliente());
            fila.put("cheque", factura.cheque());
            fila.put("total", VistaPortal.pesos(factura.total()));
            fila.put("tipo", factura.tipo());
            fila.put("claseEnvio", VistaPortal.clase(factura.estadoEnvio()));
            fila.put("etiquetaEnvio", VistaPortal.etiquetaEnvio(factura.estadoEnvio()));
            boolean fallo = "error".equals(factura.estadoEnvio()) || "pendiente".equals(factura.estadoEnvio());
            fila.put("mensaje", fallo ? factura.mensajeEnvio() : "");
            fila.put("claseDian", VistaPortal.clase(factura.estadoDian()));
            fila.put("etiquetaDian", VistaPortal.etiquetaDian(factura.estadoDian()));
            fila.put("detalle", factura.detalleDian());
            fila.put("reimprimible", factura.reimprimible());
            filas.add(fila);
        }
        return ResponseEntity.ok(Map.of("facturas", filas));
    }

    @PostMapping("/secuencias/entrar")
    public String entrar(@RequestParam String usuario, @RequestParam String clave, HttpServletRequest request) {
        if (!secuencias.entrar(usuario, clave)) {
            return "redirect:/secuencias?error=acceso";
        }
        HttpSession anterior = request.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
        }
        request.getSession(true).setAttribute(SESION, usuario.trim());
        return "redirect:/secuencias";
    }

    @PostMapping("/secuencias/guardar")
    public String guardar(@RequestParam String csrf,
            @RequestParam String prefijoFactura,
            @RequestParam long proximoFactura,
            @RequestParam String prefijoNota,
            @RequestParam long proximoNota,
            HttpSession session) {
        if (session.getAttribute(SESION) == null) {
            return "redirect:/secuencias";
        }
        if (!csrf.equals(session.getAttribute(CSRF))) {
            return "redirect:/secuencias?error=token";
        }
        try {
            secuencias.guardar(SecuenciaService.FACTURA, prefijoFactura, proximoFactura);
            secuencias.guardar(SecuenciaService.NOTA_CREDITO, prefijoNota, proximoNota);
            return "redirect:/secuencias?ok=secuencia";
        } catch (IllegalArgumentException e) {
            return "redirect:/secuencias?error=datos";
        }
    }

    @PostMapping("/secuencias/empresa")
    public String guardarEmpresa(@RequestParam String csrf,
            @RequestParam(defaultValue = "") String razonSocial,
            @RequestParam(defaultValue = "") String nit,
            @RequestParam(defaultValue = "") String direccion,
            @RequestParam(defaultValue = "") String telefono,
            @RequestParam(defaultValue = "") String textosFiscales,
            @RequestParam(defaultValue = "") String pieFactura,
            HttpSession session) {
        if (session.getAttribute(SESION) == null) {
            return "redirect:/secuencias";
        }
        if (!csrf.equals(session.getAttribute(CSRF))) {
            return "redirect:/secuencias/empresa?error=token";
        }
        try {
            secuencias.guardarEmisor(new DatosEmisor(
                    razonSocial, nit, direccion, telefono, textosFiscales, pieFactura));
            return "redirect:/secuencias/empresa?ok=empresa";
        } catch (IllegalArgumentException e) {
            return "redirect:/secuencias/empresa?error=empresa";
        }
    }

    @GetMapping("/secuencias/logo")
    public ResponseEntity<byte[]> verLogo(HttpSession session) throws java.io.IOException {
        if (!sesionActiva(session)) {
            return ResponseEntity.status(401).build();
        }
        byte[] bytes = logoFactura.bytes();
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logoFactura.tipo()))
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }

    @PostMapping("/secuencias/logo")
    public String subirLogo(@RequestParam String csrf,
            @RequestParam("logo") MultipartFile logo,
            HttpSession session) {
        if (session.getAttribute(SESION) == null) {
            return "redirect:/secuencias";
        }
        if (!csrf.equals(session.getAttribute(CSRF))) {
            return "redirect:/secuencias/empresa?error=token";
        }
        try {
            logoFactura.guardar(logo == null ? new byte[0] : logo.getBytes());
            return "redirect:/secuencias/empresa?ok=logo";
        } catch (IllegalArgumentException | java.io.IOException e) {
            return "redirect:/secuencias/empresa?error=logo";
        }
    }

    @GetMapping(value = "/secuencias/clave", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> clave(HttpSession session,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "ok", required = false) String ok) {
        if (!secuencias.lista()) {
            return html(sinBase());
        }
        if (!sesionActiva(session)) {
            return html(login(null));
        }
        return html(VistaPortal.clave(usuario(session), csrf(session), error, ok));
    }

    @PostMapping("/secuencias/clave")
    public String cambiarClave(@RequestParam String csrf,
            @RequestParam String claveNueva,
            HttpSession session) {
        if (session.getAttribute(SESION) == null) {
            return "redirect:/secuencias";
        }
        if (!csrf.equals(session.getAttribute(CSRF))) {
            return "redirect:/secuencias/clave?error=token";
        }
        try {
            secuencias.cambiarClave(session.getAttribute(SESION).toString(), claveNueva);
            return "redirect:/secuencias/clave?ok=clave";
        } catch (IllegalArgumentException e) {
            return "redirect:/secuencias/clave?error=clave";
        }
    }

    @PostMapping("/secuencias/reenviar")
    public String reenviar(@RequestParam String csrf,
            @RequestParam String id,
            HttpSession session) {
        String destino = "/secuencias/estado";
        String regreso = destino + "?id=" + URLEncoder.encode(id == null ? "" : id, StandardCharsets.UTF_8);
        if (session.getAttribute(SESION) == null) {
            return "redirect:/secuencias";
        }
        if (!csrf.equals(session.getAttribute(CSRF))) {
            return "redirect:" + destino + "?error=token";
        }
        Resumen factura = enviadas.resumenDe(id);
        if (factura == null) {
            return "redirect:" + destino + "?error=copia";
        }
        String payload = enviadas.payloadDe(id);
        if (payload.isBlank()) {
            payload = pendientes.payloadDeDocumento(factura.cufe(), factura.numero());
        }
        if (payload.isBlank()) {
            enviadas.marcarPorId(id, false, "No hay copia del JSON para reenviar este documento.");
            return "redirect:" + regreso + "&error=envio";
        }
        PortalEnvioService.Resultado respuesta = portal.enviar(payload);
        enviadas.marcarPorId(id, respuesta.aceptado(), respuesta.mensaje());
        if (respuesta.aceptado()) {
            pendientes.quitarDocumento(factura.cufe());
            return "redirect:" + regreso + "&ok=reenvio";
        }
        return "redirect:" + regreso + "&error=envio";
    }

    @PostMapping("/secuencias/reimprimir")
    public String reimprimir(@RequestParam String csrf,
            @RequestParam String id,
            HttpSession session) {
        String destino = "/secuencias/estado";
        if (session.getAttribute(SESION) == null) {
            return "redirect:/secuencias";
        }
        if (!csrf.equals(session.getAttribute(CSRF))) {
            return "redirect:" + destino + "?error=token";
        }
        JsonNode ticket = enviadas.ticket(id);
        if (ticket == null) {
            return "redirect:" + destino + "?error=copia";
        }
        try {
            impresion.imprimirFactura(ticket);
            return "redirect:" + destino + "?ok=reimpresion";
        } catch (RuntimeException e) {
            return "redirect:" + destino + "?error=reimpresion";
        }
    }

    @PostMapping("/secuencias/salir")
    public String salir(HttpSession session) {
        session.invalidate();
        return "redirect:/secuencias";
    }

    private String textoAviso(String ok, String error, String id) {
        if (!"reenvio".equals(ok) && !"envio".equals(error)) {
            return "";
        }
        Resumen factura = id == null ? null : enviadas.resumenDe(id);
        String numero = factura == null || factura.numero().isBlank() ? "el documento" : factura.numero();
        if ("reenvio".equals(ok)) {
            return "Se reenvió " + numero + " al portal.";
        }
        String motivo = factura == null || factura.mensajeEnvio().isBlank()
                ? "El portal no aceptó el documento."
                : factura.mensajeEnvio();
        return "No se pudo reenviar " + numero + ". " + motivo;
    }

    private static boolean sesionActiva(HttpSession session) {
        return session.getAttribute(SESION) != null;
    }

    private static String usuario(HttpSession session) {
        return session.getAttribute(SESION).toString();
    }

    private static String csrf(HttpSession session) {
        Object actual = session.getAttribute(CSRF);
        if (actual instanceof String token && !token.isBlank()) {
            return token;
        }
        String token = UUID.randomUUID().toString();
        session.setAttribute(CSRF, token);
        return token;
    }

    private static ResponseEntity<String> html(String cuerpo) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(cuerpo);
    }

    static String sinBase() {
        return VistaPortal.sinBase();
    }

    static String login(String error) {
        return VistaPortal.login(error);
    }

    static String panel(String usuario, ConfiguracionSecuencia factura, ConfiguracionSecuencia nota,
            String token, String error, String ok) {
        return VistaPortal.panel(usuario, factura, nota, token, error, ok);
    }
}
