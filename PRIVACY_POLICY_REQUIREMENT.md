# Privacy Policy Requirement - RECORD_AUDIO Permission

## Issue
**Error:** "Your APK or Android App Bundle is using permissions that require a privacy policy: (android.permission.RECORD_AUDIO)"

## Why This Is Required
Google Play requires a privacy policy URL for apps that use sensitive permissions like:
- `RECORD_AUDIO` - Used by BilaWoga for AI emergency detection
- `ACCESS_FINE_LOCATION` - Used for emergency location sharing
- Other sensitive permissions

## Solution: Add Privacy Policy URL in Play Console

### Step-by-Step Instructions:

1. **Go to Play Console**
   - Navigate to: **App content** → **Privacy policy**

2. **Add Privacy Policy URL**
   - Enter your privacy policy URL (must be publicly accessible)
   - Example: `https://yourwebsite.com/privacy-policy` or `https://yourwebsite.com/privacy`
   - The URL must be:
     - Accessible without login
     - HTTPS (secure)
     - Contains your actual privacy policy

3. **What Should Be in Your Privacy Policy**
   Your privacy policy must explain:
   - **Audio Recording**: Why you record audio, when it's recorded, how it's used
   - **Location Data**: How location is collected and shared
   - **Emergency Contacts**: How contact information is stored and used
   - **Data Storage**: Where data is stored (locally on device)
   - **Data Sharing**: Who receives emergency data (trusted contacts only)
   - **Stealth Mode**: How stealth mode works and why no UI indication appears

4. **Where to Host Your Privacy Policy**
   - Your own website
   - GitHub Pages (free)
   - Google Sites (free)
   - Firebase Hosting (free)
   - Any web hosting service

5. **Example Privacy Policy Sections for BilaWoga**
   ```
   Audio Recording:
   - BilaWoga records audio only when an emergency is detected by AI
   - Audio is recorded for 1-10 minutes during emergency situations
   - Recorded audio is sent only to your trusted emergency contacts
   - Audio is stored locally on your device and not shared with third parties
   - Audio recordings are used solely for emergency assistance purposes
   
   Location Data:
   - Your location is collected only when you send an emergency alert
   - Location is shared only with your trusted emergency contacts
   - Location data is not stored or shared with third parties
   ```

## Version Code Updated ✅

- **Old Version Code:** 1
- **New Version Code:** 2
- **Version Name:** 1.0.1
- **New AAB:** Ready to upload

## Next Steps

1. ✅ **Version code updated** - New AAB built with version code 2
2. ⚠️ **Add Privacy Policy URL** in Play Console:
   - Go to: **App content** → **Privacy policy**
   - Enter your privacy policy URL
   - Save changes
3. **Upload the new AAB** (version code 2) to Play Console
4. The errors should be resolved!

## Important Notes

- The privacy policy URL must be added **before** you can publish your app
- You can use the privacy policy content already in your app (from `PolicyViewerActivity`)
- Make sure the URL is publicly accessible and uses HTTPS
- The privacy policy should be comprehensive and explain all data collection practices








