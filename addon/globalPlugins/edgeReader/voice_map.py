# -*- coding: utf-8 -*-

# Til kodi -> manba (inglizcha) til nomi. Bu satrlar _() orqali locale/*/LC_MESSAGES
# katalogida uz/ru/en tillariga tarjima qilingan (lazy - faqat chaqirilganda hisoblanadi).
_LANG_NAME_KEYS = {
	'af': "Afrikaans", 'am': "Amharic", 'ar': "Arabic", 'az': "Azerbaijani",
	'sq': "Albanian", 'bn': "Bengali", 'bs': "Bosnian", 'bg': "Bulgarian",
	'my': "Burmese", 'ca': "Catalan", 'zh': "Chinese", 'zh-cn': "Chinese",
	'zh-tw': "Chinese (Taiwan)", 'hr': "Croatian", 'cs': "Czech", 'da': "Danish",
	'nl': "Dutch", 'en': "English", 'et': "Estonian", 'fil': "Filipino",
	'fi': "Finnish", 'fr': "French", 'gl': "Galician", 'ka': "Georgian",
	'de': "German", 'el': "Greek", 'gu': "Gujarati", 'he': "Hebrew",
	'hi': "Hindi", 'hu': "Hungarian", 'is': "Icelandic", 'id': "Indonesian",
	'iu': "Inuktitut", 'ga': "Irish", 'it': "Italian", 'ja': "Japanese",
	'jv': "Javanese", 'kn': "Kannada", 'kk': "Kazakh", 'km': "Khmer",
	'ko': "Korean", 'ky': "Kyrgyz", 'lo': "Lao", 'lv': "Latvian",
	'lt': "Lithuanian", 'mk': "Macedonian", 'ms': "Malay", 'ml': "Malayalam",
	'mt': "Maltese", 'mr': "Marathi", 'mn': "Mongolian", 'ne': "Nepali",
	'no': "Norwegian", 'nb': "Norwegian", 'ps': "Pashto", 'fa': "Persian",
	'pl': "Polish", 'pt': "Portuguese", 'pa': "Punjabi", 'ro': "Romanian",
	'ru': "Russian", 'sr': "Serbian", 'si': "Sinhala", 'sk': "Slovak",
	'sl': "Slovenian", 'so': "Somali", 'es': "Spanish", 'su': "Sundanese",
	'sw': "Swahili", 'sv': "Swedish", 'ta': "Tamil", 'te': "Telugu",
	'th': "Thai", 'tr': "Turkish", 'uk': "Ukrainian", 'ur': "Urdu",
	'uz': "Uzbek", 'vi': "Vietnamese", 'cy': "Welsh", 'zu': "Zulu",
	'tl': "Filipino",
}


def get_lang_display_name(lang_code):
	"""Til kodi uchun 'kod — to'liq nom' ko'rinishidagi yorliqni qaytaradi (lokalizatsiyalangan)."""
	base_code = lang_code.split('-')[0].lower()
	key = _LANG_NAME_KEYS.get(lang_code.lower()) or _LANG_NAME_KEYS.get(base_code)
	if key:
		return "{code} \u2014 {name}".format(code=lang_code, name=_(key))
	return lang_code


