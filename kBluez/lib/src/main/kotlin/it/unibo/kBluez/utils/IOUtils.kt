package it.unibo.kBluez.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
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