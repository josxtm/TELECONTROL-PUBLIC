package es.josxtm.telecontrol.community;

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

@SuppressWarnings("deprecation")
public final class ActivitySmokeTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public ActivitySmokeTest() { super(MainActivity.class); }
    private MainActivity activity;
    @Override protected void setUp() throws Exception {
        super.setUp();
        android.content.Context context = getInstrumentation().getTargetContext();
        try (LocalStore store = new LocalStore(context)) {
            store.importCatalog(new java.io.StringReader("[{\"id\":\"SYN-UI-001\",\"label\":\"Synthetic UI fixture\"}]"), "id");
        }
        activity = getActivity(); waitIdle();
    }
    private void waitIdle() throws Exception {
        for (int i = 0; i < 200; i++) {
            getInstrumentation().waitForIdleSync();
            final boolean[] processing = {false};
            getInstrumentation().runOnMainSync(() -> processing[0] = contains(activity.getWindow().getDecorView(), "Procesando en el dispositivo…"));
            if (!processing[0]) return;
            Thread.sleep(25);
        }
        fail("Operation timed out");
    }
    private static boolean contains(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (contains(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    private static View find(View view, String text, boolean input) {
        if (input && view instanceof EditText && text.contentEquals(((EditText) view).getHint())) return view;
        if (!input && view instanceof Button && text.contentEquals(((Button) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = find(((ViewGroup) view).getChildAt(i), text, input); if (result != null) return result;
        }
        return null;
    }
    public void testLaunchSearchSaveNoteAndDiary() throws Throwable {
        getInstrumentation().runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView();
            assertTrue(contains(root, "TELECONTROL Público"));
            ((EditText) find(root, "Identificador exacto", true)).setText("SYN-UI-001");
            find(root, "Buscar", false).performClick();
        });
        waitIdle();
        getInstrumentation().runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView(); assertTrue(contains(root, "Synthetic UI fixture"));
            ((EditText) find(root, "Nota local (hasta 16000 caracteres)", true)).setText("Synthetic UI note");
            find(root, "Guardar nota", false).performClick();
        });
        waitIdle();
        getInstrumentation().runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView();
            ((EditText) find(root, "Nueva entrada del diario", true)).setText("Synthetic UI diary");
            find(root, "Añadir entrada", false).performClick();
        });
        waitIdle();
        try (LocalStore store = new LocalStore(getInstrumentation().getTargetContext())) {
            assertEquals("Synthetic UI note", store.note("SYN-UI-001"));
            assertEquals("Synthetic UI diary", store.journal(Long.MAX_VALUE).get(0).body);
        }
    }
    public void testCanceledSystemPickerPreservesCatalog() throws Throwable {
        getInstrumentation().runOnMainSync(() -> activity.onActivityResult(11, android.app.Activity.RESULT_CANCELED, new Intent()));
        try (LocalStore store = new LocalStore(getInstrumentation().getTargetContext())) {
            assertNotNull(store.find("SYN-UI-001"));
        }
    }
    @Override protected void tearDown() throws Exception {
        super.tearDown();
        getInstrumentation().getTargetContext().deleteDatabase("community-local.db");
    }
}
