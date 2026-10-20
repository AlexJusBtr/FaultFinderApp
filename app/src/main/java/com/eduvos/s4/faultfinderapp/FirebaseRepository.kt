package com.eduvos.s4.faultfinderapp

import android.net.Uri
import android.location.Location
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class FirebaseRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

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
                    .addOnSuccessListener {
                        onSuccess(user)
                    }
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
            .addOnSuccessListener {
                onSuccess()
            }
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
                    onFailure(Exception("User profile was not found"))
                }
            }
            .addOnFailureListener(onFailure)
    }

    fun uploadFaultImage(
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onFailure(Exception("You must be logged in"))
            return
        }

        val filename = "${UUID.randomUUID()}.jpg"

        val imageRef = storage.reference
            .child("fault_images")
            .child(userId)
            .child(filename)

        imageRef.putFile(imageUri)
            .continueWithTask { task ->

                if (!task.isSuccessful) {
                    throw task.exception
                        ?: Exception("Image upload failed")
                }

                imageRef.downloadUrl
            }
            .addOnSuccessListener { uri ->
                onSuccess(uri.toString())
            }
            .addOnFailureListener(onFailure)
    }

    fun createFault(
        category: String,
        description: String,
        latitude: Double,
        longitude: Double,
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

        if (description.isBlank()) {
            onFailure(Exception("Please enter a description"))
            return
        }

        val reference = db.collection("faults").document()
        val now = System.currentTimeMillis()

        val fault = Fault(
            id = reference.id,
            userId = userId,
            category = category,
            description = description.trim(),
            latitude = latitude,
            longitude = longitude,
            imageUrl = imageUrl,
            priority = "Medium",
            status = "Submitted",
            createdAt = now,
            updatedAt = now
        )

        reference.set(fault)
            .addOnSuccessListener {

                val history = StatusHistory(
                    status = "Submitted",
                    changedBy = userId,
                    comment = "Fault submitted",
                    timestamp = now
                )

                reference.collection("statusHistory")
                    .add(history)
                    .addOnSuccessListener {
                        onSuccess(reference.id)
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun createFaultWithImage(
        category: String,
        description: String,
        latitude: Double,
        longitude: Double,
        imageUri: Uri?,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        if (imageUri == null) {

            createFault(
                category,
                description,
                latitude,
                longitude,
                "",
                onSuccess,
                onFailure
            )

            return
        }

        uploadFaultImage(
            imageUri,
            onSuccess = { imageUrl ->

                createFault(
                    category,
                    description,
                    latitude,
                    longitude,
                    imageUrl,
                    onSuccess,
                    onFailure
                )
            },
            onFailure = onFailure
        )
    }

    fun getAllFaults(
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("faults")
            .orderBy(
                "createdAt",
                Query.Direction.DESCENDING
            )
            .get()
            .addOnSuccessListener { result ->

                onSuccess(
                    result.toObjects(Fault::class.java)
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun getMyFaults(
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onFailure(Exception("No user is logged in"))
            return
        }

        db.collection("faults")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->

                val faults = result
                    .toObjects(Fault::class.java)
                    .sortedByDescending {
                        it.createdAt
                    }

                onSuccess(faults)
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

                val fault =
                    document.toObject(Fault::class.java)

                if (fault != null) {
                    onSuccess(fault)
                } else {
                    onFailure(
                        Exception("Fault report not found")
                    )
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

                val fault =
                    snapshot?.toObject(Fault::class.java)

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
            .orderBy(
                "timestamp",
                Query.Direction.ASCENDING
            )
            .get()
            .addOnSuccessListener { result ->

                onSuccess(
                    result.toObjects(
                        StatusHistory::class.java
                    )
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun updateFaultStatus(
        faultId: String,
        newStatus: String,
        comment: String = "",
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onFailure(Exception("No user is logged in"))
            return
        }

        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        val validStatuses = listOf(
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

        val reference =
            db.collection("faults")
                .document(faultId)

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
                    comment = comment,
                    timestamp = now
                )

                reference
                    .collection("statusHistory")
                    .add(history)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun updateFaultPriority(
        faultId: String,
        priority: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onFailure(Exception("No user is logged in"))
            return
        }

        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        val validPriorities = listOf(
            "Low",
            "Medium",
            "High",
            "Critical"
        )

        if (priority !in validPriorities) {
            onFailure(Exception("Invalid priority"))
            return
        }

        db.collection("faults")
            .document(faultId)
            .update(
                mapOf(
                    "priority" to priority,
                    "updatedAt" to
                        System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener(onFailure)
    }

    fun addFaultComment(
        faultId: String,
        message: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onFailure(
                Exception(
                    "You must be logged in to add a comment"
                )
            )
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

                val userName =
                    userDocument.getString("name")
                        ?: "User"

                val commentReference =
                    db.collection("faults")
                        .document(faultId)
                        .collection("comments")
                        .document()

                val comment = FaultComment(
                    id = commentReference.id,
                    userId = userId,
                    userName = userName,
                    message = message.trim(),
                    createdAt =
                        System.currentTimeMillis()
                )

                commentReference
                    .set(comment)
                    .addOnSuccessListener {
                        onSuccess()
                    }
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
            .orderBy(
                "createdAt",
                Query.Direction.ASCENDING
            )
            .get()
            .addOnSuccessListener { result ->

                val comments =
                    result.toObjects(
                        FaultComment::class.java
                    )

                onSuccess(comments)
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
            .orderBy(
                "createdAt",
                Query.Direction.ASCENDING
            )
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    onFailure(error)
                    return@addSnapshotListener
                }

                val comments =
                    snapshot?.toObjects(
                        FaultComment::class.java
                    ) ?: emptyList()

                onChange(comments)
            }
    }

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

                val role =
                    document.getString("role")
                        ?: "citizen"

                onSuccess(
                    role.equals(
                        "municipal",
                        ignoreCase = true
                    )
                )
            }
            .addOnFailureListener(onFailure)
    }

    fun assignFaultToDepartment(
        faultId: String,
        department: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val userId = auth.currentUser?.uid

        if (userId == null) {
            onFailure(Exception("No user is logged in"))
            return
        }

        if (faultId.isBlank()) {
            onFailure(Exception("Fault ID is required"))
            return
        }

        if (department.isBlank()) {
            onFailure(Exception("Department is required"))
            return
        }

        val reference =
            db.collection("faults")
                .document(faultId)

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
                    comment =
                        "Assigned to ${department.trim()}"
                )

                reference
                    .collection("statusHistory")
                    .add(history)
                    .addOnSuccessListener {
                        onSuccess()
                    }
                    .addOnFailureListener(onFailure)
            }
            .addOnFailureListener(onFailure)
    }

    fun getFaultsByStatus(
        status: String,
        onSuccess: (List<Fault>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val validStatuses = listOf(
            "Submitted",
            "Validated",
            "Assigned",
            "In Progress",
            "Resolved"
        )

        if (status !in validStatuses) {
            onFailure(Exception("Invalid fault status"))
            return
        }

        db.collection("faults")
            .whereEqualTo("status", status)
            .get()
            .addOnSuccessListener { result ->

                val faults =
                    result.toObjects(Fault::class.java)
                        .sortedByDescending {
                            it.createdAt
                        }

                onSuccess(faults)
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
            .whereEqualTo(
                "department",
                department
            )
            .get()
            .addOnSuccessListener { result ->

                val faults =
                    result.toObjects(Fault::class.java)
                        .sortedByDescending {
                            it.createdAt
                        }

                onSuccess(faults)
            }
            .addOnFailureListener(onFailure)
    }

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

    if (distanceThresholdMetres <= 0) {
        onFailure(Exception("Distance threshold must be greater than zero"))
        return
    }

    db.collection("faults")
        .whereEqualTo("category", category)
        .get()
        .addOnSuccessListener { result ->

            val possibleDuplicates = result
                .toObjects(Fault::class.java)
                .filter { existingFault ->

                    // Resolved faults should not be considered duplicates
                    if (existingFault.status == "Resolved") {
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

            onSuccess(possibleDuplicates)
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
fun markFaultAsDuplicate(
    duplicateFaultId: String,
    originalFaultId: String,
    onSuccess: () -> Unit,
    onFailure: (Exception) -> Unit
) {
    val userId = auth.currentUser?.uid

    if (userId == null) {
        onFailure(Exception("No user is logged in"))
        return
    }

    if (duplicateFaultId.isBlank()) {
        onFailure(Exception("Duplicate fault ID is required"))
        return
    }

    if (originalFaultId.isBlank()) {
        onFailure(Exception("Original fault ID is required"))
        return
    }

    if (duplicateFaultId == originalFaultId) {
        onFailure(Exception("A fault cannot be a duplicate of itself"))
        return
    }

    val duplicateReference =
        db.collection("faults")
            .document(duplicateFaultId)

    val originalReference =
        db.collection("faults")
            .document(originalFaultId)

    originalReference
        .get()
        .addOnSuccessListener { originalDocument ->

            if (!originalDocument.exists()) {
                onFailure(
                    Exception("Original fault report was not found")
                )
                return@addOnSuccessListener
            }

            duplicateReference
                .get()
                .addOnSuccessListener { duplicateDocument ->

                    if (!duplicateDocument.exists()) {
                        onFailure(
                            Exception("Duplicate fault report was not found")
                        )
                        return@addOnSuccessListener
                    }

                    val now = System.currentTimeMillis()

                    duplicateReference
                        .update(
                            mapOf(
                                "isDuplicate" to true,
                                "duplicateOfFaultId" to originalFaultId,
                                "updatedAt" to now
                            )
                        )
                        .addOnSuccessListener {

                            val history = StatusHistory(
                                status = duplicateDocument
                                    .getString("status")
                                    ?: "Submitted",
                                changedBy = userId,
                                timestamp = now,
                                comment =
                                    "Marked as duplicate of fault $originalFaultId"
                            )

                            duplicateReference
                                .collection("statusHistory")
                                .add(history)
                                .addOnSuccessListener {
                                    onSuccess()
                                }
                                .addOnFailureListener(onFailure)
                        }
                        .addOnFailureListener(onFailure)
                }
                .addOnFailureListener(onFailure)
        }
        .addOnFailureListener(onFailure)
}
}
