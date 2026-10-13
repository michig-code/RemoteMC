package de.remotemc.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AuditCleanTest {
    @Test
    void controlCharactersCannotFakeEntries() {
        assertEquals("teleport x  2026-01-01 [admin] fake", AuditLog.clean("teleport x\r\n2026-01-01 [admin] fake"));
        assertEquals("a b", AuditLog.clean("a\u001bb"));
        assertEquals("", AuditLog.clean(null));
    }
}
