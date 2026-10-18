package com.eduvos.s4.faultfinderapp

data class User(

    var userId: String = "",
    var name: String = "",
    var email: String = "",
    var role: String = "citizen",
    var createdAt: Long = System.currentTimeMillis()
)

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
