package com.prueba.factura.Services;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class ComunicadorBase {
    private static final Logger logger = LoggerFactory.getLogger(ComunicadorBase.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired 
    private JdbcTemplate jdbcTemplate;

    public JsonNode buscarPorIdentificacion(String identificacion){
        if(identificacion == null || identificacion.isBlank()){
            return null;
        }

        String sql = "SELECT TOP 1 identificacion, nombre, apellido, email, telefono, direccion, fecha_nacimiento" + "FROM dbo.Clientes WHERE identificacion = ?";
        
        try{
            Map<String, Object> fila = jdbcTemplate.queryForMap(sql, identificacion);
            return objectMapper.convertValue(fila, JsonNode.class);
        }catch (EmptyResultDataAccessException e){
            logger.warn("Cliente no encontrado con identificacion:{}", identificacion);
            return null;
        }catch(Exception e){
            logger.error("Error consultando cliente en SQL Express:{}", e.getMessage());
            return null;
        }
    }
    
    public JsonNode buscarPorCheckId(String checkId){
        if(checkId == null || checkId.isBlank()){
            return null;
        }
        
        String sql = """
                SELECT TOP 1 
                c.identificacion, c.nombre, c.apellido, c.telefono, c.direccion, c.fecha_nacimiento
                FROM dbo.Clientes c 
                INNER JOIN dbo.Factura_Cliente fc ON c.id = fc.usuario_id
                LEFT JOIN dbo.Clientes_email ce ON c.id = ce.Cliente_id
                WHERE fc.check_id = ?
                """;

        try{
            Map<String, Object> fila = jdbcTemplate.queryForMap(sql, checkId);
            return objectMapper.convertValue(fila, JsonNode.class);
        }catch (EmptyResultDataAccessException e){
            logger.warn("Cliente no encontrado con check_id:{}", checkId);
            return null;
        }catch(Exception e){
            logger.error("Error consultando cliente en SQL Express:{}", e.getMessage());
            return null;    
        }
    }
}

