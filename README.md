# Jira Webhook Receiver
![Java](https://img.shields.io/badge/Java-17%2B-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-6DB33F.svg)
![MySQL](https://img.shields.io/badge/MySQL-5.7+-4479A1.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

Módulo de integración Spring Boot que actúa como **receptor** en la arquitectura de sincronización bidireccional Clorian. Elimina la latencia operativa entre plataformas recibiendo actualizaciones de Jira en tiempo real y persistiéndolas en MySQL.

> **Parte de un Ecosistema:** Este es el RECEPTOR (Jira → MySQL). Para una sincronización completa, debe desplegarse junto con el EMISOR ([Clorian DB Connector](https://github.com/jaime-urrutia-it/clorian-db-connector)).

⚠️ **Estado Actual (Agosto 2026):**
- Este proyecto es un MVP funcional para demostración técnica.
- Las credenciales de base de datos están externalizadas vía variables de entorno.
- Para ver el código real, consulta los archivos `.java` en `src/main/java/`.

---

## 🎯 Propósito y Arquitectura

### Contexto de Negocio
En entornos de SSC y Business Operations, la inmediatez de la información es crítica. Este componente demuestra cómo el uso de webhooks (en lugar de polling constante) elimina la latencia operativa, garantiza la trazabilidad de los cambios de estado y reduce la carga innecesaria sobre las APIs externas, un patrón aplicable a cualquier flujo de negocio que requiera sincronización en tiempo real.

### Flujo de Trabajo
```text
1. Usuario cambia el estado de un issue en Jira Cloud.
2. Jira dispara un evento HTTP POST al endpoint /api/jira-webhook.
3. Spring Boot recibe la petición y la encola en un ExecutorService (procesamiento asíncrono).
4. Se responde a Jira inmediatamente con HTTP 200 OK (latencia < 100ms).
5. En segundo plano, se ejecuta un UPSERT en MySQL para actualizar el estado del ticket.
```

---

## ✅ Características Principales

- **Procesamiento Asíncrono:** Uso de `ExecutorService` con pool de hilos fijo para manejar picos de carga sin bloquear el hilo principal de Spring Boot.
- **UPSERT Optimizado:** Implementación de `INSERT ... ON DUPLICATE KEY UPDATE` en una única sentencia SQL, eliminando riesgos de recursión o condiciones de carrera.
- **Mapeo de Estados Completo:** Traducción robusta de los 5 estados de Jira (To Do, In Progress, Waiting for Customer, Resolved, Closed) al ENUM de MySQL.
- **Seguridad y Configuración:** Credenciales de base de datos externalizadas mediante variables de entorno (`MYSQL_USER`, `MYSQL_PASSWORD`), con fallback local solo para desarrollo.
- **Logging Estructurado:** Configuración de Logback con rotación diaria y archivos independientes (`jira-webhook-receiver.log`, `sync-webhook.log`).

---

## 🛠️ Stack Tecnológico

| Tecnología | Versión | Propósito |
|---|---|---|
| Framework | Spring Boot 3.3.3 | Motor web y gestión de dependencias |
| Lenguaje | Java 17+ | Lógica de negocio y procesamiento asíncrono |
| Base de Datos | MySQL 5.7+ | Persistencia de estados sincronizados |
| Driver JDBC | MySQL Connector/J 8.x | Conectividad con la base de datos |
| Build Tool | Maven 3.8+ | Compilación y empaquetado |

---

## 📂 Estructura del Proyecto

```text
jira-webhook-receiver/
 ├── src/main/
 │   ├── java/com/clorian/webhook/
 │   │   ├── WebhookApplication.java       # Punto de entrada de Spring Boot
 │   │   └── WebhookController.java        # Endpoint y lógica de procesamiento
 │   └── resources/
 │       ├── application.properties        # Configuración externalizada
 │       └── logback.xml                   # Configuración de logging con rotación
 ├── pom.xml                               # Dependencias de Maven
 └── README.md
```

---

## 🚀 Instalación y Configuración

### 1. Requisitos Previos
- Java JDK 17 o superior
- MySQL Server 5.7+ con la base de datos `clorian_db` creada
- Maven 3.8+

### 2. Configuración de la Base de Datos
Asegúrate de que la tabla `SupportTickets` tenga el ENUM actualizado con los 5 estados:
```sql
ALTER TABLE SupportTickets 
MODIFY COLUMN status ENUM('Open', 'In Progress', 'Waiting for Customer', 'Resolved', 'Closed') DEFAULT 'Open';
```

### 3. Externalización de Credenciales
El proyecto está configurado para leer variables de entorno. En producción, no uses el archivo `application.properties` para secretos.
```properties
# src/main/resources/application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/clorian_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=${MYSQL_USER:root}
spring.datasource.password=${MYSQL_PASSWORD:}
server.port=8080
```

### 4. Compilación y Ejecución
```bash
# Compilar el proyecto
mvn clean package

# Ejecutar (asegúrate de establecer las variables de entorno en tu sistema)
export MYSQL_USER="tu_usuario"
export MYSQL_PASSWORD="tu_contraseña"
java -jar target/JiraWebhookReceiver-1.0.0.jar
```

---

## 🔐 Seguridad

### Estado Actual
✅ Credenciales de base de datos externalizadas vía variables de entorno.  
✅ Uso de `PreparedStatement` para prevenir inyección SQL.  
✅ Gestión segura de recursos con bloques `try-with-resources`.

### Mejoras Recomendadas para Producción
⚠️ Implementar validación de firma HMAC-SHA256 para verificar que los webhooks provienen genuinamente de Jira.  
⚠️ Desplegar detrás de un proxy inverso (Nginx) con HTTPS obligatorio.  
⚠️ Configurar una whitelist de IPs de Atlassian en el firewall del servidor.

---

### ⚠️ Limitaciones Conocidas del MVP (Agosto 2026)
Este proyecto es un MVP de demostración, no un sistema de producción. Las siguientes limitaciones están documentadas intencionalmente como parte del roadmap de maduración:

| Limitación | Impacto | Plan de mitigación |
|---|---|---|
| Endpoint webhook sin autenticación HMAC | Cualquiera podría enviar payloads falsos | Implementar validación HMAC-SHA256 (ver Roadmap) |
| Logging por consola (`System.out`) en algunos puntos | Sin rotación ni niveles estructurados | Migrar completamente a SLF4J + Logback (`logback.xml` ya configurado) |
| Polling cada 30s en modo standalone (DB Connector) | Carga innecesaria sobre API de Jira | Aumentar intervalo o migrar a webhook-only |

**Nota sobre el alcance:** Estas limitaciones están documentadas porque un entorno SSC/Business Operations valora tanto el control de un sistema como la honestidad sobre su estado. La decisión de abordarlas (o aceptarlas como riesgo controlado en un entorno de bajo volumen) corresponde al equipo de operaciones que adopte el proyecto.

---

## 📈 Roadmap

### Pista de Negocio
- [ ] Dashboard de KPIs de servicio (tiempo medio de sincronización, errores de webhook)
- [ ] Alertas automáticas ante fallos de persistencia en MySQL
- [ ] Integración con ERPs para ampliar el alcance operacional

### Pista Técnica
- [ ] Validación de firma HMAC-SHA256 en webhooks
- [ ] Dockerización oficial (Dockerfile + Docker Compose)
- [ ] Cola de mensajes (RabbitMQ/ActiveMQ) para desacoplar recepción de procesamiento
- [ ] Logging profesional completo (SLF4J + Logback en todos los puntos)

---

## 🤝 Contribución
Este es un proyecto abierto. Si encuentras bugs o tienes sugerencias:
1. Abre un issue en este repositorio.
2. Incluye logs relevantes y pasos para reproducir el error.

---

## 📄 Licencia y Autoría
Distribuido bajo licencia MIT. Ver [LICENSE](LICENSE) para más detalles.

Desarrollado por **Jaime (Yago) Urrutia**  
[GitHub](https://github.com/jaime-urrutia-it) · [Portfolio](https://yagourrutia.com) · [LinkedIn](https://www.linkedin.com/in/jaime-urrutia-multilingue/?locale=es-ES)  
Barcelona, España

**Versión:** 1.0.0 | **Última actualización:** Agosto 2026
