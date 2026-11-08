package es.josxtm.telecontrol.community;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.LinkedHashMap;

public final class RecordRulesTest {
    @Test public void exactIdentifierPreservesLeadingZerosAndWhitespace() throws Exception {
        LinkedHashMap<String, String> record = new LinkedHashMap<>();
        record.put("syntheticCode", "001"); assertEquals("001", LocalStore.identifier(record, "syntheticCode"));
        record.put("syntheticCode", " SYN-A "); assertEquals(" SYN-A ", LocalStore.identifier(record, "syntheticCode"));
    }
    @Test public void missingBlankNullOrOversizedIdentifierRejected() throws Exception {
        LinkedHashMap<String, String> record = new LinkedHashMap<>();
        for (String invalid : new String[]{null, "", "  ", "X".repeat(257)}) {
            record.put("id", invalid);
            try { LocalStore.identifier(record, "id"); fail(); } catch (IOException expected) { }
        }
        try { LocalStore.identifier(record, "other"); fail(); } catch (IOException expected) { }
    }
    @Test public void noteLimitAllowsEmptyAndUnicodeAndRejectsOversized() throws Exception {
        LocalStore.validateBody(""); LocalStore.validateBody("Synthetic α\n");
        LocalStore.validateBody("x".repeat(JsonCodec.MAX_TEXT));
        try { LocalStore.validateBody("x".repeat(JsonCodec.MAX_TEXT + 1)); fail(); } catch (IOException expected) { }
    }
}
