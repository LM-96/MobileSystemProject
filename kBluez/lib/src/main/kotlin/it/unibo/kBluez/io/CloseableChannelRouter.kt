package it.unibo.kBluez.io

import java.io.Closeable

interface CloseableChannelRouter<T> : ChannelRoute<T>, Closeable, AutoCloseable {
}