// src/main/java/com/clorian/webhook/WebhookController.java
package com.clorian.webhook;

import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PreDestroy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class WebhookController {

    // 🔐 Credenciales vía variables de entorno con fallback local (sin secretos en código)
    private static final String DB_URL  = System.getenv().getOrDefault("MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/clorian_db");
    private static final String DB_USER = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String DB_PASS = System.getenv().getOrDefault("MYSQL_PASSWORD", "");

    // 🧵 Pool acotado (sustituye a new Thread() por webhook)
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    @PreDestroy
    public void shutdown() { EXECUTOR.shutdown(); }

    @PostMapping("/api/jira-webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String body) {
        try {
            if (body == null || body.trim().isEmpty()) {
                System.err.println("❌ Cuerpo vacío");
                return ResponseEntity.badRequest().body("Body is null");
            }
            EXECUTOR.submit(() -> {
                try { procesarWebhookEnBackground(body); }
                catch (Exception e) { System.err.println("❌ Error en background: " + e.getMessage()); e.printStackTrace(); }
            });
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            System.err.println("❌ Error en handleWebhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private void procesarWebhookEnBackground(String body) throws Exception {
        JSONObject payload = new JSONObject(body);
        if (!payload.has("issue") || payload.isNull("issue")) {
            System.err.println("❌ No se encontró el objeto 'issue' en el payload");
            return;
        }
        String issueKey = payload.getJSONObject("issue").getString("key");
        String eventType = payload.getString("webhookEvent");
        System.out.println("📩 Webhook recibido: " + eventType + " para " + issueKey);

        if ("jira:issue_created".equals(eventType)) {
            JSONObject fields = payload.getJSONObject("issue").getJSONObject("fields");
            String summary = fields.getString("summary");
            String description = fields.optString("description", "");
            String priority = fields.has("priority") && !fields.isNull("priority")
                    ? fields.getJSONObject("priority").getString("name") : "Medium";
            insertSupportTicket(issueKey, summary, description, priority);
            System.out.println("✅ Incidencia creada en MySQL: " + issueKey);
        } else if ("jira:issue_updated".equals(eventType)) {
            String status = payload.getJSONObject("issue").getJSONObject("fields").getJSONObject("status").getString("name");
            JSONObject fields = payload.getJSONObject("issue").getJSONObject("fields");
            String resolutionDate = (fields.has("resolutiondate") && !fields.isNull("resolutiondate"))
                    ? fields.getString("resolutiondate") : null;
            upsertSupportTicketStatus(issueKey, status, resolutionDate);
            System.out.println("✅ Estado actualizado en MySQL: " + issueKey + " → " + status);
        }
    }

    private void insertSupportTicket(String jiraKey, String summary, String description, String priority) {
        String sql = "INSERT INTO SupportTickets (customer_id, subject, description, status, priority, created_at, jira_issue_key, last_sync_status) " +
                     "VALUES (1, ?, ?, 'Open', ?, NOW(), ?, 'Open')";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, summary);
            stmt.setString(2, description);
            stmt.setString(3, mapJiraPriorityToMySQL(priority));
            stmt.setString(4, jiraKey);
            System.out.println("DEBUG: Se insertaron " + stmt.executeUpdate() + " filas para " + jiraKey);
        } catch (Exception e) {
            System.err.println("❌ Error al insertar en MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 🔄 UPSERT en UNA sentencia (sin recursión): crea si no existe, actualiza si existe
    private void upsertSupportTicketStatus(String jiraKey, String status, String resolutionDate) {
        String mappedStatus = mapJiraStatusToMySQL(status);
        String sql = "INSERT INTO SupportTickets (customer_id, subject, description, status, priority, jira_issue_key, last_sync_status, resolved_at) " +
                     "VALUES (1, 'Actualizado desde webhook', '', ?, 'Medium', ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE status = VALUES(status), last_sync_status = VALUES(last_sync_status), resolved_at = VALUES(resolved_at)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, mappedStatus);
            stmt.setString(2, jiraKey);
            stmt.setString(3, mappedStatus);
            if (resolutionDate != null && resolutionDate.length() >= 19) {
                stmt.setString(4, resolutionDate.substring(0, 19).replace("T", " "));
            } else {
                stmt.setNull(4, java.sql.Types.TIMESTAMP);
            }
            System.out.println("✅ UPSERT completado para " + jiraKey + " (" + stmt.executeUpdate() + " fila(s))");
        } catch (Exception e) {
            System.err.println("❌ Error al actualizar MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 🗺️ Mapeo limitado EXCLUSIVAMENTE a los 5 valores del ENUM de MySQL
    private String mapJiraStatusToMySQL(String jiraStatus) {
        if (jiraStatus == null || jiraStatus.trim().isEmpty()) { return "Open"; }
        return switch (jiraStatus.trim().toUpperCase()) {
            case "TO DO", "TAREAS POR HACER", "POR HACER"                  -> "Open";
            case "IN PROGRESS", "EN CURSO"                                 -> "In Progress";
            case "WAITING FOR CUSTOMER", "ESPERANDO POR EL CLIENTE", "ESPERANDO" -> "Waiting for Customer";
            case "RESOLVED", "RESUELTA", "DONE"                            -> "Resolved";
            case "CLOSED", "CERRADA"                                       -> "Closed";
            default -> { System.err.println("⚠️ Estado no mapeado, se registra como Open: " + jiraStatus); yield "Open"; }
        };
    }

    private String mapJiraPriorityToMySQL(String jiraPriority) {
        return switch (jiraPriority) {
            case "Highest", "High" -> "High";
            case "Low", "Lowest" -> "Low";
            default -> "Medium";
        };
    }
}
