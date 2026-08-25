import os

en_md = """# Edge Reader

Edge Reader is an NVDA add-on that allows you to easily save any spoken text as high-quality MP3 files using Microsoft Edge Neural TTS voices.

## What's New (v1.2.4)
* **Multi-Language Toggle:** Added an option in settings to turn multi-language reading (auto language detection) on or off. When disabled, the language selection dialog no longer appears.
* **Language-Specific Voices:** You can now select and save favorite voices for each language separately.
* **Regional Variants:** Languages with multiple regional variants (e.g., US vs UK English, Spanish, etc.) are now presented as separate options for more precise voice selection.
* **Favorite Voices:** Pin your most frequently used voices to the top by adding them to the Favorites list in the settings.
* **New Shortcut:** Convert currently selected text directly to MP3 without manually copying it.

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
"""

en_html = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>Edge Reader Help</title>
<link rel="stylesheet" href="../style.css">
</head>
<body>
<h1>Edge Reader</h1>

<p>Edge Reader is an NVDA add-on that allows you to easily save any spoken text as high-quality MP3 files using Microsoft Edge Neural TTS voices.</p>

<p>#<h1>What's New (v1.2.4)</h1></p>
<ul>
<li><strong>Multi-Language Toggle:</strong> Added an option in settings to turn multi-language reading (auto language detection) on or off. When disabled, the language selection dialog no longer appears.</li>
<li><strong>Language-Specific Voices:</strong> You can now select and save favorite voices for each language separately.</li>
<li><strong>Regional Variants:</strong> Languages with multiple regional variants (e.g., US vs UK English, Spanish, etc.) are now presented as separate options for more precise voice selection.</li>
<li><strong>Favorite Voices:</strong> Pin your most frequently used voices to the top by adding them to the Favorites list in the settings.</li>
<li><strong>New Shortcut:</strong> Convert currently selected text directly to MP3 without manually copying it.</li>
</ul>

<p>#<h1>Keyboard Shortcuts</h1></p>
<ul>
<li><strong>Unassigned:</strong> Toggles the MP3 auto-saving mode (saves every utterance).</li>
<li><strong>Unassigned:</strong> Saves the last spoken phrase as an MP3 file.</li>
<li><strong>Unassigned:</strong> Compiles clipboard text to MP3.</li>
<li><strong>Unassigned:</strong> Compiles the currently selected text on the screen directly to MP3 (no need to copy first).</li>
<li><strong>Unassigned:</strong> Select a TXT, PDF, or DOCX file from your computer and convert it to MP3.</li>
<li><strong>Unassigned:</strong> Opens the destination folder for saved MP3s (Default: Downloads/EdgeReader_MP3_Results).</li>
<li><strong>Unassigned:</strong> Opens Edge Reader settings.</li>
<li><strong>Unassigned:</strong> Smart compile - automatically converts selection, file, focused text or clipboard to MP3.</li>
</ul>

<h3>How to assign keyboard shortcuts?</h3>
<p>After installing the add-on, you need to assign shortcut keys to its functions based on your keyboard layout:</p>
<ol>
<li>Open the <strong>NVDA Menu</strong> (<code>NVDA + N</code>).</li>
<li>Go to <strong>Preferences</strong> -&gt; <strong>Input Gestures</strong>.</li>
<li>Find and expand the <strong>Edge Reader</strong> category.</li>
<li>Select the function you want to assign a shortcut to.</li>
<li>Click the <strong>Add</strong> button and press your preferred key combination (e.g., <code>NVDA + Windows + E</code>).</li>
<li>A context menu will appear. Choose the keyboard layout (Desktop, Laptop, or all layouts).</li>
<li>Click <strong>OK</strong> to save.</li>
</ol>

<h2>Settings</h2>
<p>NVDA Menu -> Preferences -> Settings -> Edge Reader:</p>
<ul>
<li>Configure your default voice, rate, pitch, and favorites list.</li>
<li>Enable Cyrillic to Latin transliteration or Roman numeral conversion.</li>
</ul>

<h2>Developer & Contacts</h2>
<p><strong>Developer:</strong> Komil Hamzayev<br>
<strong>Email:</strong> <a href="mailto:hamzayevkomil52@gmail.com">hamzayevkomil52@gmail.com</a><br>
<strong>Telegram Channel:</strong> <a href="https://t.me/it_help_uz">@it_help_uz</a><br>
<strong>GitHub Repository:</strong> <a href="https://github.com/komilblindev/edgeReader">github.com/komilblindev/edgeReader</a></p>

</body>
</html>
"""

with open('addon/doc/en/readme.md', 'w', encoding='utf-8') as f:
    f.write(en_md)

with open('addon/doc/en/readme.html', 'w', encoding='utf-8') as f:
    f.write(en_html)
