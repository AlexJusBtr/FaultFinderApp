package com.eduvos.s4.faultfinderapp
/**
 Every time a user submits a fault or we load pins onto OpenStreetMap, Firebase Firestore converts a database document into this object.
 IMPORTANT FOR WHEN WE CODE THIS:
 Every variable inside `data class FaultReport(...)` MUST have a default value (e.g., `val title: String = ""`) because Firestore requires an empty constructor
 to automatically read documents using `.toObject(FaultReport::class.java)`.
 FIELDS THIS DATA CLASS NEEDS TO STORE:
 1. Core Report Info:
 - `id` (String): Firestore document ID (for updating/deleting).
 - `ref` (String): Reference number shown in the UI (e.g., "#CT-0001").
 - `title` (String): Short fault title from ReportActivity.
 - `description` (String): Full fault details from ReportActivity.
 - `category` (String): Roads, Water, Electricity, Streetlights, Waste, or Other.
 - `date` (String) & `timestamp` (Long): For display and sorting newest reports first.
 2. Location & OpenStreetMap Data:
 - `location` (String): Street name / suburb (e.g., "Main Rd, Table View").
 - `lat` (Double) & `lon` (Double): GPS coordinates for placing pins on MapActivity and MainActivity, and for calculating "Near me" distances.
 3. Research Features (Prioritisation, Tracking & Duplicate Prevention):
 - `status` (String): "Pending", "In Progress", or "Resolved" (drives the 4-step timeline in TrackActivity and the counters in MyReportsActivity).
 - `priority` (String): "High", "Medium", or "Low" (auto-assigned by our rule-based triaging logic).
 - `department` (String): Assigned municipal department (e.g., "Roads & Stormwater").
 - `confirmations` (Int): Counter for how many citizens confirmed/upvoted this same fault instead of creating a duplicate report.
 4. User & Media Info:
 - `userId` (String): Firebase Auth UID of the person who submitted it (so we can filter `MyReportsActivity` to only show the current user's reports).
 - `isAnonymous` (Boolean): True if "Report anonymously" was checked.
 - `photoUrl` / `photoBase64` (String): Fault image attached by the user.
 */
class FaultReport {
}