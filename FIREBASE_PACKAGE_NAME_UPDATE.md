# Firebase Package Name Update Guide

## ✅ Fixed: Updated `google-services.json`

The `google-services.json` file has been updated to use the new package name:
- **Old:** `com.example.bilawoga`
- **New:** `com.bilawoga.safety`

## ⚠️ Important: Update Firebase Console

You also need to add the new package name in Firebase Console:

### Option 1: Add New Android App (Recommended)
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: **bila-uoga**
3. Go to **Project Settings** (gear icon)
4. Scroll down to **Your apps** section
5. Click **Add app** → **Android**
6. Enter package name: `com.bilawoga.safety`
7. Download the new `google-services.json` file
8. Replace the existing `app/google-services.json` with the new one

### Option 2: Update Existing App (If Possible)
1. Go to Firebase Console → Project Settings
2. Find your existing Android app
3. If Firebase allows, update the package name
4. Download updated `google-services.json`

### Option 3: Keep Both Package Names
You can have both package names in Firebase:
- Keep the old app with `com.example.bilawoga` (for testing)
- Add new app with `com.bilawoga.safety` (for Play Store)
- Use the new `google-services.json` for production builds

## 🔨 After Updating Firebase

1. **Download new `google-services.json`** from Firebase Console
2. **Replace** `app/google-services.json` with the new file
3. **Sync project** in Android Studio
4. **Rebuild** the AAB:
   ```bash
   .\gradlew clean
   .\gradlew bundleRelease
   ```

## ✅ Verification

After updating, verify:
- [x] `google-services.json` has `"package_name": "com.bilawoga.safety"`
- [ ] New package name added in Firebase Console
- [ ] New `google-services.json` downloaded from Firebase
- [ ] Project synced in Android Studio
- [ ] Build succeeds without errors

## 📝 Current Status

- ✅ `google-services.json` updated locally
- ⚠️ **Need to add package name in Firebase Console**
- ⚠️ **Download new `google-services.json` from Firebase**

---

**Next Steps:**
1. Add `com.bilawoga.safety` as a new Android app in Firebase Console
2. Download the new `google-services.json` file
3. Replace the current file with the new one
4. Sync and rebuild