# Davlat/skript kodi -> manba (inglizcha) nomi. _() orqali uz/ru/en tillariga tarjima qilinadi.
_COUNTRY_NAME_KEYS = {
	'AE': "United Arab Emirates", 'AF': "Afghanistan", 'AL': "Albania", 'AR': "Argentina",
	'AT': "Austria", 'AU': "Australia", 'AZ': "Azerbaijan", 'BA': "Bosnia and Herzegovina",
	'BD': "Bangladesh", 'BE': "Belgium", 'BG': "Bulgaria", 'BH': "Bahrain",
	'BO': "Bolivia", 'BR': "Brazil", 'CA': "Canada", 'CH': "Switzerland",
	'CL': "Chile", 'CN': "China", 'CO': "Colombia", 'CR': "Costa Rica",
	'CU': "Cuba", 'CZ': "Czechia", 'Cans': "Canadian Syllabics", 'DE': "Germany",
	'DK': "Denmark", 'DO': "Dominican Republic", 'DZ': "Algeria", 'EC': "Ecuador",
	'EE': "Estonia", 'EG': "Egypt", 'ES': "Spain", 'ET': "Ethiopia",
	'FI': "Finland", 'FR': "France", 'GB': "United Kingdom", 'GE': "Georgia",
	'GQ': "Equatorial Guinea", 'GR': "Greece", 'GT': "Guatemala", 'HK': "Hong Kong",
	'HN': "Honduras", 'HR': "Croatia", 'HU': "Hungary", 'ID': "Indonesia",
	'IE': "Ireland", 'IL': "Israel", 'IN': "India", 'IQ': "Iraq",
	'IR': "Iran", 'IS': "Iceland", 'IT': "Italy", 'JO': "Jordan",
	'JP': "Japan", 'KE': "Kenya", 'KH': "Cambodia", 'KR': "South Korea",
	'KW': "Kuwait", 'KZ': "Kazakhstan", 'LA': "Laos", 'LB': "Lebanon",
	'LK': "Sri Lanka", 'LT': "Lithuania", 'LV': "Latvia", 'LY': "Libya",
	'Latn': "Latin Script", 'MA': "Morocco", 'MK': "North Macedonia", 'MM': "Myanmar",
	'MN': "Mongolia", 'MT': "Malta", 'MX': "Mexico", 'MY': "Malaysia",
	'NG': "Nigeria", 'NI': "Nicaragua", 'NL': "Netherlands", 'NO': "Norway",
	'NP': "Nepal", 'NZ': "New Zealand", 'OM': "Oman", 'PA': "Panama",
	'PE': "Peru", 'PH': "Philippines", 'PK': "Pakistan", 'PL': "Poland",
	'PR': "Puerto Rico", 'PT': "Portugal", 'PY': "Paraguay", 'QA': "Qatar",
	'RO': "Romania", 'RS': "Serbia", 'RU': "Russia", 'SA': "Saudi Arabia",
	'SE': "Sweden", 'SG': "Singapore", 'SI': "Slovenia", 'SK': "Slovakia",
	'SO': "Somalia", 'SV': "El Salvador", 'SY': "Syria", 'TH': "Thailand",
	'TN': "Tunisia", 'TR': "Turkey", 'TW': "Taiwan", 'TZ': "Tanzania",
	'UA': "Ukraine", 'US': "United States", 'UY': "Uruguay", 'UZ': "Uzbekistan",
	'VE': "Venezuela", 'VN': "Vietnam", 'YE': "Yemen", 'ZA': "South Africa",
}


def _get_regions_for_base(base_lang):
	"""Berilgan asosiy til kodi ('en' kabi) uchun VOICE_DB'dagi barcha noyob
	davlat/skript kodlarini ('US', 'GB', ... yoki 'Latn'/'Cans') qaytaradi."""
	voices = VOICE_DB.get(base_lang, [])
	return sorted(set(v.split('-')[1] for v in voices if len(v.split('-')) >= 2))


def get_selectable_languages():
	"""Tanlash uchun til/lokal kodlari ro'yxatini qaytaradi. Bir nechta davlat
	varianti bo'lgan tillar (masalan 'en') alohida-alohida ('en-US', 'en-GB', ...)
	qaytariladi; faqat bitta variantli tillar asosiy kod bilan qaytariladi."""
	result = []
	for lang in VOICE_DB.keys():
		regions = _get_regions_for_base(lang)
		if len(regions) <= 1:
			result.append(lang)
		else:
			for r in regions:
				result.append("{}-{}".format(lang, r))
	return sorted(result)


