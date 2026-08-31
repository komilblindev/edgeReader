# Edge Reader 

Edge Reader - bu NVDA ekran o'quvchisi yordamida o'qilayotgan matnlarni sifatli MP3 fayllarga aylantirib beruvchi kengaytma. U Microsoft Edge brauzerining yuqori sifatli (Neural) ovozlarini ishlatadi.

## Yangiliklar (v1.2.7)
* **Ko'p tilli o'qishni boshqarish:** Sozlamalarda ko'p tilli o'qishni (avto til aniqlashni) o'chirib/yoqish imkoniyati qo'shildi. O'chirilganda, tillarni tanlash oynasi endi chiqmaydi.
* **Tillarga xos ovozlar:** Endi har bir til uchun alohida sevimli ovozlarni tanlash va saqlash mumkin.
* **Mintaqaviy tillar:** Bir nechta mintaqaviy variantga ega tillar (masalan, AQSh va Britaniya ingliz tili, ispan tili va hk.) endi alohida ko'rsatiladi va har biriga alohida ovoz tanlash mumkin.
* **Sevimli ovozlar:** O'zingiz ko'p ishlatadigan ovozlarni "Sevimli ovozlar" ro'yxatiga qo'shishingiz mumkin, shunda ular tillar ro'yxatida birinchi bo'lib chiqib turadi.
* **Yangi tugma:** Endi istalgan joyda matnni shunchaki belgilab (Ctrl+C ni bosmasdan) to'g'ridan to'g'ri MP3 qilsa bo'ladi!
* **Aqlli kompylyatsiyaning yangilanishi:** Endi Windows Explorer (Provodnik) da fayl ustida turganda uni buferga nusxalamasdan (Ctrl+C bosmasdan) to'g'ridan to'g'ri o'qish mumkin. Dastur fayl manzilini avtomatik aniqlaydi.

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


## Litsenziyalar va uchinchi tomon komponentlari

Bu kengaytma quyidagi ochiq kodli kutubxonalardan foydalanadi:
* **edge-tts**: (GPL-3.0)
* **aiohttp, yarl, multidict**: (Apache-2.0)
* **python-docx, langdetect, tabulate**: (MIT)
* **PyPDF2, lxml**: (BSD-3-Clause)
* **certifi**: (MPL-2.0)

Ushbu kutubxonalardan foydalanish ularning litsenziyalariga to'liq mos keladi.
