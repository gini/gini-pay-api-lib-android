package net.gini.android.authorization;

import static net.gini.android.helpers.TestUtils.areEqualURIQueries;

import static org.mockito.Mockito.verify;

import android.os.SystemClock;
import android.support.test.filters.MediumTest;
import android.test.InstrumentationTestCase;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;

import net.gini.android.GiniApiType;
import net.gini.android.requests.DefaultRetryPolicyFactory;
import net.gini.android.requests.RetryPolicyFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Date;

import bolts.Task;

@MediumTest
public class UserCenterAPICommunicatorTest extends InstrumentationTestCase {
    private UserCenterAPICommunicator apiManager;
    private RequestQueue mRequestQueue;
    private RetryPolicyFactory retryPolicyFactory;

    @Override
    public void setUp() {
        // https://code.google.com/p/dexmaker/issues/detail?id=2
        System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());
        retryPolicyFactory = new DefaultRetryPolicyFactory();
        mRequestQueue = Mockito.mock(RequestQueue.class);
        apiManager = new UserCenterAPICommunicator(mRequestQueue, "https://user.gini.net/", GiniApiType.DEFAULT, "foobar", "1234",
                retryPolicyFactory);
    }

    /**
     * Requests are executed by volley on another thread (NetworkDispatcher). This is a really dump
     * helper method which blocks the current thread so the network thread will finish and you can
     * make assertions on the network instance. Meant to be replaced by a more clever solution.
     */
    private void waitForRequests() {
        SystemClock.sleep(2);
    }

    public void testLoginClientShouldReturnTask() {
        assertNotNull(apiManager.loginClient());
    }

    public void testLoginClientRequestHasCorrectUrl() {
        apiManager.loginClient();

        final ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mRequestQueue).add(requestCaptor.capture());
        final Request request = requestCaptor.getValue();
        assertEquals("https://user.gini.net/oauth/token?grant_type=client_credentials",
                request.getUrl());
    }

    public void testLoginClientHasCorrectData() throws AuthFailureError {
        apiManager.loginClient();

        final ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mRequestQueue).add(requestCaptor.capture());
        final Request request = requestCaptor.getValue();
        assertEquals("Basic Zm9vYmFyOjEyMzQ=", request.getHeaders().get("Authorization"));
    }

    public void testLoginUserShouldReturnTask() {
        assertNotNull(apiManager.loginClient());
    }

    public void testLoginUserRequestHasCorrectUrl() {
        UserCredentials userCredentials = new UserCredentials("foobar", "1234");
        apiManager.loginUser(userCredentials);

        final ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mRequestQueue).add(requestCaptor.capture());
        final Request request = requestCaptor.getValue();
        assertEquals("https://user.gini.net/oauth/token?grant_type=password", request.getUrl());
    }


    public void testLoginUserRequestHasCorrectData() throws AuthFailureError {
        UserCredentials userCredentials = new UserCredentials("foobar", "1234");
        apiManager.loginUser(userCredentials);
        waitForRequests();

        final ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mRequestQueue).add(requestCaptor.capture());
        final Request request = requestCaptor.getValue();
        assertTrue(areEqualURIQueries("username=foobar&password=1234", new String(request.getBody())));
    }

    public void testGetGiniApiSessionUserInfoShouldReturnTask() {
        assertNotNull(apiManager.getGiniApiSessionTokenInfo(new Session("", new Date())));
    }

    public void testGetGiniApiSessionUserInfoHasCorrectUrl() {
        Session giniApiSession = new Session("example_token", new Date());
        apiManager.getGiniApiSessionTokenInfo(giniApiSession);

        final ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mRequestQueue).add(requestCaptor.capture());
        final Request request = requestCaptor.getValue();
        assertEquals("https://user.gini.net/oauth/check_token?token=example_token", request.getUrl());
    }

    public void testGetUserIdShouldReturnTask() {
        assertNotNull(apiManager.getUserId(new Session("", new Date())));
    }

    public void testGetUserIdExtractsUserIdFromResponse() throws InterruptedException {
        final String userId = "JohnDoe";
        apiManager = new UserCenterAPICommunicator(mRequestQueue, "https://user.gini.net/", GiniApiType.DEFAULT, "foobar", "1234",
                retryPolicyFactory) {
            @Override
            Task<JSONObject> getGiniApiSessionTokenInfo(Session giniApiSession) {
                JSONObject responseJson = new JSONObject(Collections.singletonMap("user_name", userId));
                return Task.forResult(responseJson);
            }
        };

        Session giniApiSession = new Session("example_token", new Date());
        Task<String> userIdTask = apiManager.getUserId(giniApiSession);
        userIdTask.waitForCompletion();

        assertEquals(userId, userIdTask.getResult());
    }

    public void testUpdateEmailShouldReturnTask() throws JSONException {
        assertNotNull(apiManager.updateEmail("exampleUserId", "beispiel.com", "example.com", new Session("example_token", new Date())));
    }

    public void testUpdateEmailHasCorrectUrl() throws JSONException {
        String userId = "exampleUserId";
        apiManager.updateEmail(userId, "1234@beispiel.com", "5678@example.com", new Session("example_token", new Date()));

        final ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mRequestQueue).add(requestCaptor.capture());
        final Request request = requestCaptor.getValue();
        assertEquals("https://user.gini.net/api/users/" + userId, request.getUrl());
    }

    public void testUpdateEmailHasCorrectData() throws JSONException, AuthFailureError, InterruptedException {
        String newEmail = "1234@beispiel.com";
        String oldEmail = "5678@example.com";
        apiManager.updateEmail("exampleUserId", newEmail, oldEmail, new Session("example_token", new Date()));

        final ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mRequestQueue).add(requestCaptor.capture());
        final Request request = requestCaptor.getValue();

        String requestBody = new String(request.getBody());
        JSONObject requestBodyJson = new JSONObject(requestBody);

        assertEquals(newEmail, requestBodyJson.getString("email"));
        assertEquals(oldEmail, requestBodyJson.getString("oldEmail"));
    }

}