def get_locale_display_name(locale_code):
	"""Kod uchun (masalan 'en-US') 'kod — Til (Davlat)' ko'rinishidagi yorliqni
	qaytaradi. Agar kodda davlat qismi bo'lmasa (yoki til bitta variantli bo'lsa),
	oddiy get_lang_display_name natijasini qaytaradi."""
	if '-' not in locale_code:
		return get_lang_display_name(locale_code)
	base, region = locale_code.split('-', 1)
	if len(_get_regions_for_base(base)) <= 1:
		return get_lang_display_name(base)
	lang_key = _LANG_NAME_KEYS.get(base.lower())
	lang_name = _(lang_key) if lang_key else base
	country_key = _COUNTRY_NAME_KEYS.get(region)
	country_name = _(country_key) if country_key else region
	return "{code} \u2014 {lang} ({country})".format(code=locale_code, lang=lang_name, country=country_name)


def get_voices_for_locale(locale_code):
	"""Berilgan kod ('uz' yoki 'en-US') uchun VOICE_DB'dan mos ovozlar ro'yxatini qaytaradi."""
	if '-' not in locale_code:
		return list(VOICE_DB.get(locale_code, []))
	base, region = locale_code.split('-', 1)
	return [v for v in VOICE_DB.get(base, []) if len(v.split('-')) >= 2 and v.split('-')[1] == region]


def get_locale_key_for_voice(voice_id):
	"""Ovoz ID'si ('en-US-GuyNeural' kabi) uchun mos tanlanadigan til kodini
	('en-US' yoki bitta variantli tillarda 'uz') aniqlaydi."""
	parts = voice_id.split('-')
	if len(parts) < 2:
		return voice_id
	base, region = parts[0], parts[1]
	if len(_get_regions_for_base(base)) > 1:
		return "{}-{}".format(base, region)
	return base


def get_multi_lang_voices(config_string):
	"""'multi_lang_voices' konfiguratsiyasidagi JSON qatorini {lang: [voices]} dict qilib qaytaradi.
	Xato yoki bo'sh bo'lsa - bo'sh dict qaytaradi."""
	import json
	try:
		data = json.loads(config_string) if config_string else {}
		if not isinstance(data, dict):
			return {}
		# Har bir qiymat ro'yxat (list) ekanligini kafolatlaymiz
		return {k: list(v) for k, v in data.items() if isinstance(v, (list, tuple))}
	except (ValueError, TypeError):
		return {}


def dump_multi_lang_voices(voices_dict):
	"""{lang: [voices]} dict'ni config'ga yozish uchun JSON qatorga aylantiradi."""
	import json
	try:
		return json.dumps(voices_dict, ensure_ascii=False)
	except (ValueError, TypeError):
		return "{}"


def get_voices_for_lang(lang, config_string):
	"""Berilgan (aniqlangan) til uchun sozlamalarda belgilangan ovozlar ro'yxatini
	qaytaradi. Sozlamalarda til kod bo'yicha ('en') yoki lokal-variant bo'yicha
	('en-US', 'en-GB', ...) saqlangan bo'lishi mumkin - shu asosiy kodga mos
	barcha buketlar birlashtirib qaytariladi. Hech narsa topilmasa - VOICE_DB
	dagi standart ro'yxat qaytadi."""
	multi_voices = get_multi_lang_voices(config_string)
	base = lang.split('-')[0].lower()
	collected = []
	for key, voices in multi_voices.items():
		if key.split('-')[0].lower() == base:
			for v in voices:
				if v not in collected:
					collected.append(v)
	if collected:
		return collected
	return list(VOICE_DB.get(base, []))


