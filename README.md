# ms-mascotas

Microservicio para gestión de mascotas con enfoque en registro de animales perdidos/encontrados, validación por `X-User-Id` y almacenamiento flexible de características en MongoDB.

## Objetivo

El servicio permite registrar mascotas, mantener su estado y consultar mascotas con filtros opcionales según atributos dinámicos descritos por el usuario.

## Arquitectura actual

- Spring Boot 3 / Java 17
- MongoDB
- Spring Data MongoDB
- Documentos flexibles para campos dinámicos
- Jackson para parseo de texto/JSON a `Map<String, Object>`

## Modelo principal

La entidad `Mascota` representa una mascota con un identificador propio `idMascota` y un `usuarioId` asociado al usuario autenticado.

### Campos principales

- `idMascota`: identificador de la mascota
- `usuarioId`: ID del dueño o usuario que registra la mascota
- `tipoMascota`: perro, gato, conejo, otro
- `nombre`: nombre opcional
- `color`: color principal
- `fotografia`: URL o referencia de foto
- `estado`: estado activo de la mascota
- `ubicacion`: ubicación textual o geográfica
- `fecha`: fecha de registro
- `descripcion`: descripción general
- `caracteristicas`: `Map<String, Object>` almacenado como JSONB

## Características dinámicas

La parte flexible se guarda en `caracteristicas`, por ejemplo:

```json
{
  "manchas": "blancas",
  "cola": "corta",
  "cicatriz": false,
  "pelo": "largo",
  "edad": 3,
  "raza": "mestizo"
}
```

Esto permite manejar atributos que no siempre son conocidos de antemano, sin crear columnas nuevas para cada propiedad.

## Jackson y parseo de atributos

Cuando llega una descripción o un bloque JSON, se usa Jackson para convertirlo a `Map<String, Object>` antes de persistirlo en MongoDB.

Ejemplo conceptual:

```java
Map<String, Object> mapa = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
```

## API

### Base path

```http
/mascotas
```

### Endpoints principales

- `POST /mascotas`
- `GET /mascotas`
- `GET /mascotas/{idMascota}`
- `GET /mascotas/mis-mascotas`
- `PATCH /mascotas/{idMascota}`
- `PATCH /mascotas/{idMascota}/estado`
- `DELETE /mascotas/{idMascota}`

### Header requerido

Toda operación sensible valida el usuario mediante el header:

```http
X-User-Id
```

## Filtros

El listado principal permite filtrar por varios parámetros opcionales:

```http
GET /mascotas?estado=EXTRAVIADO&tipoMascota=PERRO&color=negro
```

## MongoDB

La propiedad `caracteristicas` se guarda como un objeto embebido dentro del documento, permitiendo atributos dinámicos sin una estructura rígida.

La propiedad `ubicacion` se almacena como un punto GeoJSON usando `GeoJsonPoint`, con coordenadas en el orden MongoDB: longitud y latitud.

## Reglas de negocio

- Se elimina la `Factory Method` y las subclases redundantes.
- La mascota tiene una sola entidad común.
- La validación de propiedad se hace con `usuarioId` y el header `X-User-Id`.
- El identificador público del recurso se usa como `idMascota`.

## Dependencias relevantes

- `spring-boot-starter-data-mongodb`
- `spring-boot-starter-webmvc`
- Jackson incluido en Spring Boot

## Configuración recomendada

```properties
spring.data.mongodb.uri=mongodb://localhost:27017/mascotas_db
```

Para usar MongoDB Atlas, configurar la URI mediante la variable de entorno `MONGODB_URI`:

```properties
spring.data.mongodb.uri=${MONGODB_URI}
```

Consulta el detalle de la migración en [REFACTOR-MONGODB.md](REFACTOR-MONGODB.md).

## Nota

El proyecto está adaptado para un enfoque de datos dinámicos y flexibles, ideal para rasgos descriptivos de mascotas que pueden cambiar según el caso o la experiencia del usuario.


./mvnw.cmd -DskipTests package