package it.unibo.kBluez.io

import kotlinx.coroutines.channels.Channel

data class ChannelRoute<T> (
    val name : String,
    val passage : (T) -> Boolean,
    val channel : Channel<T>
)