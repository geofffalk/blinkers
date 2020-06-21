package app.blinkers.data

data class LedStatus(val led: Led, val isOn: Boolean)

enum class Led {
    RED, BLUE, GREEN
}