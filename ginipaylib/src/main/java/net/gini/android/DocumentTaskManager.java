package net.gini.android;

import static android.graphics.Bitmap.CompressFormat.JPEG;

import static net.gini.android.Utils.CHARSET_UTF8;
import static net.gini.android.Utils.checkNotNull;

import android.graphics.Bitmap;
import android.net.Uri;

import net.gini.android.authorization.Session;
import net.gini.android.authorization.SessionManager;
import net.gini.android.models.Box;
import net.gini.android.models.CompoundExtraction;
import net.gini.android.models.Document;
import net.gini.android.models.Extraction;
import net.gini.android.models.ExtractionsContainer;
import net.gini.android.models.ReturnReason;
import net.gini.android.models.SpecificExtraction;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import bolts.Continuation;
import bolts.Task;

/**
 * The DocumentTaskManager is a high level API on top of the Gini API, which is used via the ApiCommunicator. It
 * provides high level methods to handle document related tasks easily.
 */
public class DocumentTaskManager {

    private final GiniApiType mGiniApiType;
    private Map<Document, Boolean> mDocumentPollingsInProgress = new ConcurrentHashMap<>();

    /**
     * The available document type hints. See the documentation for more information.
     */
    public enum DocumentType {
        BANK_STATEMENT("BankStatement"),
        CONTRACT("Contract"),
        INVOICE("Invoice"),
        RECEIPT("Receipt"),
        REMINDER("Reminder"),
        REMITTANCE_SLIP("RemittanceSlip"),
        TRAVEL_EXPENSE_REPORT("TravelExpenseReport"),
        OTHER("Other");

        private final String apiDoctypeHint;

        DocumentType(String apiDoctypeHint) {
            this.apiDoctypeHint = apiDoctypeHint;
        }

        public String getApiDoctypeHint() {
            return apiDoctypeHint;
        }
    }

    /**
     * The time in milliseconds between HTTP requests when a document is polled.
     */
    public static long POLLING_INTERVAL = 1000;

    /**
     * The default compression rate which is used for JPEG compression in per cent.
     */
    public final static int DEFAULT_COMPRESSION = 50;

    /**
     * The ApiCommunicator instance which is used to communicate with the Gini API.
     */
    final ApiCommunicator mApiCommunicator;  // Visible for testing
    /**
     * The SessionManager instance which is used to create the documents.
     */
    private final SessionManager mSessionManager;

    public DocumentTaskManager(final ApiCommunicator apiCommunicator, final SessionManager sessionManager,
            final GiniApiType giniApiType) {
        mApiCommunicator = checkNotNull(apiCommunicator);
        mSessionManager = checkNotNull(sessionManager);
        mGiniApiType = checkNotNull(giniApiType);
    }

    /**
     * A Continuation that uses the JSON response from the Gini API and returns a new Document instance from the JSON.
     */
    private static final Continuation<JSONObject, Document> DOCUMENT_FROM_RESPONSE =
            new Continuation<JSONObject, Document>() {
                @Override
                public Document then(Task<JSONObject> task) throws Exception {
                    return Document.fromApiResponse(task.getResult());
                }
            };

