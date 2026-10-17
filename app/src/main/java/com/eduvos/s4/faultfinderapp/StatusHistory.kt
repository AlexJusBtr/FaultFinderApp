package com.eduvos.s4.faultfinderapp

import com.google.firebase.Timestamp

data class StatusHistory(

    var status: String = "",
    var changedBy: String = "",
    var timestamp: Timestamp = Timestamp.now(), //just changed System.currentTimeMillis() to Timestamp.now()
    var comment: String = ""
)
