package net.gini.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.support.test.filters.LargeTest;
import android.support.test.filters.SdkSuppress;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.android.volley.toolbox.NoCache;

import net.gini.android.authorization.EncryptedCredentialsStore;
import net.gini.android.authorization.UserCredentials;
import net.gini.android.helpers.TestUtils;
import net.gini.android.models.Document;
import net.gini.android.models.ExtractionsContainer;
import net.gini.android.models.SpecificExtraction;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import bolts.Continuation;
import bolts.Task;

import static android.support.test.InstrumentationRegistry.getTargetContext;
import static net.gini.android.helpers.TrustKitHelper.resetTrustKit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class GiniIntegrationTestAccounting {

    private Gini gini;
    private String clientId;
    private String clientSecret;
    private String apiUriAccounting;
    private String userCenterUri;
    private InputStream testDocumentAsStream;
    private final DocumentTaskManager.DocumentType documentType = DocumentTaskManager.DocumentType.INVOICE;
    private final String filename = "test.jpg";

    @Before
    public void setUp() throws Exception {
        final AssetManager assetManager = getTargetContext().getResources().getAssets();
        final InputStream testPropertiesInput = assetManager.open("test.properties");
        assertNotNull("test.properties not found", testPropertiesInput);
        final Properties testProperties = new Properties();
        testProperties.load(testPropertiesInput);
        clientId = getProperty(testProperties, "testClientIdAccounting");
        clientSecret = getProperty(testProperties, "testClientSecretAccounting");
        apiUriAccounting = getProperty(testProperties, "testApiUriAccounting");
        userCenterUri = getProperty(testProperties, "testUserCenterUri");

        Log.d("TEST", "testClientId " + clientId);
        Log.d("TEST", "testClientSecret " + clientSecret);
        Log.d("TEST", "testApiUriAccounting " + apiUriAccounting);
        Log.d("TEST", "testUserCenterUri " + userCenterUri);

        testDocumentAsStream = assetManager.open(filename);
        assertNotNull("test image test.jpg could not be loaded", testDocumentAsStream);

        resetTrustKit();

        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, "example.com").
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                build();
    }

    private static String getProperty(Properties properties, String propertyName) {
        Object value = properties.get(propertyName);
        assertNotNull(propertyName + " not set!", value);
        return value.toString();
    }

    @Test
    public void processDocumentByteArray() throws IOException, InterruptedException, JSONException {
        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        analyzeDocumentAndAssertExtractions(testDocument, filename, DocumentTaskManager.DocumentType.INVOICE);
    }

    @Test
    public void processDocumentWithCustomCache() throws IOException, JSONException, InterruptedException {
        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, "example.com").
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                setCache(new NoCache()).
                build();

        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        analyzeDocumentAndAssertExtractions(testDocument, filename, documentType);
    }

    @Test
    public void sendFeedback() throws Exception {
        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        final Map<Document, Map<String, SpecificExtraction>> documentExtractions = analyzeDocumentAndAssertExtractions(testDocument, filename,
                documentType);
        final Document document = documentExtractions.keySet().iterator().next();
        final Map<String, SpecificExtraction> extractions = documentExtractions.values().iterator().next();

        // All extractions are correct, that means we have nothing to correct and will only send positive feedback
        // we should only send feedback for extractions we have seen and accepted
        final Map<String, SpecificExtraction> feedback = new HashMap<>();
        feedback.put("amountToPay", extractions.get("amountToPay"));
        feedback.put("senderName", extractions.get("senderName"));

        final Task<Document> sendFeedback = gini.getDocumentTaskManager().sendFeedbackForExtractions(document, feedback);
        sendFeedback.waitForCompletion();
        if (sendFeedback.isFaulted()) {
            Log.e("TEST", Log.getStackTraceString(sendFeedback.getError()));
        }
        assertTrue("Sending feedback should be completed", sendFeedback.isCompleted());
        assertFalse("Sending feedback should be successful", sendFeedback.isFaulted());
    }

    @Test
    public void documentUploadWorksAfterNewUserWasCreatedIfUserWasInvalid() throws IOException, JSONException, InterruptedException {
        EncryptedCredentialsStore credentialsStore = new EncryptedCredentialsStore(
                getTargetContext().getSharedPreferences("GiniTests", Context.MODE_PRIVATE), getTargetContext());
        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, "example.com").
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                setCredentialsStore(credentialsStore).
                build();

        // Create invalid user credentials
        UserCredentials invalidUserCredentials = new UserCredentials("invalid@example.com", "1234");
        credentialsStore.storeUserCredentials(invalidUserCredentials);

        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        analyzeDocumentAndAssertExtractions(testDocument, filename, documentType);

        // Verify that a new user was created
        assertNotSame(invalidUserCredentials.getUsername(), credentialsStore.getUserCredentials().getUsername());
    }

    @Test
    public void emailDomainIsUpdatedForExistingUserIfEmailDomainWasChanged() throws IOException, JSONException, InterruptedException {
        // Upload a document to make sure we have a valid user
        EncryptedCredentialsStore credentialsStore = new EncryptedCredentialsStore(
                getTargetContext().getSharedPreferences("GiniTests", Context.MODE_PRIVATE), getTargetContext());
        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, "example.com").
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                setCredentialsStore(credentialsStore).
                build();

        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        analyzeDocumentAndAssertExtractions(testDocument, filename, documentType);

        // Create another Gini instance with a new email domain (to simulate an app update)
        // and verify that the new email domain is used
        String newEmailDomain = "beispiel.com";
        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, newEmailDomain).
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                setCredentialsStore(credentialsStore).
                build();

        analyzeDocumentAndAssertExtractions(testDocument, filename, documentType);

        UserCredentials newUserCredentials = credentialsStore.getUserCredentials();
        assertEquals(newEmailDomain, extractEmailDomain(newUserCredentials.getUsername()));
    }

    @Test
    public void publicKeyPinningWithMatchingPublicKey() throws Exception {
        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, "example.com").
                setNetworkSecurityConfigResId(net.gini.android.test.R.xml.network_security_config).
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                build();

        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        analyzeDocumentAndAssertExtractions(testDocument, filename, documentType);
    }

    @Test
    public void publicKeyPinningWithCustomCache() throws Exception {
        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, "example.com").
                setNetworkSecurityConfigResId(net.gini.android.test.R.xml.network_security_config).
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                setCache(new NoCache()).
                build();

        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        analyzeDocumentAndAssertExtractions(testDocument, filename, documentType);
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void publicKeyPinningWithWrongPublicKey() throws Exception {
        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, "example.com").
                setNetworkSecurityConfigResId(net.gini.android.test.R.xml.wrong_network_security_config).
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                build();

        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        final DocumentTaskManager documentTaskManager = gini.getDocumentTaskManager();

        final Task<Document> upload = documentTaskManager.createDocument(testDocument, filename, documentType);
        final Task<Document> processDocument = upload.onSuccessTask(new Continuation<Document, Task<Document>>() {
            @Override
            public Task<Document> then(Task<Document> task) throws Exception {
                Document document = task.getResult();
                return documentTaskManager.pollDocument(document);
            }
        });

        final Task<ExtractionsContainer> retrieveExtractions = processDocument.onSuccessTask(
                new Continuation<Document, Task<ExtractionsContainer>>() {
                    @Override
                    public Task<ExtractionsContainer> then(Task<Document> task) throws Exception {
                        return documentTaskManager.getAllExtractions(task.getResult());
                    }
                });

        retrieveExtractions.waitForCompletion();
        if (retrieveExtractions.isFaulted()) {
            Log.e("TEST", Log.getStackTraceString(retrieveExtractions.getError()));
        }

        assertTrue("extractions shouldn't have succeeded", retrieveExtractions.isFaulted());
    }

    @Test
    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void publicKeyPinningWithMultiplePublicKeys() throws Exception {
        gini = new GiniBuilder(getTargetContext(), clientId, clientSecret, "example.com").
                setNetworkSecurityConfigResId(net.gini.android.test.R.xml.multiple_keys_network_security_config).
                setApiBaseUrl(apiUriAccounting).
                setGiniApiType(GiniApiType.ACCOUNTING).
                setUserCenterApiBaseUrl(userCenterUri).
                setConnectionTimeoutInMs(60000).
                build();

        final byte[] testDocument = TestUtils.createByteArray(testDocumentAsStream);
        analyzeDocumentAndAssertExtractions(testDocument, filename, documentType);
    }

    private String extractEmailDomain(String email) {
        String[] components = email.split("@");
        if (components.length > 1) {
            return components[1];
        }
        return "";
    }

    private Map<Document, Map<String, SpecificExtraction>> analyzeDocumentAndAssertExtractions(byte[] documentBytes, String filename, DocumentTaskManager.DocumentType documentType)
            throws InterruptedException, JSONException {
        final DocumentTaskManager documentTaskManager = gini.getDocumentTaskManager();

        final Task<Document> upload = documentTaskManager.createDocument(documentBytes, filename, documentType);
        final Task<Document> processDocument = upload.onSuccessTask(new Continuation<Document, Task<Document>>() {
            @Override
            public Task<Document> then(Task<Document> task) throws Exception {
                Document document = task.getResult();
                return documentTaskManager.pollDocument(document);
            }
        });

        final Task<ExtractionsContainer> retrieveExtractions = processDocument.onSuccessTask(
                new Continuation<Document, Task<ExtractionsContainer>>() {
                    @Override
                    public Task<ExtractionsContainer> then(Task<Document> task) throws Exception {
                        return documentTaskManager.getAllExtractions(task.getResult());
                    }
                });

        retrieveExtractions.waitForCompletion();
        if (retrieveExtractions.isFaulted()) {
            Log.e("TEST", Log.getStackTraceString(retrieveExtractions.getError()));
        }

        assertFalse("extractions should have succeeded", retrieveExtractions.isFaulted());

        final Map<String, SpecificExtraction> extractions = retrieveExtractions.getResult().getSpecificExtractions();

        assertEquals("Amount to pay should be found", "1.00:EUR", extractions.get("amountToPay").getValue());
        assertEquals("Payee should be found", "Uno Flüchtlingshilfe", extractions.get("senderName").getValue());

        return Collections.singletonMap(upload.getResult(), extractions);
    }
}