    /**
     * Deletes a Gini partial document and all its parent composite documents.
     * <br>
     * Partial documents can be deleted only, if they don't belong to any composite documents and
     * this method deletes the parents before deleting the partial document.
     *
     * @param documentId The id of an existing partial document
     *
     * @return A Task which will resolve to an empty string.
     */
    public Task<String> deletePartialDocumentAndParents(@NonNull final String documentId) {
        return getDocument(documentId).onSuccessTask(new Continuation<Document, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Document> documentTask) throws Exception {
                final Document document = documentTask.getResult();
                return deleteDocuments(document.getCompositeDocuments());
            }
        }, Task.BACKGROUND_EXECUTOR).onSuccessTask(new Continuation<Void, Task<Session>>() {
            @Override
            public Task<Session> then(final Task<Void> task) throws Exception {
                return mSessionManager.getSession();
            }
        }, Task.BACKGROUND_EXECUTOR).onSuccessTask(new Continuation<Session, Task<String>>() {
            @Override
            public Task<String> then(final Task<Session> task) throws Exception {
                final Session session = task.getResult();
                return mApiCommunicator.deleteDocument(documentId, session);
            }
        });
    }

    /**
     * Deletes a Gini document.
     *
     * For deleting partial documents use {@link #deletePartialDocumentAndParents(String)} instead.
     *
     * @param documentId The id of an existing document
     *
     * @return A Task which will resolve to an empty string.
     */
    public Task<String> deleteDocument(@NonNull final String documentId) {
        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<String>>() {
            @Override
            public Task<String> then(final Task<Session> task) throws Exception {
                final Session session = task.getResult();
                return mApiCommunicator.deleteDocument(documentId, session);
            }
        });
    }

    private Task<Void> deleteDocuments(@NonNull final List<Uri> documentUris) {
        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<Void>>() {
            @Override
            public Task<Void> then(Task<Session> sessionTask) throws Exception {
                final Session session = sessionTask.getResult();
                final List<Task<String>> deleteTasks = new ArrayList<>();
                for (final Uri documentUri : documentUris) {
                    deleteTasks.add(mApiCommunicator.deleteDocument(documentUri, session));
                }
                return Task.whenAll(deleteTasks);
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Uploads raw data and creates a new Gini partial document.
     *
     * @param document     A byte array representing an image, a pdf or UTF-8 encoded text
     * @param contentType  The media type of the uploaded data
     * @param filename     Optional the filename of the given document
     * @param documentType Optional a document type hint. See the documentation for the document type hints for
     *                     possible values
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     */
    public Task<Document> createPartialDocument(@NonNull final byte[] document, @NonNull final String contentType,
            @Nullable final String filename, @Nullable final DocumentType documentType) {
        return createPartialDocumentInternal(document, contentType, filename, documentType, null);
    }

    /**
     * Uploads raw data and creates a new Gini partial document.
     *
     * @param document          A byte array representing an image, a pdf or UTF-8 encoded text
     * @param contentType       The media type of the uploaded data
     * @param filename          Optional the filename of the given document
     * @param documentType      Optional a document type hint. See the documentation for the document type hints for
     *                          possible values
     * @param documentMetadata  Additional information related to the document (e.g. the branch id
     *                          to which the client app belongs)
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     */
    public Task<Document> createPartialDocument(@NonNull final byte[] document, @NonNull final String contentType,
            @Nullable final String filename, @Nullable final DocumentType documentType, @NonNull final DocumentMetadata documentMetadata) {
        return createPartialDocumentInternal(document, contentType, filename, documentType, documentMetadata);
    }

    private Task<Document> createPartialDocumentInternal(@NonNull final byte[] document, @NonNull final String contentType,
            @Nullable final String filename, @Nullable final DocumentType documentType, @Nullable final DocumentMetadata documentMetadata) {
        if (!mGiniApiType.getGiniJsonMediaType().equals(MediaTypes.GINI_JSON_V2)) {
            throw new UnsupportedOperationException(
                    "Partial documents may be used only with the default Gini API. Use GiniApiType.DEFAULT.");
        }
        return createDocumentInternal(new Continuation<Session, Task<Uri>>() {
            @Override
            public Task<Uri> then(Task<Session> sessionTask) throws Exception {
                String apiDoctypeHint = null;
                if (documentType != null) {
                    apiDoctypeHint = documentType.getApiDoctypeHint();
                }
                final Session session = sessionTask.getResult();
                final String partialDocumentMediaType = MediaTypes
                        .forPartialDocument(mGiniApiType.getGiniPartialMediaType(), checkNotNull(contentType));
                return mApiCommunicator
                        .uploadDocument(document, partialDocumentMediaType, filename, apiDoctypeHint, session, documentMetadata);
            }
        });
    }

    /**
     * Creates a new Gini composite document.
     *
     * @param documents    A list of partial documents which should be part of a multi-page document
     * @param documentType Optional a document type hint. See the documentation for the document type hints for
     *                     possible values
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     */
    public Task<Document> createCompositeDocument(@NonNull final List<Document> documents, @Nullable final DocumentType documentType) {
        if (!mGiniApiType.getGiniJsonMediaType().equals(MediaTypes.GINI_JSON_V2)) {
            throw new UnsupportedOperationException(
                    "Composite documents may be used only with the default Gini API. Use GiniApiType.DEFAULT.");
        }
        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<Uri>>() {
            @Override
            public Task<Uri> then(Task<Session> sessionTask) throws Exception {
                String apiDoctypeHint = null;
                if (documentType != null) {
                    apiDoctypeHint = documentType.getApiDoctypeHint();
                }
                final Session session = sessionTask.getResult();
                final byte[] compositeJson = createCompositeJson(documents);
                return mApiCommunicator
                        .uploadDocument(compositeJson, mGiniApiType.getGiniCompositeJsonMediaType(), null, apiDoctypeHint, session, null);
            }
        }, Task.BACKGROUND_EXECUTOR).onSuccessTask(new Continuation<Uri, Task<Document>>() {
            @Override
            public Task<Document> then(Task<Uri> uploadTask) throws Exception {
                return getDocument(uploadTask.getResult());
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Creates a new Gini composite document. The input Map must contain the partial documents as keys. These will be
     * part of the multi-page document. The value for each partial document key is the amount in degrees the document
     * has been rotated by the user.
     *
     * @param documentRotationMap A map of partial documents and their rotation in degrees
     * @param documentType        Optional a document type hint. See the documentation for the document type hints for
     *                            possible values
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     */
    public Task<Document> createCompositeDocument(@NonNull final LinkedHashMap<Document, Integer> documentRotationMap,
            @Nullable final DocumentType documentType) {
        if (!mGiniApiType.getGiniJsonMediaType().equals(MediaTypes.GINI_JSON_V2)) {
            throw new UnsupportedOperationException(
                    "Composite documents may be used only with the default Gini API. Use GiniApiType.DEFAULT.");
        }
        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<Uri>>() {
            @Override
            public Task<Uri> then(Task<Session> sessionTask) throws Exception {
                String apiDoctypeHint = null;
                if (documentType != null) {
                    apiDoctypeHint = documentType.getApiDoctypeHint();
                }
                final Session session = sessionTask.getResult();
                final byte[] compositeJson = createCompositeJson(documentRotationMap);
                return mApiCommunicator
                        .uploadDocument(compositeJson, mGiniApiType.getGiniCompositeJsonMediaType(), null, apiDoctypeHint, session, null);
            }
        }, Task.BACKGROUND_EXECUTOR).onSuccessTask(new Continuation<Uri, Task<Document>>() {
            @Override
            public Task<Document> then(Task<Uri> uploadTask) throws Exception {
                return getDocument(uploadTask.getResult());
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    private byte[] createCompositeJson(@NonNull final List<Document> documents)
            throws JSONException {
        final LinkedHashMap<Document, Integer> documentRotationMap = new LinkedHashMap<>();
        for (final Document document : documents) {
            documentRotationMap.put(document, 0);
        }
        return createCompositeJson(documentRotationMap);
    }

    private byte[] createCompositeJson(@NonNull final LinkedHashMap<Document, Integer> documentRotationMap)
            throws JSONException {
        final JSONObject jsonObject = new JSONObject();
        final JSONArray partialDocuments = new JSONArray();
        for (final Map.Entry<Document, Integer> entry : documentRotationMap.entrySet()) {
            final Document document = entry.getKey();
            int rotation = entry.getValue();
            // Converts input degrees to degrees between [0,360)
            rotation = ((rotation % 360) + 360) % 360;
            final JSONObject partialDoc = new JSONObject();
            partialDoc.put("document", document.getUri());
            partialDoc.put("rotationDelta", rotation);
            partialDocuments.put(partialDoc);
        }
        jsonObject.put("partialDocuments", partialDocuments);
        return jsonObject.toString().getBytes(CHARSET_UTF8);
    }

    /**
     * Uploads raw data and creates a new Gini document.
     *
     * @param document     A byte array representing an image, a pdf or UTF-8 encoded text
     * @param filename     Optional the filename of the given document.
     * @param documentType Optional a document type hint. See the documentation for the document type hints for
     *                     possible values.
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     *
     * <b>Important:</b> If using the default Gini API, then use {@link #createPartialDocument(byte[], String, String, DocumentType)} to upload the
     * document and then call {@link #createCompositeDocument(LinkedHashMap, DocumentType)}
     * (or {@link #createCompositeDocument(List, DocumentType)}) to finish document creation. The
     * returned composite document can be used to poll the processing state, to retrieve extractions
     * and to send feedback.
     */
    public Task<Document> createDocument(@NonNull final byte[] document, @Nullable final String filename,
            @Nullable final DocumentType documentType) {
        return createDocumentInternal(document, filename, documentType, null);
    }

    /**
     * Uploads raw data and creates a new Gini document.
     *
     * @param document          A byte array representing an image, a pdf or UTF-8 encoded text
     * @param filename          Optional the filename of the given document.
     * @param documentType      Optional a document type hint. See the documentation for the document type hints for
     *                          possible values.
     * @param documentMetadata  Additional information related to the document (e.g. the branch id
     *                          to which the client app belongs)
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     *
     * <b>Important:</b> If using the default Gini API, then use {@link #createPartialDocument(byte[], String, String, DocumentType)} to upload the
     * document and then call {@link #createCompositeDocument(LinkedHashMap, DocumentType)}
     * (or {@link #createCompositeDocument(List, DocumentType)}) to finish document creation. The
     * returned composite document can be used to poll the processing state, to retrieve extractions
     * and to send feedback.
     */
    public Task<Document> createDocument(@NonNull final byte[] document, @Nullable final String filename,
            @Nullable final DocumentType documentType, @NonNull final DocumentMetadata documentMetadata) {
        return createDocumentInternal(document, filename, documentType, documentMetadata);
    }

    private Task<Document> createDocumentInternal(@NonNull final byte[] document, @Nullable final String filename,
            @Nullable final DocumentType documentType, @Nullable final DocumentMetadata documentMetadata) {
        return createDocumentInternal(new Continuation<Session, Task<Uri>>() {
            @Override
            public Task<Uri> then(Task<Session> sessionTask) throws Exception {
                String apiDoctypeHint = null;
                if (documentType != null) {
                    apiDoctypeHint = documentType.getApiDoctypeHint();
                }
                final Session session = sessionTask.getResult();
                return mApiCommunicator
                        .uploadDocument(document, MediaTypes.IMAGE_JPEG, filename, apiDoctypeHint, session, documentMetadata);
            }
        });
    }

    private Task<Document> createDocumentInternal(@NonNull final Continuation<Session, Task<Uri>> successContinuation) {
        return mSessionManager.getSession()
                .onSuccessTask(successContinuation, Task.BACKGROUND_EXECUTOR)
                .onSuccessTask(new Continuation<Uri, Task<Document>>() {
                    @Override
                    public Task<Document> then(Task<Uri> uploadTask) throws Exception {
                        return getDocument(uploadTask.getResult());
                    }
                }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Uploads the given photo of a document and creates a new Gini document.
     *
     * @param document        A Bitmap representing the image
     * @param filename        Optional the filename of the given document.
     * @param documentType    Optional a document type hint. See the documentation for the document type hints for
     *                        possible values.
     * @param compressionRate Optional the compression rate of the created JPEG representation of the document.
     *                        Between 0 and 90.
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     *
     * @deprecated If using the default Gini API, then use {@link #createPartialDocument(byte[], String, String, DocumentType)} to upload the
     * document and then call {@link #createCompositeDocument(LinkedHashMap, DocumentType)}
     * (or {@link #createCompositeDocument(List, DocumentType)}) to finish document creation. The
     * returned composite document can be used to poll the processing state, to retrieve extractions
     * and to send feedback.
     * <p>
     * If using the accounting Gini API, then use {@link #createDocument(byte[], String, DocumentType)}.
     */
    @Deprecated
    public Task<Document> createDocument(@NonNull final Bitmap document, @Nullable final String filename,
            @Nullable final String documentType, final int compressionRate) {
        return createDocumentInternal(document, filename, documentType, compressionRate, null);
    }

    /**
     * Uploads the given photo of a document and creates a new Gini document.
     *
     * @param document          A Bitmap representing the image
     * @param filename          Optional the filename of the given document.
     * @param documentType      Optional a document type hint. See the documentation for the document type hints for
     *                          possible values.
     * @param compressionRate   Optional the compression rate of the created JPEG representation of the document.
     *                          Between 0 and 90.
     * @param documentMetadata  Additional information related to the document (e.g. the branch id
     *                          to which the client app belongs)
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     *
     * @deprecated If using the default Gini API, then use {@link #createPartialDocument(byte[], String, String, DocumentType, DocumentMetadata)} to upload the
     * document and then call {@link #createCompositeDocument(LinkedHashMap, DocumentType)}
     * (or {@link #createCompositeDocument(List, DocumentType)}) to finish document creation. The
     * returned composite document can be used to poll the processing state, to retrieve extractions
     * and to send feedback.
     * <p>
     * If using the accounting Gini API, then use {@link #createDocument(byte[], String, DocumentType, DocumentMetadata)}.
     */
    @Deprecated
    public Task<Document> createDocument(@NonNull final Bitmap document, @Nullable final String filename,
            @Nullable final String documentType, final int compressionRate, @NonNull final DocumentMetadata documentMetadata) {
        return createDocumentInternal(document, filename, documentType, compressionRate, documentMetadata);
    }

    /**
     * Uploads the given photo of a document and creates a new Gini document.
     *
     * @param document        A Bitmap representing the image
     * @param filename        Optional the filename of the given document.
     * @param documentType    Optional a document type hint.
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     *
     * @deprecated If using the default Gini API, then use {@link #createPartialDocument(byte[], String, String, DocumentType)} to upload the
     * document and then call {@link #createCompositeDocument(LinkedHashMap, DocumentType)}
     * (or {@link #createCompositeDocument(List, DocumentType)}) to finish document creation. The
     * returned composite document can be used to poll the processing state, to retrieve extractions
     * and to send feedback.
     * <p>
     * If using the accounting Gini API, then use {@link #createDocument(byte[], String, DocumentType)}.
     */
    public Task<Document> createDocument(@NonNull final Bitmap document, @Nullable final String filename,
            @Nullable final DocumentType documentType) {
        String apiDoctypeHint = null;
        if (documentType != null) {
            apiDoctypeHint = documentType.getApiDoctypeHint();
        }
        return createDocumentInternal(document, filename, apiDoctypeHint, DEFAULT_COMPRESSION, null);
    }

    /**
     * Uploads the given photo of a document and creates a new Gini document.
     *
     * @param document          A Bitmap representing the image
     * @param filename          Optional the filename of the given document.
     * @param documentType      Optional a document type hint.
     * @param documentMetadata  Additional information related to the document (e.g. the branch id
     *                          to which the client app belongs)
     *
     * @return A Task which will resolve to the Document instance of the freshly created document.
     *
     * @deprecated If using the default Gini API, then use {@link #createPartialDocument(byte[], String, String, DocumentType, DocumentMetadata)} to upload the
     * document and then call {@link #createCompositeDocument(LinkedHashMap, DocumentType)}
     * (or {@link #createCompositeDocument(List, DocumentType)}) to finish document creation. The
     * returned composite document can be used to poll the processing state, to retrieve extractions
     * and to send feedback.
     * <p>
     * If using the accounting Gini API, then use {@link #createDocument(byte[], String, DocumentType, DocumentMetadata)}.
     */
    public Task<Document> createDocument(@NonNull final Bitmap document, @Nullable final String filename,
            @Nullable final DocumentType documentType, @NonNull final DocumentMetadata documentMetadata) {
        String apiDoctypeHint = null;
        if (documentType != null) {
            apiDoctypeHint = documentType.getApiDoctypeHint();
        }
        return createDocumentInternal(document, filename, apiDoctypeHint, DEFAULT_COMPRESSION,
                documentMetadata);
    }

    private Task<Document> createDocumentInternal(@NonNull final Bitmap document, @Nullable final String filename,
            @Nullable final String apiDoctypeHint, final int compressionRate,
            @Nullable final DocumentMetadata documentMetadata) {
        return createDocumentInternal(new Continuation<Session, Task<Uri>>() {
            @Override
            public Task<Uri> then(Task<Session> sessionTask) throws Exception {
                final Session session = sessionTask.getResult();
                final ByteArrayOutputStream documentOutputStream = new ByteArrayOutputStream();
                document.compress(JPEG, compressionRate, documentOutputStream);
                final byte[] uploadData = documentOutputStream.toByteArray();
                return mApiCommunicator
                        .uploadDocument(uploadData, MediaTypes.IMAGE_JPEG, filename, apiDoctypeHint, session, documentMetadata);
            }
        });
    }

    /**
     * Get the extractions for the given document.
     *
     * @param document The Document instance for whose document the extractions are returned.
     *
     * @return A Task which will resolve to a mapping, where the key is a String with the name of the
     * specific. See the
     * <a href="http://developer.gini.net/gini-api/html/document_extractions.html">Gini API documentation</a>
     * for a list of the names of the specific extractions.
     *
     * @deprecated Use {@link #getAllExtractions(Document)} instead to be able to receive compound extractions, too.
     */
    public Task<Map<String, SpecificExtraction>> getExtractions(@NonNull final Document document) {
        final String documentId = document.getId();
        return mSessionManager.getSession()
                .onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
                    @Override
                    public Task<JSONObject> then(Task<Session> sessionTask) {
                        final Session session = sessionTask.getResult();
                        return mApiCommunicator.getExtractions(documentId, session);
                    }
                }, Task.BACKGROUND_EXECUTOR)
                .onSuccess(new Continuation<JSONObject, Map<String, SpecificExtraction>>() {
                    @Override
                    public Map<String, SpecificExtraction> then(Task<JSONObject> task) throws Exception {
                        final JSONObject responseData = task.getResult();
                        final JSONObject candidatesData = responseData.getJSONObject("candidates");
                        HashMap<String, List<Extraction>> candidates =
                                extractionCandidatesFromApiResponse(candidatesData);

                        final HashMap<String, SpecificExtraction> extractionsByName =
                                new HashMap<String, SpecificExtraction>();
                        final JSONObject extractionsData = responseData.getJSONObject("extractions");
                        @SuppressWarnings("unchecked")
                        // Quote Android Source: "/* Return a raw type for API compatibility */"
                        final Iterator<String> extractionsNameIterator = extractionsData.keys();
                        while (extractionsNameIterator.hasNext()) {
                            final String extractionName = extractionsNameIterator.next();
                            final JSONObject extractionData = extractionsData.getJSONObject(extractionName);
                            final Extraction extraction = extractionFromApiResponse(extractionData);
                            List<Extraction> candidatesForExtraction = new ArrayList<Extraction>();
                            if (extractionData.has("candidates")) {
                                final String candidatesName = extractionData.getString("candidates");
                                if (candidates.containsKey(candidatesName)) {
                                    candidatesForExtraction = candidates.get(candidatesName);
                                }
                            }
                            final SpecificExtraction specificExtraction =
                                    new SpecificExtraction(extractionName, extraction.getValue(),
                                            extraction.getEntity(), extraction.getBox(),
                                            candidatesForExtraction);
                            extractionsByName.put(extractionName, specificExtraction);
                        }

                        return extractionsByName;
                    }
                }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Get the extractions for the given document.
     *
     * @param document The Document instance for whose document the extractions are returned.
     *
     * @return A Task which will resolve to an {@link ExtractionsContainer} object.
     */
    public Task<ExtractionsContainer> getAllExtractions(@NonNull final Document document) {
        final String documentId = document.getId();
        return mSessionManager.getSession()
                .onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
                    @Override
                    public Task<JSONObject> then(Task<Session> sessionTask) {
                        final Session session = sessionTask.getResult();
                        return mApiCommunicator.getExtractions(documentId, session);
                    }
                }, Task.BACKGROUND_EXECUTOR)
                .onSuccess(new Continuation<JSONObject, ExtractionsContainer>() {
                    @Override
                    public ExtractionsContainer then(Task<JSONObject> task) throws Exception {
                        final JSONObject responseData = task.getResult();
                        final JSONObject candidatesData = responseData.getJSONObject("candidates");
                        Map<String, List<Extraction>> candidates =
                                extractionCandidatesFromApiResponse(candidatesData);

                        final Map<String, SpecificExtraction> specificExtractions =
                                parseSpecificExtractions(responseData.getJSONObject("extractions"), candidates);

                        final Map<String, CompoundExtraction> compoundExtractions =
                                parseCompoundExtractions(responseData.optJSONObject("compoundExtractions"), candidates);

                        final List<ReturnReason> returnReasons = parseReturnReason(responseData.optJSONArray("returnReasons"));

                        return new ExtractionsContainer(specificExtractions, compoundExtractions, returnReasons);
                    }
                }, Task.BACKGROUND_EXECUTOR);

    }

    @NonNull
    private Map<String, SpecificExtraction> parseSpecificExtractions(@NonNull final JSONObject specificExtractionsJson,
            @NonNull final Map<String, List<Extraction>> candidates)
            throws JSONException {
        final Map<String, SpecificExtraction> specificExtractions = new HashMap<>();
        @SuppressWarnings("unchecked")
        // Quote Android Source: "/* Return a raw type for API compatibility */"
        final Iterator<String> extractionsNameIterator = specificExtractionsJson.keys();
        while (extractionsNameIterator.hasNext()) {
            final String extractionName = extractionsNameIterator.next();
            final JSONObject extractionData = specificExtractionsJson.getJSONObject(extractionName);
            final Extraction extraction = extractionFromApiResponse(extractionData);
            List<Extraction> candidatesForExtraction = new ArrayList<Extraction>();
            if (extractionData.has("candidates")) {
                final String candidatesName = extractionData.getString("candidates");
                if (candidates.containsKey(candidatesName)) {
                    candidatesForExtraction = candidates.get(candidatesName);
                }
            }
            final SpecificExtraction specificExtraction =
                    new SpecificExtraction(extractionName, extraction.getValue(),
                            extraction.getEntity(), extraction.getBox(),
                            candidatesForExtraction);
            specificExtractions.put(extractionName, specificExtraction);
        }
        return specificExtractions;
    }

    private Map<String, CompoundExtraction> parseCompoundExtractions(@Nullable final JSONObject compoundExtractionsJson,
            @NonNull final Map<String, List<Extraction>> candidates)
            throws JSONException {
        if (compoundExtractionsJson == null) {
            return Collections.emptyMap();
        }
        final HashMap<String, CompoundExtraction> compoundExtractions = new HashMap<>();
        final Iterator<String> extractionsNameIterator = compoundExtractionsJson.keys();
        while (extractionsNameIterator.hasNext()) {
            final String extractionName = extractionsNameIterator.next();
            final List<Map<String, SpecificExtraction>> specificExtractionMaps = new ArrayList<>();
            final JSONArray compoundExtractionData = compoundExtractionsJson.getJSONArray(extractionName);
            for (int i = 0; i < compoundExtractionData.length(); i++) {
                final JSONObject specificExtractionsData = compoundExtractionData.getJSONObject(i);
                specificExtractionMaps.add(parseSpecificExtractions(specificExtractionsData, candidates));
            }
            compoundExtractions.put(extractionName, new CompoundExtraction(extractionName, specificExtractionMaps));
        }
        return compoundExtractions;
    }

    private List<ReturnReason> parseReturnReason(@Nullable final JSONArray returnReasonsJson) throws JSONException {
        if (returnReasonsJson == null) {
            return Collections.emptyList();
        }
        final List<ReturnReason> returnReasons = new ArrayList<>();
        for (int i = 0; i < returnReasonsJson.length(); i++) {
            final JSONObject returnReasonJson = returnReasonsJson.getJSONObject(i);

            Map<String, String> localizedLabels = new HashMap<>();

            final Iterator<String> keys = returnReasonJson.keys();
            while (keys.hasNext()) {
                final String key = keys.next();
                if (key.equals("id")) {
                    continue;
                }
                localizedLabels.put(key, returnReasonJson.getString(key));
            }
            returnReasons.add(new ReturnReason(returnReasonJson.getString("id"), localizedLabels));
        }
        return returnReasons;
    }

    /**
     * Get the document with the given unique identifier.
     *
     * @param documentId The unique identifier of the document.
     *
     * @return A document instance representing all the document's metadata.
     */
    public Task<Document> getDocument(@NonNull final String documentId) {
        checkNotNull(documentId);
        return mSessionManager.getSession()
                .onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
                    @Override
                    public Task<JSONObject> then(Task<Session> sessionTask) throws Exception {
                        final Session session = sessionTask.getResult();
                        return mApiCommunicator.getDocument(documentId, session);
                    }
                }, Task.BACKGROUND_EXECUTOR)
                .onSuccess(DOCUMENT_FROM_RESPONSE, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Get the document with the given unique identifier.
     *
     * <b>Please note that this method may use a slightly corrected URI from which it gets the document (e.g. if the
     * URI's host does not conform to the base URL of the Gini API). Therefore it is not possibly to use this method to
     * get a document from an arbitrary URI.</b>
     *
     * @param documentUri The URI of the document.
     *
     * @return A document instance representing all the document's metadata.
     */
    public Task<Document> getDocument(@NonNull final Uri documentUri) {
        checkNotNull(documentUri);
        return mSessionManager.getSession()
                .onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
                    @Override
                    public Task<JSONObject> then(Task<Session> sessionTask) throws Exception {
                        final Session session = sessionTask.getResult();
                        return mApiCommunicator.getDocument(documentUri, session);
                    }
                }, Task.BACKGROUND_EXECUTOR)
                .onSuccess(DOCUMENT_FROM_RESPONSE, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Continually checks the document status (via the Gini API) until the document is fully processed. To avoid
     * flooding the network, there is a pause of at least the number of seconds that is set in the POLLING_INTERVAL
     * constant of this class.
     *
     * <b>This method returns a Task which will resolve to a new document instance. It does not update the given
     * document instance.</b>
     *
     * @param document The document which will be polled.
     */
    public Task<Document> pollDocument(@NonNull final Document document) {
        if (document.getState() != Document.ProcessingState.PENDING) {
            return Task.forResult(document);
        }
        mDocumentPollingsInProgress.put(document, false);
        final String documentId = document.getId();
        return getDocument(documentId).continueWithTask(new Continuation<Document, Task<Document>>() {
            @Override
            public Task<Document> then(Task<Document> task) throws Exception {
                if (task.isFaulted() || task.isCancelled()
                        || task.getResult().getState() != Document.ProcessingState.PENDING) {
                    mDocumentPollingsInProgress.remove(document);
                    return task;
                } else {
                    if (mDocumentPollingsInProgress.containsKey(document)
                            && mDocumentPollingsInProgress.get(document)) {
                        mDocumentPollingsInProgress.remove(document);
                        return Task.cancelled();
                    } else {
                        // The continuation is executed in a background thread by Bolts, so it does not block the UI
                        // when we sleep here. Infinite recursions are also prevented by Bolts (the task will then resolve
                        // to a failure).
                        Thread.sleep(POLLING_INTERVAL);
                        return pollDocument(document);
                    }
                }
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Cancels document polling.
     *
     * @param document The document which is being polled
     */
    public void cancelDocumentPolling(@NonNull final Document document) {
        if (mDocumentPollingsInProgress.containsKey(document)) {
            mDocumentPollingsInProgress.put(document, true);
        }
    }

    /**
     * Sends approved and conceivably corrected extractions for the given document. This is called "submitting feedback
     * on extractions" in
     * the Gini API documentation.
     *
     * @param document    The document for which the extractions should be updated.
     * @param extractions A Map where the key is the name of the specific extraction and the value is the
     *                    SpecificExtraction object. This is the same structure as returned by the getExtractions
     *                    method of this manager.
     *
     * @return A Task which will resolve to the same document instance when storing the updated
     * extractions was successful.
     *
     * @throws JSONException When a value of an extraction is not JSON serializable.
     */
    public Task<Document> sendFeedbackForExtractions(@NonNull final Document document,
            @NonNull final Map<String, SpecificExtraction> extractions)
            throws JSONException {
        final String documentId = document.getId();
        final JSONObject feedbackForExtractions = new JSONObject();
        for (Map.Entry<String, SpecificExtraction> entry : extractions.entrySet()) {
            final Extraction extraction = entry.getValue();
            final JSONObject extractionData = new JSONObject();
            extractionData.put("value", extraction.getValue());
            extractionData.put("entity", extraction.getEntity());
            feedbackForExtractions.put(entry.getKey(), extractionData);
        }

        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
            @Override
            public Task<JSONObject> then(Task<Session> task) throws Exception {
                final Session session = task.getResult();
                return mApiCommunicator.sendFeedback(documentId, feedbackForExtractions, session);
            }
        }, Task.BACKGROUND_EXECUTOR).onSuccess(new Continuation<JSONObject, Document>() {
            @Override
            public Document then(Task<JSONObject> task) throws Exception {
                for (Map.Entry<String, SpecificExtraction> entry : extractions.entrySet()) {
                    entry.getValue().setIsDirty(false);
                }
                return document;
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    public Task<Document> sendFeedbackForExtractions(@NonNull final Document document,
            @NonNull final Map<String, SpecificExtraction> extractions,
            @NonNull final Map<String, CompoundExtraction> compoundExtractions)
            throws JSONException {
        final String documentId = document.getId();

        final JSONObject feedbackForExtractions = new JSONObject();
        for (Map.Entry<String, SpecificExtraction> entry : extractions.entrySet()) {
            final Extraction extraction = entry.getValue();
            final JSONObject extractionData = new JSONObject();
            extractionData.put("value", extraction.getValue());
            extractionData.put("entity", extraction.getEntity());
            feedbackForExtractions.put(entry.getKey(), extractionData);
        }

        final JSONObject feedbackForCompoundExtractions = new JSONObject();
        for (Map.Entry<String, CompoundExtraction> compoundExtractionEntry : compoundExtractions.entrySet()) {
            final CompoundExtraction compoundExtraction = compoundExtractionEntry.getValue();
            final JSONArray specificExtractionsFeedbackObjects = new JSONArray();
            for (final Map<String, SpecificExtraction> specificExtractionMap : compoundExtraction.getSpecificExtractionMaps()) {
                final JSONObject specificExtractionsFeedback = new JSONObject();
                for (Map.Entry<String, SpecificExtraction> specificExtractionEntry : specificExtractionMap.entrySet()) {
                    final Extraction extraction = specificExtractionEntry.getValue();
                    final JSONObject extractionData = new JSONObject();
                    extractionData.put("value", extraction.getValue());
                    extractionData.put("entity", extraction.getEntity());
                    specificExtractionsFeedback.put(specificExtractionEntry.getKey(), extractionData);
                }
                specificExtractionsFeedbackObjects.put(specificExtractionsFeedback);
            }
            feedbackForCompoundExtractions.put(compoundExtractionEntry.getKey(), specificExtractionsFeedbackObjects);
        }

        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
            @Override
            public Task<JSONObject> then(Task<Session> task) throws Exception {
                final Session session = task.getResult();
                return mApiCommunicator.sendFeedback(documentId, feedbackForExtractions, feedbackForCompoundExtractions, session);
            }
        }, Task.BACKGROUND_EXECUTOR).onSuccess(new Continuation<JSONObject, Document>() {
            @Override
            public Document then(Task<JSONObject> task) throws Exception {
                for (Map.Entry<String, SpecificExtraction> entry : extractions.entrySet()) {
                    entry.getValue().setIsDirty(false);
                }
                return document;
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Sends an error report for the given document to Gini. If the processing result for a document was not
     * satisfactory (e.g. extractions where empty or incorrect), you can create an error report for a document. This
     * allows Gini to analyze and correct the problem that was found.
     *
     * <b>The owner of this document must agree that Gini can use this document for debugging and error analysis.</b>
     *
     * @param document    The erroneous document.
     * @param summary     Optional a short summary of the occurred error.
     * @param description Optional a more detailed description of the occurred error.
     *
     * @return A Task which will resolve to an error ID. This is a unique identifier for your error report
     * and can be used to refer to the reported error towards the Gini support.
     */
    public Task<String> reportDocument(@NonNull final Document document, @Nullable final String summary,
            @Nullable final String description) {
        final String documentId = document.getId();
        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
            @Override
            public Task<JSONObject> then(Task<Session> task) throws Exception {
                final Session session = task.getResult();
                return mApiCommunicator.errorReportForDocument(documentId, summary, description, session);
            }
        }, Task.BACKGROUND_EXECUTOR).onSuccess(new Continuation<JSONObject, String>() {
            @Override
            public String then(Task<JSONObject> task) throws Exception {
                final JSONObject responseData = task.getResult();
                return responseData.getString("errorId");
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Gets the layout of a document. The layout of the document describes the textual content of a document with
     * positional information, based on the processed document.
     *
     * @param document The document for which the layouts is requested.
     *
     * @return A task which will resolve to a string containing the layout xml.
     */
    public Task<JSONObject> getLayout(@NonNull final Document document) {
        final String documentId = document.getId();
        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
            @Override
            public Task<JSONObject> then(Task<Session> task) throws Exception {
                final Session session = task.getResult();
                return mApiCommunicator.getLayoutForDocument(documentId, session);
            }
        }, Task.BACKGROUND_EXECUTOR);
    }

    /**
     * Helper method which takes the JSON response of the Gini API as input and returns a mapping where the key is the
     * name of the candidates list (e.g. "amounts" or "dates") and the value is a list of extraction instances.
     *
     * @param responseData The JSON data of the key candidates from the response of the Gini API.
     *
     * @return The created mapping as described above.
     *
     * @throws JSONException If the JSON data does not have the expected structure or if there is invalid data.
     */
    protected HashMap<String, List<Extraction>> extractionCandidatesFromApiResponse(@NonNull final JSONObject responseData)
            throws JSONException {
        final HashMap<String, List<Extraction>> candidatesByEntity = new HashMap<String, List<Extraction>>();

        @SuppressWarnings("unchecked") // Quote Android Source: "/* Return a raw type for API compatibility */"
        final Iterator<String> entityNameIterator = responseData.keys();
        while (entityNameIterator.hasNext()) {
            final String entityName = entityNameIterator.next();
            final JSONArray candidatesListData = responseData.getJSONArray(entityName);
            final ArrayList<Extraction> candidates = new ArrayList<Extraction>();
            for (int i = 0, length = candidatesListData.length(); i < length; i += 1) {
                final JSONObject extractionData = candidatesListData.getJSONObject(i);
                candidates.add(extractionFromApiResponse(extractionData));
            }
            candidatesByEntity.put(entityName, candidates);
        }
        return candidatesByEntity;
    }

    /**
     * Helper method which creates an Extraction instance from the JSON data which is returned by the Gini API.
     *
     * @param responseData The JSON data.
     *
     * @return The created Extraction instance.
     *
     * @throws JSONException If the JSON data does not have the expected structure or if there is invalid data.
     */
    protected Extraction extractionFromApiResponse(@NonNull final JSONObject responseData) throws JSONException {
        final String entity = responseData.getString("entity");
        final String value = responseData.getString("value");
        // The box is optional for some extractions.
        Box box = null;
        if (responseData.has("box")) {
            box = Box.fromApiResponse(responseData.getJSONObject("box"));
        }
        return new Extraction(value, entity, box);
    }


    /**
     * A builder to configure the upload of a bitmap.
     */
    public static class DocumentUploadBuilder {

        private byte[] mDocumentBytes;
        private Bitmap mDocumentBitmap;
        private String mFilename;
        private String mDocumentType;
        private DocumentType mDocumentTypeHint;
        private int mCompressionRate;

        public DocumentUploadBuilder() {
            mCompressionRate = DocumentTaskManager.DEFAULT_COMPRESSION;
        }

        /**
         * @deprecated Use {@link #DocumentUploadBuilder()} instead.
         *
         * @param documentBitmap A Bitmap representing the image.
         */
        @Deprecated
        public DocumentUploadBuilder(@NonNull final Bitmap documentBitmap) {
            mDocumentBitmap = documentBitmap;
            mCompressionRate = DocumentTaskManager.DEFAULT_COMPRESSION;
        }

        /**
         * Set the document as a byte array. If a {@link Bitmap} was also set, the bitmap will be used.
         */
        public DocumentUploadBuilder setDocumentBytes(@NonNull byte[] documentBytes) {
            this.mDocumentBytes = documentBytes;
            return this;
        }

        /**
         * Set the document as a {@link Bitmap}. This bitmap will be used instead of the byte array, if both were set.
         */
        public DocumentUploadBuilder setDocumentBitmap(@NonNull Bitmap documentBitmap) {
            this.mDocumentBitmap = documentBitmap;
            return this;
        }

        /**
         * Set the document' s filename.
         */
        public DocumentUploadBuilder setFilename(@NonNull final String filename) {
            mFilename = filename;
            return this;
        }

        /**
         * Set the document's type. (This feature is called document type hint in the Gini API documentation). By
         * providing the doctype, Gini’s document processing is optimized in many ways.
         *
         * @deprecated Use {@link #setDocumentType(DocumentType)} instead.
         */
        @Deprecated
        public DocumentUploadBuilder setDocumentType(@NonNull final String documentType) {
            mDocumentType = documentType;
            return this;
        }

        /**
         * Set the document's type. (This feature is called document type hint in the Gini API documentation). By
         * providing the doctype, Gini’s document processing is optimized in many ways.
         */
        public DocumentUploadBuilder setDocumentType(@NonNull final DocumentType documentType) {
            mDocumentTypeHint = documentType;
            return this;
        }

        /**
         * The bitmap (if set) will be converted into a JPEG representation. Set the compression rate for the JPEG
         * representation.
         *
         * @deprecated The default compression rate is set to get the best extractions for the smallest image byte size.
         */
        @Deprecated
        public DocumentUploadBuilder setCompressionRate(final int compressionRate) {
            mCompressionRate = compressionRate;
            return this;
        }

        /**
         * Use the given DocumentTaskManager instance to upload the document with all the features which were set with
         * this builder.
         *
         * @param documentTaskManager The instance of a DocumentTaskManager whill will be used to upload the document.
         *
         * @return A task which will resolve to a Document instance.
         */
        public Task<Document> upload(@NonNull final DocumentTaskManager documentTaskManager) {
            if (mDocumentBitmap != null) {
                if (mDocumentTypeHint != null) {
                    return documentTaskManager.createDocument(mDocumentBitmap, mFilename, mDocumentTypeHint);
                } else {
                    return documentTaskManager.createDocument(mDocumentBitmap, mFilename, mDocumentType, mCompressionRate);
                }
            } else {
                return documentTaskManager.createDocument(mDocumentBytes, mFilename, mDocumentTypeHint);
            }
        }
    }
}
