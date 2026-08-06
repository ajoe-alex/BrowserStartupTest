# WebDriver executables

This folder ships empty. Download the driver version that matches your
installed browser and place it here manually before running `run.sh` /
`run.bat`.

## Naming convention

| Browser | Windows           | macOS / Linux |
|---------|--------------------|---------------|
| chrome  | `chromedriver.exe` | `chromedriver` |
| firefox | `geckodriver.exe`  | `geckodriver`  |
| edge    | `msedgedriver.exe` | `msedgedriver` |
| safari  | not needed — Safari uses the driver built into macOS. Enable it once with `safaridriver --enable`. |

The filename must match exactly as shown above (no version suffix, no
extra text). On macOS/Linux the file is made executable automatically on
first run if it isn't already.
