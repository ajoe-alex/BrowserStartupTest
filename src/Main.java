import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Reads url/browser/wait settings from app.properties.json, launches the requested
 * browser via the matching driver executable in webdrivers/, keeps the session alive
 * for the configured duration, then quits and cleans up.
 */
public class Main {

    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath();
    private static final Path CONFIG_FILE = PROJECT_ROOT.resolve("app.properties.json");
    private static final Path WEBDRIVERS_DIR = PROJECT_ROOT.resolve("webdrivers");
    private static final Path LOGS_DIR = PROJECT_ROOT.resolve("logs");

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        setupLogger();

        WebDriver driver = null;
        try {
            Map<String, Object> config = readConfig();
            String url = requireString(config, "url");
            String browser = requireString(config, "browser").toLowerCase();
            long waitTimeInSeconds = requireLong(config, "waitTimeInSeconds");
            String browserBinaryPath = optionalString(config, "browser_binary_path");
            Map<String, Object> capabilities = optionalMap(config, "capabilities");

            LOGGER.info("Launching browser [" + browser + "] with url [" + url + "] and wait time [" + waitTimeInSeconds + "s]");

            driver = createDriver(browser, browserBinaryPath, capabilities);

            if (isChromiumBased(browser)) {
                driver.get("about:blank");
                LOGGER.info("Loaded about:blank as initial page for Chromium-based browser [" + browser + "]");
            }

            driver.get(url);
            logBrowserDetails(driver);

            LOGGER.info("Keeping session active for up to " + waitTimeInSeconds
                    + " second(s), or until 'Close Session' is clicked...");
            boolean closedManually = awaitSessionClose(browser, url, waitTimeInSeconds);
            LOGGER.info(closedManually
                    ? "Session closed manually via the 'Close Session' button."
                    : "Wait time elapsed; closing session automatically.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "BrowserStartupTest failed: " + e.getMessage(), e);
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                    LOGGER.info("Browser session closed and cleaned up.");
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error while quitting the browser: " + e.getMessage(), e);
                }
            }
        }
    }

    private static boolean isChromiumBased(String browser) {
        return "chrome".equals(browser) || "edge".equals(browser);
    }

    private static void logBrowserDetails(WebDriver driver) {
        Dimension size = driver.manage().window().getSize();
        LOGGER.info("Page title: " + driver.getTitle());
        LOGGER.info("Current URL: " + driver.getCurrentUrl());
        LOGGER.info("Window size: " + size.getWidth() + "x" + size.getHeight());
        LOGGER.info("Window handle: " + driver.getWindowHandle());
        LOGGER.info("Page source length: " + driver.getPageSource().length() + " characters");
        if (driver instanceof JavascriptExecutor) {
            Object userAgent = ((JavascriptExecutor) driver).executeScript("return navigator.userAgent;");
            LOGGER.info("User agent: " + userAgent);
        }
    }

    /**
     * Shows a small always-on-top control window with a "Close Session" button and blocks
     * until either that button (or the window's own close box) is used, or waitTimeInSeconds
     * elapses — whichever comes first. Returns true if the session was closed manually.
     */
    private static boolean awaitSessionClose(String browser, String url, long waitTimeInSeconds)
            throws InterruptedException, java.lang.reflect.InvocationTargetException {
        CountDownLatch closeLatch = new CountDownLatch(1);
        JFrame[] windowHolder = new JFrame[1];

        SwingUtilities.invokeAndWait(() -> windowHolder[0] =
                buildControlWindow(browser, url, waitTimeInSeconds, closeLatch));

        boolean closedManually = closeLatch.await(waitTimeInSeconds, TimeUnit.SECONDS);
        SwingUtilities.invokeLater(windowHolder[0]::dispose);
        return closedManually;
    }

    private static JFrame buildControlWindow(String browser, String url, long waitTimeInSeconds,
                                              CountDownLatch closeLatch) {
        JFrame frame = new JFrame("BrowserStartupTest - Session Control");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setAlwaysOnTop(true);
        frame.setResizable(false);

        JLabel infoLabel = new JLabel("<html>Browser: " + browser + "<br>URL: " + url + "</html>");
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));

        JLabel countdownLabel = new JLabel("Auto-closing in " + waitTimeInSeconds + "s", SwingConstants.CENTER);
        countdownLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        JButton closeButton = new JButton("Close Session");
        closeButton.addActionListener(e -> {
            closeButton.setEnabled(false);
            closeButton.setText("Closing...");
            closeLatch.countDown();
        });
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeButton.doClick();
            }
        });

        long[] remainingSeconds = {waitTimeInSeconds};
        Timer countdownTimer = new Timer(1000, null);
        countdownTimer.addActionListener(e -> {
            remainingSeconds[0]--;
            if (remainingSeconds[0] <= 0) {
                countdownLabel.setText("Closing...");
                ((Timer) e.getSource()).stop();
            } else {
                countdownLabel.setText("Auto-closing in " + remainingSeconds[0] + "s");
            }
        });
        countdownTimer.start();

        JPanel content = new JPanel(new BorderLayout());
        content.add(infoLabel, BorderLayout.NORTH);
        content.add(countdownLabel, BorderLayout.CENTER);
        content.add(closeButton, BorderLayout.SOUTH);
        frame.setContentPane(content);

        frame.pack();
        frame.setSize(Math.max(320, frame.getWidth()), frame.getHeight());
        positionBottomRight(frame);
        frame.setVisible(true);
        return frame;
    }

    /** Places the window in the bottom-right corner of the screen, clear of the taskbar/dock. */
    private static void positionBottomRight(JFrame frame) {
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int margin = 20;
        int x = screen.x + screen.width - frame.getWidth() - margin;
        int y = screen.y + screen.height - frame.getHeight() - margin;
        frame.setLocation(x, y);
    }

    private static void setupLogger() {
        try {
            Files.createDirectories(LOGS_DIR);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            FileHandler fileHandler = new FileHandler(
                    LOGS_DIR.resolve("BrowserStartupTest_" + timestamp + ".log").toString(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Failed to initialize file logger in " + LOGS_DIR + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readConfig() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            throw new IOException("Config file not found: " + CONFIG_FILE);
        }
        String json = new String(Files.readAllBytes(CONFIG_FILE), StandardCharsets.UTF_8);
        Map<String, Object> config = new Json().toType(json, Map.class);
        if (config == null) {
            throw new IOException("Config file is empty or not a JSON object: " + CONFIG_FILE);
        }
        return config;
    }

    private static String requireString(Map<String, Object> config, String key) {
        String value = optionalString(config, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required config key '" + key + "' in " + CONFIG_FILE);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        String str = String.valueOf(value).trim();
        return str.isEmpty() ? null : str;
    }

    private static long requireLong(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("Missing required numeric config key '" + key + "' in " + CONFIG_FILE);
        }
        return ((Number) value).longValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optionalMap(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (!(value instanceof Map)) {
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) value;
        return map.isEmpty() ? null : map;
    }

    // Newer Chrome/Edge versions reject the DevTools websocket handshake used by older
    // Selenium clients unless origins are explicitly allowed (--remote-allow-origins=*);
    // the rest keep background tabs/windows from throttling and fix the initial window size.
    private static final String[] CHROMIUM_ARGS = {
            "-disable-web-security",
            "--remote-allow-origins=*",
            "--disable-backgrounding-occluded-windows",
            "--window-size=2000,1600"
    };

    private static WebDriver createDriver(String browser, String browserBinaryPath, Map<String, Object> capabilities) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        switch (browser) {
            case "chrome": {
                System.setProperty("webdriver.chrome.driver", resolveDriverPath("chromedriver", isWindows));
                ChromeOptions options = new ChromeOptions();
                options.addArguments(CHROMIUM_ARGS);
                if (browserBinaryPath != null) {
                    LOGGER.info("Using custom browser binary: " + browserBinaryPath);
                    options.setBinary(browserBinaryPath);
                }
                applyCapabilities(options, capabilities, options::addArguments,
                        prefs -> options.setExperimentalOption("prefs", prefs));
                return new ChromeDriver(options);
            }
            case "firefox": {
                System.setProperty("webdriver.gecko.driver", resolveDriverPath("geckodriver", isWindows));
                FirefoxOptions options = new FirefoxOptions();
                if (browserBinaryPath != null) {
                    LOGGER.info("Using custom browser binary: " + browserBinaryPath);
                    options.setBinary(browserBinaryPath);
                }
                applyCapabilities(options, capabilities, options::addArguments,
                        prefs -> prefs.forEach(options::addPreference));
                return new FirefoxDriver(options);
            }
            case "edge": {
                System.setProperty("webdriver.edge.driver", resolveDriverPath("msedgedriver", isWindows));
                EdgeOptions options = new EdgeOptions();
                options.addArguments(CHROMIUM_ARGS);
                if (browserBinaryPath != null) {
                    LOGGER.info("Using custom browser binary: " + browserBinaryPath);
                    options.setBinary(browserBinaryPath);
                }
                applyCapabilities(options, capabilities, options::addArguments,
                        prefs -> options.setExperimentalOption("prefs", prefs));
                return new EdgeDriver(options);
            }
            case "safari": {
                // Safari ships its own driver with macOS (enable via `safaridriver --enable`);
                // no executable is placed in webdrivers/ for this browser, and Safari has no
                // concept of a swappable binary path, launch args, or browser prefs, so
                // browser_binary_path and capabilities.args/prefs are all ignored here.
                if (browserBinaryPath != null) {
                    LOGGER.warning("browser_binary_path is not supported for safari; ignoring.");
                }
                SafariOptions options = new SafariOptions();
                applyCapabilities(options, capabilities, null, null);
                return new SafariDriver(options);
            }
            default:
                throw new IllegalArgumentException("Unsupported browser '" + browser
                        + "'. Supported values: chrome, edge, firefox, safari");
        }
    }

    /**
     * Applies an arbitrary, user-supplied capability/options object (the "capabilities" key
     * in app.properties.json) to the given options, e.g.:
     * {@code "capabilities": {"args": ["--start-maximized"], "prefs": {...}, "pageLoadStrategy": "eager"}}.
     * <p>
     * "args" and "prefs" are handled specially via argsApplier/prefsApplier (each may be null
     * if unsupported for the browser) because Selenium's *Options classes rebuild their
     * vendor-prefixed capability block (e.g. "goog:chromeOptions") from their own internal
     * fields on serialization — a raw merge of that block gets silently discarded, so these
     * two need to go through the browser's real addArguments(...)/setExperimentalOption(...)
     * (or addPreference(...) for Firefox) methods instead. Everything else is merged as-is,
     * which covers genuine top-level W3C capabilities like "pageLoadStrategy".
     */
    private static void applyCapabilities(MutableCapabilities options, Map<String, Object> capabilities,
                                           Consumer<List<String>> argsApplier,
                                           Consumer<Map<String, Object>> prefsApplier) {
        if (capabilities == null) {
            return;
        }
        Map<String, Object> remaining = new LinkedHashMap<>(capabilities);

        Object rawArgs = remaining.remove("args");
        if (rawArgs instanceof List) {
            List<String> args = new ArrayList<>();
            for (Object item : (List<?>) rawArgs) {
                args.add(String.valueOf(item));
            }
            if (!args.isEmpty()) {
                if (argsApplier != null) {
                    LOGGER.info("Applying extra browser args from capabilities: " + args);
                    argsApplier.accept(args);
                } else {
                    LOGGER.warning("capabilities.args is not supported for this browser; ignoring: " + args);
                }
            }
        }

        Object rawPrefs = remaining.remove("prefs");
        if (rawPrefs instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> prefs = (Map<String, Object>) rawPrefs;
            if (!prefs.isEmpty()) {
                if (prefsApplier != null) {
                    LOGGER.info("Applying browser preferences from capabilities: " + prefs);
                    prefsApplier.accept(prefs);
                } else {
                    LOGGER.warning("capabilities.prefs is not supported for this browser; ignoring: " + prefs);
                }
            }
        }

        if (!remaining.isEmpty()) {
            LOGGER.info("Merging extra capabilities: " + remaining);
            options.merge(new MutableCapabilities(remaining));
        }
    }

    private static String resolveDriverPath(String driverName, boolean isWindows) {
        String fileName = isWindows ? driverName + ".exe" : driverName;
        Path driverPath = WEBDRIVERS_DIR.resolve(fileName);
        if (!Files.exists(driverPath)) {
            throw new IllegalStateException("WebDriver executable not found: " + driverPath
                    + ". Please place the correct driver in the webdrivers folder.");
        }
        if (!isWindows) {
            driverPath.toFile().setExecutable(true);
        }
        return driverPath.toAbsolutePath().toString();
    }
}
