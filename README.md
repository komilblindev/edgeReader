# Edge Reader - NVDA Add-on

[![License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-blue.svg)](https://www.gnu.org/licenses/gpl-2.0.html)
[![NVDA Compatibility](https://img.shields.io/badge/NVDA-2024.1%20--%202026.1-green.svg)](https://www.nvaccess.org/)

**Edge Reader** is a specialized NVDA screen reader add-on designed to convert spoken text, clipboard content, selected text, and whole documents (PDF, DOCX, TXT) into high-quality MP3 audio files using **Microsoft Edge Neural TTS** voices.

---

## рџЊџ Key Features

- **Document to Audio Conversion**: Convert entire books or documents (`.txt`, `.pdf`, `.docx`) into MP3 audio tracks.
- **Direct Selection / Clipboard to MP3**: Quickly compile selected screen text or clipboard text to MP3.
- **Smart Compile Enhancement**: Convert files directly from Windows Explorer just by focusing on them, without needing to copy them to the clipboard (Ctrl+C). The add-on will automatically detect the file path.
- **Smart Multi-Language Support**: Automatically detects multiple languages in a single text. You can easily toggle this auto-detection on or off in the add-on settings.
- **Language-Specific Voices**: Assign and manage multiple favorite voices for each language separately.
- **Regional Variants**: Languages with multiple regions (e.g., US vs UK English, Spanish, Arabic) are presented as distinct options for precise voice selection.
- **Automatic MP3 Recording Mode**: Auto-save utterances read by NVDA into separate MP3 files.
- **Favorite Voices**: Star and organize preferred voices by language for fast selection.
- **Transliteration & Pronunciation Tools**: Cyrillic to Latin transliteration, Roman numeral pronunciation, and rate/pitch adjustments.
- **Multi-language Interface**: Full support for Uzbek, Russian, and English interfaces.
- **NVDA Compatibility**: Compatible with NVDA 2024.1 up to 2026.1+.

---

## вЊЁпёЏ Shortcuts

| Shortcut | Description |
| :--- | :--- |
| `Unassigned` | Toggle MP3 auto-saving mode |
| `Unassigned` | Save the last spoken phrase as MP3 |
| `Unassigned` | Compile clipboard text to MP3 |
| `Unassigned` | Compile selected text directly to MP3 |
| `Unassigned` | Convert TXT, PDF, or DOCX document to MP3 audiobook |
| `Unassigned` | Open output folder with saved MP3 files |
| `Unassigned` | Open Edge Reader settings |
| `Unassigned` | Smart compile: converts selection, file, focused text or clipboard to MP3 |

**Note:** There are no default shortcut keys assigned. You must assign them manually by going to NVDA Menu -> Preferences -> Input Gestures -> Edge Reader.

---

## рџ‘ЁвЂЌрџ’» Developer & Contacts

- **Developer**: Komil Hamzayev
- **Email**: hamzayevkomil52@gmail.com
- **Telegram Channel**: [@it_help_uz](https://t.me/it_help_uz)
- **GitHub Repository**: [komilblindev/edgeReader](https://github.com/komilblindev/edgeReader)

---

## рџ“„ Third-Party Components & Licenses

This add-on uses the following open-source third-party libraries, which are bundled with the add-on:

*   **edge-tts**: (GPL-3.0 License) Used for communicating with Microsoft Edge TTS API.
*   **aiohttp, aiosignal, yarl, multidict**: (Apache License 2.0) Used for asynchronous HTTP requests.
*   **python-docx**: (MIT License) Used to extract text from DOCX files.
*   **PyPDF2**: (BSD-3-Clause License) Used to extract text from PDF files.
*   **langdetect**: (MIT License) Used for automatic language detection.
*   **lxml**: (BSD-3-Clause License) XML and HTML processing.
*   **tabulate**: (MIT License) Formatting data.
*   **certifi**: (MPL-2.0 License) SSL certificates.

The inclusion of these libraries complies with their respective licenses. Their source code can be found at their official repositories on PyPI and GitHub.
