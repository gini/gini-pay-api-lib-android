package net.gini.android;

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.NoCache;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RequestQueueBuilderTest {

    @Test
    public void testCreateDefaultRequestQueue() throws Exception {
        RequestQueueBuilder requestQueueBuilder = new RequestQueueBuilder(getTargetContext());
        RequestQueue requestQueue = requestQueueBuilder.build();

        assertNotNull(requestQueue);
    }

    @Test
    public void testCacheConfiguration() {
        RequestQueueBuilder requestQueueBuilder = new RequestQueueBuilder(getTargetContext());
        NoCache cache = new NoCache();
        RequestQueue requestQueue = requestQueueBuilder
                .setCache(cache)
                .build();

        assertSame(cache, requestQueue.getCache());
    }

}
