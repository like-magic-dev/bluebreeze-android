package dev.likemagic.bluebreeze

class BBConstants {
    companion object {
        const val DEFAULT_MTU: Int = 23
    }

    class UUID {
        companion object {
            val cccd = BBUUID.fromString("2902").uuid
        }
    }
}