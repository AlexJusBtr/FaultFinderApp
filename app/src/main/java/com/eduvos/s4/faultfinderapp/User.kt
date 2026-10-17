package com.eduvos.s4.faultfinderapp

import com.google.firebase.Timestamp

data class User(

    var userId: String = "",
    var name: String = "",
    var email: String = "",
    var password: String = "",      //add password because the log in function/firebase auth  requires the password
    var role: String = "citizen",
    var createdAt: Timestamp = Timestamp.now()
)
