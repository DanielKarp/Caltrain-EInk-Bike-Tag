# E-Ink Bike Tag

<video width="400" controls>
  <source src="../photos/screen-20260421-202834-1776828503225~2.mp4" type="video/mp4">
</video>

An Android application designed to update E-Ink displays (tags) via NFC. It allows users to select from a library of preloaded bitmap images and "flash" them onto a compatible electronic paper display.

## How to Use

1. **Prepare Images**: Place your `.bmp` files in the `app/src/main/assets/images` directory.
2. **Naming Convention**: The app parses filenames to determine the display name and an optional highlight color in the list.
    * **Format**: `[SortIndex]_[OptionalColor]_[Name].bmp`
    * **Examples**:
        * `01_R_Foo.bmp` -> Displays as "Foo" with a **Red** highlight.
        * `02_B_Bar Baz.bmp` -> Displays as "Bar Baz" with a **Blue** highlight.
        * `03_Example.bmp` -> Displays as "Example" with no highlight.
    * **Supported Color Codes**: `R` (Red), `O` (Orange), `Y` (Yellow), `G` (Green), `B` (Blue), `I` (Indigo), `V` (Violet).
3. **Select an Image**: Launch the app and tap an item in the list. A preview of the image will appear at the top.
4. **Write to Tag**: 
    * Ensure NFC is enabled on your device.
    * Hold the back of your phone against the E-Ink display.
    * Keep the device steady while the progress percentage updates.
    * The app will provide a success tone and vibration once the transfer is complete.

## How It Works

* **Asset Management**: On startup, the app scans the `assets/images` folder, parsing the naming convention to build a list of `ImageItem` objects.
* **UI/UX**: Uses a `RecyclerView` for the image list with custom highlights based on the filename metadata. When an item is selected, the list automatically adjusts to keep the selection visible.
* **NFC Communication**:
  * Uses `NfcAdapter.enableReaderMode` to detect tags.
  * Uses a custom `EpdWriter` class to handle the low-level NFC communication (IsoDep) required to transfer bitmap data to the display's flash memory.
* **Feedback**: Integrates `ToneGenerator` and `Vibrator` services to provide immediate haptic and audible feedback for both successful writes and errors.

## Technical Details

* **Language**: Kotlin
* **UI**: XML Layouts with `ComponentActivity`
* **Dependencies**: AndroidX, Material Design, RecyclerView

## Driving the Waveshare NFC E-Ink Display

Getting the Waveshare 2.7" NFC e-paper display to work from Android turned out to be a journey through several layers of the stack.

The obvious starting point was Waveshare's own Android SDK JAR. The main handler class is package-private with obfuscated method names, so we wrapped it in a thin `WaveShareHandler` class to expose a clean interface. This worked for the older tag firmware, but the new tag revision the hardware shipped with wasn't supported by the SDK.

With the SDK ruled out, Waveshare provided the raw driver command specification directly. We wrote `EpdWriter` to implement these commands from scratch. The first attempt used `NfcA.transceive()` — the low-level ISO 14443-3 interface — but every single command failed immediately with `TagLostException`. After ruling out timing and power-harvesting issues, the real problem became clear: the commands are structured as ISO 7816-4 APDUs (`CLA INS P1 P2` format), which need to go through `IsoDep`, not `NfcA`. Switching the transport layer fixed the communication entirely.

The display sequence itself then needed two more corrections. The hardware reset command (`RST LOW`) kills the NFC interface from the inside — it literally resets the chip providing RF power — so it has to be skipped entirely when operating over NFC. And the bitmap pipeline needed fixes on three fronts: color inversion (1=black, not white), 90° rotation (the controller scans in portrait orientation despite the landscape physical layout), and LSB-first bit packing within each byte rather than MSB-first.

Once all of that was sorted, the full init → image transfer → busy-poll refresh cycle completed cleanly in about 4 seconds over NFC.