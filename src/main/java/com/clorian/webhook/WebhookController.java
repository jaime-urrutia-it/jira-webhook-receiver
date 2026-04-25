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
    private static final String DB_URL = "jdbc:mysql://127.0.0.1:3306/clorian_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    /**
     * 🚀 WEBHOOK ENDPOINT - RESPUESTA INMEDIATA
     * 
     * Patrón: Responder rápido, procesar en background
     * - Respuesta a Jira: < 100ms ✅
     * - Procesamiento real: Hilo separado (async)
     */
    @PostMapping("/api/jira-webhook")
    public ResponseEntity<String> handleWebhook(@RequestBody String body) {
        try {
            // ✅ Validación rápida
            if (body == null || body.trim().isEmpty()) {
                System.err.println("❌ Cuerpo vacío");
                return ResponseEntity.badRequest().body("Body is null");
            }

            // 👇 PROCESAR EN BACKGROUND (nuevo hilo)
            // Esto es ASINCRONÍA: respondemos YA, procesamos DESPUÉS
            new Thread(() -> {
                try {
                    procesarWebhookEnBackground(body);
                } catch (Exception e) {
                    System.err.println("❌ Error en background: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();

            // 👉 RESPONDER INMEDIATAMENTE a Jira (< 100ms)
            // ✅ Jira feliz, sin timeout, sin reintentos
            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            System.err.println("❌ Error en handleWebhook: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /**
     * 🔧 Lógica de negocio original (ahora en método separado)
     * Se ejecuta en hilo de background, no bloquea la respuesta HTTP
     */
    private void procesarWebhookEnBackground(String body) throws Exception {
        JSONObject payload = new JSONObject(body);

        // ✅ Verifica si existe "issue"
        if (!payload.has("issue") || payload.isNull("issue")) {
            System.err.println("❌ No se encontró el objeto 'issue' en el payload");
            return;
        }

        String issueKey = payload.getJSONObject("issue").getString("key");
        String eventType = payload.getString("webhookEvent");
        
        System.out.println("📩 Webhook recibido: " + eventType + " para " + issueKey);

        if ("jira:issue_created".equals(eventType)) {
            // ➕ CREACIÓN de ticket
            String summary = payload.getJSONObject("issue").getJSONObject("fields").getString("summary");
            String description = payload.getJSONObject("issue").getJSONObject("fields").optString("description", "");
            String priority = payload.getJSONObject("issue").getJSONObject("fields").getJSONObject("priority").getString("name");
            
            insertSupportTicket(issueKey, summary, description, priority);
            System.out.println("✅ Incidencia creada en MySQL: " + issueKey);

        } else if ("jira:issue_updated".equals(eventType)) {
            // 🔄 ACTUALIZACIÓN de estado
            String status = payload.getJSONObject("issue").getJSONObject("fields").getJSONObject("status").getString("name");
            
            // Extraer fecha de resolución (puede ser null)
            JSONObject fields = payload.getJSONObject("issue").getJSONObject("fields");
            String resolutionDate = null;
            if (fields.has("resolutiondate") && !fields.isNull("resolutiondate")) {
                resolutionDate = fields.getString("resolutiondate");
            }
            
            updateSupportTicketStatus(issueKey, status, resolutionDate);
            System.out.println("✅ Estado actualizado en MySQL: " + issueKey + " → " + status + " (Resuelto: " + resolutionDate + ")");
        }
    }

    /**
     * 📝 INSERT en MySQL (UPSERT implícito)
     */
    private void insertSupportTicket(String jiraKey, String summary, String description, String priority) {
        String sql = "INSERT INTO SupportTickets (customer_id, subject, description, status, priority, created_at, jira_issue_key, last_sync_status) VALUES (1, ?, ?, 'Open', ?, NOW(), ?, 'Open')";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, summary);
            stmt.setString(2, description);
            stmt.setString(3, mapJiraPriorityToMySQL(priority));
            stmt.setString(4, jiraKey);
            
            int rows = stmt.executeUpdate();
            System.out.println("DEBUG: Se insertaron " + rows + " filas para " + jiraKey);
            
        } catch (Exception e) {
            System.err.println("❌ Error al insertar en MySQL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 🔄 UPDATE en MySQL (con UPSERT si no existe)
     */
    private void updateSupportTicketStatus(String jiraKey, String status, String resolutionDate) throws Exception {
        String sql = "UPDATE SupportTickets SET status = ?, last_sync_status = ?, resolved_at = ? WHERE jira_issue_key = ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            String mappedStatus = mapJiraStatusToMySQL(status);
            stmt.setString(1, mappedStatus);
            stmt.setString(2, mappedStatus);
            
            if (resolutionDate != null) {
                // Limpiar fecha: "2026-04-25T04:12:29.685+0200" → "2026-04-25 04:12:29"
                String cleanDate = resolutionDate.substring(0, 19).replace("T", " ");
                stmt.setString(3, cleanDate);
            } else {
                stmt.setNull(3, java.sql.Types.TIMESTAMP);
            }
            
            stmt.setString(4, jiraKey);
            
            int rows = stmt.executeUpdate();
            
            // 🚀 UPSERT: Si no actualizó nada, es que no existe → Lo creamos
            if (rows == 0) {
                System.out.println("ℹ️ Ticket no encontrado, creando nuevo registro: " + jiraKey);
                // Creamos un ticket básico y luego lo actualizamos
                insertSupportTicket(jiraKey, "Actualizado desde webhook", "", "Medium");
                // Recursivamente actualizamos el estado
                updateSupportTicketStatus(jiraKey, status, resolutionDate);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error al actualizar MySQL: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 🗺️ Mapeo de estados Jira → MySQL
     */
    private String mapJiraStatusToMySQL(String jiraStatus) {
        if (jiraStatus == null || jiraStatus.trim().isEmpty()) {
            return "Unknown";
        }
        
        return switch (jiraStatus.trim().toUpperCase()) {
            // Mapeo de estados REALES de Jira Cloud (Inglés)
            case "TO DO"                -> "To Do";
            case "IN PROGRESS"          -> "In Progress";
            case "WAITING FOR CUSTOMER" -> "Waiting for Customer";
            case "RESOLVED"             -> "Resolved";
            case "CLOSED"               -> "Closed";
            case "DONE"                 -> "Done";
            
            // Mapeo por si pruebas en Español
            case "TAREAS POR HACER", "POR HACER"          -> "To Do";
            case "EN CURSO"                                -> "In Progress";
            case "ESPERANDO POR EL CLIENTE", "ESPERANDO"   -> "Waiting for Customer";
            case "RESUELTA"                                -> "Resolved";
            case "CERRADA"                                 -> "Closed";
            
            default -> {
                System.err.println("⚠️ Estado no mapeado reconocido, guardando original: " + jiraStatus);
                yield jiraStatus.trim();
            }
        };
    }

    /**
     * 🗺️ Mapeo de prioridades Jira → MySQL
     */
    private String mapJiraPriorityToMySQL(String jiraPriority) {
        return switch (jiraPriority) {
            case "Highest", "High" -> "High";
            case "Low", "Lowest" -> "Low";
            default -> "Medium"; // Medium y Default
        };
    }
}
