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
