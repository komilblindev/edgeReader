import os
import markdown

langs = ['en', 'ru', 'uz']
base_dir = r"C:\Users\komil\.gemini\antigravity\scratch\edgeReader\addon\doc"

third_party_text_en = """

## Third-Party Components & Licenses

This add-on bundles several open-source libraries:
* **edge-tts**: (GPL-3.0) Microsoft Edge TTS API wrapper.
* **aiohttp, yarl, multidict**: (Apache-2.0) HTTP components.
* **python-docx, langdetect, tabulate**: (MIT) Document & language utilities.
* **PyPDF2, lxml**: (BSD-3-Clause) PDF and XML processing.
* **certifi**: (MPL-2.0) SSL certificates.

Their inclusion complies with their respective licenses.
"""

third_party_text_ru = """

## Лицензии и сторонние компоненты

Это дополнение использует следующие библиотеки с открытым исходным кодом:
* **edge-tts**: (GPL-3.0)
* **aiohttp, yarl, multidict**: (Apache-2.0)
* **python-docx, langdetect, tabulate**: (MIT)
* **PyPDF2, lxml**: (BSD-3-Clause)
* **certifi**: (MPL-2.0)

Включение этих библиотек соответствует их лицензиям.
"""

third_party_text_uz = """

## Litsenziyalar va uchinchi tomon komponentlari

Bu kengaytma quyidagi ochiq kodli kutubxonalardan foydalanadi:
* **edge-tts**: (GPL-3.0)
* **aiohttp, yarl, multidict**: (Apache-2.0)
* **python-docx, langdetect, tabulate**: (MIT)
* **PyPDF2, lxml**: (BSD-3-Clause)
* **certifi**: (MPL-2.0)

Ushbu kutubxonalardan foydalanish ularning litsenziyalariga to'liq mos keladi.
"""

texts = {'en': third_party_text_en, 'ru': third_party_text_ru, 'uz': third_party_text_uz}

for lang in langs:
    md_path = os.path.join(base_dir, lang, "readme.md")
    html_path = os.path.join(base_dir, lang, "readme.html")
    
    if os.path.exists(md_path):
        with open(md_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # Update version to 1.2.7
        content = content.replace("1.2.4", "1.2.7")
        content = content.replace("1.2.6", "1.2.7")
        
        # Add third-party notices
        if "edge-tts" not in content:
            content += texts[lang]
            
        with open(md_path, 'w', encoding='utf-8') as f:
            f.write(content)
            
        # Convert to HTML
        html_body = markdown.markdown(content, extensions=['tables'])
        html_content = f'''<!DOCTYPE html>
<html lang="{lang}">
<head>
<meta charset="UTF-8">
<title>Edge Reader</title>
<link rel="stylesheet" href="../style.css">
</head>
<body>
{html_body}
</body>
</html>'''
        with open(html_path, 'w', encoding='utf-8') as f:
            f.write(html_content)
        print(f"Updated {lang} docs")
