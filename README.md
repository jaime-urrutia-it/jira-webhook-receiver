# Jira Webhook Receiver

![Java](https://img.shields.io/badge/Java-17%2B-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-6DB33F.svg)
![MySQL](https://img.shields.io/badge/MySQL-5.7+-4479A1.svg)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

Spring Boot integration module that acts as the **RECEIVER** in the Clorian bidirectional synchronization architecture. It eliminates operational latency between platforms by receiving Jira updates in real-time and persisting them in MySQL.

> **Part of an Ecosystem:** This is the RECEIVER (Jira → MySQL). For complete synchronization, it must be deployed alongside the EMITTER ([Clorian DB Connector](https://github.com/jaime-urrutia-it/clorian-db-connector)).

⚠️ **Current State (August 2026):**
- This project is a functional MVP for technical demonstration.
- Database credentials are externalized via environment variables.
- To view the actual implementation, consult the `.java` files in `src/main/java/`.

---

## 🎯 Purpose and Architecture

### Business Context
In SSC and Business Operations environments, information immediacy is critical. This component demonstrates how using webhooks (instead of constant polling) eliminates operational latency, guarantees state change traceability, and reduces unnecessary load on external APIs—a pattern applicable to any business workflow requiring real-time synchronization.

### Workflow
```text
1. User changes an issue status in Jira Cloud.
2. Jira triggers an HTTP POST event to the /api/jira-webhook endpoint.
3. Spring Boot receives the request and queues it in an ExecutorService (asynchronous processing).
4. Jira is immediately responded to with HTTP 200 OK (latency < 100ms).
5. In the background, an UPSERT is executed in MySQL to update the ticket status.
```

---

## ✅ Main Features

- **Asynchronous Processing:** Uses `ExecutorService` with a fixed thread pool to handle load spikes without blocking Spring Boot's main thread.
- **Optimized UPSERT:** Implements `INSERT ... ON DUPLICATE KEY UPDATE` in a single SQL statement, eliminating recursion risks or race conditions.
- **Complete State Mapping:** Robust translation of the 5 Jira states (To Do, In Progress, Waiting for Customer, Resolved, Closed) to the MySQL ENUM.
- **Security & Configuration:** Database credentials externalized via environment variables (`MYSQL_USER`, `MYSQL_PASSWORD`), with local fallback only for development.
- **Structured Logging:** Logback configuration with daily rotation and independent files (`jira-webhook-receiver.log`, `sync-webhook.log`).

---

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Framework | Spring Boot 3.3.3 | Web engine and dependency management |
| Language | Java 17+ | Business logic and asynchronous processing |
| Database | MySQL 5.7+ | Persistence of synchronized states |
| JDBC Driver | MySQL Connector/J 8.x | Database connectivity |
| Build Tool | Maven 3.8+ | Compilation and packaging |

---

## 📂 Project Structure

```text
jira-webhook-receiver/
 ├── src/main/
 │   ├── java/com/clorian/webhook/
 │   │   ├── WebhookApplication.java       # Spring Boot entry point
 │   │   └── WebhookController.java        # Endpoint and processing logic
 │   └── resources/
 │       ├── application.properties        # Externalized configuration
 │       └── logback.xml                   # Logging configuration with rotation
 ├── pom.xml                               # Maven dependencies
 └── README.md
```

---

## 🚀 Installation and Configuration

### 1. Prerequisites
- Java JDK 17 or higher
- MySQL Server 5.7+ with the `clorian_db` database created
- Maven 3.8+

### 2. Database Configuration
Ensure the `SupportTickets` table has the updated ENUM with all 5 states:
```sql
ALTER TABLE SupportTickets 
MODIFY COLUMN status ENUM('Open', 'In Progress', 'Waiting for Customer', 'Resolved', 'Closed') DEFAULT 'Open';
```

### 3. Externalizing Credentials
The project is configured to read environment variables. In production, do not use `application.properties` for secrets.
```properties
# src/main/resources/application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/clorian_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=${MYSQL_USER:root}
spring.datasource.password=${MYSQL_PASSWORD:}
server.port=8080
```

### 4. Compilation and Execution
```bash
# Compile the project
mvn clean package

# Run (ensure environment variables are set in your system)
export MYSQL_USER="your_user"
export MYSQL_PASSWORD="your_password"
java -jar target/JiraWebhookReceiver-1.0.0.jar
```

---

## 🔐 Security

### Current State
✅ Database credentials externalized via environment variables.  
✅ Use of `PreparedStatement` to prevent SQL injection.  
✅ Secure resource management with `try-with-resources` blocks.

### Recommended Improvements for Production
⚠️ Implement HMAC-SHA256 signature validation to verify that webhooks genuinely originate from Jira.  
⚠️ Deploy behind a reverse proxy (Nginx) with mandatory HTTPS.  
⚠️ Configure an Atlassian IP whitelist on the server firewall.

---

### ⚠️ Known MVP Limitations (August 2026)
This project is a demonstration MVP, not a production system. The following limitations are intentionally documented as part of the maturation roadmap:

| Limitation | Impact | Mitigation Plan |
|---|---|---|
| Webhook endpoint without HMAC authentication | Anyone could send fake payloads | Implement HMAC-SHA256 validation (see Roadmap) |
| Console logging (`System.out`) in some code points | No rotation or structured levels | Fully migrate to SLF4J + Logback (`logback.xml` already configured) |
| Polling every 30s in standalone mode (DB Connector) | Unnecessary load on Jira API | Increase interval or migrate to webhook-only |

**Note on scope:** These limitations are documented because an SSC/Business Operations environment values both the control of a system and honesty about its state. The decision to address them (or accept them as a controlled risk in a low-volume environment) corresponds to the operations team that adopts the project.

---

## 📈 Roadmap

### Business Track
- [ ] Service KPIs dashboard (average sync time, webhook errors)
- [ ] Automatic alerts for MySQL persistence failures
- [ ] Integration with ERPs to expand operational scope

### Technical Track
- [ ] HMAC-SHA256 signature validation on webhooks
- [ ] Official Dockerization (Dockerfile + Docker Compose)
- [ ] Message queue (RabbitMQ/ActiveMQ) to decouple reception from processing
- [ ] Complete professional logging (SLF4J + Logback across all points)

---

## 🤝 Contribution
This is an open project. If you find bugs or have suggestions:
1. Open an issue in this repository.
2. Include relevant logs and steps to reproduce the error.

---

## 📄 License and Authorship
Distributed under the MIT License. See [LICENSE](LICENSE) for more details.

Developed by **Jaime (Yago) Urrutia**  
[GitHub](https://github.com/jaime-urrutia-it) · [Portfolio](https://yagourrutia.com) · [LinkedIn](https://www.linkedin.com/in/jaime-urrutia-multilingue/?locale=en-US)  
Barcelona, Spain

**Version:** 1.0.0 | **Last update:** August 2026
