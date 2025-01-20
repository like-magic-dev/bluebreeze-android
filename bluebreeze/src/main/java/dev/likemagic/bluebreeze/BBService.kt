package dev.likemagic.bluebreeze

import BBUUID

data class BBService(
    val uuid: BBUUID,
    val characteristics: List<BBCharacteristic>,
) {
    val name: String?
        get() = BBConstants.Service.knownUUIDs[uuid]
}
