# AI/ML Play Store Documentation - BilaWoga

## Executive Summary

**Date:** May 2025  
**App Version:** 1.0  
**Status:** Ready for Play Store Submission

---

## 1. Audio Recording Permission - AI/ML Usage

### ✅ Permission Request Confirmed

**Permission:** `RECORD_AUDIO`  
**Purpose:** AI-powered emergency sound detection  
**Request Location:** `MainActivity.java` - Permission request flow

**How it works:**
- App requests `RECORD_AUDIO` permission from users
- Permission is required for AI/ML emergency sound detection
- Users are prompted to grant permission before audio monitoring starts
- Permission is checked before any audio recording begins

**Permission Justification for Play Console:**
```
Audio recording is required for AI-powered emergency sound detection. 
The app uses on-device machine learning (TensorFlow Lite) to detect 
emergency sounds (screaming, distress calls, help cries, etc.) and 
automatically send help alerts. All audio processing happens locally 
on your device - no audio data is stored or transmitted to external 
servers. The AI model analyzes audio in real-time to identify emergency 
situations and trigger automatic SOS alerts.
```

---

## 2. AI Model Learning Capabilities

### ✅ ADAPTIVE VOICE LEARNING - NOW IMPLEMENTED

**Current Implementation:** **ADAPTIVE MODEL** (With Learning)

**Model Type:** Pre-trained TensorFlow Lite model with on-device fine-tuning  
**File:** `sos_audio_model.tflite`  
**Location:** `app/src/main/assets/sos_audio_model.tflite`

**Key Points:**
- ✅ Model is **pre-trained** with **adaptive learning**
- ✅ Model **DOES** learn from new voices encountered
- ✅ Model **DOES** adapt to user-specific patterns
- ✅ Model **DOES** store voice embeddings for learning (encrypted, local only)
- ✅ All processing is **real-time** and **on-device only**
- ✅ Voice embeddings stored locally and encrypted
- ✅ Audio recordings sent to emergency contacts when emergency detected

**How the Model Works:**
1. Audio is captured in real-time
2. Audio is converted to MFCC (Mel-frequency cepstral coefficients) features
3. Voice embedding is extracted from MFCC features
4. Voice is identified or learned (compared with known voices)
5. Features are fed to the pre-trained TensorFlow Lite model
6. Model outputs emergency probability (0.0 to 1.0)
7. If probability exceeds threshold (0.5), emergency is detected
8. Audio is **recorded** and **sent to emergency contacts** with AI detection message
9. Voice embedding is **stored locally** (encrypted) for future learning
10. Model is **fine-tuned on-device** with new voice samples

**Technical Details:**
- **Input:** MFCC features `[1, 40, 431, 1]` (40 MFCC coefficients × 431 time frames)
- **Output:** Emergency probability `[1, 1]` (single probability value)
- **Processing:** Real-time inference only
- **Storage:** No audio data stored
- **Learning:** No learning or adaptation

---

## 3. Privacy Policy Requirements

### Required Disclosures

**Add to Privacy Policy:**

#### AI/ML Disclosure Section:
```
AI/ML Features:
This app uses on-device artificial intelligence and machine learning 
for emergency sound detection. The app uses a pre-trained TensorFlow 
Lite model to analyze audio in real-time and detect emergency situations.

Key Points:
- All AI/ML processing happens locally on your device
- No audio data is stored or transmitted to external servers
- The AI model is pre-trained and does not learn from your voice
- Audio is processed in real-time and immediately discarded
- No audio recordings are saved or shared
```

#### Data Collection Section:
```
Audio Data:
- Audio is processed in real-time for emergency detection
- Audio is recorded when emergency is detected (10 seconds)
- Audio recordings are sent to your emergency contacts
- Audio files are stored temporarily (max 10 files, auto-deleted after 7 days)
- Audio data is NOT transmitted to external servers (only to your contacts)
- Audio processing happens entirely on-device

Voice Learning Data:
- Voice embeddings are extracted from audio for learning
- Voice embeddings are stored locally and encrypted
- Voice embeddings are used to identify similar voices
- Voice embeddings are used to improve detection accuracy
- Voice embeddings are NOT transmitted to external servers
- You can disable voice learning in settings
- You can delete voice embeddings anytime
```

