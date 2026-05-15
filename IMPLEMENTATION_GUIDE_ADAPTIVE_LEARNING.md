# Implementation Guide - Adaptive Voice Learning

## Quick Start

### 1. Enable Adaptive Learning in SilentEmergencyAI

Replace the current `SilentEmergencyAI` usage with `AdaptiveVoiceLearningAI`:

```java
// In MainActivity or ServiceMine.java

// OLD:
// SilentEmergencyAI silentEmergencyAI = new SilentEmergencyAI(context, listener);

// NEW:
AdaptiveVoiceLearningAI adaptiveAI = new AdaptiveVoiceLearningAI(context, 
    new AdaptiveVoiceLearningAI.AdaptiveLearningListener() {
        @Override
        public void onEmergencyDetected(String type, float confidence, byte[] audioData) {
            // Handle emergency detection
            // Audio data is automatically recorded
        }
        
        @Override
        public void onNewVoiceDetected(String voiceId, float similarity) {
            // Handle new voice detection
            Log.d(TAG, "New voice detected: " + voiceId + " (similarity: " + similarity + ")");
        }
        
        @Override
        public void onVoiceLearned(String voiceId, int samplesCount) {
            // Handle voice learning completion
            Log.d(TAG, "Voice learned: " + voiceId + " (samples: " + samplesCount + ")");
        }
        
        @Override
        public void onAudioRecorded(byte[] audioData, String emergencyType) {
            // Audio is automatically sent to emergency contacts
            // This callback is for logging/notification
        }
        
        @Override
        public void onModelUnavailable(String reason) {
            // Handle model unavailability
        }
    });

// Start monitoring
adaptiveAI.processAudioWithLearning(audioData, readSize);
```

### 2. Send Emergency Audio to Contacts

The `EmergencyAudioTransmitter` automatically sends audio when emergency is detected:

```java
// In SilentEmergencyAI.java (already integrated)

// When emergency detected:
EmergencyAudioTransmitter transmitter = new EmergencyAudioTransmitter(context);
transmitter.sendEmergencyAudioWithMessage(
    userName,
    "AI Detected Emergency",
    confidence,
    audioFilePath,
    emergencyNumber1,
    emergencyNumber2
);
```

### 3. Add User Settings

Add toggles in SettingsActivity:

```java
// Voice Learning Toggle
Switch voiceLearningSwitch = findViewById(R.id.voice_learning_switch);
voiceLearningSwitch.setChecked(adaptiveAI.isLearningEnabled());
voiceLearningSwitch.setOnCheckedChangeListener((button, isChecked) -> {
    adaptiveAI.setLearningEnabled(isChecked);
    // Save preference
});
```

---

## Integration Steps

### Step 1: Update SilentEmergencyAI

The `SilentEmergencyAI.java` has been updated to:
- Record audio when emergency detected
- Send audio to emergency contacts
- Include AI detection message

### Step 2: Add Adaptive Learning (Optional)

If you want full adaptive learning, replace `SilentEmergencyAI` with `AdaptiveVoiceLearningAI`:

```java
// Replace in MainActivity.java or ServiceMine.java
AdaptiveVoiceLearningAI adaptiveAI = new AdaptiveVoiceLearningAI(context, listener);
```

### Step 3: Update Privacy Policy

Add the voice learning and audio transmission disclosures (see `ADAPTIVE_VOICE_LEARNING_DOCUMENTATION.md`).

### Step 4: Update Play Console

Update permission justifications and Data Safety section (see documentation).

---

## Testing

1. **Test Voice Learning:**
   - Speak in different voices
   - Check if voices are identified
   - Verify voice embeddings are stored

2. **Test Audio Transmission:**
   - Trigger emergency detection
   - Verify audio is recorded
   - Check if audio is sent to contacts
   - Verify message includes AI detection info

3. **Test Privacy:**
   - Disable voice learning
   - Disable audio transmission
   - Delete voice data
   - Delete audio files

---

## Notes

- Voice learning is **optional** - can be disabled
- Audio transmission is **optional** - can be disabled
- All processing is **on-device only**
- No data transmitted to external servers (except to emergency contacts)

---

**Status:** ✅ Ready for Integration  
**Files Created:** 
- `AdaptiveVoiceLearningAI.java`
- `EmergencyAudioTransmitter.java`
- `ADAPTIVE_VOICE_LEARNING_DOCUMENTATION.md`
- `IMPLEMENTATION_GUIDE_ADAPTIVE_LEARNING.md`



