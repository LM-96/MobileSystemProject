package it.unibo.kBluez.socket

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.SocketAddress
import java.net.SocketImpl

class BluetoothSocketImpl : SocketImpl() {

    override fun setOption(optID: Int, value: Any?) {
        throw IllegalArgumentException("This socket does not support this operation")
    }

    override fun getOption(optID: Int): Any {
        throw IllegalArgumentException("This socket does not support this operation")
    }

    override fun create(stream: Boolean) {
        throw IllegalArgumentException("This socket does not support this operation")
    }

    override fun connect(host: String?, port: Int) {
        TODO("Not yet implemented")
    }

    override fun connect(address: InetAddress?, port: Int) {
        TODO("Not yet implemented")
    }

    override fun connect(address: SocketAddress?, timeout: Int) {
        TODO("Not yet implemented")
    }

    override fun bind(host: InetAddress?, port: Int) {
        TODO("Not yet implemented")
    }

    override fun listen(backlog: Int) {
        TODO("Not yet implemented")
    }

    override fun accept(s: SocketImpl?) {
        TODO("Not yet implemented")
    }

    override fun getInputStream(): InputStream {
        TODO("Not yet implemented")
    }

    override fun getOutputStream(): OutputStream {
        TODO("Not yet implemented")
    }

    override fun available(): Int {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun sendUrgentData(data: Int) {
        TODO("Not yet implemented")
    }
}