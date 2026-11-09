package es.josxtm.telecontrol.community;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Uses local synthetic status values only; never performs a network probe. */
@SuppressWarnings("deprecation")
public final class TabletVariantSmokeTest extends ActivityInstrumentationTestCase2<es.josxtm.telecontrol.MainActivity> {
    public TabletVariantSmokeTest() { super(es.josxtm.telecontrol.MainActivity.class); }

    private static WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean query(WebView web, String javascript) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] result = {false};
        getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(javascript, value -> {
            result[0] = "true".equals(value);
            latch.countDown();
        }));
        assertTrue("WebView did not evaluate the synthetic UI assertion", latch.await(5, TimeUnit.SECONDS));
        return result[0];
    }

    public void testVariantsKeepMobileOriginalAndTabletIndustrialIsolated() throws Exception {
        es.josxtm.telecontrol.MainActivity activity = getActivity();
        final WebView[] web = {null};
        getInstrumentation().runOnMainSync(
            () -> web[0] = findWebView(activity.getWindow().getDecorView()));
        assertNotNull(web[0]);
        String flavor = BuildConfig.FLAVOR;
        boolean ready = false;
        for (int i = 0; i < 24 && !ready; i++) {
            ready = query(web[0], flavor.equals("tablet")
                ? "Boolean(window.TCTabletView && document.body.classList.contains('tablet-industrial') && document.getElementById('tablet-topnav'))"
                : "Boolean(document.getElementById('home') && !document.body.classList.contains('tablet-industrial') && !document.getElementById('tablet-topnav'))");
            if (!ready) Thread.sleep(300);
        }
        assertTrue("Expected flavor-specific UI did not load", ready);
        if (!flavor.equals("tablet")) {
            assertTrue("Mobile must not activate the tablet motion controller",
                query(web[0], "typeof window.TabletMotionController === 'undefined'"));
            return;
        }
        boolean motionReady = false;
        for (int attempt = 0; attempt < 20 && !motionReady; attempt++) {
            motionReady = query(web[0],
                "Boolean(window.TabletMotionController && window.TabletMotionController.effects.length === 4)");
            if (!motionReady) Thread.sleep(250);
        }
        assertTrue("Tablet motion script did not initialize", motionReady);
        assertTrue("All four motion patterns and destinations are registered",
            query(web[0], "(function(){var c=window.TabletMotionController;" +
                "return c.transitionFor('home','weekly')==='fade-through' " +
                "&&c.transitionFor('detail','tablet-network')==='shared-x' " +
                "&&c.transitionFor('home','config')==='shared-z' " +
                "&&c.transitionFor('home','detail')==='container' " +
                "&&c.transitionFor('weekly','weekly-detail')==='container';})()"));
        assertTrue("Tablet graph must initially show no claimed measurements",
            query(web[0],
                "(function(){window.TCTabletView.open();return document.querySelectorAll('#tablet-network article.pending').length===6 && document.getElementById('tablet-status').textContent.includes('NO VERIFICADO')})()"));
        assertTrue("Synthetic verified status is reflected without a real ping",
            query(web[0],
                "(function(){window.TCTabletView.renderMeasurements({tablet:'ok',mango:'ok',router:'ok',equipment:[{name:'CC T1',status:'ok',rtt:12}]});return document.querySelectorAll('#tablet-network .net-node.mango.status-ok').length===1 && document.querySelectorAll('#tablet-network article.ok').length===1 && document.querySelectorAll('#tablet-network article.pending').length===5})()"));
        assertTrue("Synthetic failed status remains visually distinct",
            query(web[0],
                "(function(){window.TCTabletView.renderMeasurements({tablet:'ok',mango:'failed',router:'unchecked',equipment:[]});return document.querySelectorAll('#tablet-network article.ok').length===0 && document.querySelectorAll('#tablet-network article.pending').length===6})()"));
    }
}
