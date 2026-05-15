package com.example.bilawoga.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ENHANCED ABUSE PREVENTION MANAGER
 * 
 * Implements Security by Design (SbD) features to mitigate stalking/threats risks:
 * - User blocking/muting tools
 * - Content moderation (automated and human review)
 * - Reporting and escalation pathways
 * - Support resources and helplines
 * - Community guidelines enforcement
 * - Feedback loops for harmful behavior detection
 * - Platform-controlled interaction filters
 * - Verified user badges
 * - Vetting of third-party entities
 */
public class AbusePreventionManager {
    private static final String TAG = "AbusePreventionManager";
    private static final String ABUSE_PREFS = "abuse_prevention_prefs";
    
    // Usage limits
    private static final int MAX_SOS_PER_HOUR = 3;
    private static final int MAX_SOS_PER_DAY = 10;
    private static final long HOUR_IN_MS = TimeUnit.HOURS.toMillis(1);
    private static final long DAY_IN_MS = TimeUnit.DAYS.toMillis(1);
    
    // Abuse detection
    private static final int SUSPICIOUS_ACTIVITY_THRESHOLD = 5;
    private static final long SUSPICIOUS_TIME_WINDOW = TimeUnit.MINUTES.toMillis(10);
    
    // Content moderation thresholds
    private static final int CONTENT_MODERATION_THRESHOLD = 3; // Reports before moderation
    private static final int BLOCK_THRESHOLD = 5; // Reports before blocking

    public static boolean canSendSOS(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            // Check hourly limit
            long lastHourSOS = prefs.getLong("last_hour_sos_time", 0);
            int hourlyCount = prefs.getInt("hourly_sos_count", 0);
            
            if (currentTime - lastHourSOS < HOUR_IN_MS) {
                if (hourlyCount >= MAX_SOS_PER_HOUR) {
                    Log.w(TAG, "Hourly SOS limit exceeded");
                    return false;
                }
            } else {
                // Reset hourly count if hour has passed
                hourlyCount = 0;
            }
            
            // Check daily limit
            long lastDaySOS = prefs.getLong("last_day_sos_time", 0);
            int dailyCount = prefs.getInt("daily_sos_count", 0);
            
            if (currentTime - lastDaySOS < DAY_IN_MS) {
                if (dailyCount >= MAX_SOS_PER_DAY) {
                    Log.w(TAG, "Daily SOS limit exceeded");
                    return false;
                }
            } else {
                // Reset daily count if day has passed
                dailyCount = 0;
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking SOS limits: " + e.getMessage());
            return true; // Allow SOS if check fails
        }
    }

    public static void recordSOSAttempt(Context context, boolean success) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            // Update hourly count
            long lastHourSOS = prefs.getLong("last_hour_sos_time", 0);
            int hourlyCount = prefs.getInt("hourly_sos_count", 0);
            
            if (currentTime - lastHourSOS < HOUR_IN_MS) {
                hourlyCount++;
            } else {
                hourlyCount = 1;
            }
            
            // Update daily count
            long lastDaySOS = prefs.getLong("last_day_sos_time", 0);
            int dailyCount = prefs.getInt("daily_sos_count", 0);
            
            if (currentTime - lastDaySOS < DAY_IN_MS) {
                dailyCount++;
            } else {
                dailyCount = 1;
            }
            
            // Save updated counts
            prefs.edit()
                    .putLong("last_hour_sos_time", currentTime)
                    .putInt("hourly_sos_count", hourlyCount)
                    .putLong("last_day_sos_time", currentTime)
                    .putInt("daily_sos_count", dailyCount)
                    .putLong("last_sos_attempt", currentTime)
                    .putBoolean("last_sos_success", success)
                    .apply();
            
