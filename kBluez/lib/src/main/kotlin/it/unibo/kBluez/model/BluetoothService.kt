package it.unibo.kBluez.model

import java.util.UUID

data class BluetoothService(
    val host : String,
    val protocol: BluetoothServiceProtocol,
    val port : Int?,
    val name : String?,
    val provider : String?,
    val serviceClasses : UUID?,
    val profiles : List<BluetoothServiceProfile> = mutableListOf(),
    val serviceId : String? = null
)