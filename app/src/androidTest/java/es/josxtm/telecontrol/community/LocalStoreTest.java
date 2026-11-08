package es.josxtm.telecontrol.community;

import android.test.AndroidTestCase;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

/** All fixtures are generated synthetic data; exercises real Android JSON and SQLite. */
@SuppressWarnings("deprecation")
public final class LocalStoreTest extends AndroidTestCase {
    private LocalStore store;
    @Override protected void setUp() throws Exception {
        super.setUp(); getContext().deleteDatabase("synthetic-test.db"); store = new LocalStore(getContext(), "synthetic-test.db");
    }
    @Override protected void tearDown() throws Exception {
        store.close(); getContext().deleteDatabase("synthetic-test.db"); super.tearDown();
    }
    private void catalog() throws Exception {
        assertEquals(2, store.importCatalog(new StringReader("[{\"code\":\"SYN-001\",\"label\":\"Synthetic α\",\"value\":7},{\"code\":\"SYN-002\",\"active\":true,\"empty\":null}]"), "code"));
    }
    private String backup() throws Exception { StringWriter w = new StringWriter(); store.backup(w); return w.toString(); }
    private void rejected(String input) throws Exception {
        String before = backup();
        try { store.importCatalog(new StringReader(input), "code"); fail("Must reject synthetic invalid input"); }
        catch (java.io.IOException | android.database.SQLException | IllegalStateException expected) { }
        assertEquals(before, backup());
    }
    public void testImportExactIndexAndScalarFields() throws Exception {
        catalog(); assertEquals("Synthetic α", store.find("SYN-001").get("label"));
        assertEquals("7", store.find("SYN-001").get("value"));
        assertEquals("true", store.find("SYN-002").get("active"));
        assertNull(store.find("SYN-002").get("empty")); assertNull(store.find("SYN"));
        assertNull(store.find("' OR 1=1 --")); assertEquals("code", store.setting("identifier"));
    }
    public void testInvalidImportRollsBackCatalogNotesAndSettings() throws Exception {
        catalog(); store.saveNote("SYN-001", "Synthetic note"); store.setting("visible", "{\"label\":\"\"}");
        rejected("[{\"code\":\"SYN-X\"},{\"code\":\"SYN-X\"}]");
        rejected("[{\"code\":\"SYN-X\"},{\"other\":\"missing\"}]");
        rejected("[{\"code\":\"SYN-X\",\"nested\":{}}]"); rejected("[{\"code\":\"SYN-X\",\"nested\":[]}]");
        rejected("[{\"code\":\"SYN-X\",\"code\":\"SYN-Y\"}]");
        rejected("[{\"code\":null}]"); rejected("[{\"code\":\"  \"}]"); rejected("[]");
    }
    public void testMalformedAndTrailingInputRollBack() throws Exception {
        catalog(); rejected("[{\"code\":\"SYN-X\"}"); rejected("[{\"code\":\"SYN-X\"}] {}");
        rejected("/*synthetic*/[]"); rejected("[{code:'SYN-X'}]");
    }
    public void testSuccessfulReplacementRemovesNotesPreservesJournal() throws Exception {
        catalog(); store.saveNote("SYN-001", "Synthetic note"); store.addJournal("Synthetic diary");
        store.importCatalog(new StringReader("[{\"code\":\"SYN-NEW\"}]"), "code");
        assertNull(store.find("SYN-001")); assertEquals("", store.note("SYN-001")); assertEquals(1, store.journal(Long.MAX_VALUE).size());
    }
    public void testBackupRestoreRoundTripAndReopen() throws Exception {
        catalog(); store.saveNote("SYN-001", "Synthetic note\nα"); store.addJournal("Synthetic diary");
        store.setting("visible", "{\"label\":\"\"}"); String snapshot = backup();
        store.importCatalog(new StringReader("[{\"id\":\"SYN-OTHER\"}]"), "id");
        store.restore(new StringReader(snapshot)); assertEquals(snapshot, backup());
        store.close(); store = new LocalStore(getContext(), "synthetic-test.db");
        assertEquals(snapshot, backup()); assertEquals("Synthetic note\nα", store.note("SYN-001"));
        assertEquals("Synthetic diary", store.journal(Long.MAX_VALUE).get(0).body);
    }
    public void testInvalidBackupNeverReplacesData() throws Exception {
        catalog(); store.saveNote("SYN-001", "Synthetic note"); store.addJournal("Synthetic diary");
        String snapshot = backup();
        for (String broken : new String[]{snapshot.substring(0, snapshot.length() - 5),
                snapshot.replace("\"version\":1", "\"version\":999"),
                snapshot.replace("\"id\":\"SYN-001\"", "\"id\":\"SYN-UNKNOWN\""),
                snapshot.replace("\"telecontrol-community\"", "\"other-public-app\""), snapshot + "{}"}) {
            try { store.restore(new StringReader(broken)); fail("Must reject invalid snapshot"); }
            catch (java.io.IOException | android.database.SQLException | IllegalStateException expected) { }
            assertEquals(snapshot, backup());
        }
    }
    public void testEmptyDatabaseBackupAndEmptySelection() throws Exception {
        String empty = backup(); store.restore(new StringReader(empty)); assertEquals(empty, backup());
        catalog(); store.setting("visible", "{}"); String snapshot = backup(); store.restore(new StringReader(snapshot)); assertEquals(snapshot, backup());
    }
    public void testJournalPaginationAndLimits() throws Exception {
        for (int i = 0; i < 30; i++) store.addJournal("Synthetic entry " + i);
        List<LocalStore.Entry> first = store.journal(Long.MAX_VALUE); assertEquals(25, first.size());
        List<LocalStore.Entry> next = store.journal(first.get(24).id); assertEquals(5, next.size());
        assertTrue(next.get(0).id < first.get(24).id);
        try { store.addJournal(" "); fail(); } catch (java.io.IOException expected) { }
        try { store.addJournal("x".repeat(JsonCodec.MAX_TEXT + 1)); fail(); } catch (java.io.IOException expected) { }
    }
    public void testFieldAndTokenLimits() throws Exception {
        catalog(); rejected("[{\"code\":\"" + "x".repeat(257) + "\"}]");
        rejected("[{\"code\":\"SYN-X\",\"body\":\"" + "x".repeat(70000) + "\"}]");
        StringBuilder fields = new StringBuilder("[{\"code\":\"SYN-X\"");
        for (int i = 0; i < 101; i++) fields.append(",\"synthetic").append(i).append("\":0");
        rejected(fields.append("}]").toString());
    }
    public void testInputFailureRollsBackAndCanContinue() throws Exception {
        catalog(); String snapshot = backup();
        Reader broken = new Reader() {
            @Override public int read(char[] target, int offset, int length) throws java.io.IOException {
                throw new java.io.IOException("Synthetic read failure");
            }
            @Override public void close() { }
        };
        try { store.importCatalog(broken, "code"); fail(); } catch (java.io.IOException expected) { }
        assertEquals(snapshot, backup()); catalog();
    }
    public void testLargeCatalogUsesIncrementalReader() throws Exception {
        // Reader produces 20000 synthetic rows on demand, never creates a complete file in memory.
        Reader generated = new Reader() {
            private int row, position;
            private String chunk = "[";
            private boolean ended;
            @Override public int read(char[] target, int offset, int length) {
                if (length == 0) return 0;
                if (position == chunk.length()) {
                    if (ended) return -1;
                    if (row == 20000) { chunk = "]"; ended = true; }
                    else { chunk = (row == 0 ? "" : ",") + "{\"id\":\"SYN-" + row++ + "\",\"label\":\"Synthetic\"}"; }
                    position = 0;
                }
                int count = Math.min(length, chunk.length() - position); chunk.getChars(position, position + count, target, offset); position += count; return count;
            }
            @Override public void close() { }
        };
        assertEquals(20000, store.importCatalog(generated, "id")); assertNotNull(store.find("SYN-19999"));
    }
}
