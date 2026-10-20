# Icons

ShackScreen's icons. The app loads the application icons straight from this folder, so a new or better icon here reaches every TV and tablet within a week, with no new APK.

## Application icons

| Folder | Used when |
|---|---|
| [`color/`](color) | Settings → Appearance → Application icons: **Colour** (default) |
| [`mono/`](mono) | Settings → Appearance → Application icons: **Black and white** |

- **Size:** 512 × 512 px PNG (the app draws them at 192 px; 512 keeps them sharp on 4K screens).
- **Shape:** square and **full-bleed**: fill the whole canvas, edge to edge. No rounded corners, no transparent border, no drop shadow. The app rounds the corners itself.
- **Name:** the application's id from [`catalog.json`](../app/src/main/assets/catalog.json), such as `gmrshub.png`.
- **Black and white:** greys only. If an app has no `mono/` icon, its colour one is shown in grey.
- **Smallest accepted:** 96 × 96 px. Anything smaller is ignored.

The icons here now are what ShackScreen showed before the set existed: each site's own icon, or a category symbol where the site has none. Replace them freely.

| File | Application | Category |
|---|---|---|
| `aprsfi.png` | APRS.fi | Ham radio |
| `openhamclock.png` | OpenHamClock | Ham radio |
| `pskreporter.png` | PSKReporter | Ham radio |
| `repeaterbook.png` | RepeaterBook | Ham radio |
| `shackboard.png` | ShackBoard | Ham radio |
| `gmrshub.png` | GMRS Hub | GMRS |
| `mygmrs.png` | myGMRS | GMRS |
| `amsatstatus.png` | AMSAT status | Satellites |
| `heavensabove.png` | Heavens-Above | Satellites |
| `isstracker.png` | ISS Tracker | Satellites |
| `n2yo.png` | N2YO | Satellites |
| `satnogs.png` | SatNOGS | Satellites |
| `satellitemap.png` | Satellite map | Satellites |
| `tinygs.png` | TinyGS | Satellites |
| `meshcoremap.png` | MeshCore Map | Mesh |
| `meshmap.png` | MeshMap | Mesh |
| `meshmonitor.png` | MeshMonitor | Mesh |
| `meshtasticnode.png` | Meshtastic node | Mesh |
| `broadcastify.png` | Broadcastify | SDR & scanners |
| `kiwisdr.png` | KiwiSDR map | SDR & scanners |
| `websdr.png` | WebSDR | SDR & scanners |
| `adsbexchange.png` | ADS-B Exchange | Aviation |
| `flightaware.png` | FlightAware | Aviation |
| `flightradar24.png` | Flightradar24 | Aviation |
| `tar1090.png` | My ADS-B receiver (tar1090) | Aviation |
| `adsbfi.png` | adsb.fi | Aviation |
| `adsblol.png` | adsb.lol | Aviation |
| `marinetraffic.png` | MarineTraffic | Marine |
| `aiscatcher.png` | My AIS receiver (AIS-catcher) | Marine |
| `myshiptracking.png` | MyShipTracking | Marine |
| `vesselfinder.png` | VesselFinder | Marine |
| `blitzortung.png` | Lightning map | Weather |
| `windy.png` | Windy | Weather |
| `custom.png` | Other web page | Other |

`custom.png` is **Other web page**.

## ShackScreen itself

In [`app/`](app):

| File | Size | Used for |
|---|---|---|
| `shackscreen-color.png` / `-mono.png` | 512 × 512 px | App icon, the logo on the dashboard and in All applications, the README |
| `tv-banner-color.png` / `-mono.png` | 640 × 360 px (1280 × 720 for a sharper master) | The banner in the Android TV / Google TV / Fire TV app row |

The app icon can have rounded corners (it's shown as is). These are copied into the app when it's built, so a new one needs a new release.

## Symbols

[`symbols/`](symbols): the line icons for categories, widgets and settings, as SVG on a 24 × 24 grid with 2 px strokes. They are drawn white on the category colours, or tinted to the theme.
