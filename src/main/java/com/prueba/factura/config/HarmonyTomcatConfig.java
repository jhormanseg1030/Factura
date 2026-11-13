package com.prueba.factura.config;

import java.io.IOException;

import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;

/**
 * Harmony envía el XML con Content-Type vacío o como formulario.
 * Tomcat responde 400 si llega a interpretar ese cuerpo antes del controlador.
 */
@Component
public class HarmonyTomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addEngineValves(new HarmonyTipoContenidoValve());
    }

    static final class HarmonyTipoContenidoValve extends ValveBase {

        @Override
        public void invoke(Request request, Response response) throws IOException, ServletException {
            if ("POST".equalsIgnoreCase(request.getMethod())
                    && HarmonyTipoContenidoFilter.esRutaHarmony(request.getRequestURI())) {
                forzarXml(request.getCoyoteRequest());
            }
            Valve siguiente = getNext();
            if (siguiente != null) {
                siguiente.invoke(request, response);
            }
        }

        private static void forzarXml(org.apache.coyote.Request coyote) {
            MimeHeaders headers = coyote.getMimeHeaders();
            if (!HarmonyTipoContenidoFilter.debeForzarXml(headers.getHeader("content-type"))) {
                return;
            }
            MessageBytes valor = headers.setValue("content-type");
            valor.setString(HarmonyTipoContenidoFilter.TIPO_XML);
            coyote.setContentType(valor);
            coyote.setCharsetHolder(null);
        }
    }
}
