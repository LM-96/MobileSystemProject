package it.unibo.kBluez.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.selects.select
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BufferedReaderChannelWrapper(
    private val reader : BufferedReader,
    scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    private val channel = Channel<String>()
    private val job = scope.launch(Dispatchers.IO) {
        var line : String?
        try {
            do{
                println("Reader | waiting...")
                line = reader.readLine()
                println("Reader | readed line: $line")
                if(line != null) {
                    channel.send(line)
                }
            } while (line != null)
        } catch (ioe : IOException) {
            channel.close(ioe)
        } catch (_: CancellationException) {
            //Exit cycle in case of channel cancellation
        } catch (_ : ClosedSendChannelException) {
            //Exit cycle in case of channel close
        }

        //Needed in case of reaching EOF in the stream
        if(!channel.isClosedForSend) {
            channel.close()
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

class OutputStreamStringChannelWrapper(
    private val writer : BufferedWriter,
    private val scope : CoroutineScope
) : Closeable, AutoCloseable {

    private val channel = Channel<String>()
    private val job = scope.launch(Dispatchers.IO) {
        var line : String
        try {
            while(true) {
                println("Writer | waiting...")
                line = channel.receive()
                println("Writer | writing: $line")
                writer.write(line)
                writer.newLine()
                writer.flush()
            }
        } catch (_ : ClosedReceiveChannelException) {
            //Exit cycle in case of channel closed
            writer.close()
        } catch (ioe : IOException) {
            channel.close(ioe)
        } catch (_ : CancellationException) {
            //Exit cycle in case of chanel cancellation
            writer.close()
        }

        if(!channel.isClosedForReceive) {
            channel.close()
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

data class ChannelRoute<T>(
    val name : String,
    val passage : (T) -> Boolean,
    val channel : Channel<T> = Channel()
)

data class ChannelRouterResponse(
    val responseCode : Int,
    val obj : Any? = null
)

data class ChannelRouterRequest<T>(
    val cmd : Int,
    val obj : Any? = null,
    val interactionChan : Channel<ChannelRouterResponse> = Channel()
)

class ChannelRouter<T>(
    private val sourceChan : Channel<T>,
    private val scope : CoroutineScope = GlobalScope
) : Closeable, AutoCloseable {

    companion object {
        const val ADD_ROUTE_REQ = 0
        const val REMOVE_ROUTE_REQ = 1
        const val GET_ROUTE_REQ = 2
        const val TERMINATE_REQ = 3

        const val OK_RES = 0
        const val ERR_RES = 1
    }

    private val requestChan = Channel<ChannelRouterRequest<T>>()
    private val responseChan = Channel<ChannelRouterResponse>()
    private val routes = mutableMapOf<String, ChannelRoute<T>>()
    private val job = scope.launch {
        var terminated = false
        while(isActive && !terminated) {
            select<Unit> {

                requestChan.onReceive {
                    when(it.cmd) {
                        ADD_ROUTE_REQ -> {
                            when(it.obj) {
                                is ChannelRoute<*> -> {

                                }
                                is List<*> {

                                }
                            }
                        }
                        REMOVE_ROUTE_REQ -> {
                            routes.remove(it.obj!!.name)
                            it.interactionChan.send(ChannelRouterResponse(OK_RES))
                        }
                        GET_ROUTE_REQ -> {
                            responseChan.send(ChannelRouterResponse(OK_RES, routes[it.obj!!.name]))
                        }
                        TERMINATE_REQ -> {
                            terminated = true
                            responseChan.send((ChannelRouterResponse(OK_RES))
                        }
                    }
                }

                sourceChan.onReceive {
                    routes.values.forEach { route ->
                        if(route.passage(it))
                            route.channel.send(it)
                    }
                }
            }
        }
    }

    suspend fun newRoute(name : String,
                         passage : (T) -> Boolean,
                         routeChannel : Channel<T> = Channel()) : ChannelRoute<T> {
        val route = ChannelRoute(name, passage, routeChannel)
        val req = ChannelRouterRequest(ADD_ROUTE_REQ, route)
        requestChan.send()

    }

    fun getRoute(name : String) : ReceiveChannel<T>? {
        return routes[name]?.channel
    }

    operator fun get(name : String) : ReceiveChannel<T>? {
        return routes[name]?.channel
    }

    private suspend fun performRequest(req : ChannelRouterRequest<T>) : ChannelRouterResponse {
        requestChan.send(req)
        return req.interactionChan.receive()
    }

    override fun close() {
        runBlocking {
            requestChan.send(ChannelRouterCmd(TERMINATE_REQ))
            var code : Int
            do {
                code = responseChan.receive().cmd
            } while (code != TERMINATE_REQ)
        }
    }

}