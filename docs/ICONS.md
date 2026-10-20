# Contributing Icons

ShackScreen shows every application as an icon on the home row and in All applications. Anyone can design a better one. Icons you add here reach every TV and tablet within a week, with no app update.

---

## Quick Start

1. Find the application's file in [`icons/color/`](../icons/color), [`icons/dark/`](../icons/dark) or [`icons/light/`](../icons/light).
2. Design a replacement at **512 × 512 px**, shaped like the [template](../icons/template-512.png).
3. Send it in, either way:
   - **Pull request:** replace the file under the same name and open a PR, or
   - **Issue:** attach the PNG to an [icon request](../../../issues/new?template=icon_request.yml) and we'll add it.

---

## Icon Specs

| | |
|---|---|
| **Format** | PNG, RGBA |
| **Size** | 512 × 512 px |
| **Shape** | Rounded square, corner radius **136 px**, transparent outside the corners |
| **Fill** | Edge to edge inside the shape. No extra border, frame or drop shadow |
| **Logo area** | Keep text and fine detail inside the orange guide on the [template](../icons/template-512.png) (the middle 70%) |
| **Smallest the app accepts** | 96 × 96 px |

The shape is exactly what ShackScreen draws: an 18 dp corner on a 68 dp tile. The app shows these files as they are, so what you design is what appears on screen.

**Starting files:**

- [`icons/template-512.png`](../icons/template-512.png): the shape, centre lines and logo-area guide
- [`icons/mask-512.png`](../icons/mask-512.png): the shape alone, white on transparent, to clip your artwork in any editor
- The current icons in `icons/color/`, `icons/dark/` and `icons/light/` are already the right shape, so you can edit them directly

---

## Colour, Dark and Light

People choose **Settings → Appearance → Application icons**: **Colour**, **Dark**, **Light**, or **Match the theme** (Dark icons with the dark themes, Light icons with the Light theme).

| Folder | Style |
|---|---|
| [`icons/color/`](../icons/color) | Full colour. The brand's own colours are best. |
| [`icons/dark/`](../icons/dark) | For dark screens: a dark tile (black or near-black) with a light logo. |
| [`icons/light/`](../icons/light) | For light screens: a light tile (white or near-white) with a dark logo. |

Dark and light icons can be greys only or keep one accent colour; they should feel like one set. An application without a dark or light icon shows its colour one, so a colour-only contribution is welcome too.

---

## File Names

Each file is named after the application's `id` in [`catalog.json`](../app/src/main/assets/catalog.json). The full list, with names and categories, is in [`icons/README.md`](../icons/README.md). For example:

| File | Application |
|---|---|
| `gmrshub.png` | GMRS Hub |
| `mygmrs.png` | myGMRS |
| `openhamclock.png` | OpenHamClock |
| `meshmonitor.png` | MeshMonitor |
| `custom.png` | Other web page |

For an application that isn't in ShackScreen yet, ask for it with an [app request](../../../issues/new?template=app_request.yml) first.

---

## ShackScreen's Own Icons

In [`icons/app/`](../icons/app):

| File | Size | Used for |
|---|---|---|
| `shackscreen-color.png`, `shackscreen-dark.png`, `shackscreen-light.png` | 512 × 512 px | App icon, GitHub logo |
| `shackscreen-tower.png` | 512 × 512 px, transparent, no background | Logo next to SHACKSCREEN on the dashboard |
| `tv-banner-color.png`, `tv-banner-dark.png`, `tv-banner-light.png` | 640 × 360 px (1280 × 720 master welcome) | Banner in the Android TV / Google TV / Fire TV app row |

These are built into the app, so a change ships with the next release.

---

## Symbols

[`icons/symbols/`](../icons/symbols) holds the line icons for categories, widgets and settings: SVG on a **24 × 24** grid with **2 px** round-capped strokes, one colour. The app tints them, so keep them single-colour.

---

## Checklist

- [ ] 512 × 512 PNG, transparent outside the 136 px rounded corners
- [ ] File named after the catalog `id`
- [ ] Logo inside the middle 70%
- [ ] Dark version: dark tile, light logo. Light version: light tile, dark logo
- [ ] You made it, or the logo's owner allows its use (official brand logos used to identify the service are fine). Say where it came from in the PR or issue.

Thank you, and 73!
