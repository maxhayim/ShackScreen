package com.maxhayim.shackscreen;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Satellite pass prediction from orbital elements (CelesTrak OMM, as OpenHamClock serves them).
 * A Kepler orbit with the J2 secular drift: lighter than SGP4 and, for low-orbit amateur
 * satellites with elements under a day old, within seconds of it, which is plenty for pass times.
 */
final class Orbit {

    private static final double MU = 398600.4418, RE = 6378.137, J2 = 1.08262668e-3, F = 1 / 298.257223563;

    private final double n0, a, e, i, raan0, argp0, m0, ndot, epochMs, raanDot, argpDot, mDot;

    /** A predicted pass: rise and set times (epoch ms) and the highest elevation in degrees. */
    static final class Pass {
        final long rise, set;
        final double maxEl;

        Pass(long rise, long set, double maxEl) {
            this.rise = rise;
            this.set = set;
            this.maxEl = maxEl;
        }
    }

    Orbit(JSONObject omm) throws ParseException, JSONException {
        n0 = omm.getDouble("MEAN_MOTION") * 2 * Math.PI / 86400;
        e = omm.getDouble("ECCENTRICITY");
        i = Math.toRadians(omm.getDouble("INCLINATION"));
        raan0 = Math.toRadians(omm.getDouble("RA_OF_ASC_NODE"));
        argp0 = Math.toRadians(omm.getDouble("ARG_OF_PERICENTER"));
        m0 = Math.toRadians(omm.getDouble("MEAN_ANOMALY"));
        ndot = omm.optDouble("MEAN_MOTION_DOT", 0); // rev/day², already halved as in a TLE
        epochMs = parseEpoch(omm.getString("EPOCH"));
        a = Math.cbrt(MU / (n0 * n0));
        double p = a * (1 - e * e), k = J2 * n0 * (RE / p) * (RE / p), ci = Math.cos(i);
        raanDot = -1.5 * k * ci;
        argpDot = 0.75 * k * (5 * ci * ci - 1);
        mDot = n0 + 0.75 * k * Math.sqrt(1 - e * e) * (3 * ci * ci - 1);
    }

    /** Geostationary and other high orbits sit still or crawl; they don't make passes. */
    boolean passes() {
        return n0 * 86400 / (2 * Math.PI) >= 1.5;
    }

    /** Elevation in degrees above the observer's horizon at time t (epoch ms). */
    double elevation(double latDeg, double lonDeg, long t) {
        double dt = (t - epochMs) / 1000.0, days = dt / 86400;
        double raan = raan0 + raanDot * dt, argp = argp0 + argpDot * dt;
        double m = m0 + mDot * dt + 2 * Math.PI * ndot * days * days;
        double ea = m;
        for (int k = 0; k < 8; k++) ea -= (ea - e * Math.sin(ea) - m) / (1 - e * Math.cos(ea));
        double x = a * (Math.cos(ea) - e), y = a * Math.sqrt(1 - e * e) * Math.sin(ea);
        double cO = Math.cos(raan), sO = Math.sin(raan), cw = Math.cos(argp), sw = Math.sin(argp);
        double ci = Math.cos(i), si = Math.sin(i);
        double X = (cO * cw - sO * sw * ci) * x + (-cO * sw - sO * cw * ci) * y;
        double Y = (sO * cw + cO * sw * ci) * x + (-sO * sw + cO * cw * ci) * y;
        double Z = (sw * si) * x + (cw * si) * y;
        // Earth-fixed: turn by Greenwich sidereal time.
        double jd = t / 86400000.0 + 2440587.5;
        double g = Math.toRadians((280.46061837 + 360.98564736629 * (jd - 2451545.0)) % 360);
        double xe = Math.cos(g) * X + Math.sin(g) * Y, ye = -Math.sin(g) * X + Math.cos(g) * Y;
        // The observer on the WGS84 ellipsoid at sea level.
        double lat = Math.toRadians(latDeg), lon = Math.toRadians(lonDeg);
        double e2 = F * (2 - F), sl = Math.sin(lat), cl = Math.cos(lat);
        double nRad = RE / Math.sqrt(1 - e2 * sl * sl);
        double ox = nRad * cl * Math.cos(lon), oy = nRad * cl * Math.sin(lon), oz = nRad * (1 - e2) * sl;
        double rx = xe - ox, ry = ye - oy, rz = Z - oz;
        double up = cl * Math.cos(lon) * rx + cl * Math.sin(lon) * ry + sl * rz;
        return Math.toDegrees(Math.asin(up / Math.sqrt(rx * rx + ry * ry + rz * rz)));
    }

    /** Passes that rise (or are already up) between now and now + hours, highest point at least minEl. */
    List<Pass> passes(double lat, double lon, long now, int hours, double minEl) {
        List<Pass> out = new ArrayList<>();
        long step = 60_000, end = now + hours * 3_600_000L;
        boolean up = elevation(lat, lon, now) > 0;
        long rise = up ? now : 0;
        double max = up ? elevation(lat, lon, now) : -90;
        for (long t = now + step; t <= end; t += step) {
            double el = elevation(lat, lon, t);
            if (!up && el > 0) {
                up = true;
                rise = edge(lat, lon, t - step, t);
                max = el;
            } else if (up && el > 0) {
                max = Math.max(max, el);
            } else if (up) {
                up = false;
                if (max >= minEl) out.add(new Pass(rise, edge(lat, lon, t, t - step), max));
            }
        }
        return out;
    }

    /** The second when the satellite crosses the horizon, between below and above (either order). */
    private long edge(double lat, double lon, long below, long above) {
        for (int k = 0; k < 6; k++) {
            long mid = (below + above) / 2;
            if (elevation(lat, lon, mid) > 0) above = mid;
            else below = mid;
        }
        return above;
    }

    private static long parseEpoch(String s) throws ParseException {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        long ms = f.parse(s.substring(0, 19)).getTime();
        if (s.length() > 19 && s.charAt(19) == '.') ms += Math.round(Double.parseDouble("0" + s.substring(19)) * 1000);
        return ms;
    }
}