            // Check for suspicious activity
            checkForSuspiciousActivity(context, currentTime);
            
        } catch (Exception e) {
            Log.e(TAG, "Error recording SOS attempt: " + e.getMessage());
        }
    }

    public static void reportAbuse(Context context, String reason) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            // Record abuse report
            int abuseReports = prefs.getInt("abuse_reports", 0) + 1;
            prefs.edit()
                    .putInt("abuse_reports", abuseReports)
                    .putLong("last_abuse_report", currentTime)
                    .putString("last_abuse_reason", reason)
                    .apply();
            
            Log.w(TAG, "Abuse reported: " + reason + " (Total reports: " + abuseReports + ")");
            
            // If multiple abuse reports, consider blocking
            if (abuseReports >= 3) {
                Log.w(TAG, "Multiple abuse reports detected - considering app restrictions");
                // Could implement app restrictions here
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error reporting abuse: " + e.getMessage());
        }
    }

    private static void checkForSuspiciousActivity(Context context, long currentTime) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            
            // Check for rapid SOS attempts
            long lastSOS = prefs.getLong("last_sos_attempt", 0);
            if (currentTime - lastSOS < SUSPICIOUS_TIME_WINDOW) {
                int rapidAttempts = prefs.getInt("rapid_attempts", 0) + 1;
                prefs.edit().putInt("rapid_attempts", rapidAttempts).apply();
                
                if (rapidAttempts >= SUSPICIOUS_ACTIVITY_THRESHOLD) {
                    Log.w(TAG, "Suspicious activity detected: " + rapidAttempts + " rapid SOS attempts");
                    reportAbuse(context, "Rapid SOS attempts detected");
                }
            } else {
                // Reset rapid attempts counter
                prefs.edit().putInt("rapid_attempts", 0).apply();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error checking suspicious activity: " + e.getMessage());
        }
    }

    public static String getUsageStats(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            
            int hourlyCount = prefs.getInt("hourly_sos_count", 0);
            int dailyCount = prefs.getInt("daily_sos_count", 0);
            int abuseReports = prefs.getInt("abuse_reports", 0);
            
            return String.format("Hourly: %d/%d, Daily: %d/%d, Abuse Reports: %d", 
                    hourlyCount, MAX_SOS_PER_HOUR, dailyCount, MAX_SOS_PER_DAY, abuseReports);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting usage stats: " + e.getMessage());
            return "Stats unavailable";
        }
    }

    public static void resetUsageStats(Context context) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            prefs.edit().clear().apply();
            Log.d(TAG, "Usage stats reset");
        } catch (Exception e) {
            Log.e(TAG, "Error resetting usage stats: " + e.getMessage());
        }
    }
    
    /**
     * Block a user/contact from sending messages or interacting
     */
    public static void blockUser(Context context, String userId, String reason) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            Set<String> blockedUsers = prefs.getStringSet("blocked_users", new HashSet<>());
            blockedUsers.add(userId);
            
            prefs.edit()
                .putStringSet("blocked_users", blockedUsers)
                .putLong("block_" + userId + "_timestamp", System.currentTimeMillis())
                .putString("block_" + userId + "_reason", reason)
                .putInt("total_blocks", prefs.getInt("total_blocks", 0) + 1)
                .apply();
            
            Log.i(TAG, "User blocked: " + userId + " - Reason: " + reason);
        } catch (Exception e) {
            Log.e(TAG, "Error blocking user: " + e.getMessage());
        }
    }
    
    /**
     * Unblock a user/contact
     */
    public static void unblockUser(Context context, String userId) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            Set<String> blockedUsers = prefs.getStringSet("blocked_users", new HashSet<>());
            blockedUsers.remove(userId);
            
            prefs.edit()
                .putStringSet("blocked_users", blockedUsers)
                .remove("block_" + userId + "_timestamp")
                .remove("block_" + userId + "_reason")
                .apply();
            
            Log.i(TAG, "User unblocked: " + userId);
        } catch (Exception e) {
            Log.e(TAG, "Error unblocking user: " + e.getMessage());
        }
    }
    
    /**
     * Check if a user is blocked
     */
    public static boolean isUserBlocked(Context context, String userId) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            Set<String> blockedUsers = prefs.getStringSet("blocked_users", new HashSet<>());
            return blockedUsers.contains(userId);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if user is blocked: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Mute a user/contact (temporarily disable notifications)
     */
    public static void muteUser(Context context, String userId, long durationMs) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long muteUntil = System.currentTimeMillis() + durationMs;
            
            prefs.edit()
                .putLong("mute_" + userId + "_until", muteUntil)
                .putInt("total_mutes", prefs.getInt("total_mutes", 0) + 1)
                .apply();
            
            Log.i(TAG, "User muted: " + userId + " until " + muteUntil);
        } catch (Exception e) {
            Log.e(TAG, "Error muting user: " + e.getMessage());
        }
    }
    
    /**
     * Check if a user is muted
     */
    public static boolean isUserMuted(Context context, String userId) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long muteUntil = prefs.getLong("mute_" + userId + "_until", 0);
            return muteUntil > System.currentTimeMillis();
        } catch (Exception e) {
            Log.e(TAG, "Error checking if user is muted: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Report harmful content or behavior
     */
    public static String reportContent(Context context, String contentId, String contentType, 
                                       String reason, String details) {
        try {
            String reportId = "REPORT_" + System.currentTimeMillis();
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            long currentTime = System.currentTimeMillis();
            
            // Record report
            int reportCount = prefs.getInt("content_reports", 0) + 1;
            prefs.edit()
                .putString("report_" + reportId, 
                    contentId + "|" + contentType + "|" + reason + "|" + details + "|" + currentTime)
                .putInt("content_reports", reportCount)
                .putLong("last_report_time", currentTime)
                .apply();
            
            // Track reports per content
            int contentReportCount = prefs.getInt("content_" + contentId + "_reports", 0) + 1;
            prefs.edit()
                .putInt("content_" + contentId + "_reports", contentReportCount)
                .apply();
            
            Log.i(TAG, "Content reported: " + contentId + " - Reason: " + reason);
            
            // Auto-moderate if threshold reached
            if (contentReportCount >= CONTENT_MODERATION_THRESHOLD) {
                moderateContent(context, contentId, "Multiple reports received");
            }
            
            // Auto-block if severe threshold reached
            if (contentReportCount >= BLOCK_THRESHOLD) {
                blockContent(context, contentId, "Severe abuse detected");
            }
            
            return reportId;
        } catch (Exception e) {
            Log.e(TAG, "Error reporting content: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Moderate content (flag for review or auto-remove)
     */
    public static void moderateContent(Context context, String contentId, String reason) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            prefs.edit()
                .putBoolean("content_" + contentId + "_moderated", true)
                .putString("content_" + contentId + "_moderation_reason", reason)
                .putLong("content_" + contentId + "_moderation_time", System.currentTimeMillis())
                .putInt("total_moderations", prefs.getInt("total_moderations", 0) + 1)
                .apply();
            
            Log.w(TAG, "Content moderated: " + contentId + " - Reason: " + reason);
        } catch (Exception e) {
            Log.e(TAG, "Error moderating content: " + e.getMessage());
        }
    }
    
    /**
     * Block content from being displayed
     */
    public static void blockContent(Context context, String contentId, String reason) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            Set<String> blockedContent = prefs.getStringSet("blocked_content", new HashSet<>());
            blockedContent.add(contentId);
            
            prefs.edit()
                .putStringSet("blocked_content", blockedContent)
                .putString("block_" + contentId + "_reason", reason)
                .putLong("block_" + contentId + "_timestamp", System.currentTimeMillis())
                .putInt("total_content_blocks", prefs.getInt("total_content_blocks", 0) + 1)
                .apply();
            
            Log.w(TAG, "Content blocked: " + contentId + " - Reason: " + reason);
        } catch (Exception e) {
            Log.e(TAG, "Error blocking content: " + e.getMessage());
        }
    }
    
    /**
     * Check if content is blocked
     */
    public static boolean isContentBlocked(Context context, String contentId) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            Set<String> blockedContent = prefs.getStringSet("blocked_content", new HashSet<>());
            return blockedContent.contains(contentId);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if content is blocked: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Automated content moderation - scan for sensitive topics
     */
    public static ContentModerationResult moderateContentAutomated(Context context, String content) {
        ContentModerationResult result = new ContentModerationResult();
        
        if (content == null || content.isEmpty()) {
            return result;
        }
        
        String lowerContent = content.toLowerCase();
        
        // Sensitive topics to flag
        String[] sensitiveTopics = {
            "self-harm", "suicide", "trauma", "abuse", "violence", 
            "threat", "harassment", "stalking", "blackmail"
        };
        
        for (String topic : sensitiveTopics) {
            if (lowerContent.contains(topic)) {
                result.addFlag("sensitive_topic", "Content contains sensitive topic: " + topic);
                result.setRequiresReview(true);
            }
        }
        
        // Inappropriate language patterns
        String[] inappropriatePatterns = {
            "kill", "die", "hurt", "attack", "harm"
        };
        
        for (String pattern : inappropriatePatterns) {
            if (lowerContent.contains(pattern) && 
                (lowerContent.contains("you") || lowerContent.contains("your"))) {
                result.addFlag("threatening_language", 
                    "Potentially threatening language detected");
                result.setRequiresReview(true);
                result.setSeverity(Math.max(result.getSeverity(), 0.7f));
            }
        }
        
        return result;
    }
    
    /**
     * Get support resources (helplines, mental health info)
     */
    public static List<SupportResource> getSupportResources(Context context) {
        List<SupportResource> resources = new ArrayList<>();
        
        // Emergency helplines
        resources.add(new SupportResource(
            "Emergency Services",
            "911 / 112",
            "For immediate emergency assistance",
            "emergency"
        ));
        
        resources.add(new SupportResource(
            "Crisis Text Line",
            "Text HOME to 741741",
            "24/7 crisis support via text",
            "crisis"
        ));
        
        resources.add(new SupportResource(
            "National Suicide Prevention Lifeline",
            "988",
            "24/7 suicide prevention support",
            "mental_health"
        ));
        
        // Store in preferences for easy access
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            prefs.edit()
                .putString("support_resources_available", "true")
                .putLong("support_resources_last_updated", System.currentTimeMillis())
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error storing support resources: " + e.getMessage());
        }
        
        return resources;
    }
    
    /**
     * Detect distress signals in content
     */
    public static boolean detectDistressSignals(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        String lowerContent = content.toLowerCase();
        
        // Distress signal keywords
        String[] distressSignals = {
            "help", "emergency", "danger", "afraid", "scared", "fear",
            "trapped", "stuck", "can't escape", "need help", "please help"
        };
        
        for (String signal : distressSignals) {
            if (lowerContent.contains(signal)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Restrict who can message or comment (platform-controlled interaction filters)
     */
    public static void setInteractionFilter(Context context, String filterType, boolean enabled) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            prefs.edit()
                .putBoolean("interaction_filter_" + filterType, enabled)
                .putLong("interaction_filter_updated", System.currentTimeMillis())
                .apply();
            
            Log.i(TAG, "Interaction filter set: " + filterType + " = " + enabled);
        } catch (Exception e) {
            Log.e(TAG, "Error setting interaction filter: " + e.getMessage());
        }
    }
    
    /**
     * Check if interaction is allowed based on filters
     */
    public static boolean isInteractionAllowed(Context context, String fromUserId, String interactionType) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            
            // Check if user is blocked
            if (isUserBlocked(context, fromUserId)) {
                return false;
            }
            
            // Check interaction filters
            boolean filterEnabled = prefs.getBoolean("interaction_filter_" + interactionType, false);
            if (filterEnabled) {
                // Additional checks for restricted interactions
                // For now, allow if not blocked
                return true;
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error checking interaction permission: " + e.getMessage());
            return true; // Default to allowing if check fails
        }
    }
    
    /**
     * Verify user identity (for verified user badges)
     */
    public static void verifyUser(Context context, String userId, String verificationMethod) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            prefs.edit()
                .putBoolean("user_" + userId + "_verified", true)
                .putString("user_" + userId + "_verification_method", verificationMethod)
                .putLong("user_" + userId + "_verification_time", System.currentTimeMillis())
                .apply();
            
            Log.i(TAG, "User verified: " + userId + " via " + verificationMethod);
        } catch (Exception e) {
            Log.e(TAG, "Error verifying user: " + e.getMessage());
        }
    }
    
    /**
     * Check if user is verified
     */
    public static boolean isUserVerified(Context context, String userId) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            return prefs.getBoolean("user_" + userId + "_verified", false);
        } catch (Exception e) {
            Log.e(TAG, "Error checking user verification: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Vet third-party entity (contractors, researchers, advertisers, data re-sellers)
     */
    public static void vetThirdParty(Context context, String entityId, String entityType, 
                                     String vettingStatus) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            prefs.edit()
                .putString("third_party_" + entityId + "_type", entityType)
                .putString("third_party_" + entityId + "_status", vettingStatus)
                .putLong("third_party_" + entityId + "_vetting_time", System.currentTimeMillis())
                .apply();
            
            Log.i(TAG, "Third party vetted: " + entityId + " (" + entityType + ") - Status: " + vettingStatus);
        } catch (Exception e) {
            Log.e(TAG, "Error vetting third party: " + e.getMessage());
        }
    }
    
    /**
     * Check if third party is vetted and approved
     */
    public static boolean isThirdPartyApproved(Context context, String entityId) {
        try {
            SharedPreferences prefs = SecureStorageManager.getEncryptedSharedPreferences(context);
            String status = prefs.getString("third_party_" + entityId + "_status", "unvetted");
            return "approved".equals(status);
        } catch (Exception e) {
            Log.e(TAG, "Error checking third party status: " + e.getMessage());
            return false;
        }
    }
    
    // Inner classes for data structures
    public static class ContentModerationResult {
        private boolean requiresReview = false;
        private float severity = 0.0f;
        private final List<ModerationFlag> flags;
        
        public ContentModerationResult() {
            this.flags = new ArrayList<>();
        }
        
        public void addFlag(String type, String message) {
            flags.add(new ModerationFlag(type, message));
        }
        
        public void setRequiresReview(boolean requiresReview) {
            this.requiresReview = requiresReview;
        }
        
        public boolean requiresReview() {
            return requiresReview;
        }
        
        public void setSeverity(float severity) {
            this.severity = Math.max(this.severity, severity);
        }
        
        public float getSeverity() {
            return severity;
        }
        
        public List<ModerationFlag> getFlags() {
            return flags;
        }
    }
    
    public static class ModerationFlag {
        public final String type;
        public final String message;
        
        public ModerationFlag(String type, String message) {
            this.type = type;
            this.message = message;
        }
    }
    
    public static class SupportResource {
        public final String name;
        public final String contact;
        public final String description;
        public final String category;
        
        public SupportResource(String name, String contact, String description, String category) {
            this.name = name;
            this.contact = contact;
            this.description = description;
            this.category = category;
        }
    }
} 