//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

import BBUUID

data class BBService(
    val uuid: BBUUID,
    val characteristics: List<BBCharacteristic>,
) {
    val name: String?
        get() = BBConstants.Service.knownUUIDs[uuid]
}
