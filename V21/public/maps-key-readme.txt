To enable Google Maps in the Android app, set your API key in one of these ways (do not commit secrets):

Option A) In local.properties (not committed):
MAPS_API_KEY=YOUR_ANDROID_MAPS_API_KEY

Option B) Environment variable:
setx MAPS_API_KEY YOUR_ANDROID_MAPS_API_KEY

The key must be restricted in Google Cloud Console to:
- Package name: com.example.bilawoga
- SHA-1: 74:04:95:B1:8D:3F:C5:F6:13:89:83:85:4E:DD:8D:78:69:66:CE:B2

This project reads the key via manifestPlaceholders and injects into AndroidManifest meta-data: com.google.android.geo.API_KEY.