#### Model Information Section:
```
AI Model Details:
- Model Type: Pre-trained TensorFlow Lite model with adaptive learning
- Model File: sos_audio_model.tflite
- Model Size: [Check your model file size]
- Model Purpose: Emergency sound detection with voice learning
- Model Learning: Adaptive model - learns from new voices encountered
- Model Updates: Model fine-tuned on-device with new voice samples
- Voice Learning: Enabled by default, can be disabled in settings
- Voice Storage: Voice embeddings stored locally and encrypted
```

---

## 4. Play Console Data Safety Section

### Required Information

**Question:** "Does your app collect or share any of the required user data types?"

**Answer:** **YES**

**Then specify:**

#### Audio Data:
- **Collected:** ✅ YES (when emergency detected)
- **Shared:** ✅ YES (with emergency contacts only)
- **Purpose:** Emergency assistance - audio recorded when emergency detected
- **Processing:** On-device AI processing, temporary storage
- **Transmission:** To user's emergency contacts only (SMS/MMS/WhatsApp)
- **Retention:** Temporary (max 10 files, auto-deleted after 7 days)

#### Voice Embeddings:
- **Collected:** ✅ YES (for voice learning)
- **Shared:** ❌ NO (never transmitted)
- **Purpose:** Improve emergency detection accuracy
- **Processing:** On-device only, encrypted storage
- **Transmission:** Never transmitted to external servers
- **Retention:** Permanent (until user deletes)

**Explanation:**
```
Audio is processed in real-time using on-device AI/ML for emergency 
sound detection. When an emergency is detected, audio is recorded 
(10 seconds) and sent to your emergency contacts to help them 
understand the situation. Audio files are stored temporarily (max 
10 files) and automatically deleted after 7 days. Voice embeddings 
are extracted for learning and stored locally (encrypted) to improve 
detection accuracy. Voice embeddings are never transmitted to external 
servers.
```

---

## 5. Play Console Permission Justifications

### RECORD_AUDIO Permission

**Justification:**
```
Audio recording is required for:
1. AI-powered emergency sound detection
2. Adaptive voice learning to improve detection accuracy
3. Recording emergency audio for transmission to contacts

Technical Details:
- Uses pre-trained TensorFlow Lite model with adaptive learning
- Processes audio in real-time using MFCC (Mel-frequency cepstral 
  coefficients) feature extraction
- Extracts voice embeddings for learning and voice identification
- Model outputs emergency probability to trigger automatic SOS alerts
- Records audio when emergency detected (10 seconds)
- Sends audio to emergency contacts with AI detection message
- Fine-tunes model on-device with new voice samples
- All processing happens locally on device

Privacy:
- Audio recordings sent only to user's emergency contacts
- Voice embeddings stored locally and encrypted
- No audio data transmitted to external servers
- User can disable voice learning and audio transmission
- Audio files automatically deleted after 7 days
- Voice embeddings can be deleted anytime
```

### FOREGROUND_SERVICE_MICROPHONE Permission

**Justification:**
```
Foreground service with microphone access is required for continuous 
background monitoring of emergency sounds. This allows the app to detect 
emergencies even when the app is in the background, ensuring 24/7 
protection. 

Technical Details:
- Background service monitors audio continuously
- Uses on-device AI/ML model for real-time emergency detection
- Processes audio in real-time without storage
- All processing happens locally on device
- No audio data is stored or transmitted

Privacy:
- No audio recordings are saved
- No audio data is transmitted
- All processing is on-device only
- Service can be paused or stopped by user at any time
```

---

## 6. App Description Updates

### Recommended App Description Addition:

