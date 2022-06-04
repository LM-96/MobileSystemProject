package it.unibo.kBluez.model

import java.util.UUID

data class BluetoothServiceProfile(
    val uuid : UUID,
    val version : Int
)