import os

ru_md = """# Edge Reader

Edge Reader - это дополнение для NVDA, которое позволяет конвертировать читаемый текст в высококачественные MP3 файлы с использованием нейронных голосов Microsoft Edge TTS.

## Новое (v1.2.4)
* **Управление многоязычностью:** В настройки добавлена возможность включать/выключать многоязычное чтение (авто-определение языка). Если отключено, диалог выбора языков не появляется.
* **Голоса для каждого языка:** Теперь вы можете выбирать и сохранять избранные голоса для каждого языка отдельно.
* **Региональные варианты:** Языки с несколькими региональными вариантами (например, английский США и Великобритании, испанский и т.д.) теперь отображаются отдельно для более точного выбора голоса.
* **Избранные голоса:** Вы можете добавлять свои любимые голоса в список избранных в настройках для быстрого доступа, они всегда будут первыми в списке.
* **Новая комбинация клавиш:** Конвертируйте выделенный текст прямо в MP3 без необходимости копировать его в буфер обмена.

## Горячие клавиши
* **Без горячей клавиши:** Включает или выключает режим авто-сохранения MP3 (каждая произнесенная фраза будет сохраняться отдельно).
* **Без горячей клавиши:** Сохраняет последнюю произнесенную фразу в MP3.
* **Без горячей клавиши:** Конвертирует большой текст из буфера обмена (скопированный через Ctrl+C) в MP3.
* **Без горячей клавиши:** Конвертирует текущий выделенный на экране текст прямо в MP3 (без копирования).
* **Без горячей клавиши:** Конвертирует файл (TXT, PDF, DOCX) в аудиокнигу MP3.
* **Без горячей клавиши:** Открывает папку с сохраненными MP3 файлами (По умолчанию: Downloads/EdgeReader_MP3_Results).
* **Без горячей клавиши:** Открывает настройки Edge Reader.
* **Без горячей клавиши:** Умная компиляция - конвертирует выделение, файл, фокусный текст или буфер обмена в MP3.

### Как назначить горячие клавиши?
После установки дополнения вам нужно назначить удобные для вас горячие клавиши:
1. Откройте **Меню NVDA** (`NVDA + N`).
2. Перейдите в **Параметры (Preferences)** -> **Жесты ввода (Input Gestures)**.
3. Найдите в списке категорию **Edge Reader** и разверните её.
4. Выберите нужную функцию (например, "Открывает настройки Edge Reader").
5. Нажмите кнопку **Добавить (Add)** и нажмите удобную для вас комбинацию клавиш (например, `NVDA + Windows + E`).
6. Выберите раскладку клавиатуры (Настольный ПК, Ноутбук или для всех).
7. Нажмите **ОК**, чтобы сохранить.

## Настройки
Меню NVDA -> Параметры -> Настройки -> Edge Reader:
* Настройка основного голоса, скорости, тона и списка избранных голосов.
* Включение транслитерации (с кириллицы на латиницу) и чтения римских цифр.

## Разработчик и Контакты
* **Разработчик**: Комил Хамзаев
* **Email**: hamzayevkomil52@gmail.com
* **Telegram Канал**: [@it_help_uz](https://t.me/it_help_uz)
* **GitHub Репозиторий**: [edgeReader](https://github.com/komilblindev/edgeReader)
"""

