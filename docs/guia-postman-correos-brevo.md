# Parte 6 — Probar con Postman el envío de correos (Brevo) en Render

Guía paso a paso para comprobar, desde Postman, que la API desplegada en Render **envía los correos**
mediante la API RESTful de Brevo (rama `feature/brevo-rest-email-resilience`) y que la autenticación
JWT y los permisos por rol funcionan.

La API envía un correo en dos casos:

| Operación | Endpoint | Asunto del correo |
|---|---|---|
| Crear usuario | `POST /api/users` | Tu cuenta ha sido creada — Gestion de Usuarios |
| Actualizar usuario | `PUT /api/users/{id}` | Tu cuenta ha sido actualizada — Gestion de Usuarios |

> Eliminar un usuario y listar/consultar **no** envían correo.

---

## Antes de empezar: el orden importa

Si se hace el merge **antes** de configurar Render, el despliegue falla. `BrevoConfig` valida las
variables al arrancar y la aplicación no inicia si `BREVO_API_KEY` está vacía, no empieza por
`xkeysib-`, o si falta `BREVO_FROM_ADDRESS`.

1. **Brevo** (ver la Parte 5):
   - Crear una **clave API REST** (empieza por `xkeysib-`; no confundir con la clave SMTP).
   - En *Remitentes, dominios e IP* dejar un **remitente verificado** (es el valor de `BREVO_FROM_ADDRESS`).
   - Dejar **desactivada** la restricción por IP: Render (plan gratuito) no ofrece una IP fija.
2. **Render → tu servicio → Environment → Edit**, agregar:

   | Variable | Valor |
   |---|---|
   | `BREVO_API_KEY` | la clave `xkeysib-...` |
   | `BREVO_FROM_ADDRESS` | el remitente verificado en Brevo |
   | `BREVO_FROM_NAME` | `Gestion de Usuarios` |

   `JWT_SECRET`, `JWT_EXPIRATION_SECONDS` y las `DB_*` ya deben estar. Guardar los cambios.
3. **GitHub**: hacer el merge del Pull Request de `feature/brevo-rest-email-resilience` hacia `main`.
   Render despliega solo (`autoDeployTrigger: commit`).
4. **Render → Logs**: esperar `Your service is live` y buscar esta línea (confirma que las variables se cargaron;
   no muestra la clave, solo una huella):

   ```
   [BrevoSpringConfig] configuracion cargada. apiKeyFingerprint=... fromAddress=...
   ```

---

## Paso 1. Importar la colección y el entorno en Postman

1. En Postman, **Import** y seleccionar los dos archivos de `docs/postman/`:
   - `users-management.postman_collection.json`
   - `render.postman_environment.json`
2. En la esquina superior derecha elegir el entorno **Render - users-management-api**.

*Captura sugerida: Postman con la colección importada y el entorno seleccionado.*

## Paso 2. Completar las variables del entorno

Abrir el entorno (icono del ojo → **Edit**) y completar:

| Variable | Qué poner |
|---|---|
| `baseUrl` | La URL pública de tu servicio en Render, sin `/` al final. Ej.: `https://tu-servicio.onrender.com` |
| `adminEmail` | `admin@example.com` (usuario sembrado en `schema.sql`) |
| `adminPassword` | La contraseña del administrador (ver el comentario de `src/main/resources/schema.sql`) |
| `testName` | Nombre del usuario de prueba |
| `testEmail` | **Un correo tuyo real** donde puedas leer los mensajes. Para repetir la prueba usa otro (con Gmail sirve `tucorreo+2@gmail.com`), porque el correo es único |
| `testPassword` | Una contraseña de prueba (≥ 8 caracteres). **Aparece en texto plano en el correo de bienvenida**: no uses una real |

Guardar con **Save**. Las variables `token`, `memberToken` y `newUserId` las rellena la colección sola.

## Paso 3. Ejecutar las peticiones, en orden

Ejecutar de la **00** a la **14** una por una, o con **Run collection**. Cada petición lleva pruebas
automáticas (pestaña *Test Results*).

