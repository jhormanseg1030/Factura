package com.prueba.factura.controller;

import java.util.List;
import java.util.Locale;

import com.prueba.factura.Services.FacturaEnviadaService.Resumen;
import com.prueba.factura.Services.SecuenciaService.ConfiguracionSecuencia;
import com.prueba.factura.Services.SecuenciaService.DatosEmisor;

final class VistaPortal {

    private VistaPortal() {
    }

    static String login(String error) {
        String aviso = "acceso".equals(error)
                ? "<p class=\"aviso\">Usuario o contraseña incorrectos.</p>"
                : "";
        String cuerpo = """
                <main class="ingreso">
                  <section class="tarjeta">
                    <p class="marca">Zona K</p>
                    <h1>Ingreso</h1>
                    <p class="ayuda">Facturación y secuencias del punto de venta.</p>
                    %s
                    <form method="post" action="/secuencias/entrar">
                      <label>Usuario<input name="usuario" autocomplete="username" required></label>
                      <label>Contraseña<input name="clave" type="password" autocomplete="current-password" required></label>
                      <button type="submit">Entrar</button>
                    </form>
                  </section>
                </main>
                """.formatted(aviso);
        return pagina("Ingreso", cuerpo);
    }

    static String sinBase() {
        String cuerpo = """
                <main class="ingreso">
                  <section class="tarjeta">
                    <h1>Secuencias de facturación</h1>
                    <p class="aviso">No se pudo conectar a SQL Server. El servicio se queda encendido, pero las secuencias no están disponibles.</p>
                    <p>Ejecuta instalar.bat, indica la instancia, el usuario y la contraseña, y vuelve a iniciar el servicio.</p>
                  </section>
                </main>
                """;
        return pagina("Secuencias", cuerpo);
    }

    static String panel(String usuario, ConfiguracionSecuencia factura, ConfiguracionSecuencia nota,
            String token, String error, String ok) {
        String aviso = switch (error == null ? "" : error) {
            case "datos" -> "<p class=\"aviso\">Revise el prefijo y que el próximo número sea 1 o mayor.</p>";
            case "token" -> "<p class=\"aviso\">Vuelva a enviar el formulario.</p>";
            default -> "";
        };
        String hecho = switch (ok == null ? "" : ok) {
            case "secuencia" -> "<p class=\"ok\">Secuencias guardadas.</p>";
            default -> "";
        };
        String cuerpo = """
                <h1>Secuencias</h1>
                <p class="ayuda">El próximo número es el que tomará la siguiente factura o nota crédito.</p>
                %s
                %s
                <section class="tarjeta">
                  <form method="post" action="/secuencias/guardar">
                    <input type="hidden" name="csrf" value="%s">
                    <div class="rejilla">
                      <div>
                        <h2>Facturas</h2>
                        <label>Prefijo<input name="prefijoFactura" value="%s" maxlength="10"></label>
                        <label>Próximo número<input name="proximoFactura" type="number" min="1" value="%d" required></label>
                      </div>
                      <div>
                        <h2>Notas crédito</h2>
                        <label>Prefijo<input name="prefijoNota" value="%s" maxlength="10"></label>
                        <label>Próximo número<input name="proximoNota" type="number" min="1" value="%d" required></label>
                      </div>
                    </div>
                    <button type="submit">Guardar secuencias</button>
                  </form>
                </section>
                """.formatted(
                aviso, hecho, escapar(token),
                escapar(factura.prefijo()), factura.proximoNumero(),
                escapar(nota.prefijo()), nota.proximoNumero());
        return marco(usuario, "secuencias", token, cuerpo);
    }

    static String clave(String usuario, String token, String error, String ok) {
        String aviso = switch (error == null ? "" : error) {
            case "clave" -> "<p class=\"aviso\">La contraseña nueva debe tener al menos 4 caracteres.</p>";
            case "token" -> "<p class=\"aviso\">Vuelva a enviar el formulario.</p>";
            default -> "";
        };
        String hecho = "clave".equals(ok) ? "<p class=\"ok\">Contraseña actualizada.</p>" : "";
        String cuerpo = """
                <h1>Contraseña</h1>
                <p class="ayuda">Cambia la clave con la que entras a este sitio.</p>
                %s
                %s
                <section class="tarjeta">
                  <form method="post" action="/secuencias/clave">
                    <input type="hidden" name="csrf" value="%s">
                    <label>Nueva contraseña<input name="claveNueva" type="password" autocomplete="new-password" required></label>
                    <button type="submit">Cambiar contraseña</button>
                  </form>
                </section>
                """.formatted(aviso, hecho, escapar(token));
        return marco(usuario, "clave", token, cuerpo);
    }

