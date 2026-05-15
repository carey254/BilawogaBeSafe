# 🔐 User Identity & Logout/Restore System

## ✅ **PROBLEM SOLVED**

**User Question:** "Can the app identify users if they log out and log back in later? Will their data remain or will they lose their data?"

**Answer:** ✅ **YES - Users can now restore their data after logout!**

---

## 🆔 **HOW USER IDENTIFICATION WORKS**

### **1. User ID Generation**
- **On First Launch:** App generates a unique User ID based on:
  - Device Android ID (persists across app reinstalls)
  - Random UUID for uniqueness
  - SHA-256 hash for privacy
- **Format:** `BILA_XXXXXXXX` (32 characters)
- **Storage:** Encrypted SharedPreferences (persists across logout/login)

### **2. User ID Persistence**
- ✅ **User ID persists** even after logout
- ✅ **Same device = Same User ID** (allows restore)
- ✅ **Works across app reinstalls** (if same device)
- ✅ **Device hash verification** (security check)

---

## 📤 **LOGOUT OPTIONS**

### **Option 1: "Back up to cloud & log out"** ✅
**What Happens:**
1. ✅ Data backed up to Firebase using **User ID** (not FID)
2. ✅ User ID preserved locally (for restore)
3. ✅ Local emergency data wiped (for security)
4. ✅ User can restore data when logging back in

**Result:** ✅ **Data can be restored after logout**

### **Option 2: "Erase now"** ⚠️
**What Happens:**
1. ✅ All local data wiped
2. ✅ **User ID completely removed** (no restore possible)
3. ✅ Firebase backup remains (but can't restore without User ID)

**Result:** ⚠️ **Data cannot be restored** (complete deletion)

---

## 📥 **AUTO-RESTORE SYSTEM**

### **Automatic Restore (SplashScreen)**
When user logs back in:
1. ✅ App checks if local data exists
2. ✅ If no local data, tries to restore using **User ID**
3. ✅ Verifies device hash matches (security)
4. ✅ Restores emergency contacts, username, incident type
5. ✅ **Silent restore** - user doesn't need to do anything

### **Manual Restore (OnboardingActivity)**
If auto-restore fails:
1. ✅ User can tap "Restore Now" button
2. ✅ App tries restore using **User ID** first
3. ✅ Falls back to FID-based restore (legacy)
4. ✅ Shows success/failure message

---

## 🔒 **SECURITY FEATURES**

### **1. Device Hash Verification**
- ✅ Backup includes device hash
- ✅ Restore only works if device hash matches
- ✅ Prevents restore on different device (security)

### **2. Encrypted Storage**
- ✅ User ID stored in encrypted SharedPreferences
- ✅ Backup data encrypted using Android Keystore
- ✅ AES-256-GCM encryption

### **3. Privacy Protection**
- ✅ User ID masked in logs (privacy)
- ✅ No personal information in User ID
- ✅ Device hash for verification only

---

## 📊 **RESTORE SCENARIOS**

### **Scenario 1: Logout → Login (Same Device)** ✅
1. User logs out with "Back up to cloud & log out"
2. User ID preserved locally
3. User logs back in (same device)
4. ✅ **Auto-restore works** - Data restored automatically

### **Scenario 2: Logout → Login (Different Device)** ⚠️
1. User logs out with "Back up to cloud & log out"
2. User installs app on different device
3. Different device = Different User ID
4. ⚠️ **Auto-restore fails** (device hash mismatch)
5. ⚠️ **Manual restore fails** (different User ID)

**Note:** This is by design for security - prevents unauthorized access

### **Scenario 3: "Erase now" → Login** ❌
1. User logs out with "Erase now"
2. User ID completely removed
3. User logs back in
4. ❌ **No restore possible** (User ID deleted)

---

## 🔄 **BACKUP/RESTORE FLOW**

### **Backup Flow:**
```
User clicks "Back up to cloud & log out"
  ↓
Get User ID (or create if new)
  ↓
Encrypt data (username, contacts, incident type)
  ↓
Save to Firebase using User ID as document ID
  ↓
Add device hash for verification
  ↓
Wipe local emergency data (but keep User ID)
  ↓
Go to onboarding
```

### **Restore Flow:**
```
User logs back in
  ↓
Check if local data exists
  ↓
If no local data:
  ↓
Get User ID (persists from before)
  ↓
Query Firebase using User ID
  ↓
Verify device hash matches
  ↓
Decrypt and restore data
  ↓
User continues with restored data
```

---

## 📝 **CODE IMPLEMENTATION**

### **New File: `UserIdentityManager.java`**
- ✅ `getOrCreateUserId()` - Get or create user ID
- ✅ `getDeviceIdHash()` - Get device hash for verification
- ✅ `clearUserSession()` - Clear session but keep User ID
- ✅ `removeUserIdentity()` - Complete removal (no restore)

### **Modified Files:**
1. **`MainActivity.java`**
   - ✅ Uses User ID for backup (instead of FID)
   - ✅ Preserves User ID on logout
   - ✅ Removes User ID only on "Erase now"

2. **`SplashScreen.java`**
   - ✅ Auto-restore using User ID
   - ✅ Device hash verification
   - ✅ Falls back to FID-based restore (legacy)

3. **`OnboardingActivity.java`**
   - ✅ Manual restore using User ID
   - ✅ Device hash verification
   - ✅ Falls back to FID-based restore (legacy)

---

## ✅ **USER DATA PROTECTION**

### **What Data is Backed Up:**
- ✅ Username
- ✅ Emergency Contact 1
- ✅ Emergency Contact 2
- ✅ Incident Type

### **What Data is NOT Backed Up:**
- ❌ App lock PIN/Password (security)
- ❌ Session data (temporary)
- ❌ Security logs (privacy)

### **Data Encryption:**
- ✅ All backup data encrypted using Android Keystore
- ✅ AES-256-GCM encryption
- ✅ Separate IV for each field

---

## 🎯 **SUMMARY**

### **✅ YES - Users Can Restore Data After Logout!**

**How It Works:**
1. ✅ User ID generated on first launch
2. ✅ User ID persists across logout/login
3. ✅ Data backed up using User ID
4. ✅ Auto-restore on login (same device)
5. ✅ Manual restore available if needed

**Security:**
- ✅ Device hash verification (prevents cross-device restore)
- ✅ Encrypted storage and backup
- ✅ User ID masked in logs

**User Experience:**
- ✅ Seamless auto-restore
- ✅ Manual restore option
- ✅ Clear logout options

---

## 🚨 **IMPORTANT NOTES**

1. **Same Device Restore:** ✅ Works automatically
2. **Different Device Restore:** ⚠️ Blocked for security
3. **"Erase now" Option:** ❌ No restore possible
4. **User ID Persistence:** ✅ Survives logout, app reinstall

---

**Last Updated:** 2025
**Status:** ✅ **IMPLEMENTED & TESTED**


