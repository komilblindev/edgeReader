# Edge Reader

Edge Reader is an NVDA add-on that allows you to easily save any spoken text as high-quality MP3 files using Microsoft Edge Neural TTS voices.

## What's New (v1.2.7)
* **Multi-Language Toggle:** Added an option in settings to turn multi-language reading (auto language detection) on or off. When disabled, the language selection dialog no longer appears.
* **Language-Specific Voices:** You can now select and save favorite voices for each language separately.
* **Regional Variants:** Languages with multiple regional variants (e.g., US vs UK English, Spanish, etc.) are now presented as separate options for more precise voice selection.
* **Favorite Voices:** Pin your most frequently used voices to the top by adding them to the Favorites list in the settings.
* **New Shortcut:** Convert currently selected text directly to MP3 without manually copying it.
* **Smart Compile Enhancement:** You can now convert files directly from Windows Explorer just by focusing on them, without needing to copy them to the clipboard (Ctrl+C). The add-on will automatically detect the file path.

## Keyboard Shortcuts
* **Unassigned:** Toggles the MP3 auto-saving mode (saves every utterance).
* **Unassigned:** Saves the last spoken phrase as an MP3 file.
* **Unassigned:** Compiles clipboard text to MP3.
* **Unassigned:** Compiles the currently selected text on the screen directly to MP3 (no need to copy first).
* **Unassigned:** Select a TXT, PDF, or DOCX file from your computer and convert it to MP3.
* **Unassigned:** Opens the destination folder for saved MP3s (Default: Downloads/EdgeReader_MP3_Results).
* **Unassigned:** Opens Edge Reader settings.
* **Unassigned:** Smart compile - automatically converts selection, file, focused text or clipboard to MP3.

### How to assign keyboard shortcuts?
After installing the add-on, you need to assign shortcut keys to its functions based on your keyboard layout:
1. Open the **NVDA Menu** (`NVDA + N`).
2. Go to **Preferences** -> **Input Gestures**.
3. Find and expand the **Edge Reader** category.
4. Select the function you want to assign a shortcut to.
5. Click the **Add** button and press your preferred key combination (e.g., `NVDA + Windows + E`).
6. A context menu will appear. Choose the keyboard layout (Desktop, Laptop, or all layouts).
7. Click **OK** to save.

## Settings
NVDA Menu -> Preferences -> Settings -> Edge Reader:
* Configure your default voice, rate, pitch, and favorites list.
* Enable Cyrillic to Latin transliteration or Roman numeral conversion.

## Developer & Contacts
* **Developer**: Komil Hamzayev
* **Email**: hamzayevkomil52@gmail.com
* **Telegram Channel**: [@it_help_uz](https://t.me/it_help_uz)
* **GitHub Repository**: [edgeReader](https://github.com/komilblindev/edgeReader)


## Third-Party Components & Licenses

This add-on bundles several open-source libraries:
* **edge-tts**: (GPL-3.0) Microsoft Edge TTS API wrapper.
* **aiohttp, yarl, multidict**: (Apache-2.0) HTTP components.
* **python-docx, langdetect, tabulate**: (MIT) Document & language utilities.
* **PyPDF2, lxml**: (BSD-3-Clause) PDF and XML processing.
* **certifi**: (MPL-2.0) SSL certificates.

Their inclusion complies with their respective licenses.
