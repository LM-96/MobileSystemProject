package it.unibo.kBluez.pybluez

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import it.unibo.kBluez.KBluez
import it.unibo.kBluez.io.*
import it.unibo.kBluez.model.BluetoothDevice
import it.unibo.kBluez.model.BluetoothLookupResult
import it.unibo.kBluez.model.BluetoothService
import it.unibo.kBluez.model.BluetoothServiceProtocol
import it.unibo.kBluez.socket.BluetoothSocket
import kotlinx.coroutines.*
import mu.KLogger
import mu.KotlinLogging
import java.io.Closeable
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile

class PyKBluez(private val scope : CoroutineScope = GlobalScope) :
    KBluez, Closeable, AutoCloseable
{

    companion object {
        private const val PY_BLUEZ_WR_ORIGIN = "/python/pybluezwrapper.py"
        val PY_BLUEZ_WR_EXECUTABLE : Path
        private val LOG = KotlinLogging.logger("PyKBluez")

        init {
            val resource: InputStream = Companion::class.java.getResourceAsStream(PY_BLUEZ_WR_ORIGIN)
                ?: throw PyBluezWrapperException("Python bluez wrapper not found at \'$PY_BLUEZ_WR_ORIGIN\'\n" +
                        "Base dir: ${Companion::class.java.getResource(".")?.path}")

            PY_BLUEZ_WR_EXECUTABLE = createTempFile("pybluezwrapper.", ".py")
            Files.copy(resource, PY_BLUEZ_WR_EXECUTABLE, StandardCopyOption.REPLACE_EXISTING)
        }

        suspend fun newBluetoothSocket(protocol: BluetoothServiceProtocol) : BluetoothSocket {
            return PyKBluez().requestNewSocket(protocol)
        }
    }

    private lateinit var process : Process
    private lateinit var input : PyBluezWrapperReader
    private lateinit var output : PyBluezWrapperWriter
    private lateinit var pInRouter : CloseableChannelRouter<JsonObject>
    private lateinit var pOutRouter : CloseableChannelRouter<JsonObject>
    private lateinit var pErrRouter : CloseableChannelRouter<JsonObject>
    private lateinit var log : KLogger
    private var started = false

    init {
        runBlocking {
            startProcess()
        }
    }

    @Throws(PyBluezWrapperException::class)
    private suspend fun startProcess() {
        if(started) {
            pErrRouter.close()
            pInRouter.close()
            input.close()
            output.close()
        }

        try {
            process = ProcessBuilder("python",
                PY_BLUEZ_WR_EXECUTABLE.absolutePathString()).start()
        } catch (e : Exception) {
            LOG.catching(e)
            throw PyBluezWrapperException("Unable to start python wrapper: ${e.localizedMessage}")
        }
        log = KotlinLogging
            .logger("${javaClass.simpleName}[${process.pid()}]")
        log.info("started python process [PID=${process.pid()}]")

        pInRouter = process.inputStream.newFanOutMappedStringRouter(scope, "pIn") {
            try {
                Optional.of(JsonParser.parseString(it).asJsonObject)
            } catch (e : Exception) {
                Optional.empty<JsonObject>()
            }
        }
        pInRouter.started()

        pErrRouter = process.errorStream.newFanOutMappedStringRouter(scope, "pErr") {
            try {
                Optional.of(JsonParser.parseString(it).asJsonObject)
            } catch (e : Exception) {
                Optional.empty<JsonObject>()
            }
        }
        pErrRouter.started()

        pOutRouter = process.outputStream.newFanInMappedStringRouter<JsonObject>(scope, "pOut") {
            Optional.of(it.toString())
        }
        pOutRouter.started()

        input = PyBluezWrapperReader(
            pInRouter.newRoute("pykbluez-main-p-stdin",
                passage = allowedKeyFilterPassage(STATE_KEY, SCAN_RES_KEY, LOOKUP_RES_KEY, FIND_SERVICES_RES_KEY,
                    NEW_SOCKET_UUID)
            ).channel,
            pErrRouter.newRoute("pykbluez-main-p-stderr",
                passage = allowedKeyStringFilterPassage("source", "main")
            ).channel,
        )
        output = PyBluezWrapperWriter(
            pOutRouter.newRoute("pykbluez-main-p-stdout").channel,
            scope)

        log.info("waiting for IDLE state")
        ensureState(PyBluezWrapperState.IDLE)
        log.info("correctly started")
        started = true
    }

    @Throws(PyBluezWrapperException::class)
    private suspend fun ensureState(expected : PyBluezWrapperState) {
        val last = input.readState()
        if(last != expected)
            throw PyBluezWrapperException("Unexpected state $last [expected = $expected]")
    }

    @Throws(PyBluezWrapperException::class)
    private suspend fun ensureRunning() {
        if(!process.isAlive) {
            startProcess()
            if(!process.isAlive)
                throw PyBluezWrapperException("Tried to start python process but fails")
        }

    }

    @Throws(PyBluezWrapperException::class)
    override suspend fun scan(): List<BluetoothDevice> {
        log.info("scanning...")
        ensureRunning()
        input.skipRemaining()

        output.writeCommand(Commands.SCAN_CMD)
        ensureState(PyBluezWrapperState.SCANNING)

        val res = input.readScanResult()
        log.info("scan result = $res")
        ensureState(PyBluezWrapperState.IDLE)
        return res
    }

    @Throws(PyBluezWrapperException::class)
    override suspend fun lookup(address: String): BluetoothLookupResult {
        log.info("looking up for \"$address\"")
        ensureRunning()
        input.skipRemaining()

        output.writeLookupCommand(address)
        ensureState(PyBluezWrapperState.LOOKING_UP)
        val res = input.readLookupResult()
        log.info("lookup result [\"$address\"] = $res")
        ensureState(PyBluezWrapperState.IDLE)
        return res
    }

    @Throws(PyBluezWrapperException::class)
    override suspend fun findServices(name : String?, uuid : UUID?, address : String?): List<BluetoothService> {
        log.info("find services...")
        ensureRunning()
        input.skipRemaining()

        output.writeFindServicesCommand(name, uuid, address)
        ensureState(PyBluezWrapperState.FINDING_SERVICES)
        val res = input.readFindServicesResult()
        log.info("find services result = $res")
        ensureState(PyBluezWrapperState.IDLE)

        return res
    }

    @Throws(PyBluezWrapperException::class)
    override suspend fun requestNewSocket(protocol : BluetoothServiceProtocol): BluetoothSocket {
        log.info("creating new $protocol socket")
        ensureRunning()
        input.skipRemaining()

        output.writeNewSocketCommand(protocol)
        input.ensureState(PyBluezWrapperState.CREATING_SOCKET)
        val uuid = input.readNewSocketUUID()
        log.info("received new socket uuid: $uuid")
        input.ensureState(PyBluezWrapperState.IDLE)

        return instantiateSocket(uuid, protocol)
    }

    internal suspend fun instantiateSocket(uuid : String,
                                           protocol : BluetoothServiceProtocol
    ) : BluetoothSocket {
        val sockName = "sock_$uuid"

        log.info("adding new route to the process stdin for \'$sockName\'")
        val sockIn = pInRouter.newRoute("sock_in_$sockName",
            passage = allowedKeyStringFilterPassage("sock_uuid", uuid)
        ).channel
        if(pInRouter.getRoute("sock_in_$sockName") != null)
            log.info("stdin route correctly added")
        else {
            log.error("unable to add a route for stdin")
            throw PyBluezWrapperException("Unable to add new STDIN route for the new socket [$sockName]")
        }

        log.info("adding new route to the process stderr for \'$sockName\'")
        val sockErr = pErrRouter.newRoute("sock_err_$sockName",
            passage = allowedKeyStringFilterPassage("source", "sock_uuid_$uuid")
        ).channel
        if(pErrRouter.getRoute("sock_err_$sockName") != null)
            log.info("stderr route correctly added")
        else {
            log.error("unable to add a route for stderr")
            throw PyBluezWrapperException("Unable to add new STDERR route for the new socket [$sockName]")
        }

        log.info("adding new route to the process stdout for \'$sockName\'")
        val sockOut = pOutRouter.newRoute("sock_out_$sockName") {
            /*if(it.has("sock_uuid"))
                if(it.get("sock_uuid").asString == uuid)
                    true

            false*/ true
        }.channel
        if(pOutRouter.getRoute("sock_out_$sockName") != null)
            log.info("stdout route correctly added")
        else {
            log.error("unable to add a route for stdout")
            throw PyBluezWrapperException("Unable to add new STDOUT route for the new socket [$sockName]")
        }

        return PyBluezSocket(uuid, protocol, this, PyBluezWrapperReader(sockIn, sockErr),
            PyBluezWrapperWriter(sockOut))
    }

    @Throws(PyBluezWrapperException::class)
    override fun close() {
        runBlocking {
            log.info("closing...")
            if(process.isAlive) {
                input.skipRemaining()
                output.writeTerminateCommand()
                ensureState(PyBluezWrapperState.TERMINATED)
                process.waitFor()
                log.info("process closed with code: ${process.exitValue()}")
            }
            pErrRouter.close()
            pInRouter.close()

            input.close()
            output.close()
            log.info("closed")
        }
    }
}