
abstract class BBError : Throwable() {
    override val message: String
        get() = "Unspecified error"
}