ru_html = """<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="UTF-8">
<title>Справка Edge Reader</title>
<link rel="stylesheet" href="../style.css">
</head>
<body>
<h1>Edge Reader</h1>

<p>Edge Reader - это дополнение для NVDA, которое позволяет конвертировать читаемый текст в высококачественные MP3 файлы с использованием нейронных голосов Microsoft Edge TTS.</p>

<p>#<h1>Новое (v1.2.4)</h1></p>
<ul>
<li><strong>Управление многоязычностью:</strong> В настройки добавлена возможность включать/выключать многоязычное чтение (авто-определение языка). Если отключено, диалог выбора языков не появляется.</li>
<li><strong>Голоса для каждого языка:</strong> Теперь вы можете выбирать и сохранять избранные голоса для каждого языка отдельно.</li>
<li><strong>Региональные варианты:</strong> Языки с несколькими региональными вариантами (например, английский США и Великобритании, испанский и т.д.) теперь отображаются отдельно для более точного выбора голоса.</li>
<li><strong>Избранные голоса:</strong> Вы можете добавлять свои любимые голоса в список избранных в настройках для быстрого доступа, они всегда будут первыми в списке.</li>
<li><strong>Новая комбинация клавиш:</strong> Конвертируйте выделенный текст прямо в MP3 без необходимости копировать его в буфер обмена.</li>
</ul>

<p>#<h1>Горячие клавиши</h1></p>
<ul>
<li><strong>Без горячей клавиши:</strong> Включает или выключает режим авто-сохранения MP3 (каждая произнесенная фраза будет сохраняться отдельно).</li>
<li><strong>Без горячей клавиши:</strong> Сохраняет последнюю произнесенную фразу в MP3.</li>
<li><strong>Без горячей клавиши:</strong> Конвертирует большой текст из буфера обмена (скопированный через Ctrl+C) в MP3.</li>
<li><strong>Без горячей клавиши:</strong> Конвертирует текущий выделенный на экране текст прямо в MP3 (без копирования).</li>
<li><strong>Без горячей клавиши:</strong> Конвертирует файл (TXT, PDF, DOCX) в аудиокнигу MP3.</li>
<li><strong>Без горячей клавиши:</strong> Открывает папку с сохраненными MP3 файлами (По умолчанию: Downloads/EdgeReader_MP3_Results).</li>
<li><strong>Без горячей клавиши:</strong> Открывает настройки Edge Reader.</li>
<li><strong>Без горячей клавиши:</strong> Умная компиляция - конвертирует выделение, файл, фокусный текст или буфер обмена в MP3.</li>
</ul>

<h3>Как назначить горячие клавиши?</h3>
<p>После установки дополнения вам нужно назначить удобные для вас горячие клавиши:</p>
<ol>
<li>Откройте <strong>Меню NVDA</strong> (<code>NVDA + N</code>).</li>
<li>Перейдите в <strong>Параметры (Preferences)</strong> -&gt; <strong>Жесты ввода (Input Gestures)</strong>.</li>
<li>Найдите в списке категорию <strong>Edge Reader</strong> и разверните её.</li>
<li>Выберите нужную функцию (например, "Открывает настройки Edge Reader").</li>
<li>Нажмите кнопку <strong>Добавить (Add)</strong> и нажмите удобную для вас комбинацию клавиш (например, <code>NVDA + Windows + E</code>).</li>
<li>Выберите раскладку клавиатуры (Настольный ПК, Ноутбук или для всех).</li>
<li>Нажмите <strong>ОК</strong>, чтобы сохранить.</li>
</ol>

<h2>Настройки</h2>
<p>Меню NVDA -> Параметры -> Настройки -> Edge Reader:</p>
<ul>
<li>Настройка основного голоса, скорости, тона и списка избранных голосов.</li>
<li>Включение транслитерации (с кириллицы на латиницу) и чтения римских цифр.</li>
</ul>

<h2>Разработчик и Контакты</h2>
<p><strong>Разработчик:</strong> Комил Хамзаев<br>
<strong>Email:</strong> <a href="mailto:hamzayevkomil52@gmail.com">hamzayevkomil52@gmail.com</a><br>
<strong>Telegram Канал:</strong> <a href="https://t.me/it_help_uz">@it_help_uz</a><br>
<strong>GitHub Репозиторий:</strong> <a href="https://github.com/komilblindev/edgeReader">github.com/komilblindev/edgeReader</a></p>

</body>
</html>
"""

