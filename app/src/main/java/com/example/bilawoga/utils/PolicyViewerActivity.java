package com.example.bilawoga.utils;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bilawoga.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Locale;

public class PolicyViewerActivity extends AppCompatActivity {
    private WebView webView;
    private TextView titleText;
    private Button backButton;
    private Button acceptButton;
    private TextView errorMessage;
    
    // Accessibility features
    private FloatingActionButton accessibilityFab;
    private TextToSpeech tts;
    private boolean isHighContrast = false;
    private boolean isLargeText = false;
    private boolean isReadingGuide = false;
    private boolean isAudioActive = false;
    private Handler audioHandler = new Handler(Looper.getMainLooper());
    private Runnable audioRunnable;

    public static final String EXTRA_POLICY_TYPE = "policy_type";
    public static final String POLICY_TYPE_PRIVACY = "privacy";
    public static final String POLICY_TYPE_TERMS = "terms";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_policy_viewer);

        Log.d("PolicyViewerActivity", "onCreate called");
        Toast.makeText(this, "PolicyViewerActivity launched", Toast.LENGTH_SHORT).show();

        webView = findViewById(R.id.policy_webview);
        webView.setBackgroundColor(android.graphics.Color.WHITE);
        webView.setLayerType(WebView.LAYER_TYPE_SOFTWARE, null); // Prevents dark mode override
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            webView.getSettings().setForceDark(android.webkit.WebSettings.FORCE_DARK_OFF);
        }
        titleText = findViewById(R.id.policy_title);
        backButton = findViewById(R.id.policy_back_button);
        acceptButton = findViewById(R.id.policy_accept_button);
        errorMessage = findViewById(R.id.policy_error_message);
        errorMessage.setVisibility(View.GONE);

        String policyType = getIntent().getStringExtra(EXTRA_POLICY_TYPE);
        
        if (POLICY_TYPE_PRIVACY.equals(policyType)) {
            showPrivacyPolicy();
        } else if (POLICY_TYPE_TERMS.equals(policyType)) {
            showTermsOfUse();
        }

        setupWebView();
        setupButtons();
        setupAccessibilityFeatures();
    }

    private void setupAccessibilityFeatures() {
        // Setup accessibility FAB
        accessibilityFab = findViewById(R.id.accessibilityFab);
        if (accessibilityFab != null) {
            accessibilityFab.setOnClickListener(v -> showAccessibilityDialog());
        }
        
        // Setup Text-to-Speech
        setupTextToSpeech();
        
        // Apply initial accessibility settings
        applyAccessibilitySettings();
    }

    private void setupTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Locale ukLocale = Locale.UK;
                int result = tts.setLanguage(ukLocale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "UK English voice not available. Please install UK English TTS in your device settings.", Toast.LENGTH_LONG).show();
                }
                tts.setSpeechRate(1.0f);
                tts.setPitch(1.0f);
            }
        });
    }

    private void showAccessibilityDialog() {
        // Import the dialog layout and show accessibility options
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_accessibility_options, null);
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);
        
        // Set dialog to be smaller and positioned properly
        dialog.getWindow().setLayout(
            (int) (getResources().getDisplayMetrics().widthPixels * 0.85), // 85% of screen width
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dialog.getWindow().setGravity(android.view.Gravity.CENTER);

        // Setup dialog buttons
        setupAccessibilityDialogButtons(dialog, dialogView);
        
        dialog.show();
    }

    private void setupAccessibilityDialogButtons(android.app.Dialog dialog, View dialogView) {
        // Cancel button
        dialogView.findViewById(R.id.btnCancelAccessibility).setOnClickListener(v -> {
            dialog.dismiss();
            if (tts != null) {
                tts.speak("Accessibility options closed", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        // Text-to-Speech buttons
        dialogView.findViewById(R.id.btnStartTTS).setOnClickListener(v -> {
            startTextToSpeech();
            Toast.makeText(this, "Text-to-Speech started", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnStopAudio).setOnClickListener(v -> {
            stopAudio();
            Toast.makeText(this, "Audio stopped", Toast.LENGTH_SHORT).show();
        });

        // Visual accessibility buttons
        dialogView.findViewById(R.id.btnHighContrast).setOnClickListener(v -> {
            toggleHighContrast();
            Toast.makeText(this, "High Contrast Toggled", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnIncreaseTextSize).setOnClickListener(v -> {
            toggleTextSize();
            Toast.makeText(this, "Text Size Toggled", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnReadingGuide).setOnClickListener(v -> {
            toggleReadingGuide();
            Toast.makeText(this, "Reading Guide Toggled", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnReadPage).setOnClickListener(v -> {
            speakPolicyContent();
            Toast.makeText(this, "Reading Policy Content", Toast.LENGTH_SHORT).show();
        });

        dialogView.findViewById(R.id.btnResetAccessibility).setOnClickListener(v -> {
            resetAccessibilitySettings();
            Toast.makeText(this, "All settings reset", Toast.LENGTH_SHORT).show();
        });
    }

    private void startTextToSpeech() {
        if (isAudioActive) {
            stopAudio();
        }
        
        isAudioActive = true;
        audioRunnable = () -> {
            if (isAudioActive && tts != null) {
                speakPolicyContent();
                audioHandler.postDelayed(audioRunnable, 30000); // Repeat every 30 seconds
            }
        };
        audioHandler.post(audioRunnable);
    }

    private void stopAudio() {
        isAudioActive = false;
        if (audioHandler != null && audioRunnable != null) {
            audioHandler.removeCallbacks(audioRunnable);
        }
        if (tts != null) {
            tts.stop();
        }
    }

    private void speakPolicyContent() {
        if (tts == null) return;
        
        StringBuilder content = new StringBuilder();
        content.append("Policy Viewer Screen. ");
        
        if (titleText != null) {
            content.append("Current policy: ").append(titleText.getText()).append(". ");
        }
        
        content.append("This screen displays the complete policy document. ");
        content.append("You can scroll through the content to read all sections. ");
        content.append("The policy contains important information about data collection, usage, and your rights. ");
        content.append("Please read the entire policy carefully before proceeding. ");
        
        content.append("Navigation options: ");
        content.append("Back button: Returns to the previous screen without accepting the policy. ");
        content.append("Accept button: Acknowledges that you have read and agree to the policy terms. ");
        content.append("Accessibility button: Opens additional accessibility options including text-to-speech, high contrast, and text size adjustments. ");
        
        content.append("Policy content summary: ");
        content.append("The policy explains how your personal information is collected, stored, and used. ");
        content.append("It details your rights regarding data access, correction, and deletion. ");
        content.append("Emergency contact information is stored securely and used only for emergency situations. ");
        content.append("Location data is collected only when you activate emergency alerts. ");
        content.append("Audio monitoring features are explained and your consent is required. ");
        
        content.append("Important: By accepting this policy, you agree to the terms and conditions outlined. ");
        content.append("You can review this policy at any time through the app settings. ");
        
        tts.speak(content.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
    }
    
    private void speakFormInstructions() {
        if (tts == null) return;
        
        StringBuilder instructions = new StringBuilder();
        instructions.append("Form Instructions. ");
        instructions.append("Please fill in all required fields. ");
        instructions.append("Name field: Enter your full name as it appears on official documents. ");
        instructions.append("Primary emergency contact: Enter the phone number of your most trusted emergency contact. ");
        instructions.append("Secondary emergency contact: Enter an alternative emergency contact number. ");
        instructions.append("Incident type: Select the type of emergency from the dropdown menu. ");
        instructions.append("If your emergency type is not listed, use the text field to describe it. ");
        instructions.append("All fields marked with an asterisk are required. ");
        instructions.append("Tap the save button when you have completed all fields. ");
        
        tts.speak(instructions.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
    }
    
    private void speakPopupContent(String popupTitle, String popupMessage) {
        if (tts == null) return;
        
        StringBuilder popupContent = new StringBuilder();
        popupContent.append("Popup Alert. ");
        popupContent.append("Title: ").append(popupTitle).append(". ");
        popupContent.append("Message: ").append(popupMessage).append(". ");
        popupContent.append("Please read this information carefully. ");
        
        tts.speak(popupContent.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
    }
    
    private void speakErrorMessages(String errorMessage) {
        if (tts == null) return;
        
        StringBuilder errorContent = new StringBuilder();
        errorContent.append("Error Alert. ");
        errorContent.append("Error message: ").append(errorMessage).append(". ");
        errorContent.append("Please try again or contact support if the problem persists. ");
        
        tts.speak(errorContent.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
    }
    
    private void speakSuccessMessages(String successMessage) {
        if (tts == null) return;
        
        StringBuilder successContent = new StringBuilder();
        successContent.append("Success Alert. ");
        successContent.append("Success message: ").append(successMessage).append(". ");
        successContent.append("Your action has been completed successfully. ");
        
        tts.speak(successContent.toString(), TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void toggleHighContrast() {
        isHighContrast = !isHighContrast;
        applyAccessibilitySettings();
    }

    private void toggleTextSize() {
        isLargeText = !isLargeText;
        applyAccessibilitySettings();
    }

    private void toggleReadingGuide() {
        isReadingGuide = !isReadingGuide;
        applyAccessibilitySettings();
    }

    private void applyAccessibilitySettings() {
        // Apply high contrast
        if (isHighContrast) {
            findViewById(android.R.id.content).setBackgroundColor(Color.BLACK);
            titleText.setTextColor(Color.WHITE);
            backButton.setTextColor(Color.WHITE);
            acceptButton.setTextColor(Color.WHITE);
            errorMessage.setTextColor(Color.YELLOW);
        } else {
            findViewById(android.R.id.content).setBackgroundColor(Color.WHITE);
            titleText.setTextColor(Color.WHITE); // Keep white for header
            backButton.setTextColor(Color.WHITE);
            acceptButton.setTextColor(Color.WHITE);
            errorMessage.setTextColor(Color.RED);
        }
        
        // Apply large text
        float textSize = isLargeText ? 20f : 16f;
        titleText.setTextSize(isLargeText ? 28f : 20f);
        backButton.setTextSize(textSize);
        acceptButton.setTextSize(textSize);
        errorMessage.setTextSize(textSize);
        
        // Apply reading guide
        if (isReadingGuide) {
            webView.setBackgroundColor(Color.YELLOW);
        } else {
            webView.setBackgroundColor(Color.WHITE);
        }
    }

    private void resetAccessibilitySettings() {
        stopAudio();
        isHighContrast = false;
        isLargeText = false;
        isReadingGuide = false;
        applyAccessibilitySettings();
    }

    private void showPrivacyPolicy() {
        titleText.setText("Privacy Policy");
        
        // Create WCAG-compliant HTML content with semantic structure
        String htmlContent = "<html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<style>" +
            "body { " +
            "  color: #000000 !important; " +
            "  background-color: #FFFFFF !important; " +
            "  font-family: Arial, sans-serif; " +
            "  padding: 16px; " +
            "  line-height: 1.6; " +
            "  margin: 0; " +
            "  font-size: 16px; " +
            "}" +
            "h1, h2, h3 { " +
            "  color: #1976D2 !important; " +
            "  margin-top: 1.5em; " +
            "  margin-bottom: 0.5em; " +
            "  font-weight: bold; " +
            "}" +
            "h1 { font-size: 24px; }" +
            "h2 { font-size: 20px; }" +
            "h3 { font-size: 18px; }" +
            ".important { " +
            "  background-color: #E3F2FD; " +
            "  padding: 12px; " +
            "  border-left: 4px solid #1976D2; " +
            "  margin: 16px 0; " +
            "  border-radius: 4px; " +
            "}" +
            "p { " +
            "  margin: 8px 0; " +
            "  color: #333333 !important; " +
            "}" +
            "a { " +
            "  color: #1976D2; " +
            "  text-decoration: underline; " +
            "}" +
            "a:visited { " +
            "  color: #7B1FA2; " +
            "}" +
            "a:focus { " +
            "  outline: 2px solid #1976D2; " +
            "  outline-offset: 2px; " +
            "}" +
            "</style>" +
            "</head><body>" +
            "<main role=\"main\">" +
            "<h1>Privacy Policy</h1>" +
            getString(R.string.privacy_policy_html) + 
            "</main></body></html>";
            
        // Load the content directly
        webView.loadDataWithBaseURL("about:blank", htmlContent, "text/html", "UTF-8", null);
    }

    private void showTermsOfUse() {
        titleText.setText("Terms of Use");
        
        // Create WCAG-compliant HTML content with semantic structure
        String htmlContent = "<html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
            "<style>" +
            "body { " +
            "  color: #000000 !important; " +
            "  background-color: #FFFFFF !important; " +
            "  font-family: Arial, sans-serif; " +
            "  padding: 16px; " +
            "  line-height: 1.6; " +
            "  margin: 0; " +
            "  font-size: 16px; " +
            "}" +
            "h1, h2, h3 { " +
            "  color: #1976D2 !important; " +
            "  margin-top: 1.5em; " +
            "  margin-bottom: 0.5em; " +
            "  font-weight: bold; " +
            "}" +
            "h1 { font-size: 24px; }" +
            "h2 { font-size: 20px; }" +
            "h3 { font-size: 18px; }" +
            ".important { " +
            "  background-color: #E3F2FD; " +
            "  padding: 12px; " +
            "  border-left: 4px solid #1976D2; " +
            "  margin: 16px 0; " +
            "  border-radius: 4px; " +
            "}" +
            "p { " +
            "  margin: 8px 0; " +
            "  color: #333333 !important; " +
            "}" +
            "a { " +
            "  color: #1976D2; " +
            "  text-decoration: underline; " +
            "}" +
            "a:visited { " +
            "  color: #7B1FA2; " +
            "}" +
            "a:focus { " +
            "  outline: 2px solid #1976D2; " +
            "  outline-offset: 2px; " +
            "}" +
            "</style>" +
            "</head><body>" +
            "<main role=\"main\">" +
            "<h1>Terms of Use</h1>" +
            getString(R.string.terms_of_use_html) + 
            "</main></body></html>";
            
        // Load the content directly
        webView.loadDataWithBaseURL("about:blank", htmlContent, "text/html", "UTF-8", null);
    }

    private void setupWebView() {
        // Enable basic settings
        WebSettings settings = webView.getSettings();
        settings.setDefaultTextEncodingName("utf-8");
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        
        // Accessibility settings
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        
        // SECURITY: Configure secure settings
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        
        // Force light theme and disable dark mode
        webView.setBackgroundColor(Color.WHITE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            settings.setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        
        // Set up WebView client for error handling
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Announce page loaded for screen readers
                if (tts != null) {
                    tts.speak("Policy content loaded", TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
            
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.e("PolicyWebView", "Error loading page: " + error.getDescription());
                showError("Failed to load content. Please check your internet connection and try again.");
            }

            private void showError(String message) {
                errorMessage.setText(message);
                errorMessage.setVisibility(View.VISIBLE);
                if (tts != null) {
                    tts.speak("Error loading policy content", TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        });
        
        // SECURITY: Disable geolocation and database access
        webView.getSettings().setGeolocationEnabled(false);
        webView.getSettings().setDatabaseEnabled(false);
        
        // Safe display settings
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        
        // Force light theme and set text color explicitly
        webView.setBackgroundColor(0xFFFFFFFF); // White background
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            webView.getSettings().setForceDark(WebSettings.FORCE_DARK_OFF);
        }
        
        // Enable zoom controls
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        
        // SECURITY: Set secure WebViewClient with URL validation
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // SECURITY: Only allow local asset files
                if (url.startsWith("file:///android_asset/")) {
                    return false; // Allow loading
                }
                Log.w("PolicyViewerActivity", "Blocked external URL: " + url);
                return true; // Block external URLs
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                errorMessage.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e("PolicyViewerActivity", "WebView error: " + description + " for URL: " + failingUrl);
                errorMessage.setVisibility(View.VISIBLE);
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });
    }

    private void setupButtons() {
        // Add content descriptions for accessibility
        backButton.setContentDescription("Go back to previous screen");
        acceptButton.setContentDescription("Accept and acknowledge this policy");
        
        backButton.setOnClickListener(v -> finish());
        
        acceptButton.setOnClickListener(v -> {
            // Mark policy as accepted
            TermsOfUseManager.markPolicyAccepted(this, getIntent().getStringExtra(EXTRA_POLICY_TYPE));
            
            // Announce acceptance for screen readers
            if (tts != null) {
                tts.speak("Policy accepted", TextToSpeech.QUEUE_FLUSH, null, null);
            }
            
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (audioHandler != null && audioRunnable != null) {
            audioHandler.removeCallbacks(audioRunnable);
        }
    }
} 