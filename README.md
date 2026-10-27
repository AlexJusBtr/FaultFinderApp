# FaultFinderApp

**Eduvos Tygervalley Campus** | **Module:** ITMDA3-34 (Deliverable 4)  
**Group:** S4 | **Stack:** Kotlin, Android SDK, Firebase (Auth & Firestore), Cloudinary, OpenStreetMap (osmdroid)

---

## 🔑 Administrative Evaluation Account

Use these credentials to access the **Admin Control Panel** (status triage, audit logs, and permanent report deletion):

> **Email:** `admin@testing.com`  
> **Password:** `test123`

---

## 🚀 Core Features

- **Live OSM Mapping:** Embedded OpenStreetMap with custom canvas pins, GPS nearest-first sorting, search, and category filters.
- **100m Duplicate Detection:** Spatial check prompts a `+1` confirmation instead of creating duplicate tickets.
- **Full Citizen CRUD:** Report submission with Cloudinary photos, 4-stage tracking timeline, and long-press edit/delete in *My Reports*.
- **Real-Time Push Alerts:** System tray notifications on status changes, with configurable proximity radius (1–25 km) in Settings.
- **Admin Dashboard:** Segmented tabs separating *Active Faults* from *Resolved Archives* with status updates and delete controls.

---

## 📦 How to Build & Publish Release APK

1. **Build APK:** In Android Studio, go to `Build` > `Build Bundle(s) / APK(s)` > `Build APK(s)`. Locate the output file (`app-debug.apk`) and rename it to `FaultFinderApp-v1.0.apk`.
2. **Draft Release:** In GitHub, open **Releases** > **Draft a new release**.
3. **Tag & Upload:** Set tag to `v1.0`, drop `FaultFinderApp-v1.0.apk` into the release binaries box, and click **Publish release**.
