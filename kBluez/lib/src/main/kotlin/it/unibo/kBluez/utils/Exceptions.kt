package it.unibo.kBluez.utils

import mu.KLogger
import kotlin.reflect.KClass

data class KTryContinuation(val block : () -> Unit)

fun kTry(block: () -> Unit) : KTryContinuation {
    return KTryContinuation(block)
}

fun KTryContinuation.catch(vararg exceptions: KClass<out Throwable>,
                           catchBlock: (Throwable) -> Unit) : KTryContinuation{
    try {
        block.invoke()
    } catch (e : Exception) {
        if (e::class in exceptions)
            catchBlock(e) else throw e
    }

    return this
}

fun KTryContinuation.catch(logger : KLogger, vararg exceptions: KClass<out Throwable>,
                           catchBlock: (Throwable) -> Unit) : KTryContinuation {
    try {
        block.invoke()
    } catch (e : Exception) {
        logger.catching(e)
        if (e::class in exceptions)
            catchBlock(e) else throw e
    }

    return this
}