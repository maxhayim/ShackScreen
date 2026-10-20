# Icons

> **Want to contribute an icon?** Read [Contributing Icons](../docs/ICONS.md): sizes, shape, a template and how to send it in.

ShackScreen's icons. The app loads the application icons straight from this folder, so a new or better icon here reaches every TV and tablet within a week, with no new APK.

## Application icons

| Folder | Used when |
|---|---|
| [`color/`](color) | Settings → Appearance → Application icons: **Colour** (default) |
| [`dark/`](dark) | **Dark**, or **Match the theme** with a dark theme: dark tile, light logo |
| [`light/`](light) | **Light**, or **Match the theme** with the Light theme: light tile, dark logo |

- **Size:** 512 × 512 px PNG (the app draws them at 192 px; 512 keeps them sharp on 4K screens).
- **Shape:** cropped exactly as the app shows them: a 512 × 512 square with **rounded corners of 136 px radius** (the app's 18 dp corner on a 68 dp tile), transparent outside the corners. Fill everything inside, edge to edge; no extra border or drop shadow. Edit the files here and keep that shape: the app shows them as they are.
- **Name:** the application's id from [`catalog.json`](../app/src/main/assets/catalog.json), such as `gmrshub.png`.
- **Dark and light:** if an app has no `dark/` or `light/` icon, its colour one is shown.
- **Smallest accepted:** 96 × 96 px. Anything smaller is ignored.

The set was drawn for ShackScreen by its author, in colour, dark and light. Better versions are welcome.

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
| `shackscreen-color.png` / `-dark.png` / `-light.png` | 512 × 512 px | App icon and the README |
| `shackscreen-tower.png` | 512 × 512 px, transparent | The tower alone, next to SHACKSCREEN on the dashboard and in All applications |
| `tv-banner-color.png` / `-dark.png` / `-light.png` | 640 × 360 px (1920 × 1080 for a sharper master) | The banner in the Android TV / Google TV / Fire TV app row |

These are the masters. A new one needs a new release, because launchers read the icons built into the app.

### What each device shows when launching ShackScreen

| Where | What it uses | In the app |
|---|---|---|
| Android 8+ tablets and launchers (Pixel, Samsung, Fire tablets, Google TV's app lists) | **Adaptive icon**: a background layer and a foreground layer, each 108 × 108 dp. The launcher crops it to its own shape (circle, squircle, rounded square), so keep the artwork inside the central **66 × 66 dp** circle | `res/mipmap-anydpi-v26/ic_launcher.xml`, with `drawable/ic_launcher_background.xml` (navy gradient) and `drawable/ic_launcher_foreground.xml` (the tower, as a vector) |
| Android 13+ themed icons | **Monochrome** layer: the foreground in one colour; Android tints it | `drawable/ic_launcher_monochrome.xml` |
| Android 5–7 | Square and round PNGs: 48, 72, 96, 144 and 192 px (mdpi to xxxhdpi) | `res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png` |
| Android TV, Google TV, Fire TV app row | **Banner**, 320 × 180 dp, with the app's name on it: 320 × 180 (mdpi), 480 × 270 (hdpi), 640 × 360 (xhdpi) and 960 × 540 px (xxhdpi) | `res/drawable-*/banner.png` |

The dark and light app icons and banners are kept for the README and for a future option; Android shows one launcher icon, so the app uses the colour one.

## Symbols

[`symbols/`](symbols): the line icons for categories, widgets and settings, as SVG on a 24 × 24 grid with 2 px strokes. They are drawn white on the category colours, or tinted to the theme.
