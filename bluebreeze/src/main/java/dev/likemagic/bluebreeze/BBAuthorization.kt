package dev.likemagic.bluebreeze

enum class BBAuthorization(val string: String) {
    unknown("unknown"),
    showRationale("showRationale"),
    denied("denied"),
    authorized("authorized"),
}