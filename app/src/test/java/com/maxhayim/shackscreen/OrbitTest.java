package com.maxhayim.shackscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class OrbitTest {

    /** ISS elements as CelesTrak serves them (OMM). */
    private static JSONObject iss() throws Exception {
        return new JSONObject("{\"EPOCH\":\"2026-10-19T03:58:00.000\",\"MEAN_MOTION\":15.5,\"ECCENTRICITY\":0.0005,"
                + "\"INCLINATION\":51.64,\"RA_OF_ASC_NODE\":100.0,\"ARG_OF_PERICENTER\":90.0,\"MEAN_ANOMALY\":0.0,"
                + "\"MEAN_MOTION_DOT\":0.0001}");
    }

    @Test
    public void lowOrbitPassesAndGeostationaryDoesNot() throws Exception {
        assertTrue(new Orbit(iss()).passes());
        JSONObject geo = iss().put("MEAN_MOTION", 1.0027);
        assertFalse(new Orbit(geo).passes());
    }

    @Test
    public void elevationStaysPhysical() throws Exception {
        Orbit o = new Orbit(iss());
        long t0 = 1792454100000L;
        for (int k = 0; k < 1440; k++) {
            double el = o.elevation(40.7, -74.0, t0 + k * 60_000L);
            assertTrue(el >= -90 && el <= 90);
        }
    }

    @Test
    public void issPassesAFewTimesADayAtMidLatitudes() throws Exception {
        Orbit o = new Orbit(iss());
        List<Orbit.Pass> passes = o.passes(40.7, -74.0, 1792454100000L, 24, 0);
        // The ISS is above the horizon of a 40°N site roughly 4 to 7 times a day.
        assertTrue("passes: " + passes.size(), passes.size() >= 3 && passes.size() <= 8);
        for (Orbit.Pass p : passes) {
            long minutes = (p.set - p.rise) / 60_000;
            assertTrue("pass length " + minutes, minutes >= 1 && minutes <= 12);
            assertTrue(p.maxEl > 0 && p.maxEl <= 90);
        }
    }

    @Test
    public void readsFractionalEpochSeconds() throws Exception {
        JSONObject a = iss().put("EPOCH", "2026-10-19T03:58:00.500");
        // Half a second later epoch: the same instant sees the satellite slightly "earlier" in its orbit.
        assertEquals(new Orbit(iss()).elevation(0, 0, 1792454100000L),
                new Orbit(a).elevation(0, 0, 1792454100500L), 0.01);
    }
}
