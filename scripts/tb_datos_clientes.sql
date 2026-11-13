USE [gestion_clientes]
GO

/****** Objeto: Table [dbo].[clientes] Fecha de script: 26/10/2026 2:00:01 p. m. ******/
SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[clientes](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[num_cliente] [varchar](10) NULL,
	[tipo_id] [varchar](10) NOT NULL,
	[identificacion] [varchar](20) NOT NULL,
	[nombre] [varchar](100) NOT NULL,
	[apellido] [varchar](100) NOT NULL,
	[direccion] [varchar](255) NULL,
	[telefono] [varchar](20) NULL,
	[pais] [varchar](50) NULL,
	[fecha_desde] [date] NULL,
	[fecha_nacimiento] [date] NULL,
	[estado] [varchar](20) NOT NULL,
	[check_id] [varchar](32) NULL,
	[created_at] [datetime] NULL,
	[updated_at] [datetime] NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
UNIQUE NONCLUSTERED 
(
	[num_cliente] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
UNIQUE NONCLUSTERED 
(
	[identificacion] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO

ALTER TABLE [dbo].[clientes] ADD  DEFAULT ('activo') FOR [estado]
GO

ALTER TABLE [dbo].[clientes] ADD  DEFAULT (getdate()) FOR [created_at]
GO

ALTER TABLE [dbo].[clientes] ADD  DEFAULT (getdate()) FOR [updated_at]
GO

ALTER TABLE [dbo].[clientes]  WITH CHECK ADD  CONSTRAINT [chk_identificacion_len] CHECK  ((len([identificacion])>=(3) AND len([identificacion])<=(20)))
GO

ALTER TABLE [dbo].[clientes] CHECK CONSTRAINT [chk_identificacion_len]
GO

ALTER TABLE [dbo].[clientes]  WITH CHECK ADD CHECK  (([estado]='suspendido' OR [estado]='activo'))
GO

ALTER TABLE [dbo].[clientes]  WITH CHECK ADD CHECK  (([tipo_id]='Otro' OR [tipo_id]='PPT' OR [tipo_id]='PEP' OR [tipo_id]='NIT' OR [tipo_id]='PA' OR [tipo_id]='TI' OR [tipo_id]='CE' OR [tipo_id]='CC'))
GO

