package it.unibo.kBluez.io

import it.unibo.kBluez.utils.stringReceiveChannel
import it.unibo.kBluez.utils.stringSendChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/* FAN OUT ROUTERS ******************************************************************************** */

fun <T> ReceiveChannel<T>.newFanOutSimpleRouter(
    scope : CoroutineScope = GlobalScope,
    name : String = "") : CloseableChannelRouter<T> {
    return FanOutChannelRouter(this, scope, name) { Optional.of(it) }
}

fun <T, O> ReceiveChannel<T>.newFanOutMappedRouter(scope : CoroutineScope = GlobalScope,
                                                   name : String = "",
                                                   mapper : (T) -> Optional<O>
) : CloseableChannelRouter<O> {
    return FanOutChannelRouter(this, scope, name, mapper)
}

fun BufferedReader.newFanOutSimpleStringRouter(scope : CoroutineScope = GlobalScope, name : String = "") : CloseableChannelRouter<String> {
    return this.stringReceiveChannel(scope).newFanOutSimpleRouter(scope, name)
}

fun <O> BufferedReader.newFanOutMappedStringRouter(scope : CoroutineScope = GlobalScope,
                                                   name : String = "",
                                                   mapper : (String) -> Optional<O>
) : CloseableChannelRouter<O> {
    return FanOutChannelRouter(this.stringReceiveChannel(scope), scope, name, mapper)
}

fun InputStream.newFanOutSimpleStringRouter(scope : CoroutineScope = GlobalScope, name : String = "") : CloseableChannelRouter<String> {
    return this.bufferedReader().newFanOutSimpleStringRouter(scope, name)
}

fun <O> InputStream.newFanOutMappedStringRouter(scope : CoroutineScope = GlobalScope,
                                                name : String = "",
                                                mapper : (String) -> Optional<O>
) : CloseableChannelRouter<O> {
    return FanOutChannelRouter(this.stringReceiveChannel(scope), scope, name, mapper)
}

/* FAN IN ROUTERS ********************************************************************************* */
fun <I> SendChannel<I>.newFanInSimpleRouter(scope : CoroutineScope = GlobalScope, name : String = "") : CloseableChannelRouter<I> {
    return FanInChannelRouter(this, scope, name) { Optional.of(it) }
}

fun <I, T> SendChannel<T>.newFanInMappedRouter(scope : CoroutineScope = GlobalScope,
                                                   name : String = "",
                                                   mapper : (I) -> Optional<T>
) : CloseableChannelRouter<I> {
    return FanInChannelRouter(this, scope, name, mapper)
}

fun BufferedWriter.newFanInSimpleStringRouter(scope : CoroutineScope = GlobalScope, name : String = "") : CloseableChannelRouter<String> {
    return this.stringSendChannel(scope).newFanInSimpleRouter()
}

fun <I> BufferedWriter.newFanInMappedStringRouter(scope : CoroutineScope = GlobalScope,
                                                   name : String = "",
                                                   mapper : (I) -> Optional<String>
) : CloseableChannelRouter<I> {
    return FanInChannelRouter(this.stringSendChannel(scope), scope, name, mapper)
}

fun OutputStream.newFanInSimpleStringRouter(scope : CoroutineScope = GlobalScope, name : String = "") : CloseableChannelRouter<String> {
    return this.bufferedWriter().newFanInSimpleStringRouter(scope, name)
}

fun <I> OutputStream.newFanInMappedStringRouter(scope : CoroutineScope = GlobalScope,
                                                name : String = "",
                                                mapper : (I) -> Optional<String>
) : CloseableChannelRouter<I> {
    return FanInChannelRouter(this.stringSendChannel(scope), scope, name, mapper)
}