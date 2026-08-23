# -*- coding: utf-8 -*-
import re

ROMAN_TO_INT = {
	'M': 1000, 'CM': 900, 'D': 500, 'CD': 400,
	'C': 100, 'XC': 90, 'L': 50, 'XL': 40,
	'X': 10, 'IX': 9, 'V': 5, 'IV': 4, 'I': 1
}

# Strict canonical-form pattern: matches ONLY properly-constructed roman numerals
# (thousands, then hundreds, then tens, then units, each in valid subtractive form).
# This rejects letter clusters that merely *contain* roman letters but aren't a
# real numeral (e.g. "LID", "DID", "CIVIL", "VIVID", "MILD", "MILL", "VIM").
_ROMAN_STRICT = re.compile(
	r'^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$',
	re.IGNORECASE
)

# A handful of common real words that are, unfortunately, ALSO well-formed
# canonical roman numerals (e.g. MIX = M + IX = 1009). No pattern can tell
# these apart from a genuine numeral, so they're excluded by name.
_WORD_EXCEPTIONS = {"mix"}

# Words that, appearing right before a short numeral, strongly signal that the
# numeral is genuine (chapter/part/volume/page numbering), used only to decide
# whether an ambiguous SINGLE-letter token ("I", "V", "X", "L", "C", "D", "M")
# should be converted.
_CONTEXT_WORDS = [
	"bob", "bo'lim", "boʻlim", "bolim", "qism", "jild", "fasl", "band",
	"ilova", "bet", "sahifa", "modda",
	"chapter", "part", "volume", "vol", "section", "appendix", "page",
	"глава", "часть", "том", "раздел", "приложение", "страница", "статья",
]
_CONTEXT_PATTERN = re.compile(
	r'(?:' + '|'.join(re.escape(w) for w in _CONTEXT_WORDS) + r')\W*$',
	re.IGNORECASE
)

# Words that come AFTER a short numeral and equally signal a genuine numeral,
# e.g. "I asrda" (1st century), "XX asr", "II jahon urushi" (WWII), "V yil".
# These are matched as PREFIXES (asr/asrda/asrga/asrdan all match "asr")
# since Uzbek attaches case suffixes directly to the word.
_FOLLOWING_CONTEXT_WORDS = [
	"asr", "yil", "jahon", "ming yillik", "davr", "bosqich", "sinf",
	"kurs", "toifa", "guruh", "chorak", "semestr", "reyting", "o'rin",
	"orin", "joy", "raqam",
	"век", "год", "мировой", "класс", "курс",
]
_FOLLOWING_CONTEXT_PATTERN = re.compile(
	r'^\s*(?:' + '|'.join(re.escape(w) for w in _FOLLOWING_CONTEXT_WORDS) + r')',
	re.IGNORECASE
)


def _is_canonical_roman(token: str) -> bool:
	if not token:
		return False
	return bool(_ROMAN_STRICT.fullmatch(token))


def roman_to_int(s: str) -> int:
	if not _is_canonical_roman(s):
		return 0
	s = s.upper()
	i = 0
	num = 0
	while i < len(s):
		if i + 1 < len(s) and s[i:i + 2] in ROMAN_TO_INT:
			num += ROMAN_TO_INT[s[i:i + 2]]
			i += 2
		else:
			num += ROMAN_TO_INT.get(s[i], 0)
			i += 1
	return num


def replace_roman_with_numbers(text: str) -> str:
	def convert_match(m):
		token = m.group(0)
		start, end = m.span()

		# Known ambiguous real word (e.g. "mix") - never convert.
		if token.lower() in _WORD_EXCEPTIONS:
			return token

		# Must be a structurally valid (canonical) roman numeral, not just a
		# cluster of roman letters.
		if not _is_canonical_roman(token):
			return token

		# Single-letter tokens ("I", "V", "X", "L", "C", "D", "M") are highly
		# ambiguous on their own (e.g. English pronoun "I", grade "C", size
		# "L"/"XL", Roman-style initials). Only convert when there's a clear
		# signal this is really a numeral: either a chapter/part/page-style
		# word right before it, or it's immediately followed by heading-style
		# punctuation like "IV." / "IV)" / "IV:" and is fully uppercase.
		if len(token) == 1:
			preceding = text[max(0, start - 30):start]
			following_short = text[end:end + 1]
			following_long = text[end:end + 20]
			has_context_word = bool(_CONTEXT_PATTERN.search(preceding))
			has_heading_punct = token.isupper() and following_short in '.):'
			has_following_context = bool(_FOLLOWING_CONTEXT_PATTERN.match(following_long))
			if not (has_context_word or has_heading_punct or has_following_context):
				return token

		value = roman_to_int(token)
		if value <= 0:
			return token
		return str(value)

	return re.sub(r'\b[IVXLCDMivxlcdm]+\b', convert_match, text)


CYRILLIC_TO_LATIN = {
	'А': 'A', 'Б': 'B', 'В': 'V', 'Г': 'G', 'Д': 'D',
	'Е': 'E', 'Ё': 'Yo', 'Ж': 'J', 'З': 'Z', 'И': 'I',
	'Й': 'Y', 'К': 'K', 'Л': 'L', 'М': 'M', 'Н': 'N',
	'О': 'O', 'П': 'P', 'Р': 'R', 'С': 'S', 'Т': 'T',
	'У': 'U', 'Ф': 'F', 'Х': 'X', 'Ц': 'Ts', 'Ч': 'Ch',
	'Ш': 'Sh', 'Щ': 'Shch', 'Ъ': "'", 'Ы': 'Y', 'Ь': '',
	'Э': 'E', 'Ю': 'Yu', 'Я': 'Ya', 'Ў': 'O\'', 'Қ': 'Q',
	'Ғ': 'G\'', 'Ҳ': 'H',
	'а': 'a', 'б': 'b', 'в': 'v', 'г': 'g', 'д': 'd',
	'е': 'e', 'ё': 'yo', 'ж': 'j', 'з': 'z', 'и': 'i',
	'й': 'y', 'к': 'k', 'л': 'l', 'м': 'm', 'н': 'n',
	'о': 'o', 'п': 'p', 'р': 'r', 'с': 's', 'т': 't',
	'у': 'u', 'ф': 'f', 'х': 'x', 'ц': 'ts', 'ч': 'ch',
	'ш': 'sh', 'щ': 'shch', 'ъ': "'", 'ы': 'y', 'ь': '',
	'э': 'e', 'ю': 'yu', 'я': 'ya', 'ў': 'o\'', 'қ': 'q',
	'ғ': 'g\'', 'ҳ': 'h'
}

def transliterate_cyrillic_to_latin(text: str) -> str:
	result = []
	for char in text:
		if char in CYRILLIC_TO_LATIN:
			result.append(CYRILLIC_TO_LATIN[char])
		else:
			result.append(char)
	return "".join(result)

def process_text(text: str, do_roman: bool = False, do_translit: bool = False) -> str:
	if not text:
		return text
	
	if do_roman:
		text = replace_roman_with_numbers(text)
		
	if do_translit:
		text = transliterate_cyrillic_to_latin(text)
		
	return text
