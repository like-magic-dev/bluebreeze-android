import java.util.Locale

class BBError(
    message: String = "Unspecified error"
) : Throwable(message = message) {
    companion object {
        fun scan(timeToWait: Float): BBError = BBError(
            message = "Scanned too often, please wait $timeToWait seconds before scanning"
        )

        fun operationCancelled(): BBError = BBError(
            message = "Operation cancelled"
        )

        fun gattDisconnected(): BBError = BBError(
            message = "Gatt disconnected"
        )

        fun gattError(code: Int? = null): BBError = BBError(
            message = "Gatt error: ${code?.toString() ?: "RUNTIME"}"
        )
    }
}