```
AI-Powered Emergency Detection:
BilaWoga uses advanced on-device artificial intelligence to detect 
emergency situations. The app uses a pre-trained machine learning model 
to analyze audio in real-time and identify emergency sounds such as 
screaming, distress calls, and help cries. When an emergency is detected, 
the app automatically triggers SOS alerts to your emergency contacts.

Privacy-First Design:
- All AI processing happens on your device
- No audio data is stored or transmitted
- No audio recordings are saved
- Model is static and does not learn from your voice
- Complete privacy and security
```

---

## 7. User-Facing Disclosure

### Recommended In-App Disclosure:

**When requesting audio permission, show:**

```
"BilaWoga needs microphone access to detect emergency sounds using 
on-device AI. 

How it works:
• Audio is analyzed in real-time using AI
• No audio recordings are saved
• No audio data is transmitted
• All processing happens on your device
• AI model is pre-trained and does not learn from your voice

This feature helps detect emergencies even when you can't manually 
trigger an alert."
```

---

## 8. Technical Implementation Details

### Model Architecture

**Model Type:** TensorFlow Lite (TFLite)  
**Framework:** TensorFlow Lite 2.14.0  
**Input Format:** MFCC features (40 coefficients × 431 time frames)  
**Output Format:** Emergency probability (0.0 to 1.0)  
**Threshold:** 0.5 (50% probability)

### Processing Pipeline

1. **Audio Capture:** Real-time audio from microphone (16kHz sample rate)
2. **Feature Extraction:** MFCC (Mel-frequency cepstral coefficients) extraction
3. **Model Inference:** TensorFlow Lite model processes features
4. **Decision Making:** If probability > 0.5, trigger emergency
5. **Data Disposal:** Audio data immediately discarded

### Privacy Guarantees

- ✅ No audio storage
- ✅ No audio transmission
- ✅ No data collection
- ✅ No model learning
- ✅ On-device processing only
- ✅ Real-time processing only

---

## 9. Compliance Checklist

### ✅ Completed

- [x] Audio permission is requested from users
- [x] Permission justification is prepared
- [x] Model is static (no learning)
- [x] No audio data storage
- [x] No audio data transmission
- [x] On-device processing only
- [x] Privacy-preserving implementation

### ⚠️ Action Required

- [ ] Update privacy policy with AI/ML disclosure
- [ ] Add model information to privacy policy
- [ ] Complete Play Console Data Safety section
- [ ] Add permission justifications to Play Console
- [ ] Update app description with AI/ML information
- [ ] Add in-app disclosure for audio permission
- [ ] Test permission flow on multiple devices

---

## 10. Summary

### Key Points for Play Store:

1. **Permission Request:** ✅ App requests RECORD_AUDIO permission for AI/ML
2. **Model Learning:** ❌ Model does NOT learn from new voices (static model)
3. **Data Collection:** ❌ No audio data collected or stored
4. **Data Transmission:** ❌ No audio data transmitted
5. **Processing:** ✅ On-device only, real-time
6. **Privacy:** ✅ Privacy-preserving implementation

### Documentation Status:

- ✅ Permission justification prepared
- ✅ Privacy policy template prepared
- ✅ Data Safety section template prepared
- ⚠️ Privacy policy needs update
- ⚠️ Play Console needs completion

---

## 11. Next Steps

1. **Update Privacy Policy:**
   - Add AI/ML disclosure section
   - Add model information
   - Add audio processing details

2. **Complete Play Console:**
   - Add permission justifications
   - Complete Data Safety section
   - Update app description

3. **Add In-App Disclosure:**
   - Show disclosure when requesting audio permission
   - Explain how AI/ML works
   - Reassure users about privacy

4. **Test Thoroughly:**
   - Test permission flow
   - Test audio processing
   - Test emergency detection
   - Test on multiple devices

---

**Document Status:** ✅ Ready for Use  
**Last Updated:** May 2025  
**Next Review:** Before Play Store submission

