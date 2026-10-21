package com.maxhayim.shackscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class SyncTest {

    private static JSONObject v(String name) throws Exception {
        return new JSONObject().put("name", name);
    }

    private static JSONObject item(JSONObject value, long t) throws Exception {
        return value == null ? new JSONObject().put("t", t).put("d", true) : new JSONObject().put("t", t).put("v", value);
    }

    /** base as it is after a sync that saw these values at these times. */
    private static JSONObject base(Object... keyValueTime) throws Exception {
        JSONObject b = new JSONObject();
        for (int i = 0; i < keyValueTime.length; i += 3) {
            b.put((String) keyValueTime[i], new JSONObject().put("h", Sync.hash((JSONObject) keyValueTime[i + 1])).put("t", keyValueTime[i + 2]));
        }
        return b;
    }

    @Test
    public void joiningTvTakesOthersAndAddsItsOwn() throws Exception {
        Map<String, JSONObject> local = new HashMap<>();
        local.put("p|mine", v("Mine"));
        local.put("loc", v("FL"));
        JSONObject pending = new JSONObject().put("p|mine", 1).put("loc", 1); // a joining TV's changes count as oldest
        JSONObject remote = new JSONObject().put("p|theirs", item(v("Theirs"), 500)).put("loc", item(v("NY"), 500));
        Sync.Merge m = Sync.merge(local, new JSONObject(), pending, remote, false, 1000, "test");
        assertEquals("Theirs", m.incoming.get("p|theirs").getString("name"));
        assertEquals("NY", m.incoming.get("loc").getString("name")); // the others' location wins
        assertTrue(m.upload);
        assertEquals("Mine", remote.getJSONObject("p|mine").getJSONObject("v").getString("name"));
    }

    @Test
    public void localChangeIsSent() throws Exception {
        Map<String, JSONObject> local = new HashMap<>();
        local.put("a|1", v("New name"));
        JSONObject remote = new JSONObject().put("a|1", item(v("Old"), 100));
        Sync.Merge m = Sync.merge(local, base("a|1", v("Old"), 100L), new JSONObject().put("a|1", 900), remote, false, 1000, "test");
        assertTrue(m.upload);
        assertTrue(m.incoming.isEmpty());
        assertEquals("New name", remote.getJSONObject("a|1").getJSONObject("v").getString("name"));
        assertEquals(1000, remote.getJSONObject("a|1").getLong("t"));
    }

    @Test
    public void remoteChangeIsTaken() throws Exception {
        Map<String, JSONObject> local = new HashMap<>();
        local.put("a|1", v("Old"));
        JSONObject remote = new JSONObject().put("a|1", item(v("Changed elsewhere"), 700));
        Sync.Merge m = Sync.merge(local, base("a|1", v("Old"), 100L), new JSONObject(), remote, false, 1000, "test");
        assertFalse(m.upload);
        assertEquals("Changed elsewhere", m.incoming.get("a|1").getString("name"));
    }

    @Test
    public void laterChangeWins() throws Exception {
        Map<String, JSONObject> local = new HashMap<>();
        local.put("a|1", v("Here"));
        // Changed here at 600, elsewhere at 700: elsewhere wins.
        JSONObject remote = new JSONObject().put("a|1", item(v("There"), 700));
        Sync.Merge m = Sync.merge(local, base("a|1", v("Old"), 100L), new JSONObject().put("a|1", 600), remote, false, 1000, "test");
        assertEquals("There", m.incoming.get("a|1").getString("name"));
        assertFalse(m.upload);
        // Changed here at 800: here wins.
        local.put("a|1", v("Here"));
        remote = new JSONObject().put("a|1", item(v("There"), 700));
        m = Sync.merge(local, base("a|1", v("Old"), 100L), new JSONObject().put("a|1", 800), remote, false, 1000, "test");
        assertTrue(m.incoming.isEmpty());
        assertTrue(m.upload);
        assertEquals("Here", remote.getJSONObject("a|1").getJSONObject("v").getString("name"));
    }

    @Test
    public void deletionsTravelBothWays() throws Exception {
        // Deleted elsewhere.
        Map<String, JSONObject> local = new HashMap<>();
        local.put("l|p|a|host", v("login"));
        JSONObject remote = new JSONObject().put("l|p|a|host", item(null, 700));
        Sync.Merge m = Sync.merge(local, base("l|p|a|host", v("login"), 100L), new JSONObject(), remote, false, 1000, "test");
        assertTrue(m.deleted.contains("l|p|a|host"));
        // Deleted here: a marker goes to the others.
        remote = new JSONObject().put("p|x", item(v("X"), 100));
        m = Sync.merge(new HashMap<>(), base("p|x", v("X"), 100L), new JSONObject().put("p|x", 900), remote, false, 1000, "test");
        assertTrue(m.upload);
        assertTrue(remote.getJSONObject("p|x").getBoolean("d"));
    }

    @Test
    public void nothingChangedSendsNothing() throws Exception {
        Map<String, JSONObject> local = new HashMap<>();
        local.put("a|1", v("Same"));
        JSONObject remote = new JSONObject().put("a|1", item(v("Same"), 100));
        Sync.Merge m = Sync.merge(local, base("a|1", v("Same"), 100L), new JSONObject(), remote, false, 1000, "test");
        assertFalse(m.upload);
        assertTrue(m.incoming.isEmpty());
        assertTrue(m.deleted.isEmpty());
    }
}
