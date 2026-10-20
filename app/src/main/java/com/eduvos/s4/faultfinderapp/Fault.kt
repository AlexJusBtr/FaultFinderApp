package com.eduvos.s4.faultfinderapp

data class Fault(
    // --- Core Team Fields ---
    var id: String = "",                                      // Firestore document ID
    var userId: String = "",                                  // Firebase Auth UID of the reporting citizen
    var category: String = "Other",                           // Roads, Water, Electricity, Streetlights, Waste, Other
    var description: String = "",                             // Detailed fault description
    var latitude: Double = -33.8120,                          // GPS Latitude for OpenStreetMap pin
    var longitude: Double = 18.4900,                          // GPS Longitude for OpenStreetMap pin
    var imageUrl: String = "",                                // Cloud Storage URL or Base64 fault photo
    var priority: String = "Medium",                          // High, Medium, or Low (auto-assigned by triaging rules)
    var status: String = "Pending",                           // Pending, In Progress, or Resolved
    var isDuplicate: Boolean = false,
    var duplicateOfFaultId: String = "",
    var createdAt: Long = System.currentTimeMillis(),         // Creation timestamp (ms) for sorting newest first
    var updatedAt: Long = System.currentTimeMillis(),         // Last status update timestamp (ms)                                                               


    // --- Extra UI & Research Fields ---
    var ref: String = "",                                     // Human-readable reference ID (e.g., "#CT-0001")
    var title: String = "",                                   // Short fault title shown on cards and map markers
    var location: String = "",                                // Street name or suburb (e.g., "Main Rd, Table View")
    var department: String = "",                              // Assigned department (e.g., "Roads & Stormwater")
    var date: String = "",                                    // Formatted display date (e.g., "14 Oct 2026")
    var isAnonymous: Boolean = false,                         // True if "Report anonymously" was checked
    var confirmations: Int = 1
)
