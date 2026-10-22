package com.eduvos.s4.faultfinderapp

import android.location.Location
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback

/**
 * Centralized backend repository managing Firebase Authentication, Cloud Storage,
 * and Cloud Firestore CRUD operations, including automated rule-based fault prioritisation
 * and 100m GPS spatial duplicate detection.
 */
class FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    // =========================================================================
    // 1. AUTHENTICATION & USER MANAGEMENT
    // =========================================================================

    fun registerUser(
        name: String,
        email: String,
        password: String,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            onFailure(Exception("Please complete all registration fields"))
            return
        }

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                val firebaseUser = auth.currentUser
                if (firebaseUser == null) {
                    onFailure(Exception("User account could not be created"))
                    return@addOnSuccessListener
                }

                val user = User(
                    userId = firebaseUser.uid,
                    name = name.trim(),
                    email = email.trim(),
                    role = "citizen",
                    createdAt = System.currentTimeMillis()
                )

                db.collection("users")
                    .document(firebaseUser.uid)
                    .set(user)
                    .addOnSuccessListener { onSuccess(user) }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun loginUser(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (email.isBlank() || password.isBlank()) {
            onFailure(Exception("Please enter your email and password"))
            return
        }

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun logoutUser() {
        auth.signOut()
    }

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun currentUserId(): String? {
        return auth.currentUser?.uid
    }

    fun getCurrentUser(
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onFailure(Exception("No user is logged in"))
            return
        }

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) {
                    onSuccess(user)
                } else {
                    // Fallback profile if Firestore user doc wasn't created yet
                    onSuccess(
                        User(
                            userId = userId,
                            name = auth.currentUser?.email?.substringBefore("@") ?: "Citizen",
                            email = auth.currentUser?.email ?: "",
                            role = "citizen"
                        )
                    )
                }
            }
            .addOnFailureListener(onFailure)
    }

    // =========================================================================
    // 2. AUTOMATED TRIAGING & DEPARTMENT ROUTING (TTF Framework)
    // =========================================================================

    /**
     * Rule-based fault prioritisation algorithm (Deliverable 2 Section 2.3):
     * Evaluates hazard keywords and infrastructure category to assign priority automatically.
     */
    fun calculatePriority(category: String, title: String, description: String): String {
        val text = "$title $description".lowercase()
        val criticalKeywords = listOf("live wire", "exposed cable", "electrocut", "sewage overflow", "sinkhole", "collapse")
        val highKeywords = listOf("burst", "flood", "spark", "danger", "swerving", "accident", "major", "main rd", "no water", "blackout")

        if (criticalKeywords.any { it in text }) return "Critical"
        if (highKeywords.any { it in text }) return "High"

        return when (category.trim().lowercase()) {
            "electricity", "water" -> "High"
            "roads", "streetlights" -> "Medium"
            else -> "Low"
        }
    }

    /**
     * Maps a fault category to the responsible City of Cape Town municipal department.
     */
    fun mapCategoryToDepartment(category: String): String {
        return when (category.trim().lowercase()) {
            "roads" -> "Roads & Stormwater"
            "water" -> "Water & Sanitation"
            "electricity" -> "Energy & Electricity"
            "streetlights" -> "Public Lighting & Electrical"
            "waste" -> "Solid Waste Management"
            else -> "General Municipal Maintenance"
        }
    }

    // =========================================================================
    // 3. IMAGE UPLOAD & FAULT CRUD OPERATIONS
    // =========================================================================


    private fun uploadFaultImage(
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val preset = CloudinaryConfig.UPLOAD_PRESET

        MediaManager.get()
            .upload(imageUri)
            .unsigned(preset)
            .callback(object : UploadCallback {

                override fun onStart(requestId: String?) {
                    // Upload started
                }

                override fun onProgress(
                    requestId: String?,
                    bytes: Long,
                    totalBytes: Long
                ) {
                    // Optional: update upload progress
                }

                override fun onSuccess(
                    requestId: String?,
                    resultData: MutableMap<Any?, Any?>?
                ) {
                    val secureUrl =
                        resultData?.get("secure_url") as? String

                    if (!secureUrl.isNullOrBlank()) {
                        onSuccess(secureUrl)
                    } else {
                        onFailure(
                            Exception("Cloudinary returned no secure image URL.")
                        )
                    }
                }

                override fun onError(
                    requestId: String?,
                    error: ErrorInfo?
                ) {
                    onFailure(
                        Exception(
                            "Cloudinary upload failed: " +
                                    (error?.description ?: "Unknown error")
                        )
                    )
                }

                override fun onReschedule(
                    requestId: String?,
                    error: ErrorInfo?
                ) {
                    // Cloudinary may retry the upload automatically.
                }
            })
            .dispatch()
    }


    /**
     * CREATE: Writes a complete Fault document into Firestore and logs its initial StatusHistory.
     */
    fun createFault(
        title: String = "",
        category: String,
        description: String,
        latitude: Double,
        longitude: Double,
        location: String,
        isAnonymous: Boolean = false,
        imageUrl: String = "",
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onFailure(Exception("You must be logged in to report a fault"))
            return
        }

        if (category.isBlank()) {
            onFailure(Exception("Please select a category"))
            return
        }

        val cleanTitle = title.trim().ifEmpty { "$category Fault" }
        val cleanDesc = description.trim().ifEmpty {
            "Reported via FaultFinder mobile app. Awaiting municipal field inspection."
        }

        val reference = db.collection("faults").document()
        val now = System.currentTimeMillis()
        val refCode = "#CT-${Random.nextInt(1000, 9999)}"
        val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(now))
        val autoPriority = calculatePriority(category, cleanTitle, cleanDesc)
        val assignedDept = mapCategoryToDepartment(category)

        val fault = Fault(
            id = reference.id,
            userId = userId,
            category = category,
            description = cleanDesc,
            latitude = latitude,
            longitude = longitude,
            location = location.trim(),
            imageUrl = imageUrl,
            priority = autoPriority,
            status = "Pending",
            createdAt = now,
            updatedAt = now,
            ref = refCode,
            title = cleanTitle,
            department = assignedDept,
            date = formattedDate,
            isAnonymous = isAnonymous,
            confirmations = 1
        )

        reference.set(fault)
            .addOnSuccessListener {
                val history = StatusHistory(
                    status = "Pending",
                    changedBy = if (isAnonymous) "Anonymous Citizen" else userId,
                    comment = "Fault submitted ($autoPriority Priority) -> $assignedDept",
                    timestamp = now
                )

                reference.collection("statusHistory")
                    .add(history)
                    .addOnSuccessListener { onSuccess(refCode) }
                    .addOnFailureListener { onSuccess(refCode) }
            }
            .addOnFailureListener(onFailure)
    }


    fun createFaultWithImage(
        title: String = "",
        category: String,
        description: String,
        latitude: Double,
        longitude: Double,
        location: String,
        isAnonymous: Boolean = false,
        imageUri: Uri?,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // If no image was selected, create the fault normally.
        if (imageUri == null) {
            createFault(
                title = title,
                category = category,
                description = description,
                latitude = latitude,
                longitude = longitude,
                location = location,
                isAnonymous = isAnonymous,
                imageUrl = "",
                onSuccess = onSuccess,
                onFailure = onFailure
            )
            return
        }

        // Upload the image to Cloudinary before saving the fault.
        uploadFaultImage(
            imageUri = imageUri,
            onSuccess = { imageUrl ->
                createFault(
                    title = title,
                    category = category,
                    description = description,
                    latitude = latitude,
                    longitude = longitude,
                    location = location,
                    isAnonymous = isAnonymous,
                    imageUrl = imageUrl,
                    onSuccess = onSuccess,
                    onFailure = onFailure
                )
            },
            onFailure = { exception ->
                // Do not create a fault if its selected image fails to upload.
                onFailure(exception)
            }
        )
    }


    /**
     * READ (All): Fetches all faults ordered newest first. Seeds 4 Cape Town faults if empty.
     */
    fun getAllFaults(
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("faults")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->
                val list = result.toObjects(Fault::class.java)
                if (list.isEmpty()) {
                    seedInitialFaultsIfEmpty(onSuccess)
                } else {
                    onSuccess(normalizeFaultList(list))
                }
            }
            .addOnFailureListener(onFailure)
    }

    /**
     * Real-time Snapshot Listener for all faults. Seeds initial faults if collection is empty.
     */
    fun listenToAllFaults(
        onChange: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("faults")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    onFailure(error)
                    return@addSnapshotListener
                }

                val faults = snapshots?.toObjects(Fault::class.java) ?: emptyList()
                if (faults.isEmpty()) {
                    seedInitialFaultsIfEmpty(onChange)
                } else {
                    onChange(normalizeFaultList(faults))
                }
            }
    }

    /**
     * READ (Current User): Fetches faults submitted by the logged-in user for MyReportsActivity.
     */
    fun getMyFaults(
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onSuccess(emptyList())
            return
        }

        db.collection("faults")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                val faults = result.toObjects(Fault::class.java)
                    .sortedByDescending { it.createdAt }
                onSuccess(normalizeFaultList(faults))
            }
            .addOnFailureListener(onFailure)
    }

    fun getFaultById(
        faultId: String,
        onSuccess: (Fault) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        db.collection("faults")
            .document(faultId)
            .get()
            .addOnSuccessListener { document ->
                val fault = document.toObject(Fault::class.java)
                if (fault != null) {
                    onSuccess(fault)
                } else {
                    onFailure(Exception("Fault report not found"))
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun listenToFault(
        faultId: String,
        onChange: (Fault) -> Unit,
        onFailure: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("faults")
            .document(faultId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onFailure(error)
                    return@addSnapshotListener
                }
                val fault = snapshot?.toObject(Fault::class.java)
                if (fault != null) {
                    onChange(fault)
                }
            }
    }

    fun getStatusHistory(
        faultId: String,
        onSuccess: (List<StatusHistory>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("faults")
            .document(faultId)
            .collection("statusHistory")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                onSuccess(result.toObjects(StatusHistory::class.java))
            }
            .addOnFailureListener(onFailure)
    }

    /**
     * UPDATE: Updates a fault's status and records a StatusHistory entry.
     */
    fun updateFaultStatus(
        faultId: String,
        newStatus: String,
        comment: String = "",
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: "citizen"
        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        val validStatuses = listOf(
            "Pending",
            "Submitted",
            "Validated",
            "Assigned",
            "In Progress",
            "Resolved"
        )

        if (newStatus !in validStatuses) {
            onFailure(Exception("Invalid fault status"))
            return
        }

        val reference = db.collection("faults").document(faultId)
        val now = System.currentTimeMillis()

        reference.update(
            mapOf(
                "status" to newStatus,
                "updatedAt" to now
            )
        )
            .addOnSuccessListener {
                val history = StatusHistory(
                    status = newStatus,
                    changedBy = userId,
                    comment = comment.ifBlank { "Status updated to $newStatus" },
                    timestamp = now
                )
                reference.collection("statusHistory")
                    .add(history)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onSuccess() }
            }
            .addOnFailureListener(onFailure)
    }

    /**
     * DELETE: Deletes a fault report document from Firestore (completes full CRUD).
     */
    fun deleteFault(
        faultId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        db.collection("faults")
            .document(faultId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun updateFaultPriority(
        faultId: String,
        priority: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        val validPriorities = listOf("Low", "Medium", "High", "Critical")
        if (priority !in validPriorities) {
            onFailure(Exception("Invalid priority"))
            return
        }

        db.collection("faults")
            .document(faultId)
            .update(
                mapOf(
                    "priority" to priority,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    // =========================================================================
    // 4. COMMENTS & CITIZEN ENGAGEMENT
    // =========================================================================

    fun addFaultComment(
        faultId: String,
        message: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onFailure(Exception("You must be logged in to add a comment"))
            return
        }

        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        if (message.isBlank()) {
            onFailure(Exception("Comment cannot be empty"))
            return
        }

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { userDocument ->
                val userName = userDocument.getString("name")
                    ?: auth.currentUser?.email?.substringBefore("@")
                    ?: "Citizen"

                val commentReference = db.collection("faults")
                    .document(faultId)
                    .collection("comments")
                    .document()

                val comment = FaultComment(
                    id = commentReference.id,
                    userId = userId,
                    userName = userName,
                    message = message.trim(),
                    createdAt = System.currentTimeMillis()
                )

                commentReference.set(comment)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun getFaultComments(
        faultId: String,
        onSuccess: (List<FaultComment>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        db.collection("faults")
            .document(faultId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                onSuccess(result.toObjects(FaultComment::class.java))
            }
            .addOnFailureListener(onFailure)
    }

    fun listenToFaultComments(
        faultId: String,
        onChange: (List<FaultComment>) -> Unit,
        onFailure: (Exception) -> Unit
    ): ListenerRegistration {
        return db.collection("faults")
            .document(faultId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onFailure(error)
                    return@addSnapshotListener
                }
                val comments = snapshot?.toObjects(FaultComment::class.java) ?: emptyList()
                onChange(comments)
            }
    }

    // =========================================================================
    // 5. MUNICIPAL WORKFLOW & DUPLICATE DETECTION
    // =========================================================================

    fun isMunicipalUser(
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            onFailure(Exception("No user is logged in"))
            return
        }

        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                val role = document.getString("role") ?: "citizen"
                onSuccess(role.equals("municipal", ignoreCase = true))
            }
            .addOnFailureListener(onFailure)
    }

    fun validateFault(
        faultId: String,
        comment: String = "Fault validated",
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        updateFaultStatus(faultId, "Validated", comment, onSuccess, onFailure)
    }

    fun assignFaultToDepartment(
        faultId: String,
        department: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: "municipal"
        if (faultId.isBlank() || department.isBlank()) {
            onFailure(Exception("Fault ID and Department are required"))
            return
        }

        val reference = db.collection("faults").document(faultId)
        val now = System.currentTimeMillis()

        reference.update(
            mapOf(
                "department" to department.trim(),
                "status" to "Assigned",
                "updatedAt" to now
            )
        )
            .addOnSuccessListener {
                val history = StatusHistory(
                    status = "Assigned",
                    changedBy = userId,
                    timestamp = now,
                    comment = "Assigned to ${department.trim()}"
                )
                reference.collection("statusHistory")
                    .add(history)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun startFaultWork(
        faultId: String,
        comment: String = "Work started",
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        updateFaultStatus(faultId, "In Progress", comment, onSuccess, onFailure)
    }

    fun resolveFault(
        faultId: String,
        comment: String = "Fault resolved",
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        updateFaultStatus(faultId, "Resolved", comment, onSuccess, onFailure)
    }

    fun getActiveFaults(
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("faults")
            .get()
            .addOnSuccessListener { result ->
                val faults = result.toObjects(Fault::class.java)
                    .filter { it.status != "Resolved" && !it.isDuplicate }
                    .sortedByDescending { it.createdAt }
                onSuccess(normalizeFaultList(faults))
            }
            .addOnFailureListener(onFailure)
    }

    fun getFaultsByStatus(
        status: String,
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("faults")
            .whereEqualTo("status", status)
            .get()
            .addOnSuccessListener { result ->
                val faults = result.toObjects(Fault::class.java)
                    .sortedByDescending { it.createdAt }
                onSuccess(normalizeFaultList(faults))
            }
            .addOnFailureListener(onFailure)
    }

    fun getFaultsByDepartment(
        department: String,
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (department.isBlank()) {
            onFailure(Exception("Department is required"))
            return
        }

        db.collection("faults")
            .whereEqualTo("department", department)
            .get()
            .addOnSuccessListener { result ->
                val faults = result.toObjects(Fault::class.java)
                    .sortedByDescending { it.createdAt }
                onSuccess(normalizeFaultList(faults))
            }
            .addOnFailureListener(onFailure)
    }

    /**
     * Spatial Duplicate Detection (Deliverable 2 Section 2.2):
     * Finds unresolved faults in the same category within 100m using GPS coordinates.
     */
    fun findPotentialDuplicates(
        category: String,
        latitude: Double,
        longitude: Double,
        distanceThresholdMetres: Float = 100f,
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (category.isBlank()) {
            onFailure(Exception("Fault category is required"))
            return
        }

        db.collection("faults")
            .whereEqualTo("category", category)
            .get()
            .addOnSuccessListener { result ->
                val possibleDuplicates = result.toObjects(Fault::class.java)
                    .filter { existingFault ->
                        if (existingFault.status.equals("Resolved", ignoreCase = true)) {
                            false
                        } else {
                            val distance = FloatArray(1)
                            Location.distanceBetween(
                                latitude,
                                longitude,
                                existingFault.latitude,
                                existingFault.longitude,
                                distance
                            )
                            distance[0] <= distanceThresholdMetres
                        }
                    }
                    .sortedBy { existingFault ->
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            latitude,
                            longitude,
                            existingFault.latitude,
                            existingFault.longitude,
                            distance
                        )
                        distance[0]
                    }

                onSuccess(normalizeFaultList(possibleDuplicates))
            }
            .addOnFailureListener(onFailure)
    }

    fun checkForDuplicatesBeforeSubmission(
        category: String,
        latitude: Double,
        longitude: Double,
        distanceThresholdMetres: Float = 100f,
        onDuplicatesFound: (List<Fault>) -> Unit,
        onNoDuplicatesFound: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        findPotentialDuplicates(
            category = category,
            latitude = latitude,
            longitude = longitude,
            distanceThresholdMetres = distanceThresholdMetres,
            onSuccess = { duplicates ->
                if (duplicates.isEmpty()) {
                    onNoDuplicatesFound()
                } else {
                    onDuplicatesFound(duplicates)
                }
            },
            onFailure = onFailure
        )
    }

    /**
     * Increments the confirmation count on an existing fault when a citizen chooses
     * "Confirm Existing Fault" instead of submitting a duplicate report.
     */
    fun confirmExistingFault(
        faultId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (faultId.isBlank()) {
            onFailure(Exception("Invalid fault ID"))
            return
        }
        db.collection("faults")
            .document(faultId)
            .update(
                mapOf(
                    "confirmations" to FieldValue.increment(1),
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    fun markFaultAsDuplicate(
        duplicateFaultId: String,
        originalFaultId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: "citizen"
        if (duplicateFaultId.isBlank() || originalFaultId.isBlank() || duplicateFaultId == originalFaultId) {
            onFailure(Exception("Invalid duplicate fault IDs"))
            return
        }

        val duplicateReference = db.collection("faults").document(duplicateFaultId)
        val now = System.currentTimeMillis()

        duplicateReference.update(
            mapOf(
                "isDuplicate" to true,
                "duplicateOfFaultId" to originalFaultId,
                "updatedAt" to now
            )
        )
            .addOnSuccessListener {
                val history = StatusHistory(
                    status = "Submitted",
                    changedBy = userId,
                    timestamp = now,
                    comment = "Marked as duplicate of fault $originalFaultId"
                )
                duplicateReference.collection("statusHistory")
                    .add(history)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun removeDuplicateMark(
        faultId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid ?: "citizen"
        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        val reference = db.collection("faults").document(faultId)
        val now = System.currentTimeMillis()

        reference.update(
            mapOf(
                "isDuplicate" to false,
                "duplicateOfFaultId" to "",
                "updatedAt" to now
            )
        )
            .addOnSuccessListener {
                val history = StatusHistory(
                    status = "Pending",
                    changedBy = userId,
                    timestamp = now,
                    comment = "Duplicate classification removed"
                )
                reference.collection("statusHistory")
                    .add(history)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    // =========================================================================
    // 6. HELPERS: NORMALIZATION & INITIAL DATA SEEDING
    // =========================================================================

    /**
     * Ensures older Firestore documents that were saved without a title, ref, date, or
     * department still display cleanly in the UI.
     */
    private fun normalizeFaultList(list: List<Fault>): List<Fault> {
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        return list.mapIndexed { index, fault ->
            if (fault.title.isBlank()) {
                fault.title = "${fault.category.ifBlank { "Municipal" }} Fault"
            }
            if (fault.ref.isBlank()) {
                val suffix = (1001 + index).toString()
                fault.ref = "#CT-$suffix"
            }
            if (fault.date.isBlank()) {
                fault.date = formatter.format(Date(fault.createdAt))
            }
            if (fault.department.isBlank()) {
                fault.department = mapCategoryToDepartment(fault.category)
            }
            if (fault.location.isBlank()) {
                fault.location = String.format(Locale.US, "%.4f, %.4f", fault.latitude, fault.longitude)
            }
            fault
        }
    }

    /**
     * Seeds 4 realistic Cape Town faults if the Firestore collection is completely empty.
     */
    private fun seedInitialFaultsIfEmpty(onComplete: (List<Fault>) -> Unit) {
        val uid = auth.currentUser?.uid ?: "demo_citizen"
        val now = System.currentTimeMillis()
        val seedFaults = listOf(
            Fault(
                id = "seed_ct_1",
                userId = uid,
                ref = "#CT-1001",
                title = "Pothole on Main Rd",
                category = "Roads",
                description = "Deep pothole in the left lane near the intersection, causing vehicles to swerve.",
                location = "Main Rd, Table View",
                latitude = -33.8267,
                longitude = 18.4912,
                priority = "High",
                status = "Pending",
                department = "Roads & Stormwater",
                date = "18 Oct 2026",
                createdAt = now - 259200000L,
                updatedAt = now - 259200000L,
                confirmations = 3
            ),
            Fault(
                id = "seed_ct_2",
                userId = uid,
                ref = "#CT-1002",
                title = "Burst Water Pipe",
                category = "Water",
                description = "Clean water gushing onto the pavement and flooding the storm drain.",
                location = "Blaauwberg St, Table View",
                latitude = -33.8075,
                longitude = 18.4735,
                priority = "High",
                status = "In Progress",
                department = "Water & Sanitation",
                date = "19 Oct 2026",
                createdAt = now - 172800000L,
                updatedAt = now - 86400000L,
                confirmations = 5
            ),
            Fault(
                id = "seed_ct_3",
                userId = uid,
                ref = "#CT-1003",
                title = "Streetlight Out",
                category = "Streetlights",
                description = "Three consecutive streetlights out along the main residential stretch.",
                location = "Parklands Main Rd, Parklands",
                latitude = -33.7965,
                longitude = 18.5042,
                priority = "Medium",
                status = "Resolved",
                department = "Public Lighting & Electrical",
                date = "16 Oct 2026",
                createdAt = now - 345600000L,
                updatedAt = now - 43200000L,
                confirmations = 2
            ),
            Fault(
                id = "seed_ct_4",
                userId = uid,
                ref = "#CT-1004",
                title = "Illegal Dumping",
                category = "Waste",
                description = "Construction rubble and household refuse dumped on the road verge.",
                location = "Sandown Rd, Sunningdale",
                latitude = -33.8012,
                longitude = 18.5180,
                priority = "Low",
                status = "Pending",
                department = "Solid Waste Management",
                date = "20 Oct 2026",
                createdAt = now - 43200000L,
                updatedAt = now - 43200000L,
                confirmations = 1
            )
        )

        val batch = db.batch()
        seedFaults.forEach { fault ->
            batch.set(db.collection("faults").document(fault.id), fault)
        }
        batch.commit().addOnCompleteListener {
            onComplete(seedFaults)
        }
    }
}