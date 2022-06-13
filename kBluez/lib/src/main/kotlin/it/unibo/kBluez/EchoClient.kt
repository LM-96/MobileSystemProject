import it.unibo.kBluez.KBLUEZ
import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.newBluetoothSocket
import it.unibo.kBluez.utils.addLevelTab
import it.unibo.kBluez.utils.askTt
import it.unibo.kBluez.utils.printTt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

fun main(args : Array<String>) {
    runBlocking {
        //printTt("DEVICES: *****************************************************")
        //KBLUEZ.scan().forEach { printTt(it.toString()) }

        //printTt("SERVICES *****************************************************")
        //KBLUEZ.findServices().forEach { printTt(it.toString()) }

        val clientSock = newBluetoothSocket(BluetoothServiceProtocol.RFCOMM)
        printTt("Created new socket")

        val addr = "DC:A6:32:FC:83:5E"
        askTt("Insert the port you want to connect")
        val port = readln().trim().toInt()
        clientSock.connect(addr, port)
        printTt("connected")

        while (true) {
            askTt("Insert some text")
            clientSock.send(readln().trim().toByteArray())
            printTt("Data sent")
            printTt("Received: \'${clientSock.receive().decodeToString().trim()}\'")
        }

    }
}