    static String empresa(String usuario, DatosEmisor emisor, boolean hayLogo, String token, String error, String ok) {
        String aviso = switch (error == null ? "" : error) {
            case "empresa" -> "<p class=\"aviso\">Revise la empresa. El NIT solo lleva dígitos, sin dígito de verificación, y el teléfono solo números, espacios, + y guiones.</p>";
            case "logo" -> "<p class=\"aviso\">El logo debe ser un PNG o JPG de hasta 2 MB.</p>";
            case "token" -> "<p class=\"aviso\">Vuelva a enviar el formulario.</p>";
            default -> "";
        };
        String hecho = switch (ok == null ? "" : ok) {
            case "empresa" -> "<p class=\"ok\">Datos de la empresa guardados.</p>";
            case "logo" -> "<p class=\"ok\">Logo guardado. La próxima factura local lo imprime.</p>";
            default -> "";
        };
        DatosEmisor datos = emisor == null ? DatosEmisor.predeterminado() : emisor;
        String vistaLogo = hayLogo
                ? "<img class=\"logo\" src=\"/secuencias/logo\" alt=\"Logo actual\">"
                : "<p class=\"ayuda\">Todavía no hay un logo cargado.</p>";
        String cuerpo = """
                <h1>Empresa</h1>
                <p class="ayuda">Datos del emisor y logo de la factura local.</p>
                %s
                %s
                <section class="tarjeta">
                  <h2>Empresa emisora</h2>
                  <p class="ayuda">Salen en el encabezado de la factura y en el JSON. El NIT va sin dígito de verificación: así entra en el CUFE. Si deja un dato vacío, se usa el que traiga la caja.</p>
                  <form method="post" action="/secuencias/empresa">
                    <input type="hidden" name="csrf" value="%s">
                    <div class="rejilla">
                      <label>Razón social<input name="razonSocial" value="%s" maxlength="200"></label>
                      <label>NIT<input name="nit" value="%s" maxlength="15" inputmode="numeric"></label>
                      <label>Dirección<input name="direccion" value="%s" maxlength="300"></label>
                      <label>Teléfono<input name="telefono" value="%s" maxlength="40"></label>
                    </div>
                    <label>Textos fiscales
                      <textarea name="textosFiscales" maxlength="2000">%s</textarea>
                    </label>
                    <p class="ayuda">Una línea por texto. Se imprimen bajo el teléfono, en el encabezado.</p>
                    <label>Pie de factura
                      <textarea name="pieFactura" maxlength="2000">%s</textarea>
                    </label>
                    <p class="ayuda">Una línea por texto. Se imprime al cierre del ticket, antes del código QR.</p>
                    <button type="submit">Guardar empresa</button>
                  </form>
                </section>
                <section class="tarjeta">
                  <h2>Logo de la factura</h2>
                  <p class="ayuda">Se imprime al inicio de la factura local. PNG o JPG, hasta 2 MB.</p>
                  %s
                  <form method="post" action="/secuencias/logo" enctype="multipart/form-data">
                    <input type="hidden" name="csrf" value="%s">
                    <label>Imagen<input name="logo" type="file" accept="image/png,image/jpeg,.png,.jpg,.jpeg" required></label>
                    <button type="submit">Subir logo</button>
                  </form>
                </section>
                """.formatted(
                aviso, hecho, escapar(token),
                escapar(datos.razonSocial()), escapar(datos.nit()),
                escapar(datos.direccion()), escapar(datos.telefono()),
                escapar(datos.textosFiscales()), escapar(datos.pieFactura()),
                vistaLogo, escapar(token));
        return marco(usuario, "empresa", token, cuerpo);
    }

    static String estado(String usuario, String token, String ok, String error, String detalle, List<Resumen> filas) {
        String aviso = avisoFacturas(ok, error, detalle);
        String cuerpo = """
                <h1>Estado de facturas</h1>
                <p class="ayuda">Documentos generados en la caja. El portal se consulta de una factura en una y esta pantalla se actualiza cada 30 segundos.</p>
                {{aviso}}
                {{tabla}}
                <p class="ayuda" id="pulso">Actualizando el estado…</p>
                """.replace("{{aviso}}", aviso).replace("{{tabla}}", tabla(filas, token, "estado"));
        return marco(usuario, "estado", token, cuerpo);
    }

