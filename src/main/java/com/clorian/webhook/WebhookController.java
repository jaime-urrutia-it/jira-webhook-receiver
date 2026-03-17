// src/main/java/com/clorian/webhook/WebhookController.java

package com.clorian.webhook;

import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

@RestController
public class WebhookController {

    // 🔐 Configuración MySQL
    private static final String DB_URL = "jdbc:mysql://localhost:3306/clorian_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    @PostMapping("/api/jira-webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String body) {
        try {
            // ✅ Log del cuerpo recibido
            System.out.println("📩 Cuerpo recibido: " + body);

            if (body == null || body.trim().isEmpty()) {
                System.err.println("❌ Cuerpo vacío");
                return ResponseEntity.badRequest().body("Body is null");
            }

            JSONObject payload = new JSONObject(body);

            // ✅ Verifica si existe "issue"
            if (!payload.has("issue") || payload.isNull("issue")) {
                System.err.println("❌ No se encontró el objeto 'issue' en el payload");
                return ResponseEntity.badRequest().body("Missing 'issue' in payload");
            }

            String issueKey = payload.getJSONObject("issue").getString("key");
            String eventType = payload.getString("webhookEvent");

            System.out.println("📩 Webhook recibido: " + eventType + " para " + issueKey);

            if ("jira:issue_updated".equals(eventType)) {
                String status = payload.getJSONObject("issue").getJSONObject("fields").getJSONObject("status").getString("name");
                updateSupportTicketStatus(issueKey, status);
                System.out.println("✅ Estado actualizado en MySQL: " + issueKey + " → " + status);
            }

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            System.err.println("❌ Error en handleWebhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private void updateSupportTicketStatus(String jiraKey, String status) {
        String sql = "UPDATE SupportTickets SET status = ?, last_sync_status = ? WHERE jira_issue_key = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, mapJiraStatusToMySQL(status));
            stmt.setString(2, mapJiraStatusToMySQL(status));
            stmt.setString(3, jiraKey);
            int rows = stmt.executeUpdate();

            if (rows == 0) {
                System.err.println("❌ No se encontró ticket con jira_issue_key: " + jiraKey);
            }

        } catch (Exception e) {
            System.err.println("❌ Error al actualizar MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String mapJiraStatusToMySQL(String jiraStatus) {
        return switch (jiraStatus) {
            case "Tareas por hacer" -> "Open";
            case "En curso" -> "In Progress";
            case "Esperando por el cliente" -> "Waiting for Customer";
            case "Resuelta" -> "Resolved";
            default -> "Unknown";
        };
    }
}
