package net.gini.android;

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.volley.Cache;
import com.android.volley.RetryPolicy;

import net.gini.android.authorization.Session;
import net.gini.android.authorization.SessionManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import bolts.Task;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SdkBuilderTest {

    @Test
    public void testBuilderReturnsSdkInstance() {
        SdkBuilder builder = new SdkBuilder(getTargetContext(), "clientId", "clientSecret", "@example.com");
        assertNotNull(builder.build());
    }

    @Test
    public void testBuilderReturnsCorrectConfiguredSdkInstance() {
        SdkBuilder builder = new SdkBuilder(getTargetContext(), "clientId", "clientSecret", "@example.com");
        Gini sdkInstance = builder.build();

        assertNotNull(sdkInstance.getDocumentTaskManager());
        assertNotNull(sdkInstance.getCredentialsStore());
    }

    @Test
    public void testBuilderWorksWithAlternativeSessionManager() {
        final SessionManager sessionManager = new NullSessionManager();

        final SdkBuilder builder = new SdkBuilder(getTargetContext(), sessionManager);
        final Gini sdkInstance = builder.build();

        assertNotNull(sdkInstance);
        assertNotNull(sdkInstance.getDocumentTaskManager());
        assertNotNull(sdkInstance.getCredentialsStore());
    }

    @Test
    public void testSetWrongConnectionTimeout() {
        SdkBuilder builder = new SdkBuilder(getTargetContext(), "clientId", "clientSecret", "@example.com");
        try {
            builder.setConnectionTimeoutInMs(-1);
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException exc) {
        }
    }

    @Test
    public void testSetWrongConnectionMaxNumberOfRetries() {
        SdkBuilder builder = new SdkBuilder(getTargetContext(), "clientId", "clientSecret", "@example.com");
        try {
            builder.setMaxNumberOfRetries(-1);
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException exc) {
        }
    }

    @Test
    public void testSetWrongConnectionBackOffMultiplier() {
        SdkBuilder builder = new SdkBuilder(getTargetContext(), "clientId", "clientSecret", "@example.com");
        try {
            builder.setConnectionBackOffMultiplier(-1);
            fail("IllegalArgumentException should be thrown");
        } catch (IllegalArgumentException exc) {
        }
    }

    @Test
    public void testRetryPolicyWiring() {
        SdkBuilder builder = new SdkBuilder(getTargetContext(), "clientId", "clientSecret", "@example.com");
        builder.setConnectionTimeoutInMs(3333);
        builder.setMaxNumberOfRetries(66);
        builder.setConnectionBackOffMultiplier(1.3636f);
        Gini gini = builder.build();

        final DocumentTaskManager documentTaskManager = gini.getDocumentTaskManager();
        final RetryPolicy retryPolicy = documentTaskManager.mApiCommunicator.mRetryPolicyFactory.newRetryPolicy();
        assertEquals(3333, retryPolicy.getCurrentTimeout());
        assertEquals(0, retryPolicy.getCurrentRetryCount());
    }

    @Test
    public void testVolleyCacheConfiguration() {
        SdkBuilder builder = new SdkBuilder(getTargetContext(), "clientId", "clientSecret", "@example.com");
        NullCache nullCache = new NullCache();
        builder.setCache(nullCache);
        Gini sdkInstance = builder.build();

        assertSame(sdkInstance.getDocumentTaskManager().mApiCommunicator.mRequestQueue.getCache(), nullCache);
    }

    private static final class NullCache implements Cache {

        @Override
        public Entry get(final String key) {
            return null;
        }

        @Override
        public void put(final String key, final Entry entry) {
        }

        @Override
        public void initialize() {
        }

        @Override
        public void invalidate(final String key, final boolean fullExpire) {
        }

        @Override
        public void remove(final String key) {
        }

        @Override
        public void clear() {
        }
    }

    private class NullSessionManager implements SessionManager {

        @Override
        public Task<Session> getSession() {
            return Task.forError(new UnsupportedOperationException("NullSessionManager can't create sessions"));
        }
    }
}