uz_md = """# Edge Reader 

Edge Reader - bu NVDA ekran o'quvchisi yordamida o'qilayotgan matnlarni sifatli MP3 fayllarga aylantirib beruvchi kengaytma. U Microsoft Edge brauzerining yuqori sifatli (Neural) ovozlarini ishlatadi.

## Yangiliklar (v1.2.4)
* **Ko'p tilli o'qishni boshqarish:** Sozlamalarda ko'p tilli o'qishni (avto til aniqlashni) o'chirib/yoqish imkoniyati qo'shildi. O'chirilganda, tillarni tanlash oynasi endi chiqmaydi.
* **Tillarga xos ovozlar:** Endi har bir til uchun alohida sevimli ovozlarni tanlash va saqlash mumkin.
* **Mintaqaviy tillar:** Bir nechta mintaqaviy variantga ega tillar (masalan, AQSh va Britaniya ingliz tili, ispan tili va hk.) endi alohida ko'rsatiladi va har biriga alohida ovoz tanlash mumkin.
* **Sevimli ovozlar:** O'zingiz ko'p ishlatadigan ovozlarni "Sevimli ovozlar" ro'yxatiga qo'shishingiz mumkin, shunda ular tillar ro'yxatida birinchi bo'lib chiqib turadi.
* **Yangi tugma:** Endi istalgan joyda matnni shunchaki belgilab (Ctrl+C ni bosmasdan) to'g'ridan to'g'ri MP3 qilsa bo'ladi!

## Tezkor klavishlar
* **Tugma biriktirilmagan:** MP3 avtomatik saqlash rejimini yoqish yoki o'chirish. Bu yoqilganida NVDA ning o'qigan har bir gapi avtomatik MP3 qilib yozib boriladi.
* **Tugma biriktirilmagan:** So'nggi o'qilgan gapni MP3 qilib saqlash.
* **Tugma biriktirilmagan:** Buferdagi nusxalangan (Ctrl+C qilingan) katta matnni MP3 ga aylantirish.
* **Tugma biriktirilmagan:** Ayni paytda ekranda belgilab turilgan matnni to'g'ridan to'g'ri MP3 qilib saqlash (Nusxa olish shart emas).
* **Tugma biriktirilmagan:** Kompyuterdan TXT, PDF yoki Word (DOCX) faylini tanlab, butun boshli kitobni MP3 ga o'girish.
* **Tugma biriktirilmagan:** Tayyorlangan MP3 fayllar turgan papkani ochish (Odatiy joylashuv: Downloads/EdgeReader_MP3_Results).
* **Tugma biriktirilmagan:** Edge Reader sozlamalarini ochish.
* **Tugma biriktirilmagan:** Aqlli kompylyatsiya (Smart compile) - belgilangan, diqqat markazidagi matnni, fayl yoki buferdagi matnni avtomatik aniqlab MP3 ga o'giradi.

### Qanday qilib tezkor klavishlarni biriktirish mumkin?
Dastur funksiyalaridan foydalanish uchun o'zingizga qulay tugmalarni belgilab olishingiz kerak:
1. **NVDA menyusini** oching (`NVDA + N`).
2. **Sozlamalar (Preferences)** -> **Kirish ishoralari (Input Gestures)** bo'limiga kiring.
3. Ro'yxatdan **Edge Reader** bo'limini topib, uni yoying.
4. O'zingizga kerakli funksiyani tanlang (masalan, "Edge Reader sozlamalarini ochish").
5. **Qo'shish (Add)** tugmasini bosing va o'zingiz xohlagan klavishlar kombinatsiyasini bosing (masalan, `NVDA + Windows + E`).
6. Chiqqan ro'yxatdan klaviatura turini (Stol kompyuteri, Noutbuk yoki barchasi uchun) tanlang.
7. **OK** tugmasini bosib saqlang.

## Sozlamalar
NVDA menyusi -> Preferences (Sozlamalar) -> Settings (Sozlamalar) orqali Edge Reader bo'limiga kiring:
* Asosiy til, tezlik, ton va sevimli ovozlarni shu yerdan to'g'rilashingiz mumkin.
* Kirillchadan Lotinchaga o'girish yoki Rim raqamlarini o'qish kabi qo'shimcha imkoniyatlarni ham yoqishingiz mumkin.

## Dasturchi va Aloqa
* **Dasturchi**: Komil Hamzayev
* **Email**: hamzayevkomil52@gmail.com
* **Telegram Kanal**: [@it_help_uz](https://t.me/it_help_uz)
* **GitHub Repozitoriya**: [edgeReader](https://github.com/komilblindev/edgeReader)
"""

