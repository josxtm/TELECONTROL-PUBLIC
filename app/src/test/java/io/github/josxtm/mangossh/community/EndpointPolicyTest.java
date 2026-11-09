package io.github.josxtm.mangossh.community;

import org.junit.Test;
import static org.junit.Assert.*;

public class EndpointPolicyTest {
    @Test public void rejectsMissingOrMalformedParameters() {
        assertFalse(EndpointPolicy.valid("", 22, "user"));
        assertFalse(EndpointPolicy.valid("host invalid", 22, "user"));
        assertFalse(EndpointPolicy.valid("synthetic.invalid", 0, "user"));
        assertFalse(EndpointPolicy.valid("synthetic.invalid", 65536, "user"));
        assertFalse(EndpointPolicy.valid("synthetic.invalid", 22, "user bad"));
        assertTrue(EndpointPolicy.valid("synthetic.invalid", 22, "user"));
    }
    @Test public void commandAllowlistRejectsArbitraryAndChainedCommands() {
        assertTrue(EndpointPolicy.diagnostic("whoami"));
        assertTrue(EndpointPolicy.diagnostic("pwd"));
        assertFalse(EndpointPolicy.diagnostic("whoami; other"));
        assertFalse(EndpointPolicy.diagnostic("sh"));
    }
    @Test public void changedKeysNeverGainAutomaticTrust() {
        assertFalse(EndpointPolicy.trusted(null, "SHA256:sample"));
        assertTrue(EndpointPolicy.trusted("one", "one"));
        assertTrue(EndpointPolicy.changed("one", "two"));
        assertFalse(EndpointPolicy.changed(null, "two"));
    }
}
