package es.josxtm.telecontrol.community;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Synthetic device test of the real, non-exported MangoSSH Compose screen.
 * No profiles, CT records or external SSH destinations are required.
 */
public final class MangoComposeSmokeTest extends InstrumentationTestCase {
    private static boolean hasText(AccessibilityNodeInfo node, String value) {
        if (node == null) return false;
        CharSequence text = node.getText();
        CharSequence label = node.getContentDescription();
        if ((text != null && text.toString().contains(value))
                || (label != null && label.toString().contains(value))) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && hasText(child, value)) return true;
        }
        return false;
    }

    public void testOriginalComposeUiLaunchesInPrivateMangoProcess() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        ComponentName component = new ComponentName(context, io.github.josxtm.mangossh.MainActivity.class);
        ActivityInfo info = context.getPackageManager().getActivityInfo(component, 0);
        assertFalse("MangoSSH must be internal", info.exported);
        assertEquals(context.getPackageName() + ":mangossh", info.processName);

        Intent intent = new Intent(context, io.github.josxtm.mangossh.MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        boolean hasComposeScreens = false;
        for (int i = 0; i < 40 && !hasComposeScreens; i++) {
            Thread.sleep(250);
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            hasComposeScreens = hasText(root, "MangoSSH")
                    && hasText(root, "Conexión")
                    && hasText(root, "Diagnóstico");
        }
        assertTrue("Original MangoSSH Compose navigation not visible on Android", hasComposeScreens);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }
}