VOICE_MAP = {
	'af': 'af-ZA-AdriNeural', 'am': 'am-ET-AmehaNeural', 'ar': 'ar-SA-HamedNeural',
	'bg': 'bg-BG-BorislavNeural', 'bn': 'bn-IN-BashkarNeural', 'ca': 'ca-ES-EnricNeural',
	'cs': 'cs-CZ-AntoninNeural', 'cy': 'cy-GB-AledNeural', 'da': 'da-DK-ChristoffelNeural',
	'de': 'de-DE-KillianNeural', 'el': 'el-GR-NestorasNeural', 'en': 'en-US-GuyNeural',
	'es': 'es-ES-AlvaroNeural', 'et': 'et-EE-KertNeural', 'fa': 'fa-IR-FaridNeural',
	'fi': 'fi-FI-HarriNeural', 'fr': 'fr-FR-HenriNeural', 'gu': 'gu-IN-NiranjanNeural',
	'he': 'he-IL-AvriNeural', 'hi': 'hi-IN-MadhurNeural', 'hr': 'hr-HR-SreckoNeural',
	'hu': 'hu-HU-TamasNeural', 'id': 'id-ID-ArdiNeural', 'it': 'it-IT-DiegoNeural',
	'ja': 'ja-JP-KeitaNeural', 'kn': 'kn-IN-GaganNeural', 'ko': 'ko-KR-InJoonNeural',
	'lt': 'lt-LT-LeonasNeural', 'lv': 'lv-LV-NilsNeural', 'mk': 'mk-MK-AleksandarNeural',
	'ml': 'ml-IN-MidhunNeural', 'mr': 'mr-IN-ManoharNeural', 'nl': 'nl-NL-MaartenNeural',
	'no': 'nb-NO-FinnNeural', 'pa': 'pa-IN-OjasNeural', 'pl': 'pl-PL-MarekNeural',
	'pt': 'pt-BR-AntonioNeural', 'ro': 'ro-RO-EmilNeural', 'ru': 'ru-RU-DmitryNeural',
	'sk': 'sk-SK-LukasNeural', 'sl': 'sl-SI-RokNeural', 'sq': 'sq-AL-IlirNeural',
	'sv': 'sv-SE-MattiasNeural', 'sw': 'sw-KE-RafikiNeural', 'ta': 'ta-IN-ValluvarNeural',
	'te': 'te-IN-MohanNeural', 'th': 'th-TH-NiwatNeural', 'tl': 'fil-PH-AngeloNeural',
	'tr': 'tr-TR-EmelNeural', 'uk': 'uk-UA-OstapNeural', 'ur': 'ur-PK-AsadNeural',
	'vi': 'vi-VN-NamMinhNeural', 'zh-cn': 'zh-CN-YunxiNeural', 'zh-tw': 'zh-TW-YunJheNeural',
	'uz': 'uz-UZ-MadinaNeural', 'kk': 'kk-KZ-DauletNeural', 'ky': 'ky-KG-SyymykNeural'
}

