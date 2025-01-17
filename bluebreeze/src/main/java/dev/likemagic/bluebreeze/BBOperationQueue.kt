package dev.likemagic.bluebreeze

interface BBOperationQueue {
    suspend fun <T> operationEnqueue(operation: BBOperation<T>): T
}