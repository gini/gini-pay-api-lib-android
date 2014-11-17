package net.gini.android;

import android.graphics.Bitmap;

import net.gini.android.authorization.Session;
import net.gini.android.authorization.SessionManager;
import net.gini.android.models.Box;
import net.gini.android.models.Document;
import net.gini.android.models.Extraction;
import net.gini.android.models.SpecificExtraction;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import bolts.Continuation;
import bolts.Task;

import static android.graphics.Bitmap.CompressFormat.JPEG;
import static net.gini.android.Utils.checkNotNull;

/**
 * The DocumentTaskManager is a high level API on top of the Gini API, which is used via the ApiCommunicator. It
 * provides high level methods to handle document related tasks easily.
 */
public class DocumentTaskManager {

    /** The time in seconds between HTTP requests when a document is polled. */
    public static long POLLING_INTERVAL = 1;

    /** The default compression rate which is used for JPEG compression in per cent. */
    public final static int DEFAULT_COMPRESSION = 90;

    /** The ApiCommunicator instance which is used to communicate with the Gini API. */
    private final ApiCommunicator mApiCommunicator;
    /** The SessionManager instance which is used to create the documents. */
    private final SessionManager mSessionManager;

    /** The worker which is used to schedule the polling of documents. */
    private static final ScheduledExecutorService mWorker = Executors.newSingleThreadScheduledExecutor();

