package com.eduvos.s4.faultfinderapp

data class FaultComment(
    var id: String = "",
    var userId: String = "",
    var userName: String = "",
    var message: String = "",
    var createdAt: Long = 0L
)
