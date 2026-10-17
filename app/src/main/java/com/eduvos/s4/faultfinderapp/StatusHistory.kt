package com.eduvos.s4.faultfinderapp

data class StatusHistory(

    var status: String = "",
    var changedBy: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var comment: String = ""
)
