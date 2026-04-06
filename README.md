Proyecto de recualificación ITSM desarrollado con asistencia de IA generativa. El código fue revisado, comprendido y validado manualmente como parte de un proceso de aprendizaje estructurado en arquitecturas de integración Java/MySQL/Jira

# README: Jira Webhook Receiver

```markdown
# 🔄 Jira Webhook Receiver

Servicio **Spring Boot** receptor de webhooks para sincronización bidireccional entre **Jira Cloud/Server** y bases de datos **MySQL**. Este microservicio recibe eventos en tiempo real de Jira y actualiza automáticamente el estado de tickets en la base de datos local.

> 🏗️ **Arquitectura Complementaria**: Este proyecto forma parte de un ecosistema de sincronización junto con [Clorian DB Connector](https://github.com/jaime-urrutia-it/clorian-db-connector). Mientras que **Clorian DB Connector** envía datos de MySQL a Jira (polling cada 30s), **Jira Webhook Receiver** recibe actualizaciones de Jira hacia MySQL (tiempo real).

---

## 🎯 Propósito y Flujo de Datos

Este microservicio actúa como el **puente de entrada** en una arquitectura de sincronización bidireccional:

```
```┌─────────────────┐         Webhook          ┌──────────────────┐
│   JIRA CLOUD    │ ───────────────────────► │  JIRA WEBHOOK    │
│   (Eventos en   │    HTTP POST (JSON)      │    RECEIVER      │
│   tiempo real)  │                          │  (Este proyecto) │
└─────────────────┘                          └────────┬─────────┘
                                                      │ JDBC
                                                      ▼
                                            ┌──────────────────┐
                                            │   MYSQL SERVER   │
                                            │  (clorian_db)    │
                                            │ • SupportTickets │
                                            └────────┬─────────┘
                                                     │ JDBC
                                                     ▼
                                            ┌──────────────────┐
                                            │  CLORIAN DB      │
                                            │   CONNECTOR      │
                                            │ (Proyecto hermano│
                                            │  - sincroniza    │
                                            │    hacia Jira)   │
                                            └──────────────────┘```
```

### Flujo de Trabajo
1. Un usuario actualiza el estado de un issue en Jira (ej: "Por Hacer" → "En Curso")
2. Jira dispara automáticamente un webhook HTTP POST a este servicio
3. El servicio valida el payload, extrae el estado y la clave del issue
4. Actualiza la tabla `SupportTickets` en MySQL, sincronizando el campo `status`
5. El campo `last_sync_status` registra la última sincronización conocida

---

## 🛠️ Stack Tecnológico

| Tecnología | Versión | Descripción |
|------------|---------|-------------|
| **Spring Boot** | 3.3.3 | Framework web con Tomcat embebido |
| **Java** | 17+ | JDK requerido (LTS) |
| **Spring JDBC** | 3.3.3 | Conectividad simplificada a BD |
| **MySQL Connector/J** | 8.x | Driver JDBC |
| **org.json** | 20231013 | Parseo de payloads JSON |
| **Logback** | 1.4.x | Logging con rotación de archivos |
| **Maven** | 3.8+ | Gestión de dependencias |

---

## 📂 Estructura del Proyecto

```
jira-webhook-receiver/
├── 📦 JiraWebhookReceiver-1.0.0.jar     # Artefacto ejecutable (25MB)
├── 📄 pom.xml                           # Configuración Maven
└── 📁 src/main/
    ├── 📁 java/com/clorian/webhook/
    │   ├── WebhookApplication.java      # Clase principal Spring Boot
    │   └── WebhookController.java       # Endpoint receptor /api/jira-webhook
    │
    └── 📁 resources/
        ├── application.properties       # Configuración BD y servidor
        └── logback.xml                  # Rotación de logs (10MB/30días)
```

---

## 🚀 Instalación y Configuración

### 1. Prerrequisitos
- Java JDK 17 o superior
- MySQL Server 5.7+ con base de datos `clorian_db` (misma BD que Clorian DB Connector)
- Maven 3.8+
- Acceso de administrador a Jira Cloud/Server para configurar webhooks

### 2. Configuración de Base de Datos

Asegúrate de que exista la tabla `SupportTickets` (compatible con Clorian DB Connector):

```sql
CREATE TABLE IF NOT EXISTS SupportTickets (
    support_ticket_id INT PRIMARY KEY AUTO_INCREMENT,
    customer_id INT,
    subject VARCHAR(255),
    description TEXT,
    priority ENUM('High', 'Medium', 'Low'),
    status ENUM('Open', 'In Progress', 'Waiting for Customer', 'Resolved'),
    jira_issue_key VARCHAR(20) UNIQUE,        -- Enlace bidireccional
    last_sync_status VARCHAR(50),             -- Trazabilidad de cambios
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES Customers(customer_id)
);

