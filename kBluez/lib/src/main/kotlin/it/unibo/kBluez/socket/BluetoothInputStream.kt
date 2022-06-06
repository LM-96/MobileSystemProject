package it.unibo.kBluez.socket

import it.unibo.kBluez.pybluez.PythonWrapperReader
import kotlinx.coroutines.runBlocking
import org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.io.InputStream

class BluetoothInputStream(
    private val sock : PyBluezSocket) : InputStream() {

    private var buf : ByteArrayInputStream? = null
    private var bytesToRead = 0

    override fun read(): Int {
        if(bytesToRead == 0)
            buf = runBlocking {
                val bytebuf = sock.receive()
                bytesToRead = bytebuf.size
                ByteArrayInputStream(bytebuf)
            }
        bytesToRead--
        return buf!!.read()
    }

    override fun read(b: ByteArray): Int {
        return runBlocking {
            val readed = reader.readSocketReceive(uuid)
            if(readed.size < b.size) {
                readed.copyInto(b, 0, 0, b.size)
                b.size
            } else {
                readed.copyInto(b)
                readed.size
            }
        }
    }


}