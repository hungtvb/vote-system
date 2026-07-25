package com.hungtvb.votesystem.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserIdentityFormatterTest {
    @Test
    void derivesReadableDisplayNameAndInitialsFromEmail() {
        String displayName = UserIdentityFormatter.displayName("jane-doe_99@example.com");

        assertEquals("Jane Doe 99", displayName);
        assertEquals("JD", UserIdentityFormatter.initials(displayName));
    }

    @Test
    void fallsBackForMissingOrEmptyIdentity() {
        assertEquals("Voter", UserIdentityFormatter.displayName(null));
        assertEquals("Voter", UserIdentityFormatter.displayName("@example.com"));
        assertEquals("V", UserIdentityFormatter.initials(""));
    }
}
