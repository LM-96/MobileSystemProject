package it.unibo.kBluez.model

data class BluetoothDevice(
    val address : String,
    val name : String,
    val classCode : Int
)