uz_html = """<!DOCTYPE html>
<html lang="uz">
<head>
<meta charset="UTF-8">
<title>Edge Reader yordam sahifasi</title>
<link rel="stylesheet" href="../style.css">
</head>
<body>
<h1>Edge Reader </h1>

<p>Edge Reader - bu NVDA ekran o'quvchisi yordamida o'qilayotgan matnlarni sifatli MP3 fayllarga aylantirib beruvchi kengaytma. U Microsoft Edge brauzerining yuqori sifatli (Neural) ovozlarini ishlatadi.</p>

<p>#<h1>Yangiliklar (v1.2.4)</h1></p>
<ul>
<li><strong>Ko'p tilli o'qishni boshqarish:</strong> Sozlamalarda ko'p tilli o'qishni (avto til aniqlashni) o'chirib/yoqish imkoniyati qo'shildi. O'chirilganda, tillarni tanlash oynasi endi chiqmaydi.</li>
<li><strong>Tillarga xos ovozlar:</strong> Endi har bir til uchun alohida sevimli ovozlarni tanlash va saqlash mumkin.</li>
<li><strong>Mintaqaviy tillar:</strong> Bir nechta mintaqaviy variantga ega tillar (masalan, AQSh va Britaniya ingliz tili, ispan tili va hk.) endi alohida ko'rsatiladi va har biriga alohida ovoz tanlash mumkin.</li>
<li><strong>Sevimli ovozlar:</strong> O'zingiz ko'p ishlatadigan ovozlarni "Sevimli ovozlar" ro'yxatiga qo'shishingiz mumkin, shunda ular tillar ro'yxatida birinchi bo'lib chiqib turadi.</li>
<li><strong>Yangi tugma:</strong> Endi istalgan joyda matnni shunchaki belgilab (Ctrl+C ni bosmasdan) to'g'ridan to'g'ri MP3 qilsa bo'ladi!</li>
</ul>

<p>#<h1>Tezkor klavishlar</h1></p>
<ul>
<li><strong>Tugma biriktirilmagan:</strong> MP3 avtomatik saqlash rejimini yoqish yoki o'chirish. Bu yoqilganida NVDA ning o'qigan har bir gapi avtomatik MP3 qilib yozib boriladi.</li>
<li><strong>Tugma biriktirilmagan:</strong> So'nggi o'qilgan gapni MP3 qilib saqlash.</li>
<li><strong>Tugma biriktirilmagan:</strong> Buferdagi nusxalangan (Ctrl+C qilingan) katta matnni MP3 ga aylantirish.</li>
<li><strong>Tugma biriktirilmagan:</strong> Ayni paytda ekranda belgilab turilgan matnni to'g'ridan to'g'ri MP3 qilib saqlash (Nusxa olish shart emas).</li>
<li><strong>Tugma biriktirilmagan:</strong> Kompyuterdan TXT, PDF yoki Word (DOCX) faylini tanlab, butun boshli kitobni MP3 ga o'girish.</li>
<li><strong>Tugma biriktirilmagan:</strong> Tayyorlangan MP3 fayllar turgan papkani ochish (Odatiy joylashuv: Downloads/EdgeReader_MP3_Results).</li>
<li><strong>Tugma biriktirilmagan:</strong> Edge Reader sozlamalarini ochish.</li>
<li><strong>Tugma biriktirilmagan:</strong> Aqlli kompylyatsiya (Smart compile) - belgilangan, diqqat markazidagi matnni, fayl yoki buferdagi matnni avtomatik aniqlab MP3 ga o'giradi.</li>
</ul>

<h3>Qanday qilib tezkor klavishlarni biriktirish mumkin?</h3>
<p>Dastur funksiyalaridan foydalanish uchun o'zingizga qulay tugmalarni belgilab olishingiz kerak:</p>
<ol>
<li><strong>NVDA menyusini</strong> oching (<code>NVDA + N</code>).</li>
<li><strong>Sozlamalar (Preferences)</strong> -&gt; <strong>Kirish ishoralari (Input Gestures)</strong> bo'limiga kiring.</li>
<li>Ro'yxatdan <strong>Edge Reader</strong> bo'limini topib, uni yoying.</li>
<li>O'zingizga kerakli funksiyani tanlang (masalan, "Edge Reader sozlamalarini ochish").</li>
<li><strong>Qo'shish (Add)</strong> tugmasini bosing va o'zingiz xohlagan klavishlar kombinatsiyasini bosing (masalan, <code>NVDA + Windows + E</code>).</li>
<li>Chiqqan ro'yxatdan klaviatura turini (Stol kompyuteri, Noutbuk yoki barchasi uchun) tanlang.</li>
<li><strong>OK</strong> tugmasini bosib saqlang.</li>
</ol>

<h2>Sozlamalar</h2>
<p>NVDA menyusi -> Preferences (Sozlamalar) -> Settings (Sozlamalar) orqali Edge Reader bo'limiga kiring:</p>
<ul>
<li>Asosiy til, tezlik, ton va sevimli ovozlarni shu yerdan to'g'rilashingiz mumkin.</li>
<li>Kirillchadan Lotinchaga o'girish yoki Rim raqamlarini o'qish kabi qo'shimcha imkoniyatlarni ham yoqishingiz mumkin.</li>
</ul>

<h2>Dasturchi va Aloqa</h2>
<p><strong>Dasturchi:</strong> Komil Hamzayev<br>
<strong>Email:</strong> <a href="mailto:hamzayevkomil52@gmail.com">hamzayevkomil52@gmail.com</a><br>
<strong>Telegram Kanal:</strong> <a href="https://t.me/it_help_uz">@it_help_uz</a><br>
<strong>GitHub Repozitoriya:</strong> <a href="https://github.com/komilblindev/edgeReader">github.com/komilblindev/edgeReader</a></p>

</body>
</html>
"""

def write_file(path, content):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

write_file('addon/doc/ru/readme.md', ru_md)
write_file('addon/doc/ru/readme.html', ru_html)
write_file('addon/doc/uz/readme.md', uz_md)
write_file('addon/doc/uz/readme.html', uz_html)