CREATE INDEX idx_jira_key ON SupportTickets(jira_issue_key);
CREATE INDEX idx_status ON SupportTickets(status);
```

### 3. Configuración del Servicio

Edita `src/main/resources/application.properties`:

```properties
# Configuración MySQL (ajusta a tu entorno)
spring.datasource.url=jdbc:mysql://localhost:3306/clorian_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=tu_password_segura

# Configuración JPA (no modificar esquema automáticamente)
spring.jpa.hibernate.ddl-auto=none

# Logging (DEBUG para desarrollo, INFO para producción)
logging.level.com.clorian.webhook=DEBUG

# Puerto del servidor (default: 8080)
server.port=8080
```

**⚠️ Seguridad**: En producción, utiliza variables de entorno para las credenciales:
```properties
spring.datasource.username=${MYSQL_USER:root}
spring.datasource.password=${MYSQL_PASSWORD:}
```

### 4. Compilación

```bash
# Clonar repositorio
git clone https://github.com/jaime-urrutia-it/jira-webhook-receiver.git
cd jira-webhook-receiver

# Compilar
mvn clean package

# Verificar JAR generado
ls -lh target/JiraWebhookReceiver-1.0.0.jar
```

### 5. Ejecución

```bash
# Ejecutar JAR standalone (incluye Tomcat embebido)
java -jar target/JiraWebhookReceiver-1.0.0.jar

# O con perfil de desarrollo
java -jar -Dspring.profiles.active=dev target/JiraWebhookReceiver-1.0.0.jar
```

El servicio estará disponible en: `http://localhost:8080`

---

## 🔗 Configuración del Webhook en Jira

### Jira Cloud (Atlassian)