    public DocumentTaskManager(final ApiCommunicator apiCommunicator, final SessionManager sessionManager) {
        mApiCommunicator = checkNotNull(apiCommunicator);
        mSessionManager = checkNotNull(sessionManager);
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
     * @return                  A Task which will resolve to the Document instance of the freshly created document.
     */
    public Task<Document> createDocument(final Bitmap document, @Nullable final String filename,
                                         @Nullable final String documentType, final int compressionRate) {
        return mSessionManager.getSession().onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
            @Override
            public Task<JSONObject> then(Task<Session> sessionTask) throws Exception {
                final Session session = sessionTask.getResult();
                final ByteArrayOutputStream documentOutputStream = new ByteArrayOutputStream();
                document.compress(JPEG, compressionRate, documentOutputStream);
                final byte[] uploadData = documentOutputStream.toByteArray();
                return mApiCommunicator
                        .uploadDocument(uploadData, MediaTypes.IMAGE_JPEG, filename, documentType, session);
            }
        }).onSuccess(new Continuation<JSONObject, Document>() {
            @Override
            public Document then(Task<JSONObject> uploadTask) throws Exception {
                return Document.fromApiResponse(uploadTask.getResult());
            }
        });
    }

    /**
     * Get the extractions for the given document.
     *
     * @param document          The Document instance for whose document the extractions are returned.
     * @return                  A Task which will resolve to a mapping, where the key is a String with the name of the
     *                          specific. See the
     *                          <a href="http://developer.gini.net/gini-api/html/document_extractions.html">Gini API documentation</a>
     *                          for a list of the names of the specific extractions.
     */
    public Task<Map<String, SpecificExtraction>> getExtractions(final Document document) {
        final String documentId = document.getId();
        return mSessionManager.getSession()
                .onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
                    @Override
                    public Task<JSONObject> then(Task<Session> sessionTask) {
                        final Session session = sessionTask.getResult();
                        return mApiCommunicator.getExtractions(documentId, session);
                    }
                })
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
                        @SuppressWarnings("unchecked") // Quote Android Source: "/* Return a raw type for API compatibility */"
                        final Iterator<String> extractionsNameIterator = extractionsData.keys();
                        while (extractionsNameIterator.hasNext()) {
                            final String extractionName = extractionsNameIterator.next();
                            final JSONObject extractionData = extractionsData.getJSONObject(extractionName);
                            // TODO discuss with Andy.
                            final Extraction extraction = extractionFromApiResponse(extractionData);
                            final String candidatesName = extractionData.getString("candidates");
                            final SpecificExtraction specificExtraction =
                                    new SpecificExtraction(extractionName, extraction.getValue(),
                                                           extraction.getEntity(), extraction.getBox(),
                                                           candidates.get(candidatesName));
                            extractionsByName.put(extractionName, specificExtraction);
                        }

                        return extractionsByName;
                    }
                });
    }

    /**
     * Get the document with the given unique identifier.
     *
     * @param documentId        The unique identifier of the document.
     * @return                  A document instance representing all the document's metadata.
     */
    public Task<Document> getDocument(final String documentId) {
        checkNotNull(documentId);
        return mSessionManager.getSession()
                .onSuccessTask(new Continuation<Session, Task<JSONObject>>() {
                    @Override
                    public Task<JSONObject> then(Task<Session> sessionTask) throws Exception {
                        final Session session = sessionTask.getResult();
                        return mApiCommunicator.getDocument(documentId, session);
                    }
                })
                .onSuccess(new Continuation<JSONObject, Document>() {
                    @Override
                    public Document then(Task<JSONObject> task) throws Exception {
                        return Document.fromApiResponse(task.getResult());
                    }
                });
    }

    /**
     * Continually checks the document status (via the Gini API) until the document is fully processed. To avoid
     * flooding the network, there is a pause of at least the number of seconds that is set in the POLLING_INTERVAL
     * constant of this class.
     *
     * <b>This method returns a Task which will resolve to a new document instance. It does not update the given
     * document instance.</b>
     *
     * @param document          The document which will be polled.
     */
    public Task<Document> pollDocument(final Document document) {
        if (document.getState() != Document.ProcessingState.PENDING) {
            return Task.forResult(document);
        }
        final String documentId = document.getId();
        final Task<Document>.TaskCompletionSource completionSource = Task.create();
        final Runnable completionRunner = new Runnable() {
            @Override
            public void run() {
                final Runnable that = this;
                getDocument(documentId).continueWith(new Continuation<Document, Object>() {
                    @Override
                    public Object then(Task<Document> task) throws Exception {
                        if (task.isFaulted()) {
                            completionSource.setError(task.getError());
                        } else if (task.isCancelled()) {
                            completionSource.setCancelled();
                        } else {
                            Document polledDocument = task.getResult();
                            if (polledDocument.getState() == Document.ProcessingState.PENDING) {
                                mWorker.schedule(that, POLLING_INTERVAL, TimeUnit.SECONDS);
                            } else {
                                completionSource.setResult(polledDocument);
                            }
                        }
                        return null;
                    }
                });
            }
        };
        mWorker.execute(completionRunner);

        return completionSource.getTask();
    }

    /**
     * Helper method which takes the JSON response of the Gini API as input and returns a mapping where the key is the
     * name of the candidates list (e.g. "amounts" or "dates") and the value is a list of extraction instances.
     *
     * @param responseData      The JSON data of the key candidates from the response of the Gini API.
     * @return                  The created mapping as described above.
     * @throws JSONException    If the JSON data does not have the expected structure or if there is invalid data.
     */
    protected HashMap<String, List<Extraction>> extractionCandidatesFromApiResponse(final JSONObject responseData)
            throws JSONException {
        final HashMap<String, List<Extraction>> candidatesByEntity = new HashMap<String, List<Extraction>>();

        @SuppressWarnings("unchecked") // Quote Android Source: "/* Return a raw type for API compatibility */"
        final Iterator<String> entityNameIterator = responseData.keys();
        while (entityNameIterator.hasNext()) {
            final String entityName = entityNameIterator.next();
            final JSONArray candidatesListData = responseData.getJSONArray(entityName);
            final ArrayList<Extraction> candidates = new ArrayList<Extraction>();
            for (int i = 0, length = candidates.size(); i < length; i += 1) {
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
     * @param responseData      The JSON data.
     * @return                  The created Extraction instance.
     * @throws JSONException    If the JSON data does not have the expected structure or if there is invalid data.
     */
    protected Extraction extractionFromApiResponse(final JSONObject responseData) throws JSONException {
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

        private final Bitmap mDocumentBitmap;
        private String mFilename;
        private String mDocumentType;
        private int mCompressionRate;

        public DocumentUploadBuilder(final Bitmap documentBitmap) {
            mDocumentBitmap = documentBitmap;
            mCompressionRate = DocumentTaskManager.DEFAULT_COMPRESSION;
        }

        /**
         * Set the document' s filename.
         */
        public DocumentUploadBuilder setFilename(final String filename) {
            mFilename = filename;
            return this;
        }

        /**
         * Set the document's type. (This feature is called document type hint in the Gini API documentation). By
         * providing the doctype, Gini’s document processing is optimized in many ways.
         */
        public DocumentUploadBuilder setDocumentType(final String documentType) {
            mDocumentType = documentType;
            return this;
        }

        /**
         * The bitmap will be converted into a JPEG representation. Set the compression rate for the JPEG
         * representation.
         */
        public DocumentUploadBuilder setCompressionRate(final int compressionRate) {
            mCompressionRate = compressionRate;
            return this;
        }

        /**
         * Use the given DocumentTaskManager instance to upload the document with all the features which were set with
         * this builder.
         *
         * @param documentTaskManager The instance of a DocumentTaskManager whill will be used to upload the document.
         * @return A task which will resolve to a Document instance.
         */
        public Task<Document> upload(final DocumentTaskManager documentTaskManager) {
            return documentTaskManager.createDocument(mDocumentBitmap, mFilename, mDocumentType, mCompressionRate);
        }
    }
}
