package it.unibo.kBluez.io

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.io.Closeable

suspend fun <I, O> ReceiveChannel<I>.receiveAndMap(mapper : (I) -> O) : O {
    return mapper(receive())
}

class ReceiveChannelMapper<I, O>(
    private val inChan : ReceiveChannel<I>,
    scope : CoroutineScope,
    private val mapper : (I) -> (O)
) : Closeable, AutoCloseable{

    private val job = scope.launch {
        try {
            while (isActive) {
                mappedReceiveChan.send(mapper(inChan.receive()))
            }
        }
        catch (csce : ClosedSendChannelException){ csce.printStackTrace() }
        catch (crce : ClosedReceiveChannelException){ crce.printStackTrace() }
        catch (ce : CancellationException){ ce.printStackTrace() }
    }

    private val mappedReceiveChan = Channel<O>()

    fun getMappedReceiveChannel() : ReceiveChannel<O> {
        return mappedReceiveChan
    }

    override fun close() {
        try {
            mappedReceiveChan.close()
            runBlocking {
                job.join()
            }
        }
        catch (csce : ClosedSendChannelException){ csce.printStackTrace() }
        catch (crce : ClosedReceiveChannelException){ crce.printStackTrace() }
        catch (ce : CancellationException){ ce.printStackTrace() }
    }
}

class SendChannelMapper<I, O>(
    val inChan : SendChannel<O>,
    scope: CoroutineScope = GlobalScope,
    private val mapper : (I) -> O
) : Closeable, AutoCloseable {

    private val job = scope.launch {
        try {
            while (isActive) {
                inChan.send(mapper(mappedSendChannel.receive()))
            }
        }
        catch (_ : ClosedSendChannelException) {}
        catch (_ : ClosedReceiveChannelException) {}
        catch (_ : CancellationException) {}
    }

    private val mappedSendChannel = Channel<I>()

    fun getMappedSendChannel() : SendChannel<I> {
        return mappedSendChannel
    }

    override fun close() {
        try {
            mappedSendChannel.close()
            runBlocking {
                job.join()
            }
        }
        catch (_ : ClosedSendChannelException){}
        catch (_ : ClosedSendChannelException){}
        catch (_ : CancellationException){}
    }
}

fun <I, O> ReceiveChannel<I>.mapped(scope : CoroutineScope,
                                                     mapper : (I) -> O) :
        ReceiveChannel<O> {
    return ReceiveChannelMapper(this, scope, mapper).getMappedReceiveChannel()
}

fun  <I, O> SendChannel<O>.mapped(scope : CoroutineScope,
                                                mapper : (I) -> O) :
        SendChannel<I> {
    return SendChannelMapper(this, scope, mapper).getMappedSendChannel()
}