<p align="center">
  <img src="docs/assets/logo.png" alt="ShackScreen Logo" width="200"/>
</p>

<p align="center">
  <a href="../../releases/latest">
    <img src="https://img.shields.io/github/v/release/maxhayim/ShackScreen" alt="Latest Release">
  </a>
  <a href="https://developer.android.com/tv">
    <img src="https://img.shields.io/badge/Android%20TV%20%7C%20Google%20TV%20%7C%20Fire%20TV-5.1%2B-blue" alt="Platforms">
  </a>
  <a href="https://opensource.org/licenses/MIT">
    <img src="https://img.shields.io/badge/License-MIT-green" alt="License">
  </a>
</p>

# 📡 ShackScreen

**ShackScreen** is a free Android TV, Google TV and Fire TV app (it runs on tablets too) that turns the TV in your shack into a live radio dashboard, for people who love frequencies: hams, [**Meshtastic**](https://meshtastic.org/) and [**MeshCore**](https://meshcore.io) mesh users, satellite chasers, and ADS-B and AIS listeners.

Live **widgets** across the top: space weather, POTA and DX spots, satellite passes, mesh messages, aircraft and ships nearby, and the weather.  
Your radio **web apps** along the bottom: [**OpenHamClock**](https://github.com/accius/openhamclock), [**ShackBoard**](https://shackboard.com), [**MeshMonitor**](https://github.com/Yeraze/MeshMonitor), WebSDR, Broadcastify, Flightradar24 and more, full-screen with the remote.

No accounts.  
No ads.  
No paid services.

<p align="center">
  <img src="docs/assets/home.png" alt="ShackScreen home screen" width="49%"/>
  <img src="docs/assets/all-apps.png" alt="All apps" width="49%"/>
</p>

---

## ✅ Update Notice (v0.5.x)

v0.5 makes ShackScreen look and feel like a phone's home screen:

- ⚫ **Black background by default**, plus colour themes: Midnight, Ocean, Forest, Plum, Ember, Slate, and Light
- 📱 **App icons with names**, made square like real app icons, and an **All apps** library by category
- ✋ **Press and hold** any app or widget to move it, add or remove it from the home screen, or edit it
- 🏠 **Public site or your own server** for apps you can run yourself (OpenHamClock, WebSDR, KiwiSDR)

---

## Quick Start (5-minute setup)

### 1) Download

Get `ShackScreen-<version>.apk` from [**Releases**](../../releases/latest).

---

### 2) Install it on the TV

- **Fire TV:** install the free *Downloader* app, allow it to install unknown apps, and open the APK's link.
- **Android TV / Google TV:** copy the APK with a file-transfer app (e.g. *Send files to TV*) and open it, or from a computer:

```sh
adb connect <tv-ip>
adb install ShackScreen-<version>.apk
```

---

### 3) Open ShackScreen

Space weather and POTA spots show up straight away, from the public OpenHamClock.

---

### 4) Set your location

**Settings → Location**: a grid square (`FN31pr`) or latitude, longitude (`40.7, -74.0`).  
Needed for aircraft nearby, satellite passes and weather.

---

### 5) Add your own servers (optional)

**All apps → Add app**: pick an app, then **My own server** and type its IP. The default port is added for you.

---

## 🧩 Widgets

Pick them in **Settings → Widgets**. Press and hold a widget to **move** or **remove** it; select it to open the app its data comes from. Three fit across a TV, two on a portrait tablet, one on a phone. When nobody touches the remote for a minute, the dashboard pages through the rest on its own.

### 📻 Ham radio

| Widget | Data from |
|---|---|
| ☀️ Space weather: SFI, sunspots, K and A index, X-ray, HF band conditions | OpenHamClock or ShackBoard |
| 🌲 POTA spots | OpenHamClock or ShackBoard |
| 📡 DX cluster spots | OpenHamClock |
| 🏆 Contests, on now and coming up | OpenHamClock |
| 🌍 DXpeditions on the air | OpenHamClock |

### 🛰️ Satellites

| Widget | Data from |
|---|---|
| 🛰️ Satellite passes over you: time, max elevation, downlink | OpenHamClock's orbital data, predicted on the device |
| 📶 Satellites just heard, with frequency and mode | [SatNOGS](https://network.satnogs.org) |

### 🕸️ Mesh (via MeshMonitor)

| Widget | Data from |
|---|---|
| 🔌 Mesh sources: Meshtastic, MeshCore, MQTT, connected or not | MeshMonitor |
| 📍 Mesh nodes heard today | MeshMonitor |
| 💬 Mesh messages | MeshMonitor |

In MeshMonitor's settings, create an API token and paste it into the MeshMonitor app in ShackScreen. The token acts as your user, so it only sees what you can see.

### ✈️ 🛥️ Aviation and marine

| Widget | Data from |
|---|---|
| ✈️ Aircraft within 25 nm | **your own** tar1090 / readsb / dump1090, or [ADSB.lol](https://www.adsb.lol), [adsb.fi](https://adsb.fi) |
| 🛥️ Ships nearby | **your own** [AIS-catcher](https://github.com/jvde-github/AIS-catcher) |

### 🌦️ Local

| Widget | Data from |
|---|---|
| 🌦️ Weather | [Open-Meteo](https://open-meteo.com) |

Each widget uses the first of your apps that can supply it, and moves on to the next if a server is down.

---

## 📱 Apps

The home row holds your favourites, with **All apps** at the end. Press and hold an app (long-press OK on a remote) to move it, add it to the home row or take it off, edit it or delete it.

| Category | Apps |
|---|---|
| 📻 Ham radio | OpenHamClock, ShackBoard, APRS.fi, PSKReporter, RepeaterBook |
| 🛰️ Satellites | N2YO, Heavens-Above, AMSAT status, SatNOGS, TinyGS, Satellite map, ISS Tracker |
| 🕸️ Mesh | MeshMonitor, MeshMap (Meshtastic), MeshCore Map, your Meshtastic node |
| 🎧 SDR & scanners | WebSDR, KiwiSDR map, Broadcastify |
| ✈️ Aviation | Flightradar24, FlightAware, ADS-B Exchange, ADSB.lol, adsb.fi, your tar1090 |
| 🛥️ Marine | MarineTraffic, VesselFinder, MyShipTracking, your AIS-catcher |
| 🌦️ Weather | Windy, Lightning map |
| 🌐 Other | any web page |

### 🏠 Public site or your own server

- Apps you can also run yourself (**OpenHamClock, WebSDR, KiwiSDR**) ask **Public site or My own server** when you add them.
- **MeshMonitor, tar1090, AIS-catcher** and a **Meshtastic node** are always your own server.
- Type just the IP and the default port is added. You can add the same app more than once (say, the public OpenHamClock and the one on your shack Pi).
- Addresses and tokens are stored **only on the device**.

### 🌍 Remote access (VPN)

To reach servers at home from a TV somewhere else, use a VPN such as [Tailscale](https://tailscale.com): no port forwarding, encrypted, works behind NAT.

### ➕ Missing an app?

**All apps → Request an app…** opens an [app request](../../issues/new?template=app_request.yml) here. On a TV, scan the QR code with your phone. The app list lives in [`catalog.json`](app/src/main/assets/catalog.json) and refreshes from this repo, so new apps arrive without a new APK.

---

## 🎨 Themes and Icons

- **Settings → Appearance**: Black (default), Midnight, Ocean, Forest, Plum, Ember, Slate, Light, or follow the TV.
- Every app shows its **own icon**, fetched from the server you point it at and made square like a phone app icon. A site without a good one gets a coloured tile for its category.

---

## 🎮 Remote and Touch

| | Remote | Touch |
|---|---|---|
| Open an app or widget | **OK** | tap |
| Move, remove, edit | hold **OK** | press and hold |
| Move a widget | arrows, then **OK** | ◀ ▶, then tap |
| Back a page / home | **Back** | back |
| Reload a page | **Menu** | |

An app that can't be reached is retried every 30 seconds, and the screen stays on while one is open.

---

## 🔄 Updates

**Settings → Updates**:

- **Off** — never contacts GitHub
- **Tell me** (default) — checks GitHub Releases daily and shows an *Update* button
- **Install automatically** — downloads and installs new versions. Silent on Android 12+ once ShackScreen has updated itself once; one confirm click on older Android and Fire TV.

An update is installed only if it's ShackScreen, newer, and **signed with the same key**:

`a4b212f1b47635da599640c4c61d431b98671833b67f36b97df3a748ef5e8a32` (SHA-256)

---

## Requirements

- Android TV, Google TV or Fire TV with Android 5.1 or newer (or an Android tablet or phone)
- Internet
- Your own servers and SDR receivers: optional

No Google account.  
No root.  
No subscriptions.

---

## 🛠️ Build from Source

Needs JDK 17 and the Android SDK (platform 35). Turn on developer options and network debugging on the TV, then:

```sh
TV_HOST=192.168.1.50 ./build-and-install.sh
```

Without `TV_HOST` it only builds `app/build/outputs/apk/release/app-release.apk`.

---

## License

This project is licensed under the MIT License.

See the LICENSE file for details.  
Full license text: https://opensource.org/licenses/MIT

Includes the [Outfit](https://github.com/Outfitio/Outfit-Fonts) font (SIL Open Font License, see `FONT-LICENSE-OFL.txt`) and Project Nayuki's [QR Code generator](https://github.com/nayuki/QR-Code-generator) (MIT).

---

## Contributing

Pull requests are welcome. Open an issue first to discuss ideas or report bugs. See [CONTRIBUTING.md](CONTRIBUTING.md); adding an app to the menu is usually one entry in `catalog.json`.

---

## Acknowledgments

* Inspired by [CleanYourTV](https://github.com/Gathin23/cleanyourtv)
* [OpenHamClock](https://github.com/accius/openhamclock) by K0CJH
* [ShackBoard](https://shackboard.com)
* MeshMonitor built by [Yeraze](https://github.com/Yeraze)
* [SatNOGS](https://satnogs.org) by the Libre Space Foundation
* [ADSB.lol](https://www.adsb.lol) and [adsb.fi](https://adsb.fi) (ODbL)
* [AIS-catcher](https://github.com/jvde-github/AIS-catcher)
* [Open-Meteo](https://open-meteo.com)

Discover community-contributed scripts for MeshMonitor: https://meshmonitor.org/user-scripts.html
