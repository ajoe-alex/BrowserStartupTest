import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.safari.SafariDriver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            String config = readConfigFile();
            String url = getConfigValue(config, "url");
            String browser = getConfigValue(config, "browser").toLowerCase();
            long waitTimeInSeconds = Long.parseLong(getConfigValue(config, "waitTimeInSeconds"));
            String browserBinaryPath = getOptionalConfigValue(config, "browser_binary_path");

            LOGGER.info("Launching browser [" + browser + "] with url [" + url + "] and wait time [" + waitTimeInSeconds + "s]");

            driver = createDriver(browser, browserBinaryPath);

            if (isChromiumBased(browser)) {
                driver.get("about:blank");
                LOGGER.info("Loaded about:blank as initial page for Chromium-based browser [" + browser + "]");
            }

            driver.get(url);
            logBrowserDetails(driver);

            LOGGER.info("Keeping session active for " + waitTimeInSeconds + " second(s)...");
            Thread.sleep(waitTimeInSeconds * 1000L);
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

    private static String readConfigFile() throws IOException {
        if (!Files.exists(CONFIG_FILE)) {
            throw new IOException("Config file not found: " + CONFIG_FILE);
        }
        return new String(Files.readAllBytes(CONFIG_FILE), StandardCharsets.UTF_8);
    }

    private static String getConfigValue(String json, String key) {
        String value = getOptionalConfigValue(json, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required config key '" + key + "' in " + CONFIG_FILE);
        }
        return value;
    }

    private static String getOptionalConfigValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"([^\"]*)\"|[^,}\\s]+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(1).trim();
        return value.isEmpty() ? null : value;
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

    private static WebDriver createDriver(String browser, String browserBinaryPath) {
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
                return new ChromeDriver(options);
            }
            case "firefox": {
                System.setProperty("webdriver.gecko.driver", resolveDriverPath("geckodriver", isWindows));
                FirefoxOptions options = new FirefoxOptions();
                if (browserBinaryPath != null) {
                    LOGGER.info("Using custom browser binary: " + browserBinaryPath);
                    options.setBinary(browserBinaryPath);
                }
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
                return new EdgeDriver(options);
            }
            case "safari": {
                // Safari ships its own driver with macOS (enable via `safaridriver --enable`);
                // no executable is placed in webdrivers/ for this browser, and Safari has no
                // concept of a swappable binary path, so browser_binary_path is ignored here.
                if (browserBinaryPath != null) {
                    LOGGER.warning("browser_binary_path is not supported for safari; ignoring.");
                }
                return new SafariDriver();
            }
            default:
                throw new IllegalArgumentException("Unsupported browser '" + browser
                        + "'. Supported values: chrome, edge, firefox, safari");
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
