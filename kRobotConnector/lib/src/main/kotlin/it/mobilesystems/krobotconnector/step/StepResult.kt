package it.mobilesystems.krobotconnector.step

data class StepResult (
    val stepResultType: StepResultType,
    val dt : Long? = null,
    val reason : String? = null
) {

    fun isStepDone() : Boolean {
        return stepResultType == StepResultType.STEP_DONE
    }

    fun isStepFail() : Boolean {
        return stepResultType == StepResultType.STEP_FAIL
    }

}