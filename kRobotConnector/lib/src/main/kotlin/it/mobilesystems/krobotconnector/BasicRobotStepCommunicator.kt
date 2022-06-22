package it.mobilesystems.krobotconnector

import it.mobilesystems.krobotconnector.step.StepResult

interface BasicRobotStepCommunicator : BasicRobotBaseCommunicator {

    fun doStep(time : Long) : StepResult

}