package com.prueba.factura.Services;

import org.springframework.jdbc.core.JdbcTemplate;

final class EsquemaFactura {

    private EsquemaFactura() {
    }

    static void crear(JdbcTemplate jdbc) {
        jdbc.execute("""
                IF OBJECT_ID('dbo.FacturaEmitida', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.FacturaEmitida (
                        id varchar(200) NOT NULL PRIMARY KEY,
                        numero varchar(40) NOT NULL,
                        prefijo varchar(20) NOT NULL,
                        consecutivo bigint NOT NULL,
                        cufe varchar(128) NOT NULL,
                        fecha varchar(40) NOT NULL,
                        cliente nvarchar(200) NOT NULL,
                        tipo varchar(10) NOT NULL,
                        total bigint NOT NULL,
                        cheque varchar(40) NOT NULL,
                        ticket nvarchar(max) NULL,
                        payload nvarchar(max) NULL,
                        estado_envio varchar(20) NOT NULL,
                        mensaje_envio nvarchar(400) NOT NULL,
                        estado_dian varchar(20) NOT NULL,
                        detalle_dian nvarchar(400) NOT NULL,
                        consultado_en varchar(40) NOT NULL,
                        intentos int NOT NULL CONSTRAINT DF_FacturaEmitida_intentos DEFAULT 0,
                        fallo_portal bit NOT NULL CONSTRAINT DF_FacturaEmitida_fallo DEFAULT 0,
                        creado_en datetime2 NOT NULL CONSTRAINT DF_FacturaEmitida_creado DEFAULT SYSUTCDATETIME()
                    )
                END
                """);
        jdbc.execute("""
                IF NOT EXISTS (
                    SELECT 1 FROM sys.indexes
                    WHERE name = 'IX_FacturaEmitida_numero' AND object_id = OBJECT_ID('dbo.FacturaEmitida'))
                CREATE INDEX IX_FacturaEmitida_numero ON dbo.FacturaEmitida(numero)
                """);
        jdbc.execute("""
                IF OBJECT_ID('dbo.FacturaCufe', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.FacturaCufe (
                        identificador varchar(200) NOT NULL PRIMARY KEY,
                        numero varchar(40) NOT NULL,
                        cufe varchar(128) NOT NULL
                    )
                END
                """);
        jdbc.execute("""
                IF NOT EXISTS (
                    SELECT 1 FROM sys.indexes
                    WHERE name = 'IX_FacturaCufe_numero' AND object_id = OBJECT_ID('dbo.FacturaCufe'))
                CREATE INDEX IX_FacturaCufe_numero ON dbo.FacturaCufe(numero)
                """);
        jdbc.execute("""
                IF OBJECT_ID('dbo.FacturaPendiente', 'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.FacturaPendiente (
                        id int IDENTITY(1,1) NOT NULL PRIMARY KEY,
                        json_payload nvarchar(max) NOT NULL,
                        fecha_procesamiento varchar(40) NOT NULL,
                        intentos int NOT NULL,
                        ultimo_error nvarchar(500) NULL,
                        ultimo_intento varchar(40) NULL
                    )
                END
                """);
    }
}
