package it.unibo.kBluez.pybluez

import it.unibo.kBluez.KBluez
import it.unibo.kBluez.model.BluetoothDevice
import it.unibo.kBluez.model.BluetoothService
import mu.KLogger
import mu.KotlinLogging
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile

class PyKBluez() : KBluez {

    companion object {
        const val PY_BLUEZ_WR_ORIGIN = "/python/pybluezwrapper.py"
        val PY_BLUEZ_WR_EXECUTABLE : Path

        init {
            val resource: InputStream = Companion::class.java.getResourceAsStream(PY_BLUEZ_WR_ORIGIN)
                ?: throw PythonBluezWrapperException("Python bluez wrapper not found at \'$PY_BLUEZ_WR_ORIGIN\'\n" +
                        "Base dir: ${Companion::class.java.getResource(".")?.path}")

            PY_BLUEZ_WR_EXECUTABLE = createTempFile("pybluezwrapper.", ".py")
            Files.copy(resource, PY_BLUEZ_WR_EXECUTABLE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private val process : Process
    private val input : PythonWrapperReader
    private val output : PythonWrapperWriter
    private val log : KLogger
    init {
        try {
            process = ProcessBuilder("python3", PY_BLUEZ_WR_EXECUTABLE.absolutePathString()).start()
        } catch (e : Exception) {
            throw PythonBluezWrapperException("Unable to start python wrapper: ${e.localizedMessage}")
        }

        input = PythonWrapperReader(process)
        output = PythonWrapperWriter(process)
        log = KotlinLogging.logger(javaClass.simpleName)
    }

    override fun scan(): List<BluetoothDevice> {
        log.info("scan()")
        output.writeCommand(Commands.SCAN_CMD)
        val res = input.readScanResult()
        log.info("scan result = $res")
        return res
    }

    override fun lookup(address: String): Optional<String> {
        log.info("lookup($address)")
        output.writeLookupCommand(address)
        return try {
            val res = input.readLookupResult()
            log.info("lookup result = $res")
            Optional.of(res)
        } catch (pwe : PythonBluezWrapperException) {
            log.catching(pwe)
            Optional.empty()
        }
    }

    override fun findServices(name : String?, uuid : UUID?, address : String?): List<BluetoothService> {
        log.info("findServices()")
        output.writeFindServicesCommand(name, uuid, address)
        val res = input.readFindServicesResult()
        log.info("find services result = $res")

        return res
    }

    override fun close() {
        log.info("close()")
        output.writeTerminateCommand()
    }
}