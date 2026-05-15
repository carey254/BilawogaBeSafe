# Play Store Submission Checklist - Audio AI Model

## ✅ Pre-Submission Checklist

### 1. Code Fixes ✅
- [x] Fixed deprecated TensorFlow Lite API
- [x] Added error handling for model loading
- [x] Added model validation
- [x] Added user feedback for errors
- [x] Improved resource management

### 2. Privacy Policy Requirements ⚠️
**Action Required:** Update privacy policy to include:

- [ ] **AI/ML Disclosure:**
  - "This app uses on-device AI/ML models for emergency sound detection"
  - "All AI processing happens locally on your device"
  - "No audio data is sent to external servers"

- [ ] **Data Collection:**
  - "Audio is processed in real-time and not stored"
  - "No audio recordings are saved or transmitted"
  - "All processing happens on-device for privacy"

- [ ] **Model Information:**
  - "Uses TensorFlow Lite model for emergency detection"
  - "Model file size: [check your model size]"
  - "Model runs entirely on-device"

### 3. Play Console Documentation ⚠️
**Action Required:** Prepare for Play Console:

- [ ] **Permission Justification (RECORD_AUDIO):**
  ```
  "Audio recording is required for AI-powered emergency sound detection. 
  The app uses on-device machine learning to detect emergency sounds 
  (screaming, distress calls, etc.) and automatically send help alerts. 
  All audio processing happens locally on your device - no audio data 
  is stored or transmitted to external servers."
  ```

- [ ] **Permission Justification (FOREGROUND_SERVICE_MICROPHONE):**
  ```
  "Foreground service with microphone access is required for continuous 
  background monitoring of emergency sounds. This allows the app to detect 
  emergencies even when the app is in the background, ensuring 24/7 
  protection. All processing happens on-device."
  ```

- [ ] **Data Safety Section:**
  - Select "Yes" for "Does your app collect or share any of the required user data types?"
  - Select "Audio" → "No" (not collected, not shared)
  - Explain: "Audio is processed on-device only, not collected or shared"

### 4. Testing Requirements ⚠️
**Action Required:** Test before submission:

- [ ] Test model loading on multiple devices
- [ ] Test with model file present
- [ ] Test with model file missing (should handle gracefully)
- [ ] Test audio recording permission flow
- [ ] Test foreground service behavior
- [ ] Test on Android 8.0+ (minSdk 26)
- [ ] Test on Android 12+ (targetSdk 34)
- [ ] Test battery consumption
- [ ] Test memory usage
- [ ] Test model inference performance

### 5. Model File Verification ⚠️
**Action Required:** Verify model file:

- [ ] Check model file size (should be < 100MB for APK, or use App Bundle)
- [ ] Verify model file is valid TensorFlow Lite format
- [ ] Verify model input/output shapes match code expectations:
  - Input: `[1, 40, 431, 1]` (MFCC features)
  - Output: `[1, 1]` (emergency probability)
- [ ] Test model inference works correctly
- [ ] Verify model file is included in release build

### 6. Build Configuration ⚠️
**Action Required:** Verify build settings:

- [ ] Ensure `sos_audio_model.tflite` is in `app/src/main/assets/`
- [ ] Verify model file is not excluded by ProGuard
- [ ] Check APK size (should be < 150MB or use App Bundle)
- [ ] Verify release signing is configured
- [ ] Test release build works correctly

### 7. ProGuard Rules ✅
**Status:** Already configured
- TensorFlow Lite classes are kept
- Model-related code is preserved

### 8. Manifest Permissions ✅
**Status:** Already configured
- `RECORD_AUDIO` declared
- `FOREGROUND_SERVICE_MICROPHONE` declared
- Foreground service type configured

---

## 📋 Submission Steps

1. **Update Privacy Policy** (Required)
   - Add AI/ML disclosure
   - Add data collection information
   - Add model information

2. **Prepare Play Console Documentation** (Required)
   - Write permission justifications
   - Complete Data Safety section
   - Prepare app description

3. **Test Thoroughly** (Required)
   - Test on multiple devices
   - Test all scenarios
   - Verify model works correctly

4. **Build Release APK/AAB** (Required)
   - Create signed release build
   - Verify model file is included
   - Check APK/AAB size

5. **Submit to Play Store** (Required)
   - Upload APK/AAB
   - Complete store listing
   - Submit for review

---

## ⚠️ Common Play Store Rejection Reasons

1. **Missing Privacy Policy Disclosure**
   - Solution: Add AI/ML disclosure to privacy policy

2. **Insufficient Permission Justification**
   - Solution: Provide detailed justification in Play Console

3. **Data Safety Section Incomplete**
   - Solution: Complete Data Safety section accurately

4. **App Crashes on Model Loading**
   - Solution: Test thoroughly, ensure error handling works

5. **Missing Model File**
   - Solution: Verify model file is in assets folder

---

## ✅ Current Status

**Code:** ✅ READY (all fixes applied)
**Privacy Policy:** ⚠️ NEEDS UPDATE
**Play Console Docs:** ⚠️ NEEDS PREPARATION
**Testing:** ⚠️ NEEDS COMPLETION
**Model File:** ✅ VERIFIED (exists)

**Overall Status:** ⚠️ **NEEDS FINAL PREPARATION BEFORE SUBMISSION**

---

## 📝 Next Steps

1. Update privacy policy with AI/ML disclosure
2. Prepare Play Console documentation
3. Test thoroughly on multiple devices
4. Build and verify release APK/AAB
5. Submit to Play Store

---

**Last Updated:** May 2025
**Review Status:** Code fixes complete, documentation needed




