# Users Management — Spring Boot, Arquitectura Hexagonal y DDD

Aplicación de gestión de usuarios construida con Java 17 y Spring Boot. La API REST es el punto de entrada activo. El código de la antigua CLI se conserva como adaptador inactivo y no posee un contenedor de dependencias independiente.

Spring es el único *composition root*: `Main` inicia el contexto y las dependencias se resuelven mediante configuración y component scanning de Spring.

## Verificación

```bash
./mvnw clean test
./mvnw clean package
```

En Windows se puede utilizar `mvnw.cmd`.

## Secretos para desarrollo local

Copiar `application-secrets.properties.example` como
`.environments/application-secrets.properties` y reemplazar los valores de ejemplo.
Este archivo contiene la conexion local a MySQL, la semilla JWT y las credenciales de
Brevo. La carpeta `.environments` esta excluida de Git y del contexto de Docker.

En produccion no se utiliza este archivo: los secretos deben configurarse mediante las
variables de entorno `DB_*`, `JWT_SECRET` y `BREVO_*` del proveedor de despliegue.

## Docker Compose local

Copiar `compose.env.example` como `.env`, reemplazar todos los valores y ejecutar:

```bash
docker compose up --build
```

Compose se detiene con un error si falta `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`,
`JWT_SECRET`, `BREVO_API_KEY` o `BREVO_FROM_ADDRESS`. El archivo `.env` esta excluido
de Git y del contexto de construccion Docker. Los puertos de MySQL y la API se publican
solo en `127.0.0.1`.

## Despliegue en Render

El archivo `render.yaml` define el servicio web, el build con Docker y el despliegue
automático de cada commit que llegue a la rama `main`.

1. Crear un Blueprint en Render y seleccionar este repositorio.
2. Completar en el panel los secretos marcados como requeridos: `DB_HOST`,
   `DB_USERNAME`, `DB_PASSWORD`, `BREVO_API_KEY` y `BREVO_FROM_ADDRESS`.
3. Usar una instancia MySQL accesible desde Internet o desde la red privada de
   Render y ejecutar `src/main/resources/schema.sql` una vez para crear el esquema.
4. Desplegar el Blueprint. La API quedará disponible en el subdominio
   `onrender.com` asignado por Render y Swagger UI en `/swagger-ui.html`.

Las credenciales nunca deben guardarse en `application.properties` ni en
`render.yaml`. Para desarrollo local, deben proporcionarse como variables de entorno.

## Notificaciones por correo

Los correos transaccionales se envian con `POST /v3/smtp/email` de Brevo. La llamada
se ejecuta fuera del hilo HTTP en el executor `notificationExecutor` y dispone de
timeouts, reintentos para errores de red/408/429/5xx con backoff exponencial, circuit
breaker e idempotencia para evitar duplicados durante los reintentos.

Variables requeridas: `BREVO_API_KEY` y `BREVO_FROM_ADDRESS`. El remitente debe estar
verificado en Brevo. Los parametros de resiliencia `BREVO_CONNECT_TIMEOUT`,
`BREVO_READ_TIMEOUT`, `BREVO_RETRY_*` y `BREVO_CIRCUIT_*` son opcionales y tienen
valores predeterminados en `application.properties`.
