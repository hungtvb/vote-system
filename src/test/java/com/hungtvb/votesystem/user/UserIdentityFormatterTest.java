package com.hungtvb.votesystem.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIdentityFormatterTest {
    @Test
    void normalizesExplicitDisplayNamesAndInitials() {
        String displayName = UserIdentityFormatter.normalizeDisplayName("  Jane   Doe  99 ");

        assertEquals("Jane Doe 99", displayName);
        assertEquals("JD", UserIdentityFormatter.initials(displayName));
    }

    @Test
    void generatesAPseudonymWhenNoPublicNameIsProvided() {
        String displayName = UserIdentityFormatter.normalizeDisplayName(null);

        assertTrue(displayName.matches("Voter [A-F0-9]{8}"));
        assertTrue(UserIdentityFormatter.initials(displayName).matches("V[A-F0-9]"));
        assertEquals("V", UserIdentityFormatter.initials(""));
    }
}
