package it.unibo.kBluez.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Map

val IO_LOG = KotlinLogging.logger("IOUtils.kt")

class BufferedReaderChannelWrapper(
    private val reader : BufferedReader,
    scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    private val channel = Channel<String>()
    private val job = scope.launch(Dispatchers.IO) {
        var line : String? = null
        try {
            do{
                line = reader.readLine()
                IO_LOG.info("readed line: $line")
                if(line != null) {
                    channel.send(line)
                }
            } while (line != null)
        } catch (ioe : IOException) {
            IO_LOG.info("reader is closed")
            IO_LOG.catching(ioe)
        } catch (ce: CancellationException) {
            IO_LOG.info("channel job is cancelled: unable to send data [$line]")
            IO_LOG.catching(ce)
            reader.close()
            IO_LOG.info("reader closed")
        } catch (csce : ClosedSendChannelException) {
            IO_LOG.info("channel is closed: unable to send data [$line]")
            IO_LOG.catching(csce)
            reader.close()
            IO_LOG.info("reader closed")
        }

        //Needed in case of reaching EOF in the stream
        if(!channel.isClosedForSend) {
            channel.close()
            IO_LOG.info("channel closed")
        }
    }

    fun getReceiveChannel() : ReceiveChannel<String> {
        return channel
    }

    override fun close() {
        if(!channel.isClosedForSend)
            channel.close()
        runBlocking { job.join() }
    }
}

class BufferedReaderFlowWrapper(
    private val reader : BufferedReader,
    scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    private val internalFlow = MutableSharedFlow<String>()
    val flow = internalFlow.asSharedFlow()
    private val job = scope.launch(Dispatchers.IO) {
        var line : String?
        try {
            do{
                line = reader.readLine()
                IO_LOG.info("flowed line: $line")
                if(line != null) {
                    internalFlow.emit(line)
                }
            } while (line != null)
        } catch (ioe : IOException) {
            IO_LOG.info("reader is closed")
            IO_LOG.catching(ioe)
        }
    }

    override fun close() {
        reader.close()
    }
}

class BufferedReaderFlowWrapperMapper<O>(
    private val reader : BufferedReader,
    private val mapper : (String) -> O,
    scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    private val internalFlow = MutableSharedFlow<O>()
    val flow = internalFlow.asSharedFlow()
    private val job = scope.launch(Dispatchers.IO) {
        var line : String?
        try {
            do{
                line = reader.readLine()
                IO_LOG.info("flowed line: $line")
                if(line != null) {
                    internalFlow.emit(mapper(line))
                }
            } while (line != null)
        } catch (ioe : IOException) {
            IO_LOG.info("reader is closed")
            IO_LOG.catching(ioe)
        }
    }

    override fun close() {
        reader.close()
        IO_LOG.info("channel closed")
    }
}

class OutputStreamStringChannelWrapper(
    private val writer : BufferedWriter,
    private val scope : CoroutineScope
) : Closeable, AutoCloseable {

    private val channel = Channel<String>()
    private val job = scope.launch(Dispatchers.IO) {
        var line : String = ""
        try {
            while(true) {
                line = channel.receive()
                writer.write(line)
                writer.newLine()
                writer.flush()
                IO_LOG.info("written line: $line")
            }
        } catch (crce : ClosedReceiveChannelException) {
            IO_LOG.info("receive channel is closed")
            IO_LOG.catching(crce)
            writer.close()
            IO_LOG.info("writer closed")
        } catch (ioe : IOException) {
            IO_LOG.info("writer is closed")
            IO_LOG.catching(ioe)
        } catch (ce : CancellationException) {
            IO_LOG.info("channel job is cancelled: unable to write data [$line]")
            IO_LOG.catching(ce)
            writer.close()
            IO_LOG.info("writer closed")
        }

        if(!channel.isClosedForReceive) {
            channel.close()
            IO_LOG.info("channel closed")
        }
    }

    fun getSendChannel() : SendChannel<String> {
        return channel
    }

    override fun close() {
        if(!channel.isClosedForReceive)
            channel.close()
        runBlocking { job.join() }
    }
}

fun InputStream.stringReceiveChannel(scope : CoroutineScope = GlobalScope) : ReceiveChannel<String> {
    return this.bufferedReader().stringReceiveChannel(scope)
}