1. Ve a **Configuración (⚙️)** → **Sistema** → **WebHooks** (requiere permisos de admin)
2. Haz clic en **Crear Webhook**
3. Configura:
   - **Nombre**: `Clorian MySQL Sync`
   - **URL**: `http://<tu-servidor>:8080/api/jira-webhook`
     - Para desarrollo local con Jira Cloud, usa [ngrok](https://ngrok.com): `https://abc123.ngrok.io/api/jira-webhook`
   - **Eventos**: Selecciona **Issue** → **updated**
4. Guarda y habilita el webhook

### Jira Server/Data Center

1. **Administración** → **Sistema** → **WebHooks** (pestaña Avanzado)
2. Mismo procedimiento que Cloud

**🔐 Seguridad recomendada**:
- Configura el webhook con un **secreto** (Jira lo envía en header `X-Hub-Signature`)
- Implementa validación de IP (whitelist de rangos de Atlassian)
- Usa HTTPS en producción (certificado válido)

---

## 📡 API y Endpoints

### POST /api/jira-webhook

Recibe notificaciones de eventos de Jira.

**Headers requeridos**:
```
Content-Type: application/json
```

**Payload esperado** (ejemplo de `jira:issue_updated`):
```json
{
  "timestamp": 1711567890123,
  "webhookEvent": "jira:issue_updated",
  "user": {
    "self": "https://dominio.atlassian.net/rest/api/2/user?accountId=...",
    "accountId": "123456:abcdef"
  },
  "issue": {
    "id": "10004",
    "self": "https://dominio.atlassian.net/rest/api/2/issue/10004",
    "key": "KAN-123",
    "fields": {
      "status": {
        "self": "https://dominio.atlassian.net/rest/api/2/status/3",
        "description": "Issue currently in progress",
        "iconUrl": "...",
        "name": "En curso",
        "id": "3",
        "statusCategory": {
          "self": "...",
          "id": 4,
          "key": "indeterminate",
          "colorName": "yellow",
          "name": "En curso"
        }
      }
    }
  },
  "changelog": {
    "id": "10103",
    "items": [
      {
        "field": "status",
        "fieldtype": "jira",
        "fieldId": "status",
        "from": "10000",
        "fromString": "Tareas por hacer",
        "to": "3",
        "toString": "En curso"
      }
    ]
  }
}
```

**Respuestas**:
- `200 OK`: Procesamiento exitoso
- `400 Bad Request`: Payload inválido o faltan campos requeridos (`issue`, `webhookEvent`)
- `500 Internal Server Error`: Error al actualizar MySQL

---

## 🗺️ Mapeo de Estados

El servicio traduce automáticamente los nombres de estado de Jira (en español) a los valores ENUM de MySQL:

| Estado en Jira (UI) | Estado MySQL (`SupportTickets.status`) |
|---------------------|----------------------------------------|
| **Tareas por hacer** | `Open` |
| **En curso** | `In Progress` |
| **Esperando por el cliente** | `Waiting for Customer` |
| **Resuelta** / **Cerrada** | `Resolved` |
| *(Otros no mapeados)* | `Unknown` |

**Para modificar o agregar estados**: Edita el método `mapJiraStatusToMySQL()` en `WebhookController.java`.

---

## 📊 Monitoreo y Logging

El sistema genera logs estructurados en dos destinos:

### 1. Consola
Formato: `%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`

### 2. Archivos
- **Principal**: `logs/clorian-db-connector.log` (rotación cada 10MB, retención 30 días, máx 1GB)
- **Específico de sincronización**: `logs/polling-status-sync.log` (solo eventos de sincronización)

### Verificación de funcionamiento

```bash
# Ver logs en tiempo real
tail -f logs/clorian-db-connector.log

# Filtrar eventos de webhook recibidos
grep "Webhook recibido" logs/clorian-db-connector.log

# Filtrar errores de actualización
grep "Error al actualizar MySQL" logs/clorian-db-connector.log
```

---

## 🔒 Seguridad

### Consideraciones Actuales
- **Autenticación**: El endpoint actualmente es público. Se recomienda agregar validación de firma de webhook.
- **Red**: Restringir acceso al puerto 8080 solo a IPs de Atlassian (rangos documentados).
- **Datos**: Uso de `PreparedStatement` previene SQL Injection.

### Mejoras Recomendadas para Producción

1. **Validar firma del webhook** (Jira envía `X-Hub-Signature`):
```java
@PostMapping("/api/jira-webhook")
public ResponseEntity<String> handleWebhook(
    @RequestBody String body,
    @RequestHeader("X-Hub-Signature") String signature) {
    
    // Validar HMAC-SHA256 del body contra signature
    if (!isValidSignature(body, signature, WEBHOOK_SECRET)) {
        return ResponseEntity.status(403).body("Invalid signature");
    }
    // ... procesar
}
```

2. **Autenticación Basic** en el endpoint (si Jira lo soporta en tu versión).

3. **HTTPS obligatorio**: Usar certificado SSL válido (Let's Encrypt).

4. **Rate limiting**: Implementar limitación de requests para prevenir DoS.

---

## 🧪 Testing

### Prueba manual con cURL

```bash
curl -X POST http://localhost:8080/api/jira-webhook \
  -H "Content-Type: application/json" \
  -d '{
    "webhookEvent": "jira:issue_updated",
    "issue": {
      "key": "KAN-123",
      "fields": {
        "status": {
          "name": "En curso"
        }
      }
    }
  }'
```

**Respuesta esperada**: `OK` (y verificación en BD que el ticket KAN-123 tiene status='In Progress').

### Verificación en Base de Datos

```sql
-- Verificar sincronización
SELECT support_ticket_id, jira_issue_key, status, last_sync_status, updated_at 
FROM SupportTickets 
WHERE jira_issue_key = 'KAN-123';
-- Debería mostrar: status = 'In Progress', last_sync_status = 'In Progress'
```

---

## 🏗️ Arquitectura del Sistema Completo

Este microservicio es el **componente receptor** de una arquitectura de sincronización completa:

| Componente | Dirección | Método | Frecuencia | Propósito |
|------------|-----------|--------|------------|-----------|
| **Jira Webhook Receiver** | Jira → MySQL | HTTP Webhook | Tiempo real | Recibir cambios de Jira inmediatamente |
| **Clorian DB Connector** | MySQL → Jira | REST API + Polling | Cada 30s | Enviar nuevos tickets y sincronizar estados |

### Cuándo usar cada componente

- **Usa este proyecto (Jira Webhook Receiver)** cuando necesites que los cambios en Jira se reflejen **inmediatamente** en tu base de datos local (ej: dashboards internos, reportes en tiempo real).

- **Usa Clorian DB Connector** cuando necesites crear issues en Jira desde tu sistema local y sincronizar estados periódicamente.

**Para sincronización completa bidireccional**: Despliega **ambos proyectos** apuntando a la misma base de datos `clorian_db`.

---

## 🤝 Integración con Clorian DB Connector

Para una sincronización completa:

1. **Despliega Clorian DB Connector** (ver [su repositorio](https://github.com/jaime-urrutia-it/clorian-db-connector))
   - Configura polling cada 30s
   - Envía tickets nuevos de MySQL a Jira

2. **Despliega este proyecto (Jira Webhook Receiver)**
   - Recibe cambios de estado de Jira
   - Actualiza MySQL en tiempo real

3. **Evita ciclos infinitos**:
   - Clorian DB Connector actualiza MySQL → Jira
   - Jira dispara webhook → Jira Webhook Receiver
   - El webhook actualiza MySQL con el mismo valor (no dispara evento de cambio)
   - Ciclo detenido ✓

---

## 📝 Licencia

Desarrollado por **Jaime Urrutia**  
GitHub: [@jaime-urrutia-it](https://github.com/jaime-urrutia-it)

---

## 🚧 Roadmap

- [ ] Implementar validación de firma de webhooks (HMAC-SHA256)
- [ ] Agregar autenticación Bearer token opcional
- [ ] Soporte para múltiples proyectos de Jira simultáneos
- [ ] Endpoint de health check (`/actuator/health`)
- [ ] Métricas con Micrometer/Prometheus
- [ ] Dockerización oficial
- [ ] Soporte para PostgreSQL además de MySQL

---

**Versión**: 1.0.0  
**Compatibilidad**: Jira Cloud, Jira Server 8.x+, Jira Data Center
```

---

## 🔄 Recomendación para el README de Clorian DB Connector

**Sí, absolutamente deberías actualizarlo**. Aquí te propongo las modificaciones clave que deberías agregar al README del proyecto original:

### Secciones a agregar al README de `clorian-db-connector`:

1. **Banner de Arquitectura Integrada** (al inicio):
   ```markdown
   > 🏗️ **Parte de un Ecosistema**: Este proyecto trabaja junto con [Jira Webhook Receiver](https://github.com/jaime-urrutia-it/jira-webhook-receiver) 
   > para lograr sincronización **bidireccional completa** entre MySQL y Jira. 
   > - **Este proyecto**: MySQL → Jira (envío de tickets, polling de estados)
   > - **Webhook Receiver**: Jira → MySQL (recepción de cambios en tiempo real)
   ```

2. **Nueva sección "Arquitectura Completa"** (después de la descripción):
   ```markdown
   ## 🔄 Arquitectura Bidireccional
   
   Para sincronización completa en tiempo real, utiliza ambos proyectos:
   
   ```
   [CLORIAN DB CONNECTOR]          [JIRA WEBHOOK RECEIVER]
   (Este proyecto)                 (Proyecto complementario)
            │                                │
            │ JDBC                           │ HTTP Webhook
            ▼                                ▼
      [MYSQL SERVER] ◄────────────────► [JIRA CLOUD]
      (clorian_db)    Sincronización    (Proyecto KAN)
                      Completa
   ```
   
   - **Emisor (Este repo)**: Detecta nuevos tickets en MySQL y los crea en Jira. 
     Sincroniza estados cada 30s (polling).
   - **Receptor (Webhook)**: Escucha cambios de estado en Jira vía webhooks y 
     actualiza MySQL inmediatamente.
   ```

3. **Actualizar la sección de Características** para mencionar:
   - "Diseñado para funcionar con Jira Webhook Receiver (sincronización bidireccional)"

4. **Agregar sección "Relación con Otros Proyectos"**:
   - Explicar que es el complemento del webhook receiver
   - Cuándo usar uno, cuándo usar ambos

5. **Actualizar el diagrama de flujo** en el README para mostrar la opción del webhook.

### Beneficios de esta contextualización cruzada:

1. **Claridad arquitectónica**: Los usuarios entenderán que no es "todo en uno", sino componentes especializados
2. **Descubrimiento**: Quienes encuentren uno, conocerán la existencia del otro
3. **Mejores decisiones**: Podrán elegir si necesitan solo sincronización saliente, entrante, o ambas
4. **Mantenimiento**: Facilita entender por qué hay "dos proyectos similares"
