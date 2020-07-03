package app.blinkers.data

data class BlinkersStatus(val isConnected: Boolean,
                          val isLedOn: Boolean,
                          val latestEmotionalSnapshot: EmotionalSnapshot?,
                          val latestEEGSnapshot: EEGSnapshot?,
                          val errorMessage: String? = null) {

    override fun toString(): String {
        val errorMess = errorMessage ?: ""
        return "Connected: $isConnected - Led is ON: $isLedOn \n $errorMess\n $latestEmotionalSnapshot \n $latestEEGSnapshot"
    }
}