| # | Petición | Resultado esperado | Qué demuestra |
|---|---|---|---|
| 00 | `GET /v3/api-docs` | `200` | El servicio está vivo. En el plan gratuito, la primera llamada puede tardar ~1 min (arranque en frío) |
| 01 | `POST /api/auth/login` (admin) | `200` + `accessToken` | Login; guarda `{{token}}` |
| 02 | `POST /api/auth/login` (clave mala) | `401` | Credenciales inválidas |
| 03 | `POST /api/users` | `201`, `status: PENDING` | **Crea el usuario y dispara el correo "cuenta creada"** |
| 04 | `POST /api/users` (mismo correo) | `409` | Correo duplicado |
| 05 | `POST /api/users` (datos malos) | `400` | Validaciones de los campos |
| 06 | `GET /api/users` sin token | `401` | El endpoint exige JWT |
| 07 | `GET /api/users` (ADMIN) | `200` | Lista e incluye al usuario nuevo |
| 08 | `GET /api/users/{id}` | `200` | Consulta por id |
| 09 | `POST /api/auth/login` (usuario nuevo) | `401` | Un usuario `PENDING` no puede iniciar sesión |
| 10 | `PUT /api/users/{id}` con `status: ACTIVE` | `200` | **Activa al usuario y dispara el correo "cuenta actualizada"** |
| 11 | `POST /api/auth/login` (usuario nuevo) | `200` | Ya `ACTIVE`; guarda `{{memberToken}}` |
| 12 | `GET /api/users` con `memberToken` | `403` | Un `MEMBER` no puede listar (solo ADMIN/REVIEWER) |
| 13 | `DELETE /api/users/{id}` (ADMIN) | `204` | Limpieza |
| 14 | `GET /api/users/{id}` | `404` | El usuario ya no existe |

Los tokens duran `JWT_EXPIRATION_SECONDS` (900 s = 15 min). Si pasa ese tiempo, repite la **01**.

*Captura sugerida: respuesta de la 03 (201) y la pestaña Test Results en verde.*

## Paso 4. Comprobar que el correo realmente salió

**El `201` de la petición 03 no prueba que el correo se envió**: el envío es asíncrono (`notificationExecutor`)
y un fallo de Brevo solo queda en los logs. Verifica en tres sitios:

1. **Tu bandeja** (`testEmail`), y la carpeta de spam. Deben llegar:
   - tras la **03**: *Tu cuenta ha sido creada — Gestion de Usuarios*
   - tras la **10**: *Tu cuenta ha sido actualizada — Gestion de Usuarios*
2. **Render → Logs**. Si funcionó:

   ```
   [BrevoEmailSenderAdapter] correo aceptado por Brevo para <testEmail>.
   ```

   Si falló:

   ```
   [EmailNotificationService] correo no enviado. Causa: No se pudo enviar el correo a '<testEmail>' mediante la API de Brevo: HTTP 401
   ```
3. **Brevo → Transaccional → Logs**: cada envío aparece con su estado de entrega.

*Captura sugerida: el correo recibido, la línea "correo aceptado por Brevo" en Render y el log en Brevo.*

---

## Problemas frecuentes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| El deploy falla al arrancar con `BREVO_API_KEY ...` | Falta la variable, está vacía o no empieza por `xkeysib-` | Corregirla en Render → Environment (la clave **API**, no la SMTP) |
| `201` pero no llega el correo, log con `HTTP 401` | Clave API incorrecta o revocada | Generar otra clave en Brevo y actualizar `BREVO_API_KEY` |
| `201` pero no llega el correo, log con `HTTP 400` | El remitente `BREVO_FROM_ADDRESS` no está verificado en Brevo | Verificar el remitente en Brevo o usar el ya verificado |
| Log con `circuit breaker abierto` | Varios fallos temporales seguidos (5xx, timeouts o 429) | Esperar ~30 s (`BREVO_CIRCUIT_OPEN_DURATION`) y repetir |
| La 00 tarda ~1 min | Arranque en frío del plan gratuito | Esperar; las siguientes responden rápido |
| `401` en la 01 | Contraseña de admin incorrecta o el usuario no está `ACTIVE` | Revisar `adminPassword` y la tabla `users` |
| `409` en la 03 | Ese `testEmail` ya existe de una prueba anterior | Usar otro correo o ejecutar la 13 |
| `403` en la 10 o la 13 | El token no es de un ADMIN | Repetir la 01 |

## Limpieza

La petición **13** elimina el usuario de prueba. Si la colección se interrumpió antes, bórralo con
`DELETE /api/users/{id}` usando el token de ADMIN.

## Estado de verificación de esta guía

Los resultados esperados se obtuvieron leyendo el código (`UserRestController`, `SecurityConfig`,
`GlobalExceptionHandler`, `LoginService`, `EmailNotificationService` y `BrevoEmailSenderAdapter`). La colección
**aún no se ha ejecutado de punta a punta contra Render**: al correrla por primera vez, confirma los códigos
de la tabla del Paso 3 y las capturas sugeridas.
