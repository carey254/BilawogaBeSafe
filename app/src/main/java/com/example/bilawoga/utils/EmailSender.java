package com.example.bilawoga.utils;

import android.content.Context;
import android.util.Log;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility class to send emails automatically via HTTP (email address hidden from user)
 */
public class EmailSender {
    private static final String TAG = "EmailSender";
    
    // Email service endpoint - you can use EmailJS, SendGrid, or your own backend
    // For EmailJS: https://api.emailjs.com/api/v1.0/email/send
    // You'll need to set up an EmailJS account and get your service ID, template ID, and public key
    private static final String EMAIL_SERVICE_URL = "https://api.emailjs.com/api/v1.0/email/send";
    
    // Recipient email (hidden from user)
    private static final String RECIPIENT_EMAIL = "carenjeruto477@gmail.com";
    
    /**
     * Send email automatically via HTTP POST
     * Returns true if sent successfully, false otherwise
     */
    public static boolean sendEmail(Context context, String subject, String body, String userEmail, boolean isAnonymous) {
        try {
            // Build email body with all information
            StringBuilder emailBody = new StringBuilder();
            emailBody.append("BilaWoga Support Request\n\n");
            emailBody.append("Issue Description:\n");
            emailBody.append(body).append("\n\n");
            
            if (!isAnonymous && userEmail != null && !userEmail.isEmpty()) {
                emailBody.append("User Email: ").append(userEmail).append("\n");
            } else {
                emailBody.append("Submitted anonymously\n");
            }
            
            emailBody.append("\n---\n");
            try {
                emailBody.append("App Version: ").append(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName).append("\n");
            } catch (Exception e) {
                emailBody.append("App Version: Unknown\n");
            }
            emailBody.append("Device: ").append(android.os.Build.MODEL).append("\n");
            emailBody.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
            emailBody.append("Timestamp: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));
            
            // Method 1: Try EmailJS (now configured with your credentials)
            boolean sent = sendViaEmailJS(subject, emailBody.toString());
            if (sent) {
                return true;
            }
            
            // Method 2: Fallback to backend endpoint if EmailJS fails
            return sendViaBackend(RECIPIENT_EMAIL, subject, emailBody.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending email: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Send email via EmailJS service
     * Configured with your EmailJS credentials
     * Sends ALL possible template variables to support any EmailJS template
     */
    private static boolean sendViaEmailJS(String subject, String body) {
        try {
            // EmailJS credentials
            String serviceId = "service_s1yk2gb";
            String templateId = "template_p644kkh";
            String publicKey = "CfeWcqQJCRkZ3d-6h";
            
            // Build JSON payload with ALL possible template variables
            // This ensures compatibility with any EmailJS template variable names
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"service_id\":\"").append(serviceId).append("\",");
            jsonBuilder.append("\"template_id\":\"").append(templateId).append("\",");
            jsonBuilder.append("\"user_id\":\"").append(publicKey).append("\",");
            jsonBuilder.append("\"template_params\":{");
            
            // Send ALL possible variable names that EmailJS templates might use
            jsonBuilder.append("\"to_email\":\"").append(RECIPIENT_EMAIL).append("\",");
            jsonBuilder.append("\"to\":\"").append(RECIPIENT_EMAIL).append("\",");
            jsonBuilder.append("\"recipient\":\"").append(RECIPIENT_EMAIL).append("\",");
            jsonBuilder.append("\"recipient_email\":\"").append(RECIPIENT_EMAIL).append("\",");
            
            jsonBuilder.append("\"subject\":\"").append(escapeJsonString(subject)).append("\",");
            jsonBuilder.append("\"email_subject\":\"").append(escapeJsonString(subject)).append("\",");
            jsonBuilder.append("\"title\":\"").append(escapeJsonString(subject)).append("\",");
            
            jsonBuilder.append("\"message\":\"").append(escapeJsonString(body)).append("\",");
            jsonBuilder.append("\"body\":\"").append(escapeJsonString(body)).append("\",");
            jsonBuilder.append("\"content\":\"").append(escapeJsonString(body)).append("\",");
            jsonBuilder.append("\"text\":\"").append(escapeJsonString(body)).append("\",");
            jsonBuilder.append("\"description\":\"").append(escapeJsonString(body)).append("\",");
            jsonBuilder.append("\"issue\":\"").append(escapeJsonString(body)).append("\",");
            jsonBuilder.append("\"feedback\":\"").append(escapeJsonString(body)).append("\",");
            jsonBuilder.append("\"support_request\":\"").append(escapeJsonString(body)).append("\"");
            
            jsonBuilder.append("}");
            jsonBuilder.append("}");
            String jsonPayload = jsonBuilder.toString();
            
            Log.d(TAG, "Sending email via EmailJS to: " + RECIPIENT_EMAIL);
            
            URL url = new URL(EMAIL_SERVICE_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000); // 10 seconds
            conn.setReadTimeout(10000); // 10 seconds
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.flush();
            }
            
            int responseCode = conn.getResponseCode();
            
            // Read response body for debugging (both success and error)
            StringBuilder responseBody = new StringBuilder();
            try {
                java.io.InputStream inputStream = (responseCode >= 200 && responseCode < 300) 
                    ? conn.getInputStream() 
                    : conn.getErrorStream();
                
                if (inputStream != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                    reader.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Could not read response: " + e.getMessage());
            }
            
            conn.disconnect();
            
            // EmailJS returns 200 on success
            if (responseCode == 200) {
                Log.d(TAG, "Email sent successfully via EmailJS to " + RECIPIENT_EMAIL);
                Log.d(TAG, "EmailJS response: " + responseBody.toString());
                return true;
            } else {
                Log.e(TAG, "EmailJS returned error code: " + responseCode);
                Log.e(TAG, "EmailJS error response: " + responseBody.toString());
                Log.e(TAG, "EmailJS request payload: " + jsonPayload.substring(0, Math.min(200, jsonPayload.length())));
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending via EmailJS: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Escape JSON string to prevent injection
     */
    private static String escapeJsonString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Send email via custom backend endpoint
     * Replace BACKEND_URL with your server endpoint that sends emails
     */
    private static boolean sendViaBackend(String recipient, String subject, String body) {
        try {
            // TODO: Replace with your backend URL
            String backendUrl = "https://your-backend.com/api/send-email";
            
            // Build JSON payload
            String jsonPayload = String.format(
                "{\"to\":\"%s\",\"subject\":\"%s\",\"body\":\"%s\"}",
                recipient,
                URLEncoder.encode(subject, StandardCharsets.UTF_8.toString()),
                URLEncoder.encode(body, StandardCharsets.UTF_8.toString())
            );
            
            URL url = new URL(backendUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            if (responseCode == 200 || responseCode == 201) {
                Log.d(TAG, "Email sent successfully via backend");
                return true;
            } else {
                Log.e(TAG, "Backend returned error code: " + responseCode);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending via backend: " + e.getMessage(), e);
            return false;
        }
    }
}

