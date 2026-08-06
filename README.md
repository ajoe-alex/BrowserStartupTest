# BrowserStartupTest

Launches a browser to a configured URL via Selenium 4.3.0, keeps the session
alive for a configured duration (or until closed early from a small on-screen
control window), then quits and cleans up. No Maven/Gradle — dependencies are
plain jars in `libs/`, auto-picked up by IntelliJ.

## Project layout

```
src/Main.java           Entry point
libs/                   Dependency jars (selenium-server-4.3.0.jar is a
                         shaded jar containing the full Selenium client)
webdrivers/              Place chromedriver/geckodriver/msedgedriver here (dev);
                         README.md documents the naming convention
logs/                    Timestamped run logs + exception stack traces (dev)
app.properties.json      Dev-time config read by src/Main.java
run.sh / run.bat         Compile + run from source (dev)
build.sh / build.bat     Build a distributable fat jar into build/browser_startup_test/
public/                  Files shipped to clients — copied verbatim into the
                         build output by build.sh/build.bat (run.sh, run.bat,
                         app.properties.json, webdrivers/README.md)
build/                   Generated output (gitignored); browser_startup_test/
                         holds the fat jar + everything from public/
```

## Configuration (`app.properties.json`)

```json
{
  "url": "https://www.google.com",
  "browser": "chrome",
  "waitTimeInSeconds": 20,
  "browser_binary_path": "",
  "capabilities": null
}
```

- `url` — page to open.
- `browser` — one of `chrome`, `firefox`, `edge`, `safari`.
- `waitTimeInSeconds` — max time to keep the session open before quitting
  automatically. Can end sooner — see "Session control window" below.
- `browser_binary_path` — optional. Path to a specific browser executable
  (e.g. a non-default Chrome install). Leave empty to use the system default.
  Not supported for `safari` (ignored with a warning if set).
- `capabilities` — optional. `null`/absent is ignored. A free-form object
  applied to the browser's Selenium `Options`/capabilities. Three ways to use it:
  - **`args`** — a flat list of browser launch flags, routed through the
    browser's own `addArguments(...)` (not supported for `safari`, which has
    no launch-flag concept):
    ```json
    "capabilities": {
      "args": ["--start-maximized", "--lang=en-US"]
    }
    ```
  - **`prefs`** — a flat map of browser preferences, routed through
    `setExperimentalOption("prefs", ...)` for Chrome/Edge, or
    `addPreference(key, value)` per entry for Firefox (not supported for
    `safari`). Do **not** rely on nesting `prefs` inside a raw
    `goog:chromeOptions`/`moz:firefoxOptions` block under "anything else"
    below — Selenium regenerates those vendor blocks from its own internal
    state on serialization, so anything merged in under those specific keys
    is silently dropped. The top-level `prefs` key exists precisely to avoid
    that trap.
  - **Anything else** — merged as-is via Selenium's `MutableCapabilities`.
    This works for genuine top-level W3C capabilities, e.g.:
    ```json
    "capabilities": {
      "pageLoadStrategy": "eager"
    }
    ```

### Sample configurations

Enable notifications for Chrome/Edge (skips the permission prompt):

```json
{
  "url": "https://www.google.com",
  "browser": "chrome",
  "waitTimeInSeconds": 20,
  "browser_binary_path": "",
  "capabilities": {
    "prefs": {
      "profile.default_content_setting_values.notifications": 1
    }
  }
}
```

Set a custom user agent — Chrome/Edge (Chromium) take it as a launch flag,
Firefox takes it as a preference:

```json
{
  "url": "https://www.google.com",
  "browser": "chrome",
  "waitTimeInSeconds": 20,
  "browser_binary_path": "",
  "capabilities": {
    "args": ["--user-agent=MyCompany-BrowserStartupTest/1.0"]
  }
}
```

```json
{
  "url": "https://www.google.com",
  "browser": "firefox",
  "waitTimeInSeconds": 20,
  "browser_binary_path": "",
  "capabilities": {
    "prefs": {
      "general.useragent.override": "MyCompany-BrowserStartupTest/1.0"
    }
  }
}
```

`args` and `prefs` can be combined in the same `capabilities` object.

### Session control window

Once the page loads, a small always-on-top Swing window appears in the
bottom-right corner of the screen with a countdown and a **Close Session**
button. The session ends — driver `quit()` and cleanup — on whichever happens
first:

- `waitTimeInSeconds` (from `app.properties.json`) elapses, or
- the user clicks **Close Session** (or the window's own close box, which
  does the same thing).

Both paths run through the same cleanup code and are logged, e.g. "Session
closed manually via the 'Close Session' button." vs. "Wait time elapsed;
closing session automatically." This requires a display (`java.desktop`,
part of a normal desktop JRE) — it isn't meant for headless/CI environments.

## Running from source (development)

1. Place the matching driver executable in `webdrivers/`:
   - Windows: `webdrivers/chromedriver.exe` or `webdrivers/geckodriver.exe`
   - macOS/Linux: `webdrivers/chromedriver` or `webdrivers/geckodriver`
   - Safari uses macOS's built-in driver — no file needed (`safaridriver --enable` once).
2. Edit `app.properties.json`.
3. Run `./run.sh` (macOS/Linux) or `run.bat` (Windows). This compiles `src/Main.java`
   against `libs/*` and runs it. Exceptions and run details are logged to `logs/`.

## Building a client distribution

`./build.sh` (or `build.bat`) compiles the source, merges every jar in `libs/`
into a single fat jar, and writes everything to `build/browser_startup_test/`:

```
build/browser_startup_test/
  BrowserStartupTest.jar   Self-contained fat jar (Main-Class: Main)
  run.sh / run.bat         Copied from public/ — just runs the jar
  app.properties.json      Copied from public/ — client-editable config
  webdrivers/              Empty — client places their driver here
  logs/                    Empty — populated on first run
```

Anything added to `public/` is copied into the build output automatically —
no need to update the build scripts when adding new client-facing files.

To hand off to a client: ship the `build/browser_startup_test/` folder. They
edit `app.properties.json`, drop in the right driver executable (see
`webdrivers/README.md` for naming), and run `run.sh`/`run.bat`. Only a JRE
is required — no JDK, project setup, or source.

By default `run.sh`/`run.bat` invoke whatever `java` resolves to on `PATH`.
For corner cases where a specific JDK install must be used instead, both
scripts have a commented-out line near the top to set it explicitly:

```sh
# run.sh
# JAVA_HOME="/path/to/jdk"
```

```bat
:: run.bat
REM set "JAVA_HOME=C:\path\to\jdk"
```

Uncomment and point it at the desired JDK home; the script then runs
`$JAVA_HOME/bin/java` (`%JAVA_HOME%\bin\java.exe` on Windows) instead of the
`PATH` default.

## Notes

- `selenium-server-4.3.0.jar` is a shaded/fat jar, so no other Selenium jars
  are needed in `libs/`.
- Chrome/Edge are launched with `--remote-allow-origins=*` — newer Chrome/Edge
  versions reject the DevTools websocket handshake used by Selenium 4.3.0
  without it.
- Chrome/Edge load `about:blank` first, then navigate to the configured URL.
- Every run logs page title, current URL, window size, window handle, page
  source length, and `navigator.userAgent` after the page loads.
