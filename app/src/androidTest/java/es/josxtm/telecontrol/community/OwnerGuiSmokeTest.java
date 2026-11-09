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
        final WebView[] current = {null};
        final String[] currentUrl = {null};
        getInstrumentation().runOnMainSync(() -> {
            current[0] = findWebView(home.getWindow().getDecorView());
            if (current[0] != null) currentUrl[0] = current[0].getUrl();
        });
        WebView web = current[0];
        assertNotNull("Original WebView launcher must be present", web);
        assertNotNull("Original homepage URL must be loaded", currentUrl[0]);
        assertTrue("Homepage must be served through the local asset origin",
            currentUrl[0].startsWith("https://appassets.androidplatform.net/assets/"));
        boolean ready = false;
        for (int i = 0; i < 12 && !ready; i++) {
            CountDownLatch done = new CountDownLatch(1);
            final boolean[] valid = {false};
            getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(
                "Boolean(document.getElementById('home') && document.getElementById('open-search') && document.getElementById('home-mango') && document.getElementById('config') && document.getElementById('detail') && Array.from(document.querySelectorAll('#home .logos img')).length === 2 && Array.from(document.querySelectorAll('#home .logos img')).every(img => img.complete && img.naturalWidth > 0))",
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
