package it.unibo.kBluez.pybluez

import it.unibo.kBluez.KBluez
import it.unibo.kBluez.model.BluetoothDevice
import it.unibo.kBluez.model.BluetoothLookupResult
import it.unibo.kBluez.model.BluetoothService
import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.socket.PyBluezSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KLogger
import mu.KotlinLogging
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile

class PyKBluez(private val scope : CoroutineScope = GlobalScope) : KBluez {

    companion object {
        private const val PY_BLUEZ_WR_ORIGIN = "/python/pybluezwrapper.py"
        val PY_BLUEZ_WR_EXECUTABLE : Path
        private val LOG = KotlinLogging.logger("PyKBluez")

        init {
            val resource: InputStream = Companion::class.java.getResourceAsStream(PY_BLUEZ_WR_ORIGIN)
                ?: throw PythonBluezWrapperException("Python bluez wrapper not found at \'$PY_BLUEZ_WR_ORIGIN\'\n" +
                        "Base dir: ${Companion::class.java.getResource(".")?.path}")

            PY_BLUEZ_WR_EXECUTABLE = createTempFile("pybluezwrapper.", ".py")
            Files.copy(resource, PY_BLUEZ_WR_EXECUTABLE, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private lateinit var process : Process
    private lateinit var input : PythonWrapperReader
    private lateinit var output : PythonWrapperWriter
    private lateinit var log : KLogger
    init {
        runBlocking {
            startProcess()
        }
    }

    @Throws(PythonBluezWrapperException::class)
    private suspend fun startProcess() {
        try {
            process = ProcessBuilder("python3",
                PY_BLUEZ_WR_EXECUTABLE.absolutePathString()).start()
        } catch (e : Exception) {
            LOG.catching(e)
            throw PythonBluezWrapperException("Unable to start python wrapper: ${e.localizedMessage}")
        }
        log = KotlinLogging
            .logger("${javaClass.simpleName}[${process.pid()}]")
        log.info("started python process [PID=${process.pid()}]")
        input = PythonWrapperReader(process, scope)
        output = PythonWrapperWriter(process, scope)

        log.info("waiting for IDLE state")
        ensureState(PythonWrapperState.IDLE)
        log.info("correctly started")
    }

    @Throws(PythonBluezWrapperException::class)
    private suspend fun ensureState(expected : PythonWrapperState) {
        val last = input.readState()
        if(last != expected)
            throw PythonBluezWrapperException("Unexpected state $last [expected = $expected]")
    }

    @Throws(PythonBluezWrapperException::class)
    private suspend fun ensureRunning() {
        if(!process.isAlive) {
            startProcess()
            if(!process.isAlive)
                throw PythonBluezWrapperException("Tried to start python process but fails")
        }

    }

    @Throws(PythonBluezWrapperException::class)
    override suspend fun scan(): List<BluetoothDevice> {
        log.info("scan()")
        ensureRunning()
        input.checkErrors()

        output.writeCommand(Commands.SCAN_CMD)
        ensureState(PythonWrapperState.SCANNING)

        val res = input.readScanResult()
        log.info("scan result = $res")
        ensureState(PythonWrapperState.IDLE)
        return res
    }

    @Throws(PythonBluezWrapperException::class)
    override suspend fun lookup(address: String): BluetoothLookupResult {
        log.info("lookup($address)")
        ensureRunning()

        output.writeLookupCommand(address)
        ensureState(PythonWrapperState.LOOKING_UP)
        val res = input.readLookupResult()
        log.info("lookup result = $res")
        ensureState(PythonWrapperState.IDLE)
        return res
    }

    @Throws(PythonBluezWrapperException::class)
    override suspend fun findServices(name : String?, uuid : UUID?, address : String?): List<BluetoothService> {
        log.info("findServices()")
        ensureRunning()

        output.writeFindServicesCommand(name, uuid, address)
        ensureState(PythonWrapperState.FINDING_SERVICES)
        val res = input.readFindServicesResult()
        log.info("find services result = $res")
        ensureState(PythonWrapperState.IDLE)

        return res
    }

    override suspend fun newSocket(protocol : BluetoothServiceProtocol): PyBluezSocket {
        return PyBluezSocket.newPyBluezSocket(input, output, protocol)
    }

    @Throws(PythonBluezWrapperException::class)
    override fun close() {
        runBlocking {
            log.info("close()")
            if(process.isAlive)
                output.writeTerminateCommand()
        }
    }
}