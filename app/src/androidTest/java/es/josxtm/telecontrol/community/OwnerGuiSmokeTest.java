package es.josxtm.telecontrol.community;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Checks the actual owner-approved WebView launcher, never a generic mock form.
 * Synthetic empty catalog only; no CT data, IPs or credentials.
 */
public final class OwnerGuiSmokeTest extends ActivityInstrumentationTestCase2<es.josxtm.telecontrol.MainActivity> {
    public OwnerGuiSmokeTest() {
        super(es.josxtm.telecontrol.MainActivity.class);
    }

    private static WebView findWebView(View root) {
        if (root instanceof WebView) return (WebView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    public void testApprovedHomeAndMangoAccessExist() throws Exception {
        es.josxtm.telecontrol.MainActivity home = getActivity();
        getInstrumentation().waitForIdleSync();
        WebView web = findWebView(home.getWindow().getDecorView());
        assertNotNull("Original WebView launcher must be present", web);
        assertNotNull("Original homepage URL must be loaded", web.getUrl());
        assertTrue("Homepage must be served through the private local asset origin",
            web.getUrl().startsWith("https://appassets.androidplatform.net/assets/"));
        boolean ready = false;
        for (int i = 0; i < 12 && !ready; i++) {
            CountDownLatch done = new CountDownLatch(1);
            final boolean[] valid = {false};
            getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                "Boolean(document.getElementById('home') && document.getElementById('open-search') && document.getElementById('home-mango') && document.getElementById('config') && document.getElementById('detail'))",
                result -> {
                    valid[0] = "true".equals(result);
                    done.countDown();
                }
            ));
            assertTrue("WebView JavaScript evaluation never returned", done.await(4, TimeUnit.SECONDS));
            ready = valid[0];
            if (!ready) Thread.sleep(250);
        }
        assertTrue("Original owner-approved home UI landmarks not rendered", ready);
    }
}
