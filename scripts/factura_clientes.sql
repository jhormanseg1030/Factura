USE [gestion_clientes]
GO

/****** Objeto: Table [dbo].[factura_cliente] Fecha de script: 26/10/2026 2:00:14 p. m. ******/
SET ANSI_NULLS ON
GO

SET QUOTED_IDENTIFIER ON
GO

CREATE TABLE [dbo].[factura_cliente](
	[id] [int] IDENTITY(1,1) NOT NULL,
	[check_id] [varchar](50) NOT NULL,
	[usuario_id] [int] NOT NULL,
	[fecha_vinculacion] [datetime] NULL,
	[numero_factura] [varchar](50) NULL,
	[total_factura] [decimal](18,2) NULL,
PRIMARY KEY CLUSTERED 
(
	[id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
UNIQUE NONCLUSTERED 
(
	[check_id] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO

ALTER TABLE [dbo].[factura_cliente] ADD  DEFAULT (getdate()) FOR [fecha_vinculacion]
GO

ALTER TABLE [dbo].[factura_cliente]  WITH CHECK ADD FOREIGN KEY([usuario_id])
REFERENCES [dbo].[clientes] ([id])
ON DELETE CASCADE
GO