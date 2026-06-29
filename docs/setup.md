# Guía de Instalación

Esta guía cubre todo lo necesario para ejecutar localmente el servicio de
pesaje de envíos **HDD-T1-RS-GE-BH**.

## Requisitos previos

- **Java 17** (JDK)
- **Maven** — no es necesario; usa el wrapper incluido `./mvnw`
- **Docker** — para MongoDB y Redis
- **Mockoon** — para la API externa de balanzas "sansaweigh"
  ([aplicación de escritorio](https://mockoon.com/) o `@mockoon/cli`)

## Servicios y puertos

La aplicación espera que los siguientes servicios estén accesibles en
`localhost`:

| Servicio              | Puerto  | Notas                                                        |
| --------------------- | ------- | ------------------------------------------------------------ |
| Aplicación            | `8080`  | Puerto por defecto de Spring Boot; aquí se sirve la API REST.|
| MongoDB               | `27017` | Base de datos `hddt1rsgebh`, colección `RegistroPesaje`.     |
| Redis                 | `6379`  | Caché `scaleSpecs`, TTL de 120s (fijado en `RedisConfig`).   |
| API mock de sansaweigh| `3006`  | Mockoon; ruta base `/api/v1/scales`.                         |

Estos son los valores por defecto definidos en
`src/main/resources/application.properties` y `configs/RedisConfig.java`. Si
cambias un puerto, actualiza la configuración correspondiente (ver
[Configuración](#configuración)).

## 1. Iniciar MongoDB y Redis (Docker)

Ejecuta cada uno en su propio contenedor:

```bash
# MongoDB en 27017
docker run -d --name hddt1-mongo -p 27017:27017 mongo:latest

# Redis en 6379
docker run -d --name hddt1-redis -p 6379:6379 redis:latest
```

La aplicación crea automáticamente la base de datos `hddt1rsgebh` y la colección
`RegistroPesaje` en la primera escritura — no se requiere configurar el esquema
manualmente.

Para detenerlos y eliminarlos más tarde:

```bash
docker rm -f hddt1-mongo hddt1-redis
```

## 2. Iniciar la API mock de sansaweigh (Mockoon)

El servicio obtiene las especificaciones de las balanzas desde una API externa.
Localmente esto se simula con **Mockoon**, sirviendo en el **puerto 3006** con la
ruta base `/api/v1`.

Configura un entorno de Mockoon con dos rutas:

- `GET /api/v1/scales` — devuelve un arreglo JSON con todas las balanzas
  (actualmente una sola entrada).
- `GET /api/v1/scales/:id` — devuelve el objeto de balanza coincidente, o `404`
  si ninguna balanza tiene ese id.

Cada objeto de balanza coincide con el esquema `Scale` en
[`openapi.yaml`](./openapi.yaml). Cuerpo de ejemplo para `GET /api/v1/scales/7`:

```json
{
  "id": "7",
  "name": "Bascula Norte",
  "brand": "SansaWeigh",
  "max_capacity": 500.0,
  "precision": 0.01,
  "max_calibration_offset": 0.5
}
```

Inícialo desde la aplicación de escritorio de Mockoon (configura el puerto del
entorno en `3006`), o mediante la CLI sobre un archivo de entorno exportado:

```bash
npx @mockoon/cli start --data ./sansaweigh.json --port 3006
```

> **Nota sobre la resiliencia:** el cliente prioriza la API con reintentos
> (retrasos de 1s, 5s, 10s) y solo recurre a la caché de Redis cuando todos los
> intentos fallan. Si la API mock está caída *y* no hay nada en caché,
> `GET /scales/{id}` devuelve una balanza centinela con `id` `"-1"` y campos en
> cero — por lo que un mock mal configurado aparece como ese centinela en lugar
> de un error.

## 3. Ejecutar la aplicación

Con MongoDB, Redis y la API mock en ejecución:

```bash
./mvnw spring-boot:run
```

La API queda disponible en `http://localhost:8080`. Prueba rápida:

```bash
# crear un envío (peso en Kg)
curl -X POST http://localhost:8080/shipments \
  -H 'Content-Type: application/json' \
  -d '{"scale_id": "7", "package_id": "PKG-001", "weight": 12.5}'

# consultar la balanza mediante la API mock
curl http://localhost:8080/scales/7
```

Consulta [`openapi.yaml`](./openapi.yaml) para la referencia completa de la API.

## Otros comandos

```bash
./mvnw test            # ejecutar las pruebas
./mvnw clean package   # construir el jar (target/HDD-T1-RS-GE-BH-0.0.1-SNAPSHOT.jar)
```

## Configuración

Los ajustes editables están en `src/main/resources/application.properties`:

| Propiedad                                 | Valor por defecto                        |
| ----------------------------------------- | ---------------------------------------- |
| `spring.mongodb.uri`                      | `mongodb://localhost:27017/hddt1rsgebh`  |
| `sansaweigh.api.url`                      | `http://localhost:3006/api/v1/scales`    |
| `spring.jackson.property-naming-strategy` | `SNAKE_CASE`                             |

El host/puerto de Redis (`localhost:6379`) y el TTL de caché de 120s están
definidos en el código, en `configs/RedisConfig.java` — cámbialos ahí, no en el
archivo de propiedades.

## Resolución de problemas

- **La aplicación no inicia** — MongoDB y Redis deben estar accesibles antes del
  arranque. Confirma que los contenedores estén activos con `docker ps`.
- **`GET /scales/{id}` devuelve `id: "-1"`** — la API mock es inalcanzable y no
  hay nada en caché. Verifica que Mockoon esté ejecutándose en el puerto 3006.
- **Los horarios se comportan de forma inesperada en las reglas de paquetes
  pesados** — las reglas de pesaje usan la zona `America/Santiago`, no tu reloj
  local.
