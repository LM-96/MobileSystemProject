package it.unibo.kBluez.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.selects.select
import java.io.InputStream
import java.io.OutputStream

class ProcessException(str : String) : Exception(str)

data class KotlinIO(
    val stdOut : SendChannel<String>,
    val stdErr : ReceiveChannel<String>,
    val stdIn : ReceiveChannel<String>
) {

    @Throws(ProcessException::class)
    suspend fun readString() : String? {
        val line : String
        return try {
            select {
                stdIn.onReceive {
                    it
                }
                stdErr.onReceive {
                    throw ProcessException(it.addLevelTab(2))
                }
            }
        }
        catch (_ : CancellationException) { null }
        catch (_ : ClosedReceiveChannelException) { null }
    }

    suspend fun readUntilExit() : String? {
        val inLines = mutableListOf<String>()
        val errLines = mutableListOf<String>()
        var terminated = false
        while(!terminated) {
            try {
                select {
                    stdIn.onReceive {
                        inLines.add(it)
                    }
                    stdErr.onReceive {
                        errLines.add(it)
                    }
                }
            }
            catch (_ : CancellationException) { terminated = true }
            catch (_ : ClosedReceiveChannelException) { terminated = true }
        }

        if(errLines.isNotEmpty())
            throw ProcessException(errLines.joinToString("\n").addLevelTab(2))
        if(inLines.isEmpty())
            return null

        return inLines.joinToString("\n")
    }

}

data class JavaIO(
    val stdOut : OutputStream,
    val stdErr : InputStream,
    val stdIn : InputStream
) {
    fun toKotlinIO(scope : CoroutineScope = GlobalScope) : KotlinIO {
        return KotlinIO(
            stdOut.stringSendChannel(scope),
            stdErr.stringReceiveChannel(scope),
            stdIn.stringReceiveChannel(scope)
        )
    }
}

fun Process.stdInChannel(scope : CoroutineScope = GlobalScope) : ReceiveChannel<String> {
    return inputStream.stringReceiveChannel(scope)
}

fun Process.stdOutChannel(scope : CoroutineScope = GlobalScope) : SendChannel<String> {
    return outputStream.stringSendChannel(scope)
}

fun Process.stdErrChannel(scope : CoroutineScope = GlobalScope) : ReceiveChannel<String> {
    return errorStream.stringReceiveChannel(scope)
}

fun Process.getIO() : JavaIO {
    return JavaIO(outputStream, errorStream, inputStream)
}

fun Process.getKotlinIO(scope : CoroutineScope = GlobalScope) : KotlinIO {
    return getIO().toKotlinIO(scope)
}

@Throws(ProcessException::class)
suspend fun Process.readUntilExited(scope : CoroutineScope) : String? {
    return getKotlinIO(scope).readUntilExit()
}