# BrowserStartupTest

Launches a browser to a configured URL via Selenium 4.3.0, keeps the session
alive for a configured duration, then quits and cleans up. No Maven/Gradle —
dependencies are plain jars in `libs/`, auto-picked up by IntelliJ.

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
  "browser_binary_path": ""
}
```

- `url` — page to open.
- `browser` — one of `chrome`, `firefox`, `edge`, `safari`.
- `waitTimeInSeconds` — how long to keep the session open before quitting.
- `browser_binary_path` — optional. Path to a specific browser executable
  (e.g. a non-default Chrome install). Leave empty to use the system default.
  Not supported for `safari` (ignored with a warning if set).

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
