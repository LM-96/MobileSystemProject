package it.unibo.kBluez.socket

import it.unibo.kBluez.pybluez.PythonWrapperWriter
import kotlinx.coroutines.runBlocking
import java.io.OutputStream

class BluetoothOutputStream(
    private val sockUUID : String,
    private val writer : PythonWrapperWriter) : OutputStream() {

    override fun write(b: Int) {
        runBlocking {
            writer.writeSocketSendCommand(sockUUID, byteArrayOf(b.toByte()))
        }
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        runBlocking {
            writer.writeSocketSendCommand(sockUUID, b, off, len)
        }
    }
}