fun BufferedReader.stringSharedFlow(scope : CoroutineScope = GlobalScope) : SharedFlow<String> {
    return BufferedReaderFlowWrapper(this, scope).flow
}

fun <O> BufferedReader.stringMappedSharedFlow(scope : CoroutineScope = GlobalScope,
                                              mapper : (String) -> O
) :
        SharedFlow<O> {
    return BufferedReaderFlowWrapperMapper(this, mapper, scope).flow
}

fun InputStream.stringSharedFlow(scope : CoroutineScope = GlobalScope) : SharedFlow<String> {
    return this.bufferedReader().stringSharedFlow(scope)
}

fun <O> InputStream.stringMappedSharedFlow(scope : CoroutineScope = GlobalScope,
                                           mapper : (String) -> O
) :
        SharedFlow<O> {
    return this.bufferedReader().stringMappedSharedFlow(scope, mapper)
}

fun BufferedReader.stringReceiveChannel(scope : CoroutineScope = GlobalScope) : ReceiveChannel<String> {
    return BufferedReaderChannelWrapper(this, scope).getReceiveChannel()
}

fun OutputStream.stringSendChannel(scope : CoroutineScope = GlobalScope) : SendChannel<String> {
    return this.bufferedWriter().stringSendChannel(scope)
}

fun BufferedWriter.stringSendChannel(scope : CoroutineScope = GlobalScope) : SendChannel<String> {
    return OutputStreamStringChannelWrapper(this, scope).getSendChannel()
}

suspend fun ReceiveChannel<String>.availableText() : String? {
    if(isEmpty)
        return null

    var text = ""
    while(!isEmpty)
        text += (receive() + "\n")
    return text.trim()
}

suspend fun ReceiveChannel<String>.receiveTextUntilClosed() : String? {
    var text = ""
    try {
        while(true)
            text += (receive() + "\n")
    } catch (_ : ClosedReceiveChannelException) {
        return if(text.isBlank())
            null
        else text.trim()
    }

}

class FlowChannelWriter<T>(
    flow : Flow<T>,
    scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    private val channel = Channel<T>()
    val receiveChannel : ReceiveChannel<T> = channel
    private val job = scope.launch {
        try {
            flow.collect {
                channel.send(it)
            }
        }
        catch (_ : ClosedSendChannelException) {}
        catch (_ : CancellationException){}
    }

    override fun close() {
        try {
            channel.close()
            runBlocking { job.join() }
        } catch (_ : CancellationException) {}
    }

}

fun <T> Flow<T>.newReceiveChannel() : ReceiveChannel<T> {
    return FlowChannelWriter(this).receiveChannel
}

class ChannelToStateFlowWrapper<I, O>(
    private val channel : ReceiveChannel<I>,
    initialValue : O,
    scope : CoroutineScope = GlobalScope,
    private val mapper : (I) -> O,
) : Closeable, AutoCloseable {

    private val terminationChan = Channel<Unit>()
    private val ackChan = Channel<Exception?>()
    private val internalStateFlow = MutableStateFlow(initialValue)
    val stateFlow = internalStateFlow.asStateFlow()

    private val job = scope.launch {
        var terminated = false
        while(isActive && !terminated) {
            select<Unit> {
                try {
                    channel.onReceive {
                        println("ChannelStateFlowWrapper | emitting $it on flow")
                        internalStateFlow.emit(mapper(it))
                    }
                } catch (_ : ClosedReceiveChannelException) {
                    terminated = true
                } catch (_ : CancellationException) {
                    terminated = true
                } catch (e : Exception) {
                    e.printStackTrace()
                    //Cathces the exception of the mapper
                }
                terminationChan.onReceive {
                    terminated = true
                    ackChan.send(null)
                }
            }
        }
    }

    override fun close() {
        runBlocking {
            terminationChan.send(Unit)
            ackChan.receive()
            job.join()
        }
    }

}

fun <T, O> ReceiveChannel<T>.asStateFlow(initialValue : O, scope : CoroutineScope = GlobalScope,
                                         mapper : (T) -> O) : StateFlow<O>
{
    return ChannelToStateFlowWrapper(this, initialValue, scope, mapper).stateFlow
}

class FlowFilter<T>(
    private val flow : SharedFlow<T>,
    private val filter : (T) -> T
    ) {

}