package it.mobilesystems.krobotconnector

import it.mobilesystems.krobotconnector.step.StepResult
import java.io.InputStream
import java.io.OutputStream

class BasicRobotCommunicator(
    inputStream : InputStream,
    outputStream : OutputStream
) : BasicRobotStepCommunicator {

    private val reader = inputStream.bufferedReader()
    private val writer = outputStream.bufferedWriter()
    private val name = javaClass.simpleName
    private val seqNum = 0L

    override fun doStep(time: Long): StepResult {
        TODO("Not yet implemented")
    }

    override fun moveForward() {
        TODO("Not yet implemented")
    }

    override fun moveBackword() {
        TODO("Not yet implemented")
    }

    override fun moveLeft() {
        TODO("Not yet implemented")
    }

    override fun moveRight() {
        TODO("Not yet implemented")
    }

    override fun terminate() {
        TODO("Not yet implemented")
    }


}