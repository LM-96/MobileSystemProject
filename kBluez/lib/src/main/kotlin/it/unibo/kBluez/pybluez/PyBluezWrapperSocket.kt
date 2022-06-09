package it.unibo.kBluez.pybluez

import kotlinx.coroutines.flow.SharedFlow

class PyBluezWrapperSocket(
    val pInFlow : SharedFlow<String>,
    val pOutFlow : SharedFlow<String>
) {
}