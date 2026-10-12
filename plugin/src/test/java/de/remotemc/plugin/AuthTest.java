package de.remotemc.plugin;

import de.remotemc.plugin.auth.LoginGuard;
import de.remotemc.plugin.auth.PairingManager;
import de.remotemc.plugin.auth.Role;
import de.remotemc.plugin.auth.TokenStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AuthTest {

    private final Logger logger = Logger.getLogger("test");

    @Test
    void rolesAreOrdered() {
        assertTrue(Role.ADMIN.atLeast(Role.MODERATOR));
        assertTrue(Role.MODERATOR.atLeast(Role.VIEWER));
        assertFalse(Role.VIEWER.atLeast(Role.MODERATOR));
        assertEquals(Role.MODERATOR, Role.parse(" Moderator "));
        assertNull(Role.parse("owner"));
    }

    @Test
    void tokensAreStoredHashedAndSurviveReload(@TempDir Path dir) throws Exception {
        File file = dir.resolve("tokens.yml").toFile();
        TokenStore store = new TokenStore(file, logger);
        String token = store.create("Pixel 8", Role.MODERATOR);

        assertTrue(token.startsWith("rmc_"));
        assertFalse(Files.readString(file.toPath()).contains(token), "plain token must not be written to disk");

        TokenStore reloaded = new TokenStore(file, logger);
        reloaded.load();
        TokenStore.Token t = reloaded.authenticate(token);
        assertNotNull(t);
        assertEquals("Pixel 8", t.name());
        assertEquals(Role.MODERATOR, t.role());
        assertNull(reloaded.authenticate(token + "x"));
        assertNull(reloaded.authenticate(null));
    }

    @Test
    void duplicateNamesGetASuffixAndRevokeWorks(@TempDir Path dir) {
        TokenStore store = new TokenStore(dir.resolve("tokens.yml").toFile(), logger);
        String a = store.create("Phone", Role.ADMIN);
        store.create("Phone", Role.ADMIN);
        assertTrue(store.nameExists("Phone (2)"));
        assertTrue(store.revoke("phone"));
        assertNull(store.authenticate(a));
        assertEquals(1, store.size());
    }

    @Test
    void pairingCodesAreSingleUse() {
        PairingManager pairing = new PairingManager();
        PairingManager.Code code = pairing.create(Role.VIEWER, 5, "console");
        assertEquals(6, code.code().length());
        assertNull(pairing.redeem("not-a-code"));
        PairingManager.Code redeemed = pairing.redeem(code.code());
        assertNotNull(redeemed);
        assertEquals(Role.VIEWER, redeemed.role());
        assertNull(pairing.redeem(code.code()));
    }

    @Test
    void manyWrongGuessesInvalidateOpenCodes() {
        PairingManager pairing = new PairingManager();
        PairingManager.Code code = pairing.create(Role.ADMIN, 5, "console");
        for (int i = 0; i < 25; i++) {
            pairing.redeem("x" + i);
        }
        assertNull(pairing.redeem(code.code()));
    }

    @Test
    void loginGuardLocksOutAfterFailures() {
        LoginGuard guard = new LoginGuard(3, 60);
        assertFalse(guard.fail("1.2.3.4"));
        assertFalse(guard.fail("1.2.3.4"));
        assertTrue(guard.fail("1.2.3.4"));
        assertTrue(guard.isLocked("1.2.3.4"));
        assertFalse(guard.isLocked("5.6.7.8"));
    }

    @Test
    void versionComparison() {
        assertTrue(UpdateChecker.isNewer("2.1.0", "2.0.0"));
        assertTrue(UpdateChecker.isNewer("2.0.10", "2.0.9"));
        assertFalse(UpdateChecker.isNewer("2.0.0", "2.0.0"));
        assertFalse(UpdateChecker.isNewer("1.9", "2.0.0"));
    }
}
