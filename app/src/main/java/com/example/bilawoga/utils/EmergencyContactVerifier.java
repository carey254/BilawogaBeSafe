package com.example.bilawoga.utils;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.util.regex.Pattern;

/**
 * Emergency contact verification system
 * Validates phone numbers and ensures they are reachable
 */
public class EmergencyContactVerifier {
    private static final String TAG = "EmergencyContactVerifier";
    
    // Phone number patterns for different regions
    private static final Pattern UGANDA_PHONE_PATTERN = Pattern.compile("^(\\+256|256|0)?(7[0-9]{8})$");
    private static final Pattern KENYA_PHONE_PATTERN = Pattern.compile("^(\\+254|254|0)?(7[0-9]{8})$");
    private static final Pattern INTERNATIONAL_PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{1,14}$");
    
    private Context context;
    private TelephonyManager telephonyManager;
    
    public EmergencyContactVerifier(Context context) {
        this.context = context.getApplicationContext();
        this.telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }
    
    /**
     * Verify if a phone number is valid and properly formatted
     */
    public VerificationResult verifyPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return new VerificationResult(false, "Phone number cannot be empty");
        }
        
        String cleanedNumber = cleanPhoneNumber(phoneNumber);
        
        // Check if it's a valid emergency service number
        if (isEmergencyServiceNumber(cleanedNumber)) {
            return new VerificationResult(true, "Valid emergency service number");
        }
        
        // Check if it's a valid Uganda number
        if (UGANDA_PHONE_PATTERN.matcher(cleanedNumber).matches()) {
            return new VerificationResult(true, "Valid Uganda phone number");
        }
        
        // Check if it's a valid Kenya number
        if (KENYA_PHONE_PATTERN.matcher(cleanedNumber).matches()) {
            return new VerificationResult(true, "Valid Kenya phone number");
        }
        
        // Check if it's a valid international number
        if (INTERNATIONAL_PHONE_PATTERN.matcher(cleanedNumber).matches()) {
            return new VerificationResult(true, "Valid international phone number");
        }
        
        return new VerificationResult(false, "Invalid phone number format");
    }
    
    /**
     * Format phone number for consistent storage
     */
    public String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return "";
        }
        
        String cleaned = cleanPhoneNumber(phoneNumber);
        
        // Format Uganda numbers
        if (UGANDA_PHONE_PATTERN.matcher(cleaned).matches()) {
            if (cleaned.startsWith("0")) {
                return "+256" + cleaned.substring(1);
            } else if (cleaned.startsWith("256")) {
                return "+" + cleaned;
            } else if (cleaned.startsWith("+256")) {
                return cleaned;
            }
        }
        
        // Format Kenya numbers
        if (KENYA_PHONE_PATTERN.matcher(cleaned).matches()) {
            if (cleaned.startsWith("0")) {
                return "+254" + cleaned.substring(1);
            } else if (cleaned.startsWith("254")) {
                return "+" + cleaned;
            } else if (cleaned.startsWith("+254")) {
                return cleaned;
            }
        }
        
        // Return as is for international numbers
        return cleaned;
    }
    
    /**
     * Check if number is reachable (basic validation)
     */
    public boolean isNumberReachable(String phoneNumber) {
        if (!verifyPhoneNumber(phoneNumber).isValid()) {
            return false;
        }
        
        // Check if it's not the same as the device's own number
        String deviceNumber = getDevicePhoneNumber();
        if (deviceNumber != null && formatPhoneNumber(phoneNumber).equals(formatPhoneNumber(deviceNumber))) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validate emergency contact data
     */
    public ValidationResult validateEmergencyContact(String name, String phoneNumber1, String phoneNumber2) {
        ValidationResult result = new ValidationResult();
        
        // Validate name
        if (name == null || name.trim().length() < 2) {
            result.addError("Name must be at least 2 characters long");
        }
        
        // Validate at least one phone number
        boolean hasValidNumber = false;
        
        if (phoneNumber1 != null && !phoneNumber1.trim().isEmpty()) {
            VerificationResult phone1Result = verifyPhoneNumber(phoneNumber1);
            if (!phone1Result.isValid()) {
                result.addError("Primary number: " + phone1Result.getMessage());
            } else {
                hasValidNumber = true;
            }
        }
        
        if (phoneNumber2 != null && !phoneNumber2.trim().isEmpty()) {
            VerificationResult phone2Result = verifyPhoneNumber(phoneNumber2);
            if (!phone2Result.isValid()) {
                result.addError("Secondary number: " + phone2Result.getMessage());
            } else {
                hasValidNumber = true;
            }
        }
        
        if (!hasValidNumber) {
            result.addError("At least one valid phone number is required");
        }
        
        // Check for duplicate numbers
        if (phoneNumber1 != null && phoneNumber2 != null && 
            !phoneNumber1.trim().isEmpty() && !phoneNumber2.trim().isEmpty()) {
            String formatted1 = formatPhoneNumber(phoneNumber1);
            String formatted2 = formatPhoneNumber(phoneNumber2);
            if (formatted1.equals(formatted2)) {
                result.addError("Emergency numbers cannot be the same");
            }
        }
        
        return result;
    }
    
    /**
     * Clean phone number by removing non-digit characters except +
     */
    private String cleanPhoneNumber(String phoneNumber) {
        return phoneNumber.replaceAll("[^+\\d]", "");
    }
    
    /**
     * Check if number is an emergency service number
     */
    private boolean isEmergencyServiceNumber(String phoneNumber) {
        String[] emergencyNumbers = {
            "112", "911", "999", "000", "110", "119", "118", "117", "116", "115",
            "114", "113", "111", "100", "101", "102", "103", "104", "105", "106",
            "107", "108", "109", "120", "121", "122", "123", "124", "125", "126",
            "127", "128", "129", "130", "131", "132", "133", "134", "135", "136",
            "137", "138", "139", "140", "141", "142", "143", "144", "145", "146",
            "147", "148", "149", "150", "151", "152", "153", "154", "155", "156",
            "157", "158", "159", "160", "161", "162", "163", "164", "165", "166",
            "167", "168", "169", "170", "171", "172", "173", "174", "175", "176",
            "177", "178", "179", "180", "181", "182", "183", "184", "185", "186",
            "187", "188", "189", "190", "191", "192", "193", "194", "195", "196",
            "197", "198", "199"
        };
        
        for (String emergency : emergencyNumbers) {
            if (phoneNumber.endsWith(emergency)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get device's own phone number
     */
    private String getDevicePhoneNumber() {
        try {
            if (telephonyManager != null) {
                return telephonyManager.getLine1Number();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Cannot access device phone number: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Verification result class
     */
    public static class VerificationResult {
        private boolean valid;
        private String message;
        
        public VerificationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
    
    /**
     * Validation result class for multiple errors
     */
    public static class ValidationResult {
        private java.util.List<String> errors = new java.util.ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }
        
        public String getErrorMessage() {
            return String.join("\n", errors);
        }
    }
}
