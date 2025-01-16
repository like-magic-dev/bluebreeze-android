import java.util.Locale

class BBError(
    message: String = "Unspecified error"
) : Throwable(message = message) {
    companion object {
        fun scan(timeToWait: Float): BBError = BBError(
            message = String.format(Locale.US, "Scanned too often, please wait %f seconds before scanning", timeToWait)
        )
    }
}