    private static String avisoFacturas(String ok, String error, String detalle) {
        if (detalle != null && !detalle.isBlank()) {
            String clase = "reenvio".equals(ok) ? "ok" : "aviso";
            return "<p class=\"" + clase + "\">" + escapar(detalle) + "</p>";
        }
        String hecho = switch (ok == null ? "" : ok) {
            case "reimpresion" -> "<p class=\"ok\">Se envió la reimpresión a la impresora.</p>";
            default -> "";
        };
        String aviso = switch (error == null ? "" : error) {
            case "reimpresion" -> "<p class=\"aviso\">No se pudo imprimir. Revise que la impresora esté encendida.</p>";
            case "copia" -> "<p class=\"aviso\">Esa factura no tiene copia para reimprimir.</p>";
            case "token" -> "<p class=\"aviso\">Vuelva a enviar el formulario.</p>";
            default -> "";
        };
        return hecho + aviso;
    }

    private static String tabla(List<Resumen> filas, String token, String origen) {
        StringBuilder cuerpo = new StringBuilder();
        if (filas != null) {
        for (Resumen fila : filas) {
            cuerpo.append("<tr>");
            celda(cuerpo, fila.numero());
            celda(cuerpo, fechaCorta(fila.fecha()));
            celda(cuerpo, fila.cliente());
            celda(cuerpo, fila.cheque());
            celda(cuerpo, pesos(fila.total()));
            celda(cuerpo, fila.tipo());
            cuerpo.append("<td>").append(badge(fila.estadoEnvio(), etiquetaEnvio(fila.estadoEnvio())));
            if (mostrarMensaje(fila)) {
                cuerpo.append("<div class=\"detalle\">").append(escapar(fila.mensajeEnvio())).append("</div>");
            }
            cuerpo.append("</td>");
            cuerpo.append("<td>").append(badge(fila.estadoDian(), etiquetaDian(fila.estadoDian())));
            if (!fila.detalleDian().isBlank()) {
                cuerpo.append("<div class=\"detalle\">").append(escapar(fila.detalleDian())).append("</div>");
            }
            cuerpo.append("</td>");
            cuerpo.append("<td class=\"acciones\">");
            cuerpo.append(boton(token, fila.id(), origen, "/secuencias/reenviar", "Reenviar", "secundario"));
            if (fila.reimprimible()) {
                cuerpo.append(boton(token, fila.id(), origen, "/secuencias/reimprimir", "Reimprimir", ""));
            }
            cuerpo.append("</td></tr>");
        }
        }
        String vacio = filas == null || filas.isEmpty()
                ? "<p>Todavía no hay facturas enviadas. Las que salgan desde la caja aparecerán aquí.</p>"
                : "";
        return """
                <section class="tarjeta tabla">
                  {{vacio}}
                  <table>
                    <thead>
                      <tr>
                        <th>Número</th><th>Fecha</th><th>Cliente</th><th>Cheque</th>
                        <th>Total</th><th>Tipo</th><th>Envío</th><th>DIAN</th><th></th>
                      </tr>
                    </thead>
                    <tbody id="facturas">{{filas}}</tbody>
                  </table>
                </section>
                """.replace("{{vacio}}", vacio).replace("{{filas}}", cuerpo);
    }

    private static boolean mostrarMensaje(Resumen fila) {
        if (fila.mensajeEnvio() == null || fila.mensajeEnvio().isBlank()) {
            return false;
        }
        return "error".equals(fila.estadoEnvio()) || "pendiente".equals(fila.estadoEnvio());
    }

    private static String boton(String token, String id, String origen, String accion, String texto, String clase) {
        String estilo = clase == null || clase.isBlank() ? "" : " class=\"" + clase + "\"";
        return """
                <form method="post" action="{{accion}}">
                  <input type="hidden" name="csrf" value="{{csrf}}">
                  <input type="hidden" name="id" value="{{id}}">
                  <input type="hidden" name="origen" value="{{origen}}">
                  <button type="submit"{{estilo}}>{{texto}}</button>
                </form>
                """
                .replace("{{accion}}", accion)
                .replace("{{csrf}}", escapar(token))
                .replace("{{id}}", escapar(id))
                .replace("{{origen}}", escapar(origen))
                .replace("{{estilo}}", estilo)
                .replace("{{texto}}", escapar(texto));
    }

    private static void celda(StringBuilder cuerpo, String texto) {
        cuerpo.append("<td>").append(escapar(texto)).append("</td>");
    }

    static String clase(String estado) {
        return switch (estado == null ? "" : estado) {
            case "enviada", "aprobada" -> "ok";
            case "rechazada", "error", "no_encontrado" -> "mal";
            default -> "espera";
        };
    }

