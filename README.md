# ms-mascotas

Microservicio de **reportes de mascotas perdidas y encontradas** de Sanos y Salvos (Spring Boot 4, Java 17, MongoDB, S3, SNS).

## Qué hace

- Crea, edita, lista y cierra reportes (`EXTRAVIADO` / `ENCONTRADO` → `REUNIFICADO`) con foto y ubicación aproximada.
- Los reportes nacen `PENDIENTE` y un administrador los aprueba o rechaza; solo los aprobados son públicos.
- Publica eventos en SNS para que ms-alertas (alertas de zona y correos) y ms-chat (conversaciones) reaccionen.
- Expone endpoints internos (`/internal/**`) para que ms-alertas y ms-chat consulten el contacto y al dueño de un reporte.

## Endpoints (todo bajo `/api/v1`)

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /mascotas`, `GET /mascotas/{id}` | Público | Listado (filtros `estado`, `tipoMascota`, `comuna`; página máx. 200; orden solo por `fecha, nombre, comuna, estado, tipoMascota`) y detalle de reportes aprobados. El dueño ve el suyo aunque esté pendiente. |
| `GET /mascotas/presigned-url` | Sesión | URL firmada para subir una foto a S3 (máx. 5 MB, JPG/PNG/WEBP). La clave lleva el hash del dueño. 30 por hora y usuario. |
| `POST /mascotas` | Sesión | Crea un reporte. 10 por día y usuario. El correo de contacto sale del `id_token` verificado por el BFF, no del body. |
| `GET /mascotas/mis-mascotas` | Sesión | Reportes del usuario. |
| `PATCH /mascotas/{id}` | Dueño | Edita foto, texto, zona o características; si cambia lo público, el reporte vuelve a moderación. |
| `PATCH /mascotas/{id}/estado` | Dueño | Cambia el estado (`REUNIFICADO` es definitivo). |
| `DELETE /mascotas/{id}` | Dueño | Borra el reporte, su foto (si nadie más la usa) y publica `mascota.eliminada`. |
| `POST /mascotas/{id}/confirmar-reunificacion` | Público con token | Enlace del correo de contacto. El token se consume de forma atómica. |
| `GET/PATCH/DELETE /mascotas/admin/**` | Grupo `Administradores` | Moderación; cada decisión queda en el log con el `sub` del administrador. |
| `/internal/mascotas/{id}/contacto` (POST), `/chat` (GET) | `X-Internal-Token` | Solo para ms-alertas y ms-chat. |

La identidad (`sub`) sale siempre del JWT; un `X-User-Id` del cliente se ignora.

## Seguridad

- **`INTERNAL_API_TOKEN`** (mín. 32 caracteres, el mismo en BFF, ms-mascotas, ms-alertas y ms-chat): protege `/internal/**` y hace confiable el `X-User-Email` que manda el BFF. **El servicio no arranca sin él.**
- **Crear un reporte necesita el correo verificado:** el BFF manda `X-User-Email` junto con `X-Internal-Token`; sin ambos, `POST /mascotas` responde 400.
- **Fotos:** la clave es `mascotas/<hash del sub>/<uuid>-<archivo>`; solo se acepta para su dueño (copiar la de otra persona no sirve), y al borrar un reporte o reemplazar la foto se borra del bucket únicamente si ningún otro reporte la usa.
- `nombre`, `comuna` y `descripción` no admiten caracteres de control (inyección de encabezados en los correos, falsificación de líneas de log).
- Errores: las reglas propias devuelven su mensaje; una `IllegalArgumentException` de librería sale como "Solicitud inválida". Swagger está apagado salvo `SWAGGER_ENABLED=true` (solo el compose local).
- Cuotas por usuario en memoria de una sola instancia (`MASCOTAS_LIMITE_REPORTES_POR_DIA`, `MASCOTAS_LIMITE_SUBIDAS_POR_HORA`); lo que se sube y no se asocia a un reporte queda en S3 (falta una regla de ciclo de vida en el bucket).

## Eventos (SNS `sanos-eventos`, atributo `tipoEvento`)

| Evento | Cuándo | Quién lo consume |
|---|---|---|
| `mascota.reportada` | Al **aprobar** el reporte (no al crearlo), con la ubicación exacta | ms-alertas: crea la alerta de zona |
| `mascota.retirada` | Se rechaza, o vuelve a moderación por una edición pública | ms-alertas: desactiva la alerta (si se vuelve a aprobar, se recrea) |
| `mascota.reunificada` | El reporte se cierra | ms-alertas: desactiva la alerta y avisa por correo |
| `mascota.eliminada` | Se borra el reporte | ms-alertas (alertas) y ms-chat (conversaciones y correos) |

Si SNS falla, el cambio ya guardado no se pierde: el evento queda en la colección `eventos_pendientes` y `OutboxEventos` lo reintenta cada 30 s con espera creciente (hasta 1 hora; tras 50 intentos queda marcado `descartado` para revisión manual). Es una bandeja de reintentos, no un outbox transaccional: no cubre un reinicio justo entre el guardado y el primer intento de publicar.

## Variables de entorno

La configuración vive en el Config Server (`config/ms-mascotas.yml`).

| Variable | Descripción |
|---|---|
| `MONGODB_URI` | Base de datos (Atlas en AWS). |
| `INTERNAL_API_TOKEN` | Secreto de servicio, obligatorio. |
| `COGNITO_ISSUER_URI`, `COGNITO_JWK_SET_URI`, `COGNITO_CLIENT_ID` | Validación del JWT. |
| `AWS_S3_BUCKET_NAME`, `AWS_S3_REGION`, `AWS_REGION`, `AWS_SNS_TOPIC_ARN` | Fotos y eventos. `AWS_ENDPOINT_OVERRIDE` apunta a LocalStack en local. |
| `SWAGGER_ENABLED` | `true` solo en local. |

## Pruebas

```bash
./mvnw test
```

168 pruebas, con la cadena de seguridad real (`MascotaControllerTest`), validación de DTOs, reglas de moderación, fotos, bandeja de reintentos y cuotas. `MascotaMongoTest` prueba contra un Mongo real (`localhost:27017`, base `sanos_mascotas_test`; se omite si no hay uno) la confirmación de reunificación con dos clics simultáneos y la consulta de fotos compartidas.

En local: `cp .env.example .env`, completar los valores y `docker compose up --build` (requiere la red `sanos-y-salvos-network`, el Config Server y LocalStack de ms-alertas).