VOICE_DB = {'af': ['af-ZA-AdriNeural', 'af-ZA-WillemNeural'], 'sq': ['sq-AL-AnilaNeural', 'sq-AL-IlirNeural'], 'am': ['am-ET-AmehaNeural', 'am-ET-MekdesNeural'], 'ar': ['ar-DZ-AminaNeural', 'ar-DZ-IsmaelNeural', 'ar-BH-AliNeural', 'ar-BH-LailaNeural', 'ar-EG-SalmaNeural', 'ar-EG-ShakirNeural', 'ar-IQ-BasselNeural', 'ar-IQ-RanaNeural', 'ar-JO-SanaNeural', 'ar-JO-TaimNeural', 'ar-KW-FahedNeural', 'ar-KW-NouraNeural', 'ar-LB-LaylaNeural', 'ar-LB-RamiNeural', 'ar-LY-ImanNeural', 'ar-LY-OmarNeural', 'ar-MA-JamalNeural', 'ar-MA-MounaNeural', 'ar-OM-AbdullahNeural', 'ar-OM-AyshaNeural', 'ar-QA-AmalNeural', 'ar-QA-MoazNeural', 'ar-SA-HamedNeural', 'ar-SA-ZariyahNeural', 'ar-SY-AmanyNeural', 'ar-SY-LaithNeural', 'ar-TN-HediNeural', 'ar-TN-ReemNeural', 'ar-AE-FatimaNeural', 'ar-AE-HamdanNeural', 'ar-YE-MaryamNeural', 'ar-YE-SalehNeural'], 'az': ['az-AZ-BabekNeural', 'az-AZ-BanuNeural'], 'bn': ['bn-BD-NabanitaNeural', 'bn-BD-PradeepNeural', 'bn-IN-BashkarNeural', 'bn-IN-TanishaaNeural'], 'bs': ['bs-BA-VesnaNeural', 'bs-BA-GoranNeural'], 'bg': ['bg-BG-BorislavNeural', 'bg-BG-KalinaNeural'], 'my': ['my-MM-NilarNeural', 'my-MM-ThihaNeural'], 'ca': ['ca-ES-EnricNeural', 'ca-ES-JoanaNeural'], 'zh': ['zh-HK-HiuGaaiNeural', 'zh-HK-HiuMaanNeural', 'zh-HK-WanLungNeural', 'zh-CN-XiaoxiaoNeural', 'zh-CN-XiaoyiNeural', 'zh-CN-YunjianNeural', 'zh-CN-YunxiNeural', 'zh-CN-YunxiaNeural', 'zh-CN-YunyangNeural', 'zh-CN-liaoning-XiaobeiNeural', 'zh-TW-HsiaoChenNeural', 'zh-TW-YunJheNeural', 'zh-TW-HsiaoYuNeural', 'zh-CN-shaanxi-XiaoniNeural'], 'hr': ['hr-HR-GabrijelaNeural', 'hr-HR-SreckoNeural'], 'cs': ['cs-CZ-AntoninNeural', 'cs-CZ-VlastaNeural'], 'da': ['da-DK-ChristelNeural', 'da-DK-JeppeNeural'], 'nl': ['nl-BE-ArnaudNeural', 'nl-BE-DenaNeural', 'nl-NL-ColetteNeural', 'nl-NL-FennaNeural', 'nl-NL-MaartenNeural'], 'en': ['en-AU-WilliamMultilingualNeural', 'en-AU-NatashaNeural', 'en-CA-ClaraNeural', 'en-CA-LiamNeural', 'en-HK-YanNeural', 'en-HK-SamNeural', 'en-IN-NeerjaExpressiveNeural', 'en-IN-NeerjaNeural', 'en-IN-PrabhatNeural', 'en-IE-ConnorNeural', 'en-IE-EmilyNeural', 'en-KE-AsiliaNeural', 'en-KE-ChilembaNeural', 'en-NZ-MitchellNeural', 'en-NZ-MollyNeural', 'en-NG-AbeoNeural', 'en-NG-EzinneNeural', 'en-PH-JamesNeural', 'en-PH-RosaNeural', 'en-US-AvaNeural', 'en-US-AndrewNeural', 'en-US-EmmaNeural', 'en-US-BrianNeural', 'en-SG-LunaNeural', 'en-SG-WayneNeural', 'en-ZA-LeahNeural', 'en-ZA-LukeNeural', 'en-TZ-ElimuNeural', 'en-TZ-ImaniNeural', 'en-GB-LibbyNeural', 'en-GB-MaisieNeural', 'en-GB-RyanNeural', 'en-GB-SoniaNeural', 'en-GB-ThomasNeural', 'en-US-AnaNeural', 'en-US-AndrewMultilingualNeural', 'en-US-AriaNeural', 'en-US-AvaMultilingualNeural', 'en-US-BrianMultilingualNeural', 'en-US-ChristopherNeural', 'en-US-EmmaMultilingualNeural', 'en-US-EricNeural', 'en-US-GuyNeural', 'en-US-JennyNeural', 'en-US-MichelleNeural', 'en-US-RogerNeural', 'en-US-SteffanNeural'], 'et': ['et-EE-AnuNeural', 'et-EE-KertNeural'], 'fil': ['fil-PH-AngeloNeural', 'fil-PH-BlessicaNeural'], 'fi': ['fi-FI-HarriNeural', 'fi-FI-NooraNeural'], 'fr': ['fr-BE-CharlineNeural', 'fr-BE-GerardNeural', 'fr-CA-ThierryNeural', 'fr-CA-AntoineNeural', 'fr-CA-JeanNeural', 'fr-CA-SylvieNeural', 'fr-FR-VivienneMultilingualNeural', 'fr-FR-RemyMultilingualNeural', 'fr-FR-DeniseNeural', 'fr-FR-EloiseNeural', 'fr-FR-HenriNeural', 'fr-CH-ArianeNeural', 'fr-CH-FabriceNeural'], 'gl': ['gl-ES-RoiNeural', 'gl-ES-SabelaNeural'], 'ka': ['ka-GE-EkaNeural', 'ka-GE-GiorgiNeural'], 'de': ['de-AT-IngridNeural', 'de-AT-JonasNeural', 'de-DE-SeraphinaMultilingualNeural', 'de-DE-FlorianMultilingualNeural', 'de-DE-AmalaNeural', 'de-DE-ConradNeural', 'de-DE-KatjaNeural', 'de-DE-KillianNeural', 'de-CH-JanNeural', 'de-CH-LeniNeural'], 'el': ['el-GR-AthinaNeural', 'el-GR-NestorasNeural'], 'gu': ['gu-IN-DhwaniNeural', 'gu-IN-NiranjanNeural'], 'he': ['he-IL-AvriNeural', 'he-IL-HilaNeural'], 'hi': ['hi-IN-MadhurNeural', 'hi-IN-SwaraNeural'], 'hu': ['hu-HU-NoemiNeural', 'hu-HU-TamasNeural'], 'is': ['is-IS-GudrunNeural', 'is-IS-GunnarNeural'], 'id': ['id-ID-ArdiNeural', 'id-ID-GadisNeural'], 'iu': ['iu-Latn-CA-SiqiniqNeural', 'iu-Latn-CA-TaqqiqNeural', 'iu-Cans-CA-SiqiniqNeural', 'iu-Cans-CA-TaqqiqNeural'], 'ga': ['ga-IE-ColmNeural', 'ga-IE-OrlaNeural'], 'it': ['it-IT-GiuseppeMultilingualNeural', 'it-IT-DiegoNeural', 'it-IT-ElsaNeural', 'it-IT-IsabellaNeural'], 'ja': ['ja-JP-KeitaNeural', 'ja-JP-NanamiNeural'], 'jv': ['jv-ID-DimasNeural', 'jv-ID-SitiNeural'], 'kn': ['kn-IN-GaganNeural', 'kn-IN-SapnaNeural'], 'kk': ['kk-KZ-AigulNeural', 'kk-KZ-DauletNeural'], 'km': ['km-KH-PisethNeural', 'km-KH-SreymomNeural'], 'ko': ['ko-KR-HyunsuMultilingualNeural', 'ko-KR-InJoonNeural', 'ko-KR-SunHiNeural'], 'lo': ['lo-LA-ChanthavongNeural', 'lo-LA-KeomanyNeural'], 'lv': ['lv-LV-EveritaNeural', 'lv-LV-NilsNeural'], 'lt': ['lt-LT-LeonasNeural', 'lt-LT-OnaNeural'], 'mk': ['mk-MK-AleksandarNeural', 'mk-MK-MarijaNeural'], 'ms': ['ms-MY-OsmanNeural', 'ms-MY-YasminNeural'], 'ml': ['ml-IN-MidhunNeural', 'ml-IN-SobhanaNeural'], 'mt': ['mt-MT-GraceNeural', 'mt-MT-JosephNeural'], 'mr': ['mr-IN-AarohiNeural', 'mr-IN-ManoharNeural'], 'mn': ['mn-MN-BataaNeural', 'mn-MN-YesuiNeural'], 'ne': ['ne-NP-HemkalaNeural', 'ne-NP-SagarNeural'], 'nb': ['nb-NO-FinnNeural', 'nb-NO-PernilleNeural'], 'ps': ['ps-AF-GulNawazNeural', 'ps-AF-LatifaNeural'], 'fa': ['fa-IR-DilaraNeural', 'fa-IR-FaridNeural'], 'pl': ['pl-PL-MarekNeural', 'pl-PL-ZofiaNeural'], 'pt': ['pt-BR-ThalitaMultilingualNeural', 'pt-BR-AntonioNeural', 'pt-BR-FranciscaNeural', 'pt-PT-DuarteNeural', 'pt-PT-RaquelNeural'], 'ro': ['ro-RO-AlinaNeural', 'ro-RO-EmilNeural'], 'ru': ['ru-RU-DmitryNeural', 'ru-RU-SvetlanaNeural'], 'sr': ['sr-RS-NicholasNeural', 'sr-RS-SophieNeural'], 'si': ['si-LK-SameeraNeural', 'si-LK-ThiliniNeural'], 'sk': ['sk-SK-LukasNeural', 'sk-SK-ViktoriaNeural'], 'sl': ['sl-SI-PetraNeural', 'sl-SI-RokNeural'], 'so': ['so-SO-MuuseNeural', 'so-SO-UbaxNeural'], 'es': ['es-AR-ElenaNeural', 'es-AR-TomasNeural', 'es-BO-MarceloNeural', 'es-BO-SofiaNeural', 'es-CL-CatalinaNeural', 'es-CL-LorenzoNeural', 'es-CO-GonzaloNeural', 'es-CO-SalomeNeural', 'es-ES-XimenaNeural', 'es-CR-JuanNeural', 'es-CR-MariaNeural', 'es-CU-BelkysNeural', 'es-CU-ManuelNeural', 'es-DO-EmilioNeural', 'es-DO-RamonaNeural', 'es-EC-AndreaNeural', 'es-EC-LuisNeural', 'es-SV-LorenaNeural', 'es-SV-RodrigoNeural', 'es-GQ-JavierNeural', 'es-GQ-TeresaNeural', 'es-GT-AndresNeural', 'es-GT-MartaNeural', 'es-HN-CarlosNeural', 'es-HN-KarlaNeural', 'es-MX-DaliaNeural', 'es-MX-JorgeNeural', 'es-NI-FedericoNeural', 'es-NI-YolandaNeural', 'es-PA-MargaritaNeural', 'es-PA-RobertoNeural', 'es-PY-MarioNeural', 'es-PY-TaniaNeural', 'es-PE-AlexNeural', 'es-PE-CamilaNeural', 'es-PR-KarinaNeural', 'es-PR-VictorNeural', 'es-ES-AlvaroNeural', 'es-ES-ElviraNeural', 'es-US-AlonsoNeural', 'es-US-PalomaNeural', 'es-UY-MateoNeural', 'es-UY-ValentinaNeural', 'es-VE-PaolaNeural', 'es-VE-SebastianNeural'], 'su': ['su-ID-JajangNeural', 'su-ID-TutiNeural'], 'sw': ['sw-KE-RafikiNeural', 'sw-KE-ZuriNeural', 'sw-TZ-DaudiNeural', 'sw-TZ-RehemaNeural'], 'sv': ['sv-SE-MattiasNeural', 'sv-SE-SofieNeural'], 'ta': ['ta-IN-PallaviNeural', 'ta-IN-ValluvarNeural', 'ta-MY-KaniNeural', 'ta-MY-SuryaNeural', 'ta-SG-AnbuNeural', 'ta-SG-VenbaNeural', 'ta-LK-KumarNeural', 'ta-LK-SaranyaNeural'], 'te': ['te-IN-MohanNeural', 'te-IN-ShrutiNeural'], 'th': ['th-TH-NiwatNeural', 'th-TH-PremwadeeNeural'], 'tr': ['tr-TR-EmelNeural', 'tr-TR-AhmetNeural'], 'uk': ['uk-UA-OstapNeural', 'uk-UA-PolinaNeural'], 'ur': ['ur-IN-GulNeural', 'ur-IN-SalmanNeural', 'ur-PK-AsadNeural', 'ur-PK-UzmaNeural'], 'uz': ['uz-UZ-MadinaNeural', 'uz-UZ-SardorNeural'], 'vi': ['vi-VN-HoaiMyNeural', 'vi-VN-NamMinhNeural'], 'cy': ['cy-GB-AledNeural', 'cy-GB-NiaNeural'], 'zu': ['zu-ZA-ThandoNeural', 'zu-ZA-ThembaNeural']}