    static String badge(String estado, String etiqueta) {
        return "<span class=\"badge " + clase(estado) + "\">" + escapar(etiqueta) + "</span>";
    }

    static String etiquetaEnvio(String estado) {
        return switch (estado == null ? "" : estado) {
            case "enviada" -> "Enviada";
            case "error" -> "Error de envío";
            default -> "En cola";
        };
    }

    static String etiquetaDian(String estado) {
        return switch (estado == null ? "" : estado) {
            case "aprobada" -> "Aprobada";
            case "rechazada" -> "Rechazada";
            case "no_encontrado" -> "No encontrada";
            case "error" -> "Sin respuesta";
            default -> "Pendiente";
        };
    }

    static String fechaCorta(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return "";
        }
        String texto = fecha.replace('T', ' ');
        return texto.length() >= 19 ? texto.substring(0, 19) : texto;
    }

    static String pesos(long valor) {
        return String.format(Locale.GERMANY, "%,d", valor);
    }

    private static String marco(String usuario, String activo, String token, String cuerpo) {
        String pagina = """
                <div class="app">
                  <aside>
                    <p class="marca">Zona K</p>
                    <p class="sesion">{{usuario}}</p>
                    <nav>
                      <a class="{{secuencias}}" href="/secuencias">Secuencias</a>
                      <a class="{{empresa}}" href="/secuencias/empresa">Empresa</a>
                      <a class="{{estado}}" href="/secuencias/estado">Estado de facturas</a>
                      <a class="{{clave}}" href="/secuencias/clave">Contraseña</a>
                    </nav>
                    <form method="post" action="/secuencias/salir">
                      <button type="submit" class="salir">Salir</button>
                    </form>
                  </aside>
                  <main>
                    {{cuerpo}}
                  </main>
                </div>
                """
                .replace("{{usuario}}", escapar(usuario))
                .replace("{{secuencias}}", "secuencias".equals(activo) ? "activo" : "")
                .replace("{{empresa}}", "empresa".equals(activo) ? "activo" : "")
                .replace("{{estado}}", "estado".equals(activo) ? "activo" : "")
                .replace("{{clave}}", "clave".equals(activo) ? "activo" : "")
                .replace("{{cuerpo}}", cuerpo);
        String script = "estado".equals(activo) ? scriptEstado(token) : "";
        return pagina("Facturación", pagina + script);
    }

    private static String scriptEstado(String token) {
        return """
                <meta name="csrf" content="{{csrf}}">
                <script>
                const csrf = document.querySelector('meta[name=csrf]').content;
                function badge(clase, texto) {
                  const s = document.createElement('span');
                  s.className = 'badge ' + clase;
                  s.textContent = texto;
                  return s;
                }
                function celda(texto) {
                  const td = document.createElement('td');
                  td.textContent = texto == null ? '' : texto;
                  return td;
                }
                function fila(f) {
                  const tr = document.createElement('tr');
                  tr.append(celda(f.numero), celda(f.fecha), celda(f.cliente), celda(f.cheque), celda(f.total), celda(f.tipo));
                  const envio = document.createElement('td');
                  envio.append(badge(f.claseEnvio, f.etiquetaEnvio));
                  if (f.mensaje) {
                    const m = document.createElement('div');
                    m.className = 'detalle';
                    m.textContent = f.mensaje;
                    envio.append(m);
                  }
                  tr.append(envio);
                  const dian = document.createElement('td');
                  dian.append(badge(f.claseDian, f.etiquetaDian));
                  if (f.detalle) {
                    const d = document.createElement('div');
                    d.className = 'detalle';
                    d.textContent = f.detalle;
                    dian.append(d);
                  }
                  tr.append(dian);
                  const acc = document.createElement('td');
                  acc.className = 'acciones';
                  function oculto(nombre, valor) {
                    const input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = nombre;
                    input.value = valor;
                    return input;
                  }
                  function formulario(accion, texto, clase) {
                    const form = document.createElement('form');
                    form.method = 'post';
                    form.action = accion;
                    const boton = document.createElement('button');
                    boton.type = 'submit';
                    boton.textContent = texto;
                    if (clase) boton.className = clase;
                    form.append(oculto('csrf', csrf), oculto('id', f.id), oculto('origen', 'estado'), boton);
                    return form;
                  }
                  acc.append(formulario('/secuencias/reenviar', 'Reenviar', 'secundario'));
                  if (f.reimprimible) {
                    acc.append(formulario('/secuencias/reimprimir', 'Reimprimir', ''));
                  }
                  tr.append(acc);
                  return tr;
                }
                async function refrescar() {
                  const pulso = document.getElementById('pulso');
                  try {
                    const respuesta = await fetch('/secuencias/estado/datos', { headers: { 'Accept': 'application/json' } });
                    if (respuesta.status === 401) {
                      location.href = '/secuencias';
                      return;
                    }
                    const datos = await respuesta.json();
                    const cuerpo = document.getElementById('facturas');
                    if (!cuerpo) return;
                    cuerpo.replaceChildren();
                    (datos.facturas || []).forEach(f => cuerpo.append(fila(f)));
                    if (pulso) pulso.textContent = 'Lista actualizada.';
                  } catch (e) {
                    if (pulso) pulso.textContent = 'No se pudo actualizar el estado.';
                  }
                }
                refrescar();
                setInterval(refrescar, 30000);
                </script>
                """.replace("{{csrf}}", escapar(token));
    }

    private static String pagina(String titulo, String cuerpo) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>{{titulo}}</title>
                  <style>
                    :root { color-scheme: light; }
                    * { box-sizing: border-box; }
                    body { margin: 0; font-family: "Segoe UI", sans-serif; color: #142033; background: #eef2f6; }
                    h1, h2 { font-weight: 650; margin: 0 0 0.4rem; }
                    h1 { font-size: 1.6rem; }
                    h2 { font-size: 1.05rem; }
                    p { line-height: 1.45; }
                    a { color: inherit; text-decoration: none; }
                    button { border: 0; border-radius: 8px; padding: 0.55rem 0.9rem; background: #0f6e56; color: white; cursor: pointer; }
                    button.secundario { background: #e7eef6; color: #102033; }
                    .acciones form { margin-bottom: 0.35rem; }
                    button.salir { background: transparent; color: #d7e0ea; padding-left: 0; }
                    input, textarea { display: block; width: 100%; margin-top: 0.35rem; padding: 0.55rem 0.65rem; border: 1px solid #d5dee8; border-radius: 8px; font: inherit; }
                    textarea { min-height: 9rem; resize: vertical; }
                    label { display: block; margin: 0.8rem 0; font-size: 0.92rem; }
                    .app { display: flex; min-height: 100vh; }
                    aside { width: 240px; background: #102033; color: #e8eef5; padding: 1.4rem 1rem; display: flex; flex-direction: column; }
                    aside nav { display: flex; flex-direction: column; gap: 0.35rem; margin-top: 1.4rem; }
                    aside a { padding: 0.65rem 0.75rem; border-radius: 8px; color: #d5e0ec; }
                    aside a.activo, aside a:hover { background: #1c3b5a; color: white; }
                    .marca { letter-spacing: 0.08em; text-transform: uppercase; font-size: 0.78rem; color: #8fb8d8; margin: 0; }
                    .sesion { margin: 0.35rem 0 0; color: white; }
                    main { flex: 1; padding: 2rem; }
                    .ingreso { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 1.5rem; }
                    .tarjeta { background: white; border-radius: 16px; padding: 1.25rem 1.35rem; box-shadow: 0 10px 30px rgba(16, 32, 51, 0.06); margin-bottom: 1rem; }
                    .ingreso .tarjeta { width: min(420px, 100%); }
                    .logo { display: block; max-width: 240px; max-height: 140px; margin: 0.4rem 0 1rem; background: #f4f7fb; }
                    .ayuda { color: #5c6b7a; }
                    .aviso { color: #8a1f1f; }
                    .ok { color: #1f6b3a; }
                    .rejilla { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
                    table { width: 100%; border-collapse: collapse; }
                    th, td { text-align: left; padding: 0.7rem 0.45rem; border-bottom: 1px solid #e6edf3; vertical-align: top; font-size: 0.92rem; }
                    th { color: #5c6b7a; font-weight: 600; }
                    .badge { display: inline-block; border-radius: 999px; padding: 0.15rem 0.55rem; font-size: 0.78rem; }
                    .badge.ok { background: #e5f6ee; color: #146c43; }
                    .badge.espera { background: #fff4d8; color: #8a5a00; }
                    .badge.mal { background: #fde8e8; color: #8a1f1f; }
                    .detalle { color: #5c6b7a; font-size: 0.78rem; margin-top: 0.25rem; }
                    form { margin: 0; }
                    @media (max-width: 800px) {
                      .app { display: block; }
                      aside { width: auto; }
                      .rejilla { grid-template-columns: 1fr; }
                      .tabla { overflow-x: auto; }
                    }
                  </style>
                </head>
                <body>
                {{cuerpo}}
                </body>
                </html>
                """.replace("{{titulo}}", titulo).replace("{{cuerpo}}", cuerpo);
    }

    static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
