package com.eduvos.s4.faultfinderapp

data class User(

    var userId: String = "",
    var name: String = "",
    var email: String = "",
    var role: String = "citizen",
    var createdAt: Long = System.currentTimeMillis()
)
