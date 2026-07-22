# Play Store Setup Guide — SelfAttendance Pro

## Pehle Yeh Padho (IMPORTANT)

Yeh zip mein se 2 sensitive files **delete kar di gayi hain** security ke liye:
- `app/keystore.jks` — tumhari Play Store signing key
- `app/google-services.json` — tumhara Firebase config

Build karne se pehle **dono files wapas rakhni hongi** (neeche steps dekho).

---

## Step 1: google-services.json Add Karo

1. [Firebase Console](https://console.firebase.google.com) pe jao
2. Apna project open karo → Project Settings (gear icon)
3. "Your apps" section mein Android app select karo
4. `google-services.json` download karo
5. File ko `SelfAttendance/app/google-services.json` mein rakho

---

## Step 2: keystore.jks Add Karo

**Agar tumhare paas purani keystore hai** (pehle se Play Store pe upload ki thi):
- `app/keystore.jks` wapas rakho manually

**Agar pehli baar upload kar rahe ho** (nayi keystore banao):
```bash
keytool -genkey -v \
  -keystore app/keystore.jks \
  -alias YOUR_KEY_ALIAS \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```
⚠️ **KEYSTORE KABHI MAT KHOYE** — iske bina app update nahi kar sakte.

---

## Step 3: Environment Variables Set Karo (Local Build)

Android Studio mein ya terminal mein:
```bash
export KEYSTORE_PASSWORD="tumhara_password"
export KEY_ALIAS="tumhara_alias"
export KEY_PASSWORD="tumhara_key_password"
export VERSION_CODE=3   # Play Store pe jo version code upload karna hai
```

---

## Step 4: Release Build Banao

```bash
./gradlew assemblePlayRelease
# ya AAB ke liye (recommended):
./gradlew bundlePlayRelease
```

Output: `app/build/outputs/bundle/playRelease/app-play-release.aab`

---

## Step 5: Play Console mein Upload Karo

1. [Google Play Console](https://play.google.com/console) open karo
2. App select karo → Release → Production (ya Internal Testing pehle)
3. "Create new release" → AAB file upload karo
4. Release notes add karo
5. Review aur publish karo

---

## Kya Fix Kiya Gaya (Changes Summary)

| File | Fix |
|------|-----|
| `app/keystore.jks` | Deleted — sensitive file, manually add karo |
| `app/google-services.json` | Deleted — sensitive file, Firebase se download karo |
| `.gitignore` | `google-services.json` properly exclude ho gaya |
| `AlternativeUpdateManager.kt` | Play builds mein APK download/install completely blocked |
| `app/build.gradle` | `versionCode` ab `VERSION_CODE` env var se bhi le sakta hai |
| `AdsController.kt` | `INTERSTITIAL_FREQUENCY` 1 → 3 (AdMob policy compliant) |

---

## Play Store Console Setup Checklist

- [ ] App icon upload kiya (512x512 PNG)
- [ ] Screenshots add kiye (phone + tablet)
- [ ] Short description (80 chars) likhi
- [ ] Full description likhi
- [ ] Privacy Policy URL add kiya ✅ (already in strings.xml)
- [ ] Content rating complete kiya
- [ ] Target audience set kiya
- [ ] App category set kiya
- [ ] Data safety form fill kiya (AdMob, Firebase, Biometric use hota hai)
- [ ] Billing setup: `premium_monthly` subscription product banao Play Console mein

---

## Data Safety Form ke liye

Play Console mein "Data Safety" fill karte waqt yeh batana hoga:

| Data Type | Collected | Purpose |
|-----------|-----------|---------|
| Advertising ID | Yes (AdMob) | Advertising |
| Crash logs | Yes (Crashlytics) | App functionality |
| User authentication | Yes (Firebase Auth) | Account management |
| App activity | Yes (Firebase Analytics) | Analytics |
| Biometric data | No (sirf locally verify hota hai) | - |
