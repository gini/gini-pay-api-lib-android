package net.gini.android

import android.net.Uri
import bolts.Task
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.gini.android.models.CompoundExtraction
import net.gini.android.models.Document
import net.gini.android.models.ExtractionsContainer
import net.gini.android.models.Payment
import net.gini.android.models.PaymentProvider
import net.gini.android.models.PaymentRequest
import net.gini.android.models.PaymentRequestInput
import net.gini.android.models.ResolvePaymentInput
import net.gini.android.models.SpecificExtraction
import org.json.JSONObject

/**
 * The [DocumentManager] is a high level API on top of the Gini API, which is used via the ApiCommunicator. It
 * provides high level methods to handle document related tasks easily.
 */
class DocumentManager(private val documentTaskManager: DocumentTaskManager) {

    private val taskDispatcher = Task.BACKGROUND_EXECUTOR.asCoroutineDispatcher()

    suspend fun createPartialDocument(
        byteArray: ByteArray,
        contentType: String,
        filename: String? = null,
        documentType: DocumentTaskManager.DocumentType,
        documentMetadata: DocumentMetadata? = null,
    ): Document = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = if (documentMetadata != null) {
                documentTaskManager.createPartialDocument(byteArray, contentType, filename, documentType, documentMetadata)
            } else {
                documentTaskManager.createPartialDocument(byteArray, contentType, filename, documentType)
            }
            continuation.resumeTask(task)
        }
    }

    suspend fun deletePartialDocumentAndParents(
        documentPageId: String,
    ) = withContext(taskDispatcher) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val task = documentTaskManager.deletePartialDocumentAndParents(documentPageId)
            continuation.resumeUnitTask(task)
        }
    }

    suspend fun deleteDocument(
        documentId: String,
    ) = withContext(taskDispatcher) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val task = documentTaskManager.deleteDocument(documentId)
            continuation.resumeUnitTask(task)
        }
    }

    suspend fun createCompositeDocument(
        pages: List<Document>,
        documentType: DocumentTaskManager.DocumentType,
    ) = withContext(taskDispatcher) {
        suspendCancellableCoroutine<Document> { continuation ->
            val task = documentTaskManager.createCompositeDocument(pages, documentType)
            continuation.resumeTask(task)
        }
    }

    suspend fun createCompositeDocument(
        pages: LinkedHashMap<Document, Int>,
        documentType: DocumentTaskManager.DocumentType,
    ) = withContext(taskDispatcher) {
        suspendCancellableCoroutine<Document> { continuation ->
            val task = documentTaskManager.createCompositeDocument(pages, documentType)
            continuation.resumeTask(task)
        }
    }

    suspend fun getDocument(
        id: String,
    ): Document = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.getDocument(id)
            continuation.resumeTask(task)
        }
    }

    suspend fun getDocument(
        uri: Uri,
    ): Document = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.getDocument(uri)
            continuation.resumeTask(task)
        }
    }

    suspend fun pollDocument(
        document: Document,
    ): Document = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.pollDocument(document)
            continuation.resumeTask(task)
        }
    }

    suspend fun sendFeedback(
        document: Document,
        specificExtractions: Map<String, SpecificExtraction>,
        compoundExtractions: Map<String, CompoundExtraction>,
    ): Document = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.sendFeedbackForExtractions(document, specificExtractions, compoundExtractions)
            continuation.resumeTask(task)
        }
    }

    suspend fun reportDocument(
        document: Document,
        summary: String,
        description: String,
    ): String = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.reportDocument(document, summary, description)
            continuation.resumeTask(task)
        }
    }

    suspend fun getLayout(
        document: Document,
    ): JSONObject = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.getLayout(document)
            continuation.resumeTask(task)
        }
    }

    suspend fun getExtractions(
        document: Document,
    ) = withContext(taskDispatcher) {
        suspendCancellableCoroutine<ExtractionsContainer> { continuation ->
            val pollDocumentTask = documentTaskManager.pollDocument(document)
            pollDocumentTask.waitForCompletion()

            if (!continuation.isActive) return@suspendCancellableCoroutine

            if (!pollDocumentTask.isFaulted) {
                val extractionTask = documentTaskManager.getAllExtractions(pollDocumentTask.result)
                continuation.resumeTask(extractionTask)
            } else {
                continuation.resumeWithException(pollDocumentTask.error)
            }

            continuation.invokeOnCancellation {
                if (!pollDocumentTask.isCompleted) {
                    documentTaskManager.cancelDocumentPolling(document)
                }
            }
        }
    }

    suspend fun getPaymentProviders(): List<PaymentProvider> =
        withContext(taskDispatcher) {
            suspendCancellableCoroutine { continuation ->
                val task = documentTaskManager.paymentProviders
                continuation.resumeTask(task)
            }
        }

    suspend fun getPaymentProvider(
        id: String,
    ): PaymentProvider = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.getPaymentProvider(id)
            continuation.resumeTask(task)
        }
    }

    suspend fun createPaymentRequest(
        paymentRequestInput: PaymentRequestInput,
    ): String = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.createPaymentRequest(paymentRequestInput)
            continuation.resumeTask(task)
        }
    }

    suspend fun getPaymentRequest(
        id: String,
    ): PaymentRequest = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.getPaymentRequest(id)
            continuation.resumeTask(task)
        }
    }

    suspend fun getPaymentRequests(): List<PaymentRequest> = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.paymentRequests
            continuation.resumeTask(task)
        }
    }

    suspend fun resolvePaymentRequest(
        requestId: String,
        resolvePaymentInput: ResolvePaymentInput,
    ): String = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.resolvePaymentRequest(requestId, resolvePaymentInput)
            continuation.resumeTask(task)
        }
    }

    suspend fun getPayment(
        id: String,
    ): Payment = withContext(taskDispatcher) {
        suspendCancellableCoroutine { continuation ->
            val task = documentTaskManager.getPayment(id)
            continuation.resumeTask(task)
        }
    }

    private fun <T> Continuation<T>.resumeTask(task: Task<T>) {
        task.waitForCompletion()
        if (!task.isFaulted) {
            this.resume(task.result)
        } else {
            this.resumeWithException(task.error)
        }
    }

    private fun <T> Continuation<Unit>.resumeUnitTask(task: Task<T>) {
        task.waitForCompletion()
        if (!task.isFaulted) {
            this.resume(Unit)
        } else {
            this.resumeWithException(task.error)
        }
    }

}
