package com.maxhayim.shackscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdaterTest {

    @Test
    public void comparesVersionsByNumber() {
        assertTrue(Updater.compare("0.10.0", "0.9.9") > 0);
        assertTrue(Updater.compare("1.0.0", "0.99.0") > 0);
        assertTrue(Updater.compare("0.2.0", "0.2.1") < 0);
        assertEquals(0, Updater.compare("0.2", "0.2.0"));
        assertEquals(0, Updater.compare("0.2.0", "0.2.0"));
    }

    @Test
    public void toleratesJunkInVersions() {
        assertTrue(Updater.compare("0.3.0-beta", "0.2.0") > 0);
        assertEquals(0, Updater.compare("", ""));
    }
}
