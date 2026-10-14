package com.eduvos.s4.faultfinderapp.model

data class Fault(

    var id: String = "",
    var usesrId: String = "",
    var category: String = "",
    var description: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var imagerUrl: String = "",
    var priority: String = "Medium",
    var status: String = "Submitted",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)
