package com.maxhayim.shackscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class WidgetsTest {

    @Test
    public void readsGridSquares() {
        double[] fn31 = Widgets.grid("FN31");
        assertEquals(41.5, fn31[0], 1e-9);
        assertEquals(-73.0, fn31[1], 1e-9);
        double[] fn31pr = Widgets.grid("fn31pr");
        assertEquals(41.729, fn31pr[0], 0.01);
        assertEquals(-72.708, fn31pr[1], 0.01);
        assertNull(Widgets.grid("ZZ99"));
    }

    @Test
    public void readsLatLon() {
        double[] p = Widgets.parseLocation("40.7, -74.0");
        assertNotNull(p);
        assertEquals(40.7, p[0], 1e-9);
        assertEquals(-74.0, p[1], 1e-9);
        assertNull(Widgets.parseLocation("200, 10"));
    }

    @Test
    public void measuresNauticalMiles() {
        // One degree of latitude is 60 nm.
        assertEquals(60, Widgets.nm(40, -74, 41, -74), 0.2);
    }
}
