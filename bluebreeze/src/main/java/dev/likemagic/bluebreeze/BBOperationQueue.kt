//
// Copyright (c) Like Magic e.U. and contributors. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for details.
//

package dev.likemagic.bluebreeze

interface BBOperationQueue {
    suspend fun <T> operationEnqueue(operation: BBOperation<T>): T
}
