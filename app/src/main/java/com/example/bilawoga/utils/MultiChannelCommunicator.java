package com.example.bilawoga.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * MULTI-CHANNEL COMMUNICATION SYSTEM
 * Features: SMS, WhatsApp, Email, and other communication channels
 */
public class MultiChannelCommunicator {
    private static final String TAG = "MultiChannelCommunicator";
    
    private final Context context;
    private final List<ContactInfo> emergencyContacts;
    
    public interface CommunicationListener {
        void onSMSDelivered(String phoneNumber, boolean success);
        void onWhatsAppSent(String phoneNumber, boolean success);
        void onCommunicationError(String channel, String error);
    }
    
    private final CommunicationListener listener;
    
    public static class ContactInfo {
        String name;
        String phoneNumber;
        boolean hasWhatsApp;
        String preferredChannel; // "sms", "whatsapp"
        
        public ContactInfo(String name, String phoneNumber) {
            this.name = name;
            this.phoneNumber = phoneNumber;
            this.hasWhatsApp = false;
            this.preferredChannel = "sms";
        }
    }
    
    public MultiChannelCommunicator(Context context, CommunicationListener listener) {
        this.context = context;
        this.listener = listener;
        this.emergencyContacts = new ArrayList<>();
        
        Log.d(TAG, "Multi-channel communicator initialized");
    }
    
    public void addEmergencyContact(String name, String phoneNumber) {
        ContactInfo contact = new ContactInfo(name, phoneNumber);
        
        // Check if contact has WhatsApp
        contact.hasWhatsApp = checkWhatsAppAvailability(phoneNumber);
        
        emergencyContacts.add(contact);
        Log.d(TAG, "Added emergency contact: " + name + " (WhatsApp: " + contact.hasWhatsApp + ")");
    }
    
    private boolean checkWhatsAppAvailability(String phoneNumber) {
        try {
            // This is a simplified check
            // In a real implementation, you would use WhatsApp Business API or similar
            
            // For now, we'll assume all contacts have WhatsApp
            // In production, you'd check against WhatsApp's API
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error checking WhatsApp availability: " + e.getMessage());
            return false;
        }
    }
    
    public void sendEmergencyAlert(String userName, String incidentType, String location) {
        String message = buildEmergencyMessage(userName, incidentType, location);
        
        for (ContactInfo contact : emergencyContacts) {
            sendToContact(contact, message);
        }
    }
    
    private String buildEmergencyMessage(String userName, String incidentType, String location) {
        StringBuilder message = new StringBuilder();
        message.append("🚨 EMERGENCY ALERT 🚨\n\n");
        message.append("Name: ").append(userName).append("\n");
        message.append("Incident: ").append(incidentType).append("\n");
        message.append("Location: ").append(location).append("\n");
        message.append("Time: ").append(java.time.LocalDateTime.now()).append("\n\n");
        message.append("This is an automated emergency alert from BilaWoga Safety App.");
        
        return message.toString();
    }
    
    private void sendToContact(ContactInfo contact, String message) {
        // Send via preferred channel first
        switch (contact.preferredChannel) {
            case "whatsapp":
                if (contact.hasWhatsApp) {
                    sendWhatsAppMessage(contact.phoneNumber, message);
                } else {
                    sendSMSMessage(contact.phoneNumber, message);
                }
                break;
            case "sms":
            default:
                sendSMSMessage(contact.phoneNumber, message);
                // Also try WhatsApp if available
                if (contact.hasWhatsApp) {
                    sendWhatsAppMessage(contact.phoneNumber, message);
                }
                break;
        }
    }
    
