# FixMechanic Customer (Roadside Assistance Android App)

FixMechanic Customer is the native Android application for vehicle owners requesting 24/7 emergency roadside assistance across India, built with **Kotlin**, **Jetpack Compose**, **Material 3**, and **MVVM**.

---

## Shared Backend & Firebase Spark Compatibility
- **Firebase Project**: `fixmechanic-firebase` (Project Number `111465055412`)
- **Cloud Firestore Database**: `(default)` (unified across Customer, Mechanic, and Admin apps)
- **Authentication on Spark**: Uses **Email/Password** and **Google Sign-In** (via Jetpack Credential Manager). *Firebase Phone SMS OTP requires a Blaze billing plan and is gated with an explicit Spark notice.*
- **Spark Dispatch Backend (`http://10.0.2.2:5055`)**: Routes `createServiceRequest`, `retryServiceRequest`, `triggerOfferExpiryCheck`, `updateJobStatus` (cancel), `sendJobChatMessage`, and `submitJobRating` through the shared Spark HTTP Callable server (`fixmechanic-admin/functions/src/server.ts`).

---

## Synchronized Schema & Customer Flow
1. **Nearby Online Mechanics Discovery (`mechanicProfiles`)**:
   - Observes `approvalStatus == "APPROVED"`, `isOnline == true`, `isBusy == false` so customers see online mechanics near them in real time.
2. **GPS & Manual Geocoded Location (`LocationHelper`)**:
   - Captures high-accuracy GPS (`FusedLocationProviderClient`) or resolves manually typed street/landmark addresses into coordinates via Android `Geocoder.forwardGeocode` (no `0.0, 0.0` placeholder coordinates).
3. **Dispatch Request Creation (`createServiceRequest`)**:
   - Maps customer selections (`BIKE`/`CAR` → `TWO_WHEELER`/`FOUR_WHEELER_CAR`, service catalog items → `FLAT_TYRE`, `BATTERY_JUMPSTART`, `FUEL_DELIVERY`, etc.) and triggers server geospatial matching & `jobOffers` creation.
4. **Live Tracking, Chat & Rating**:
   - Tracks `serviceRequests/{requestId}` in real time; auto-advances from `SearchingMechanicScreen` upon `ASSIGNED` and from `JobTrackingScreen` to `JobCompletionScreen` upon `COMPLETED`.
   - Exchanges masked messages in `jobChats/{requestId}/jobMessages` via `sendJobChatMessage` and submits ratings via `submitJobRating`.

---

## Local Build & Verification
```bash
# Place google-services.json in app/google-services.json locally (never commit)
gradle :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```
