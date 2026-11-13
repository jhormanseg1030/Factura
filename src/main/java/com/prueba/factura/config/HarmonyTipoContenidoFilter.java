package com.prueba.factura.config;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HarmonyTipoContenidoFilter extends OncePerRequestFilter {

    static final String TIPO_XML = "text/xml";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (esRutaHarmony(request.getRequestURI())) {
            chain.doFilter(new SolicitudTipoXml(request), response);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean esRutaHarmony(String uri) {
        if (uri == null) {
            return false;
        }
        String ruta = uri.toLowerCase(Locale.ROOT);
        int consulta = ruta.indexOf('?');
        if (consulta >= 0) {
            ruta = ruta.substring(0, consulta);
        }
        if (ruta.endsWith("/")) {
            ruta = ruta.substring(0, ruta.length() - 1);
        }
        return ruta.equals("/api/harmony/col") || ruta.endsWith("/api/harmony/col");
    }

    static boolean debeForzarXml(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return true;
        }
        String normalizado = tipo.toLowerCase(Locale.ROOT).strip();
        return !(normalizado.startsWith("text/xml")
                || normalizado.startsWith("application/xml")
                || normalizado.startsWith("multipart/"));
    }

    static final class SolicitudTipoXml extends HttpServletRequestWrapper {

        SolicitudTipoXml(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getContentType() {
            return tipo(super.getContentType());
        }

        @Override
        public String getParameter(String name) {
            if (delegarParametros()) {
                return super.getParameter(name);
            }
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            if (delegarParametros()) {
                return super.getParameterMap();
            }
            return Map.of();
        }

        @Override
        public Enumeration<String> getParameterNames() {
            if (delegarParametros()) {
                return super.getParameterNames();
            }
            return Collections.emptyEnumeration();
        }

        @Override
        public String[] getParameterValues(String name) {
            if (delegarParametros()) {
                return super.getParameterValues(name);
            }
            return null;
        }

        private boolean delegarParametros() {
            String tipo = super.getContentType();
            return tipo != null && tipo.toLowerCase(Locale.ROOT).contains("multipart/");
        }

        @Override
        public String getHeader(String name) {
            if (esTipoContenido(name)) {
                return tipo(super.getHeader(name));
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (esTipoContenido(name)) {
                return Collections.enumeration(List.of(tipo(primerValor(name))));
            }
            return super.getHeaders(name);
        }

        private String primerValor(String name) {
            Enumeration<String> valores = super.getHeaders(name);
            if (valores == null || !valores.hasMoreElements()) {
                return null;
            }
            return valores.nextElement();
        }

        private static String tipo(String valor) {
            if (debeForzarXml(valor)) {
                return TIPO_XML;
            }
            return valor;
        }

        private static boolean esTipoContenido(String name) {
            return "content-type".equalsIgnoreCase(name);
        }
    }
}