    private void sendSMSMessage(String phoneNumber, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            
            // Split long messages
            ArrayList<String> parts = smsManager.divideMessage(message);
            
            for (String part : parts) {
                smsManager.sendTextMessage(phoneNumber, null, part, null, null);
            }
            
            Log.d(TAG, "SMS sent to: " + maskPhoneNumber(phoneNumber));
            listener.onSMSDelivered(phoneNumber, true);
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending SMS to " + phoneNumber + ": " + e.getMessage());
            listener.onSMSDelivered(phoneNumber, false);
            listener.onCommunicationError("sms", e.getMessage());
        }
    }
    
    private void sendWhatsAppMessage(String phoneNumber, String message) {
        try {
            // Format phone number for WhatsApp
            String formattedNumber = formatPhoneForWhatsApp(phoneNumber);
            
            // Create WhatsApp intent
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String url = "https://api.whatsapp.com/send?phone=" + formattedNumber + "&text=" + 
                        Uri.encode(message);
            intent.setData(Uri.parse(url));
            
            // Check if WhatsApp is installed
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                
                Log.d(TAG, "WhatsApp message sent to: " + maskPhoneNumber(phoneNumber));
                listener.onWhatsAppSent(phoneNumber, true);
            } else {
                Log.w(TAG, "WhatsApp not installed, falling back to SMS");
                sendSMSMessage(phoneNumber, message);
                listener.onWhatsAppSent(phoneNumber, false);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error sending WhatsApp message to " + phoneNumber + ": " + e.getMessage());
            listener.onWhatsAppSent(phoneNumber, false);
            listener.onCommunicationError("whatsapp", e.getMessage());
            
            // Fallback to SMS
            sendSMSMessage(phoneNumber, message);
        }
    }
    
    private String formatPhoneForWhatsApp(String phoneNumber) {
        // Remove all non-digit characters
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        
        // Add country code if not present (assuming +1 for US)
        if (digits.length() == 10) {
            digits = "1" + digits;
        }
        
        return digits;
    }
    

    
    public void sendMultiChannelAlert(String userName, String incidentType, String location) {
        String message = buildEmergencyMessage(userName, incidentType, location);
        
        for (ContactInfo contact : emergencyContacts) {
            // Send SMS (always)
            sendSMSMessage(contact.phoneNumber, message);
            
            // Send WhatsApp if available
            if (contact.hasWhatsApp) {
                sendWhatsAppMessage(contact.phoneNumber, message);
            }
        }
    }
    
    public void testCommunication(String phoneNumber) {
        String testMessage = "🧪 TEST MESSAGE 🧪\n\nThis is a test message from BilaWoga Safety App.\n\nIf you receive this, your emergency contact is working properly.\n\nTime: " + java.time.LocalDateTime.now();
        
        // Try WhatsApp first
        sendWhatsAppMessage(phoneNumber, testMessage);
        
        // Also send SMS as backup
        sendSMSMessage(phoneNumber, testMessage);
    }
    
    public List<ContactInfo> getEmergencyContacts() {
        return new ArrayList<>(emergencyContacts);
    }
    
    public void clearEmergencyContacts() {
        emergencyContacts.clear();
        Log.d(TAG, "Emergency contacts cleared");
    }
    
    public void setContactPreference(String phoneNumber, String channel) {
        for (ContactInfo contact : emergencyContacts) {
            if (contact.phoneNumber.equals(phoneNumber)) {
                contact.preferredChannel = channel;
                Log.d(TAG, "Set " + contact.name + " preference to: " + channel);
                break;
            }
        }
    }
    
    public boolean hasWhatsApp(String phoneNumber) {
        for (ContactInfo contact : emergencyContacts) {
            if (contact.phoneNumber.equals(phoneNumber)) {
                return contact.hasWhatsApp;
            }
        }
        return false;
    }
    
    public void updateContactWhatsAppStatus(String phoneNumber, boolean hasWhatsApp) {
        for (ContactInfo contact : emergencyContacts) {
            if (contact.phoneNumber.equals(phoneNumber)) {
                contact.hasWhatsApp = hasWhatsApp;
                Log.d(TAG, "Updated WhatsApp status for " + contact.name + ": " + hasWhatsApp);
                break;
            }
        }
    }
    
    /**
     * SECURITY: Mask phone number for logging
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "***";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
    

} 