package dev.likemagic.bluebreeze

enum class BBState(val string: String) {
    unknown("unknown"),
    unauthorized("unauthorized"),
    poweredOff("poweredOff"),
    poweredOn("poweredOn"),
}