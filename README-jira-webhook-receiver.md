# Jira Webhook Receiver

Servicio **Spring Boot** receptor de webhooks para sincronizacion bidireccional entre **Jira Cloud/Server** y bases de datos **MySQL**. Este microservicio recibe eventos en tiempo real de Jira y actualiza automaticamente el estado de tickets en la base de datos local.

> **Parte de un Ecosistema**: Este proyecto trabaja junto con [Clorian DB Connector](https://github.com/jaime-urrutia-it/clorian-db-connector) para lograr sincronizacion **bidireccional completa** entre MySQL y Jira.
> - **Clorian DB Connector**: MySQL → Jira (envio de tickets, polling de estados)
> - **Este proyecto (Webhook Receiver)**: Jira → MySQL (recepcion de cambios en tiempo real)

> **Contexto de negocio**: Mas alla de la integracion tecnica, este proyecto demuestra como los webhooks en tiempo real eliminan la latencia operativa entre plataformas, un patron aplicable a cualquier entorno de SSC o Business Operations donde la inmediatez de la informacion es critica.

> ⚠️ **Estado Actual (Agosto 2026):**  
> Este componente es una solucion funcional (MVP) dentro del Ecosistema Clorian. 
> - **Arquitectura:** Spring Boot como framework web, con JDBC nativo (`java.sql`) en lugar de ORMs para control explícito sobre transacciones y parseo.
> - **Procesamiento Asincrono:** Patron "fire-and-forget" (hilo separado) para respuesta inmediata a Jira (<100ms).
> - **Seguridad:** La validacion de firma HMAC-SHA256 esta documentada como mejora recomendada para produccion (Roadmap v2.0), no implementada en esta v1.0.

---

## Stack Tecnologico

| Tecnologia | Version | Descripcion |
|------------|---------|-------------|
| **Spring Boot** | 3.3.3 | Framework web con Tomcat embebido |
| **Java** | 17+ | JDK requerido (LTS) |
| **Spring JDBC** | 3.3.3 | Conectividad a BD |
| **MySQL Connector/J** | 8.x | Driver JDBC oficial |
| **org.json** | 20231013 | Parseo de payloads JSON |
| **Logback** | 1.4.x | Logging con rotacion de archivos |
| **Maven** | 3.8+ | Gestion de dependencias |

---

## Estructura del Proyecto

```
jira-webhook-receiver/
├── pom.xml                           # Configuracion Maven
└── src/main/
    ├── java/com/clorian/webhook/
    │   ├── WebhookApplication.java      # Clase principal Spring Boot
    │   └── WebhookController.java       # Endpoint receptor /api/jira-webhook
    └── resources/
        ├── application.properties       # Configuracion BD y servidor
        └── logback.xml                  # Rotacion de logs (10MB/30 dias)
```

---

## Instalacion y Configuracion

### 1. Requisitos previos
- Java JDK 17 o superior
- MySQL Server 5.7+ con base de datos `clorian_db` (misma BD que Clorian DB Connector)
- Maven 3.8+
- Acceso de administrador a Jira Cloud/Server para configurar webhooks

### 2. Configuracion de Base de Datos

Asegurate de que exista la tabla `SupportTickets` (compatible con Clorian DB Connector). El DDL completo esta disponible en el [README del ecosistema](https://github.com/jaime-urrutia-it/clorian-ecosystem).

### 3. Configuracion del Servicio

Edita `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/clorian_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=${MYSQL_USER:root}
spring.datasource.password=${MYSQL_PASSWORD:}
logging.level.com.clorian.webhook=DEBUG
server.port=8080
```

**En produccion**, utiliza variables de entorno para las credenciales (`MYSQL_USER`, `MYSQL_PASSWORD`).

### 4. Compilacion y Ejecucion

```bash
git clone https://github.com/jaime-urrutia-it/jira-webhook-receiver.git
cd jira-webhook-receiver
mvn clean package
java -jar target/JiraWebhookReceiver-1.0.0.jar
```

El servicio estara disponible en: `http://localhost:8080`

---

## Configuracion del Webhook en Jira

### Jira Cloud (Atlassian)

1. **Configuracion** → **Sistema** → **WebHooks** (requiere permisos de admin)
2. Haz clic en **Crear Webhook**
3. Configura:
   - **Nombre**: `Clorian MySQL Sync`
   - **URL**: `http://<tu-servidor>:8080/api/jira-webhook`
   - Para desarrollo local con Jira Cloud, usa [ngrok](https://ngrok.com)
   - **Eventos**: Issue → updated
4. Guarda y habilita el webhook

---

## API y Endpoints

### POST /api/jira-webhook

Recibe notificaciones de eventos de Jira.

**Payload esperado** (`jira:issue_updated`):
```json
{
  "webhookEvent": "jira:issue_updated",
  "issue": {
    "key": "KAN-123",
    "fields": {
      "status": { "name": "En curso" }
    }
  },
  "changelog": {
    "items": [{
      "field": "status",
      "toString": "En curso",
      "fromString": "Tareas por hacer"
    }]
  }
}
```

**Respuestas**:
- `200 OK`: Procesamiento exitoso
- `400 Bad Request`: Payload invalido
- `500 Internal Server Error`: Error al actualizar MySQL

---

## Mapeo de Estados

El servicio traduce nombres de estado de Jira (espanol e ingles) a los valores ENUM de MySQL. La [tabla de mapeo unificada del ecosistema](https://github.com/jaime-urrutia-it/clorian-ecosystem#mapeo-de-estados-referencia-unica) es la referencia autorizada.

| Estado en Jira (Espanol) | Estado en Jira (Ingles) | Estado MySQL |
|---|---|---|
| Tareas por hacer | To Do | `Open` |
| En curso | In Progress | `In Progress` |
| Esperando por el cliente | Waiting for Customer | `Waiting for Customer` |
| Resuelta | Resolved | `Resolved` |
| Cerrada | Closed | `Closed` |

---

## Monitoreo y Logging

El sistema genera logs en dos destinos:

- **Principal**: `logs/jira-webhook-receiver.log` (rotacion cada 10MB, retencion 30 dias, max 1GB)
- **Especifico de sincronizacion**: `logs/sync-webhook.log`

```bash
# Ver logs en tiempo real
tail -f logs/jira-webhook-receiver.log

# Filtrar errores de actualizacion
grep "Error al actualizar MySQL" logs/jira-webhook-receiver.log
```

---

## Seguridad

Para el estado actual y mejoras recomendadas, consultar la [seccion de seguridad del ecosistema](https://github.com/jaime-urrutia-it/clorian-ecosystem#seguridad).

**Mejoras especificas de este componente:**
- [ ] Validar firma del webhook HMAC-SHA256 (Jira envia `X-Hub-Signature`)
- [ ] Restringir acceso al puerto 8080 solo a IPs de Atlassian
- [ ] Implementar HTTPS obligatorio
- [ ] Rate limiting para prevencion de DoS

---

## Integracion con Clorian DB Connector

Para sincronizacion bidireccional completa:

1. **Despliega Clorian DB Connector** ([repo](https://github.com/jaime-urrutia-it/clorian-db-connector)): envía tickets nuevos de MySQL a Jira y sincroniza estados por polling.
2. **Despliega este proyecto**: recibe cambios de estado de Jira via webhooks y actualiza MySQL inmediatamente.
3. **Prevencion de ciclos infinitos**: el campo `last_sync_status` en MySQL evita bucles. Cuando el webhook actualiza MySQL, `status` y `last_sync_status` quedan iguales, por lo que el emisor no detecta cambio pendiente en su siguiente ciclo de polling.

---

## Roadmap

### Pista de Negocio
- [ ] Modulo de conciliacion O2C
- [ ] Dashboard de KPIs de servicio
- [ ] Reportes operativos exportables
- [ ] Integracion con ERPs

### Pista Tecnica
- [ ] Validacion de firma de webhooks (HMAC-SHA256)
- [ ] Autenticacion Bearer token opcional
- [ ] Soporte para multiples proyectos de Jira
- [ ] Endpoint de health check (`/actuator/health`)
- [ ] Metricas con Micrometer/Prometheus
- [ ] Dockerizacion oficial
- [ ] Soporte para PostgreSQL

---

## Licencia y Autoria

Desarrollado por **Jaime Urrutia**  
[GitHub](https://github.com/jaime-urrutia-it) | [Portfolio](https://yagourrutia.com) | [LinkedIn](https://www.linkedin.com/in/jaime-yago-urrutia-multilingue/)  

**Version**: 1.0.0 | **Ultima actualizacion**: Agosto 2026
