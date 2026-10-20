# Contributing to ShackScreen

Thank you for your interest in contributing to ShackScreen! We welcome contributions from the community and want to make the process clear and straightforward.

## Ways to Contribute

- Report bugs or request features using [GitHub Issues](../../issues).
- **Request an app or widget** with the [app request form](../../issues/new?template=app_request.yml) (or from inside ShackScreen: **+ Add app → Request an app…**).
- **Design icons**: see [Contributing Icons](docs/ICONS.md) for sizes, shape and file names, or send one with the [icon request form](../../issues/new?template=icon_request.yml).
- Improve documentation (README, code comments, examples).
- Submit Pull Requests (PRs) with bug fixes, improvements, or new features.

## Adding an App to the Menu

Most new apps need no code at all: add an entry to [`app/src/main/assets/catalog.json`](app/src/main/assets/catalog.json)
and bump its `"revision"`. Installed copies of ShackScreen pick up the new catalog from this repo on their own.

```json
{
  "id": "example",
  "name": "Example",
  "category": "Ham radio",
  "url": "https://example.com",
  "port": 8080,
  "health": "/api/health"
}
```

- `port`: set it if people also run the app themselves; ShackScreen then offers **Public site or My own server**.
- `health`: an endpoint that answers when the server is up.
- Add its icon to [`icons/`](icons) too: see [Contributing Icons](docs/ICONS.md).
- `feeds`: which widgets the app can supply (`space`, `pota`, `dx`, `contests`, `dxpeditions`, `satellites`, `satnogs`, `mesh`, `aircraft`, `ships`). A new feed needs code in `Feeds.java`.

## Code Style

- Plain Java and Android views, no third-party libraries in the app (it has to stay small for 1 GB TVs).
- Match the surrounding code: its naming, comment density and idioms.
- Comments explain *why*, not *what*.
- Keep things simple (KISS principle).
- `minSdk` is 22 (Fire OS 5 / Android TV 5.1): check `Build.VERSION.SDK_INT` before newer APIs.

## Pull Request Guidelines

1. **Fork** the repository.
2. Create a new branch for your feature or bugfix:
   ```bash
   git checkout -b feature/my-new-feature
   ```
3. Commit changes with clear, descriptive messages.
4. Ensure it builds and does not break existing functionality.
5. Open a Pull Request and describe your changes, with a screenshot if it changes what's on screen.

## Tests

- Run the unit tests before submitting a PR:
  ```bash
  ./gradlew testReleaseUnitTest lintRelease
  ```
- Try your change on a TV (D-pad remote) and, if you can, a tablet (touch).
- If possible, add tests for new logic in `app/src/test`.

## Community

We value respectful collaboration. By contributing, you agree to follow our [Code of Conduct](CODE_OF_CONDUCT.md).
