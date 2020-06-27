package app.blinkers.data

data class BlinkersStatus(val isConnected: Boolean,
                          val isRecording: Boolean,
                          val isLedOn: Boolean,
                          val latestEmotionalSnapshot: EmotionalSnapshot?,
                          val latestEEGSnapshot: EEGSnapshot?,
                          val errorMessage: String? = null) {

    override fun toString(): String {
        val errorMess = errorMessage ?: ""
        return "Connected: $isConnected - Recording: $isRecording - Led is ON: $isLedOn \n $errorMess\n $latestEmotionalSnapshot \n $latestEEGSnapshot"
    }
}