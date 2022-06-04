package it.unibo.mobilesystems

import AdvertisementDataRetrievalKeys
import dev.bluefalcon.*

class BluetoothService(context : ApplicationContext): BlueFalconDelegate {

    private val blueFalcon = BlueFalcon(context, null)

    private val devices: MutableList<BluetoothPeripheral> = mutableListOf()

    init {
        blueFalcon.delegates.add(this)
    }

    fun scan() {
        blueFalcon.scan()
    }

    fun connect(bluetoothPeripheral: BluetoothPeripheral) {
        blueFalcon.connect(bluetoothPeripheral, true)
    }

    fun disconnect(bluetoothPeripheral: BluetoothPeripheral) {
        blueFalcon.disconnect(bluetoothPeripheral)
    }

    override fun didDiscoverDevice(
        bluetoothPeripheral: BluetoothPeripheral,
        advertisementData: Map<AdvertisementDataRetrievalKeys, Any>
    ) {
        if (devices.firstOrNull {
                it.bluetoothDevice.address == bluetoothPeripheral.bluetoothDevice.address
            } == null) {
            println("Device advertised data: $advertisementData")

            devices.add(bluetoothPeripheral)
        }
    }

    override fun didConnect(bluetoothPeripheral: BluetoothPeripheral) {
        println("didConnect: $bluetoothPeripheral")
    }

    override fun didDiscoverServices(bluetoothPeripheral: BluetoothPeripheral) {
        println("didDiscoverServices: $bluetoothPeripheral")
        blueFalcon.changeMTU(bluetoothPeripheral, 250)
    }

    override fun didReadDescriptor(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristicDescriptor: BluetoothCharacteristicDescriptor
    ) {
        println("didReadDescriptor: $bluetoothPeripheral, descriptor: $bluetoothCharacteristicDescriptor")
    }

    override fun didRssiUpdate(bluetoothPeripheral: BluetoothPeripheral) {
        print("Rssi updated.")
    }

    override fun didCharacteristcValueChanged(bluetoothPeripheral: BluetoothPeripheral, bluetoothCharacteristic: BluetoothCharacteristic) {
        println("didCharacteristicValueChanged: $bluetoothPeripheral, characteristic : $bluetoothCharacteristic")
    }

    override fun didDisconnect(bluetoothPeripheral: BluetoothPeripheral) {}

    override fun didDiscoverCharacteristics(bluetoothPeripheral: BluetoothPeripheral) {}

    override fun didUpdateMTU(bluetoothPeripheral: BluetoothPeripheral) {}
    override fun didWriteCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic,
        success: Boolean
    ) {
        print("didWriteCharacteristic $bluetoothCharacteristic --> success: $success")
    }

}


fun main(args : Array<String>) {
    val service = it.unibo.mobilesystems.BluetoothService(ApplicationContext())
    println("Service created")
    service.scan()
    println("Scan started")
}