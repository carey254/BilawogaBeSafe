package com.example.bilawoga.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

/**
 * SCREEN SECURITY MANAGER
 * Prevents screenshots, screen recording, and clipboard copying
 */
public class ScreenSecurityManager {
    private static final String TAG = "ScreenSecurityManager";
    
    /**
     * Enable screenshot and screen recording prevention
     * Call this in onCreate() of activities
     */
    public static void preventScreenshots(Activity activity) {
        try {
            Window window = activity.getWindow();
            if (window != null) {
                // FLAG_SECURE prevents screenshots and screen recording
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                );
                Log.d(TAG, "Screenshot prevention enabled for: " + activity.getClass().getSimpleName());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enabling screenshot prevention: " + e.getMessage());
        }
    }
    
    /**
     * Clear clipboard to prevent data leakage
     * Call this when app goes to background or sensitive data is displayed
     */
    public static void clearClipboard(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                // Clear clipboard by setting empty clip
                ClipData clip = ClipData.newPlainText("", "");
                clipboard.setPrimaryClip(clip);
                Log.d(TAG, "Clipboard cleared");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing clipboard: " + e.getMessage());
        }
    }
    
    /**
     * Monitor clipboard for sensitive data and clear if found
     */
    public static void monitorClipboard(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clipData = clipboard.getPrimaryClip();
                if (clipData != null && clipData.getItemCount() > 0) {
                    CharSequence text = clipData.getItemAt(0).getText();
                    if (text != null) {
                        String clipboardText = text.toString();
                        // Check if clipboard contains emergency contact patterns
                        if (containsSensitiveData(clipboardText)) {
                            clearClipboard(context);
                            Log.w(TAG, "Sensitive data detected in clipboard - cleared");
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error monitoring clipboard: " + e.getMessage());
        }
    }
    
    /**
     * Check if text contains sensitive data (phone numbers, emergency contacts)
     */
    private static boolean containsSensitiveData(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        
        // Check for phone number patterns
        String phonePattern = ".*\\+?[0-9]{7,15}.*";
        if (text.matches(phonePattern)) {
            return true;
        }
        
        // Check for emergency-related keywords
        String[] sensitiveKeywords = {
            "EMERGENCY", "SOS", "emergency", "sos",
            "ENUM_1", "ENUM_2", "emergency contact"
        };
        
        String upperText = text.toUpperCase();
        for (String keyword : sensitiveKeywords) {
            if (upperText.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Disable text selection and copying in views
     * Call this on TextViews that display sensitive data (NOT on EditTexts - users need to edit)
     */
    public static void disableTextSelection(android.view.View view) {
        try {
            // Only disable selection on TextViews, not EditTexts (users need to edit their data)
            if (view instanceof android.widget.TextView && !(view instanceof android.widget.EditText)) {
                android.widget.TextView textView = (android.widget.TextView) view;
                // Disable text selection
                textView.setTextIsSelectable(false);
                // Disable long press context menu
                textView.setLongClickable(false);
                // Disable copy/paste
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    textView.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                        @Override
                        public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                            return false; // Disable action mode (copy/paste menu)
                        }
                        
                        @Override
                        public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) {
                            return false;
                        }
                        
                        @Override
                        public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) {
                            return false;
                        }
                        
                        @Override
                        public void onDestroyActionMode(android.view.ActionMode mode) {
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error disabling text selection: " + e.getMessage());
        }
    }
}

