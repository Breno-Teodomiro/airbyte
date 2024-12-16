/*
 * Copyright (c) 2024 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.cdk.load.write.object_storage

import io.airbyte.cdk.load.command.DestinationStream
import io.airbyte.cdk.load.file.object_storage.ObjectStorageClient
import io.airbyte.cdk.load.file.object_storage.PartMetadataAssembler
import io.airbyte.cdk.load.file.object_storage.RemoteObject
import io.airbyte.cdk.load.file.object_storage.StreamingUpload
import io.airbyte.cdk.load.message.Batch
import io.airbyte.cdk.load.message.object_storage.IncompletePartialUpload
import io.airbyte.cdk.load.message.object_storage.LoadablePart
import io.airbyte.cdk.load.message.object_storage.LoadedObject
import io.airbyte.cdk.load.state.object_storage.ObjectStorageDestinationState
import io.airbyte.cdk.load.util.setOnce
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred

class PartToObjectAccumulator<T : RemoteObject<*>>(
    private val stream: DestinationStream,
    private val client: ObjectStorageClient<T>,
) {
    private val log = KotlinLogging.logger {}

    data class UploadInProgress<T : RemoteObject<*>>(
        val streamingUpload: CompletableDeferred<StreamingUpload<T>> = CompletableDeferred(),
        val partMetadataAssembler: PartMetadataAssembler = PartMetadataAssembler(),
        val hasStarted: AtomicBoolean = AtomicBoolean(false),
    )
    private val uploadsInProgress = ConcurrentHashMap<String, UploadInProgress<T>>()

    suspend fun processBatch(batch: Batch): Batch {
        batch as LoadablePart
        val upload = uploadsInProgress.getOrPut(batch.part.key) { UploadInProgress() }
        if (upload.hasStarted.setOnce()) {
            // Start the upload if we haven't already. Note that the `complete`
            // here refers to the completable deferred, not the streaming upload.
            val metadata = ObjectStorageDestinationState.metadataFor(stream)
            val streamingUpload = client.startStreamingUpload(batch.part.key, metadata)
            upload.streamingUpload.complete(streamingUpload)
        }
        val streamingUpload = upload.streamingUpload.await()

        upload.partMetadataAssembler.add(batch.part)

        log.info {
            "Processing loadable part ${batch.part.partIndex} of ${batch.part.key} (empty=${batch.part.isEmpty} of size ${batch.part.bytes?.size})"
        }

        // Upload provided bytes and update indexes.
        if (batch.part.bytes != null) {
            streamingUpload.uploadPart(batch.part.bytes, batch.part.partIndex)
        }
        if (upload.partMetadataAssembler.isComplete) {
            val obj = streamingUpload.complete()
            uploadsInProgress.remove(batch.part.key)

            log.info { "Completed upload of ${obj.key}" }
            return LoadedObject(remoteObject = obj, fileNumber = batch.part.fileNumber)
        } else {
            return IncompletePartialUpload(batch.part.key)
        }
    }
}
