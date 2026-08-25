# -*- coding: UTF-8 -*-
import globalPluginHandler
import speech
import api
import ui
import os
import threading
import urllib.request
import urllib.parse
import time
import addonHandler
import config
import gui
from gui.settingsDialogs import SettingsPanel
import wx
import json
from scriptHandler import script
from .transliterate import process_text
from .voice_map import (
	VOICE_DB,
	get_lang_display_name,
	get_locale_display_name,
	get_selectable_languages,
	get_voices_for_locale,
	get_locale_key_for_voice,
	get_multi_lang_voices,
	dump_multi_lang_voices,
)

confspec = {
	"mp3_mode": "boolean(default=False)",
	"do_roman": "boolean(default=False)",
	"do_translit": "boolean(default=False)",
	"edge_voice": "string(default='uz-UZ-MadinaNeural')",
	"favorite_voices": "string_list(default=list('uz-UZ-MadinaNeural', 'uz-UZ-SardorNeural', 'ru-RU-DmitryNeural', 'ru-RU-SvetlanaNeural', 'en-US-GuyNeural'))",
	"edge_rate": "integer(default=0, min=-100, max=100)",
	"edge_pitch": "integer(default=0, min=-100, max=100)",
	"multi_lang_enabled": "boolean(default=True)",
	"multi_lang_voices": "string(default='{}')"
}
config.conf.spec["edgeReader"] = confspec

addonHandler.initTranslation()


def _migrate_legacy_favorites():
	"""Eski 'favorite_voices' ro'yxatini (tilga bo'linmagan) bir martalik
	'multi_lang_voices' (til bo'yicha guruhlangan) strukturaga o'tkazadi.
	Agar 'multi_lang_voices' allaqachon to'ldirilgan bo'lsa - hech narsa qilinmaydi."""
	try:
		existing = get_multi_lang_voices(config.conf["edgeReader"]["multi_lang_voices"])
		if existing:
			return
		favs = config.conf["edgeReader"]["favorite_voices"]
		if not favs:
			return
		grouped = {}
		for voice in favs:
			lang = get_locale_key_for_voice(voice)
			grouped.setdefault(lang, [])
			if voice not in grouped[lang]:
				grouped[lang].append(voice)
		if grouped:
			config.conf["edgeReader"]["multi_lang_voices"] = dump_multi_lang_voices(grouped)
	except Exception:
		# Migratsiya muvaffaqiyatsiz bo'lsa ham addon yuklanishida davom etadi
		pass


_migrate_legacy_favorites()

class EdgeReaderSettingsPanel(SettingsPanel):
	title = "Edge Reader"
	
	def makeSettings(self, settingsSizer):
		sHelper = gui.guiHelper.BoxSizerHelper(self, sizer=settingsSizer)
		
		self.mp3ModeCheckbox = sHelper.addItem(
			wx.CheckBox(self, label=_("Enable MP3 saving mode"))
		)
		self.mp3ModeCheckbox.SetValue(config.conf["edgeReader"]["mp3_mode"])
		
		self.doRomanCheckbox = sHelper.addItem(
			wx.CheckBox(self, label=_("Convert Roman numerals to numbers"))
		)
		self.doRomanCheckbox.SetValue(config.conf["edgeReader"]["do_roman"])
		
		self.doTranslitCheckbox = sHelper.addItem(
			wx.CheckBox(self, label=_("Transliterate Cyrillic to Latin"))
		)
		self.doTranslitCheckbox.SetValue(config.conf["edgeReader"]["do_translit"])
		
		self.multiLangEnabledCheckbox = sHelper.addItem(
			wx.CheckBox(self, label=_("Enable multilingual reading (detect languages in text)"))
		)
		self.multiLangEnabledCheckbox.SetValue(config.conf["edgeReader"]["multi_lang_enabled"])
		
		self.voice_db = VOICE_DB
		self.languages = get_selectable_languages()
		# Kod -> "kod — to'liq nom" (yoki "kod — Til (Davlat)") va teskari xarita
		self.lang_code_to_display = {code: get_locale_display_name(code) for code in self.languages}
		self.lang_display_to_code = {v: k for k, v in self.lang_code_to_display.items()}
		display_choices = sorted(self.lang_code_to_display.values())
		
		self.langComboBox = sHelper.addLabeledControl(
			_("Language:"),
			wx.ComboBox,
			choices=display_choices,
			style=wx.CB_DROPDOWN | wx.CB_READONLY
		)
		
		self.voiceComboBox = sHelper.addLabeledControl(
			_("Voice:"),
			wx.ComboBox,
			choices=[],
			style=wx.CB_DROPDOWN | wx.CB_READONLY
		)
		
		self.langComboBox.Bind(wx.EVT_COMBOBOX, self.onLangChange)
		
		# Set initial values based on current default voice
		current_default = config.conf["edgeReader"]["edge_voice"]
		initial_lang = get_locale_key_for_voice(current_default) if current_default else (self.languages[0] if self.languages else "")
		if initial_lang not in self.languages:
			initial_lang = self.languages[0] if self.languages else ""
		self.langComboBox.SetValue(self.lang_code_to_display.get(initial_lang, ""))
		self.voiceComboBox.SetItems(get_voices_for_locale(initial_lang))
			
		if current_default in self.voiceComboBox.GetItems():
			self.voiceComboBox.SetValue(current_default)
		elif self.voiceComboBox.GetCount() > 0:
			self.voiceComboBox.SetSelection(0)
			
		self.rateSpinCtrl = sHelper.addLabeledControl(
			_("Voice Rate (-100 to 100):"),
			wx.SpinCtrl,
			min=-100, max=100, initial=config.conf["edgeReader"]["edge_rate"]
		)
		
		self.pitchSpinCtrl = sHelper.addLabeledControl(
			_("Voice Pitch (-100 to 100):"),
			wx.SpinCtrl,
			min=-100, max=100, initial=config.conf["edgeReader"]["edge_pitch"]
		)
		
		fav_label = wx.StaticText(self, label=_("Favorites:"))
		sHelper.addItem(fav_label)
		
		fav_sizer = wx.BoxSizer(wx.HORIZONTAL)
		
		self.favListBox = wx.ListBox(self, style=wx.LB_SINGLE)
		favs = config.conf["edgeReader"]["favorite_voices"]
		self.favListBox.SetItems(favs)
		
		btn_sizer = wx.BoxSizer(wx.VERTICAL)
		self.btnAddFav = wx.Button(self, label=_("Add"))
		self.btnRemoveFav = wx.Button(self, label=_("Remove"))
		
		self.btnAddFav.Bind(wx.EVT_BUTTON, self.onAddFav)
		self.btnRemoveFav.Bind(wx.EVT_BUTTON, self.onRemoveFav)
		
		btn_sizer.Add(self.btnAddFav, 0, wx.BOTTOM, 5)
		btn_sizer.Add(self.btnRemoveFav, 0)
		
		fav_sizer.Add(self.favListBox, 1, wx.EXPAND | wx.RIGHT, 5)
		fav_sizer.Add(btn_sizer, 0, wx.ALIGN_CENTER_VERTICAL)
		
		sHelper.addItem(fav_sizer)
		
		# --- Ko'p tilli ovozlar (har bir til uchun alohida ovoz qo'shish/o'chirish) ---
		multi_label = wx.StaticText(self, label=_("Multilingual voices"))
		sHelper.addItem(multi_label)
		
		self.multiLangVoices = get_multi_lang_voices(config.conf["edgeReader"]["multi_lang_voices"])
		
		self.multiLangLangComboBox = sHelper.addLabeledControl(
			_("Language:"),
			wx.ComboBox,
			choices=display_choices,
			style=wx.CB_DROPDOWN | wx.CB_READONLY
		)
		# Joriy standart ovoz tili bilan bir xil tildan boshlanadi (alifbo bo'yicha
		# birinchi til emas) - shunda ikkala bo'lim izchil ko'rinadi.
		multi_default_lang = initial_lang if initial_lang in self.languages else (self.languages[0] if self.languages else "")
		self.multiLangLangComboBox.SetValue(self.lang_code_to_display.get(multi_default_lang, ""))
		self.multiLangLangComboBox.Bind(wx.EVT_COMBOBOX, self.onMultiLangLangChange)        
		multi_voice_sizer = wx.BoxSizer(wx.HORIZONTAL)
		
		self.multiLangVoiceListBox = wx.ListBox(self, style=wx.LB_SINGLE)
		multi_voice_sizer.Add(self.multiLangVoiceListBox, 1, wx.EXPAND | wx.RIGHT, 5)
		
		multi_voice_btn_sizer = wx.BoxSizer(wx.VERTICAL)
		
		self.newVoiceComboBox = wx.ComboBox(self, choices=[], style=wx.CB_DROPDOWN | wx.CB_READONLY)
		multi_voice_btn_sizer.Add(self.newVoiceComboBox, 0, wx.BOTTOM | wx.EXPAND, 5)
		
		self.btnAddMultiLangVoice = wx.Button(self, label=_("Add"))
		self.btnRemoveMultiLangVoice = wx.Button(self, label=_("Remove"))
		
		self.btnAddMultiLangVoice.Bind(wx.EVT_BUTTON, self.onAddMultiLangVoice)
		self.btnRemoveMultiLangVoice.Bind(wx.EVT_BUTTON, self.onRemoveMultiLangVoice)
		
		multi_voice_btn_sizer.Add(self.btnAddMultiLangVoice, 0, wx.BOTTOM, 5)
		multi_voice_btn_sizer.Add(self.btnRemoveMultiLangVoice, 0)
		
		multi_voice_sizer.Add(multi_voice_btn_sizer, 0, wx.ALIGN_CENTER_VERTICAL)
		
		sHelper.addItem(multi_voice_sizer)
		
		self._refreshMultiLangVoiceControls(multi_default_lang)
		
	def _getVoicesForLangSetting(self, lang):
		"""Sozlamalar panelidagi RAM'dagi ro'yxatni qaytaradi (agar sozlanmagan bo'lsa - standart ro'yxat)."""
		voices = self.multiLangVoices.get(lang)
		if voices:
			return list(voices)
		return get_voices_for_locale(lang)
		
	def _refreshMultiLangVoiceControls(self, lang):
		current_voices = self._getVoicesForLangSetting(lang)
		self.multiLangVoiceListBox.SetItems(current_voices)
		
		available = [v for v in get_voices_for_locale(lang) if v not in current_voices]
		self.newVoiceComboBox.SetItems(available)
		if available:
			self.newVoiceComboBox.SetSelection(0)
		else:
			self.newVoiceComboBox.SetValue("")
			
	def onMultiLangLangChange(self, event):
		lang = self.lang_display_to_code.get(self.multiLangLangComboBox.GetValue(), "")
		self._refreshMultiLangVoiceControls(lang)
		
	def onAddMultiLangVoice(self, event):
		lang = self.lang_display_to_code.get(self.multiLangLangComboBox.GetValue(), "")
		voice = self.newVoiceComboBox.GetValue()
		if not lang or not voice:
			return
		current_voices = self._getVoicesForLangSetting(lang)
		if voice not in current_voices:
			current_voices.append(voice)
		self.multiLangVoices[lang] = current_voices
		self._refreshMultiLangVoiceControls(lang)
		
	def onRemoveMultiLangVoice(self, event):
		lang = self.lang_display_to_code.get(self.multiLangLangComboBox.GetValue(), "")
		sel = self.multiLangVoiceListBox.GetSelection()
		if not lang or sel == wx.NOT_FOUND:
			return
		current_voices = self._getVoicesForLangSetting(lang)
		if len(current_voices) <= 1:
			gui.messageBox(
				_("At least one voice must remain for each language."),
				_("Edge Reader"),
				wx.OK | wx.ICON_INFORMATION
			)
			return
		removed_voice = self.multiLangVoiceListBox.GetString(sel)
		current_voices = [v for v in current_voices if v != removed_voice]
		self.multiLangVoices[lang] = current_voices
		self._refreshMultiLangVoiceControls(lang)
		
	def onLangChange(self, event):
		lang = self.lang_display_to_code.get(self.langComboBox.GetValue(), "")
		voices = get_voices_for_locale(lang)
		if voices:
			current_voice = self.voiceComboBox.GetValue()
			self.voiceComboBox.SetItems(voices)
			if current_voice in voices:
				self.voiceComboBox.SetValue(current_voice)
			elif self.voiceComboBox.GetCount() > 0:
				self.voiceComboBox.SetSelection(0)
				
	def onAddFav(self, event):
		voice = self.voiceComboBox.GetValue()
		if voice:
			items = self.favListBox.GetItems()
			if voice not in items:
				self.favListBox.Append(voice)
				
	def onRemoveFav(self, event):
		sel = self.favListBox.GetSelection()
		if sel != wx.NOT_FOUND:
			self.favListBox.Delete(sel)
			
	def onSave(self):
		config.conf["edgeReader"]["mp3_mode"] = self.mp3ModeCheckbox.GetValue()
		config.conf["edgeReader"]["do_roman"] = self.doRomanCheckbox.GetValue()
		config.conf["edgeReader"]["do_translit"] = self.doTranslitCheckbox.GetValue()
		config.conf["edgeReader"]["multi_lang_enabled"] = self.multiLangEnabledCheckbox.GetValue()
		
		current_voice = self.voiceComboBox.GetValue()
		if current_voice:
			config.conf["edgeReader"]["edge_voice"] = current_voice
			
		config.conf["edgeReader"]["edge_rate"] = self.rateSpinCtrl.GetValue()
		config.conf["edgeReader"]["edge_pitch"] = self.pitchSpinCtrl.GetValue()
		
		config.conf["edgeReader"]["favorite_voices"] = self.favListBox.GetItems()
		
		config.conf["edgeReader"]["multi_lang_voices"] = dump_multi_lang_voices(self.multiLangVoices)

class GlobalPlugin(globalPluginHandler.GlobalPlugin):
	scriptCategory = "Edge Reader"
	
	def __init__(self):
		super(globalPluginHandler.GlobalPlugin, self).__init__()
		self.downloads_dir = os.path.join(os.path.expanduser('~'), 'Downloads')
		
		# Translate main folder and track suffix based on NVDA language
		import languageHandler
		nvda_lang = languageHandler.getLanguage() or 'en'
		if nvda_lang.startswith('uz'):
			main_folder_name = 'EdgeReader_MP3_Natijalari'
			self.track_suffix = '_trek.mp3'
		elif nvda_lang.startswith('ru'):
			main_folder_name = 'EdgeReader_MP3_Результаты'
			self.track_suffix = '_трек.mp3'
		else:
			main_folder_name = 'EdgeReader_MP3_Results'
			self.track_suffix = '_track.mp3'
			
		self.edge_reader_dir = os.path.join(self.downloads_dir, main_folder_name)
		
		if not os.path.exists(self.edge_reader_dir):
			os.makedirs(self.edge_reader_dir)
			
		self.last_text = ""
		# Patch speech.speak
		try:
			import speech.manager
			self.orig_speak = speech.manager.speechManager.speak
			speech.manager.speechManager.speak = self.handle_speech
			self.using_manager = True
		except Exception:
			self.orig_speak = speech.speak
			speech.speak = self.handle_speech
			self.using_manager = False
		
		gui.settingsDialogs.NVDASettingsDialog.categoryClasses.append(EdgeReaderSettingsPanel)

	def terminate(self):
		if getattr(self, "using_manager", False):
			try:
				import speech.manager
				speech.manager.speechManager.speak = self.orig_speak
			except:
				pass
		else:
			speech.speak = self.orig_speak
		try:
			gui.settingsDialogs.NVDASettingsDialog.categoryClasses.remove(EdgeReaderSettingsPanel)
		except ValueError:
			pass
		
	def handle_speech(self, speechSequence, *args, **kwargs):
		text = ""
		for item in speechSequence:
			if isinstance(item, str):
				text += item + " "
				
		if text.strip():
			do_roman = config.conf["edgeReader"]["do_roman"]
			do_translit = config.conf["edgeReader"]["do_translit"]
			
			# Apply transliteration and roman conversion to the text
			processed_text = process_text(text, do_roman, do_translit)
			
			self.last_text = processed_text
			if config.conf["edgeReader"]["mp3_mode"]:
				self.generate_mp3(processed_text)
				
		return self.orig_speak(speechSequence, *args, **kwargs)
				
		pass

	def generate_mp3(self, text):
		if not text.strip():
			return
		
		voice = config.conf["edgeReader"]["edge_voice"]
		rate = config.conf["edgeReader"]["edge_rate"]
		pitch = config.conf["edgeReader"]["edge_pitch"]
		
		rate_str = f"+{rate}%" if rate >= 0 else f"{rate}%"
		pitch_str = f"+{pitch}Hz" if pitch >= 0 else f"{pitch}Hz"
		
		def save_thread(text_to_save, voice_name, r_str, p_str):
			try:
				import sys
				lib_path = os.path.join(os.path.dirname(__file__), 'lib')
				if lib_path not in sys.path:
					sys.path.insert(0, lib_path)
					
				import edge_tts
				import asyncio
				
				first_words = "_".join(text_to_save.split()[:3])
				first_words = "".join(c for c in first_words if c.isalnum() or c == '_')
				
				if not first_words:
					first_words = "audio"
				
				# Each reading gets its own dedicated folder, named after the
				# first words of the text plus a timestamp, so different
				# readings never mix together in the same folder.
				folder_name = f"{first_words}_{time.strftime('%Y-%m-%d_%H-%M-%S')}"
				session_dir = os.path.join(self.edge_reader_dir, folder_name)
				if not os.path.exists(session_dir):
					os.makedirs(session_dir)
					
				filepath = os.path.join(session_dir, f"{first_words}.mp3")
				
				async def amain():
					communicate = edge_tts.Communicate(text_to_save, voice_name, rate=r_str, pitch=p_str)
					await communicate.save(filepath)
					
				asyncio.run(amain())
				
				msg = _("MP3 saved")
				wx.CallAfter(ui.message, msg)
				wx.CallAfter(lambda: __import__("tones").beep(880, 100))
			except Exception as e:
				import logHandler
				logHandler.log.error(f"EdgeReader MP3 save error: {e}", exc_info=True)
				
		threading.Thread(target=save_thread, args=(text, voice, rate_str, pitch_str)).start()

	@script(description=_("Toggles the MP3 auto-saving mode."))
	def script_toggleMp3Mode(self, gesture):
		"""Toggles the MP3 auto-saving mode."""
		current = config.conf["edgeReader"]["mp3_mode"]
		config.conf["edgeReader"]["mp3_mode"] = not current
		if config.conf["edgeReader"]["mp3_mode"]:
			ui.message(_("MP3 saving enabled"))
		else:
			ui.message(_("MP3 saving disabled"))
			
	@script(description=_("Saves the last spoken text as MP3."))
	def script_saveLastSpeech(self, gesture):
		"""Saves the last spoken text as MP3."""
		if self.last_text:
			self.generate_mp3(self.last_text)
		else:
			ui.message(_("No text to save"))
			
	@script(description=_("Opens the folder containing saved MP3 files."))
	def script_openMp3Folder(self, gesture):
		"""Opens the folder containing saved MP3 files."""
		try:
			os.startfile(self.edge_reader_dir)
			ui.message(_("Folder opened"))
		except:
			pass

	def _get_translated_prefix(self, prefix):
		import languageHandler
		nvda_lang = languageHandler.getLanguage() or 'en'
		if nvda_lang.startswith('uz'):
			return {'Clipboard': 'Bufer', 'Focused': 'Fokuslangan', 'Selection': 'Tanlangan'}.get(prefix, prefix)
		elif nvda_lang.startswith('ru'):
			return {'Clipboard': 'Буфер_обмена', 'Focused': 'Фокус', 'Selection': 'Выделение'}.get(prefix, prefix)
		return prefix
		
	def _process_and_save(self, text, base_filename):
		import threading
		# The sentence-split + language-detection pass below can take a
		# noticeable amount of time on large texts (a full book), so it must
		# run off NVDA's main thread - otherwise it blocks the UI exactly
		# like the file-dialog freeze fixed earlier.
		threading.Thread(target=self._process_and_save_worker, args=(text, base_filename)).start()

	def _process_and_save_worker(self, text, base_filename):
		import ui
		import re
		import sys
		import wx
		import tones
		
		lib_path = os.path.join(os.path.dirname(__file__), 'lib')
		if lib_path not in sys.path: sys.path.insert(0, lib_path)
		
		multi_lang_enabled = config.conf["edgeReader"]["multi_lang_enabled"]
		
		ui.message(_("Processing started..."))
		
		# Ko'p tillilik sozlamalarda o'chirilgan bo'lsa - til aniqlash bosqichi
		# (langdetect) va MultiLangDialog umuman ishga tushmaydi, bitta standart
		# ovozda to'g'ridan-to'g'ri o'qish/saqlash boshlanadi.
		if not multi_lang_enabled:
			def start_default():
				ui.message(_("Process started"))
				self._start_generation_thread([(text, 'unknown')], {}, False, base_filename)
			wx.CallAfter(start_default)
			return
		
		try:
			import langdetect
		except Exception:
			langdetect = None
			
		from .multi_lang_dialog import MultiLangDialog
		
		raw_chunks = re.split(r'(?<=[.!?\n])\s+', text)
		raw_chunks = [c for c in raw_chunks if c.strip()]
		chunk_langs = []
		unique_langs = set()
		
		total_raw = len(raw_chunks)
		report_every = max(1, total_raw // 20)  # ~20 progress updates max
		
		for i, chunk in enumerate(raw_chunks):
			lang = 'unknown'
			if langdetect:
				try:
					lang = langdetect.detect(chunk)
				except:
					pass
			if lang != 'unknown':
				unique_langs.add(lang)
			chunk_langs.append((chunk, lang))
			
			if total_raw > 1 and ((i + 1) % report_every == 0 or (i + 1) == total_raw):
				percent = int(((i + 1) / total_raw) * 100)
				ui.message(f"{percent}%")
				try:
					tones.beep(300, 30)
				except Exception:
					pass
			
		def show_dialog():
			dlg = MultiLangDialog(None, list(unique_langs))
			ret = dlg.ShowModal()
			
			if ret == wx.ID_CANCEL:
				dlg.Destroy()
				ui.message(_("Bekor qilindi") if _("Bekor qilindi") != "Bekor qilindi" else "Canceled")
				return
				
			voice_selections = dlg.voice_selections
			is_track_by_track = dlg.is_track_by_track
			use_multi_lang = dlg.use_multi_lang
			dlg.Destroy()
			
			ui.message(_("Process started"))
			
			final_chunk_langs = chunk_langs
			if not use_multi_lang:
				final_chunk_langs = [(text, 'unknown') for text, _ in chunk_langs]
				
			self._start_generation_thread(final_chunk_langs, voice_selections, is_track_by_track, base_filename)
			
		wx.CallAfter(show_dialog)


	def _start_generation_thread(self, chunk_langs, voice_selections, is_track_by_track, base_filename):
		grouped_chunks = []
		current_voice = None
		current_text = ""
		
		default_voice = config.conf["edgeReader"]["edge_voice"]
		
		for text, lang in chunk_langs:
			voice = voice_selections.get(lang, default_voice)
			if voice == current_voice and len(current_text) < 10000:
				current_text += " " + text
			else:
				if current_text:
					grouped_chunks.append((current_text, current_voice))
				current_text = text
				current_voice = voice
		
		if current_text:
			grouped_chunks.append((current_text, current_voice))
			
		rate = config.conf["edgeReader"]["edge_rate"]
		pitch = config.conf["edgeReader"]["edge_pitch"]
		rate_str = f"+{rate}%" if rate >= 0 else f"{rate}%"
		pitch_str = f"+{pitch}Hz" if pitch >= 0 else f"{pitch}Hz"
		
		do_roman = config.conf["edgeReader"]["do_roman"]
		do_translit = config.conf["edgeReader"]["do_translit"]
		
		def save_thread():
			try:
				import edge_tts
				import asyncio
				import os
				import time
				import ui
				from .transliterate import process_text
				
				total_chunks = len(grouped_chunks)
				
				# Every reading (whether saved as one merged MP3 or as
				# separate tracks) gets its own dedicated, timestamped
				# folder, so different readings never end up mixed together.
				folder_path = os.path.join(self.edge_reader_dir, f"{base_filename}_{time.strftime('%Y-%m-%d_%H-%M-%S')}")
				os.makedirs(folder_path, exist_ok=True)
				
				if not is_track_by_track:
					filepath = os.path.join(folder_path, f"{base_filename}.mp3")
				
				async def amain():
					out_f = None
					if not is_track_by_track:
						out_f = open(filepath, 'wb')
						
					for idx, (text_chunk, voice_name) in enumerate(grouped_chunks):
						if not text_chunk.strip(): continue
						
						final_text = process_text(text_chunk, do_roman, do_translit)
						communicate = edge_tts.Communicate(final_text, voice_name, rate=rate_str, pitch=pitch_str)
						
						if is_track_by_track:
							track_path = os.path.join(folder_path, f"{idx:02d}{self.track_suffix}")
							await communicate.save(track_path)
						else:
							async for c in communicate.stream():
								if c["type"] == "audio":
									out_f.write(c["data"])
									
						percent = int(((idx + 1) / total_chunks) * 100)
						ui.message(f"{percent}%")
						try:
							import tones
							pitch = int(110 * (2 ** (percent / 25.0)))
							tones.beep(pitch, 40)
						except Exception:
							pass
						
					if not is_track_by_track:
						out_f.close()
						
				asyncio.run(amain())

				msg = _("MP3 saved")
				wx.CallAfter(ui.message, msg)
				wx.CallAfter(lambda: __import__("tones").beep(880, 100))
			except Exception as e:
				import logHandler
				logHandler.log.error(f"EdgeReader MP3 save error: {e}", exc_info=True)
				
		import threading
		threading.Thread(target=save_thread).start()

	def _compile_from_path(self, filepath):
		import ui
		import os
		import time
		import threading
		
		text = ""
		try:
			ext = os.path.splitext(filepath)[1].lower()
			if ext == '.txt':
				ui.message(_("Fayl o'qilmoqda..."))
				with open(filepath, 'r', encoding='utf-8') as f:
					text = f.read()
			elif ext == '.pdf':
				import sys
				lib_path = os.path.join(os.path.dirname(__file__), 'lib')
				if lib_path not in sys.path: sys.path.insert(0, lib_path)
				import PyPDF2
				import tones
				ui.message(_("Fayl o'qilmoqda..."))
				with open(filepath, 'rb') as f:
					reader = PyPDF2.PdfReader(f)
					total_pages = len(reader.pages)
					report_every = max(1, total_pages // 20)  # ~20 progress updates max
					for i, page in enumerate(reader.pages):
						page_text = page.extract_text()
						if page_text:
							text += page_text + "\n"
						if total_pages > 1 and ((i + 1) % report_every == 0 or (i + 1) == total_pages):
							percent = int(((i + 1) / total_pages) * 100)
							ui.message(f"{percent}%")
							try:
								tones.beep(200, 30)
							except Exception:
								pass
			elif ext == '.docx':
				import sys
				lib_path = os.path.join(os.path.dirname(__file__), 'lib')
				if lib_path not in sys.path: sys.path.insert(0, lib_path)
				import docx
				from docx.oxml.ns import qn
				from docx.table import Table
				from docx.text.paragraph import Paragraph
				import tones

				ui.message(_("Fayl o'qilmoqda..."))
				doc = docx.Document(filepath)

				def iter_block_items(parent):
					# Yields paragraphs and tables in the order they appear in the document
					parent_elm = parent.element.body
					for child in parent_elm.iterchildren():
						if child.tag == qn('w:p'):
							yield Paragraph(child, parent)
						elif child.tag == qn('w:tbl'):
							yield Table(child, parent)

				def read_table(table):
					rows_text = []
					for row in table.rows:
						cells_text = [cell.text.strip() for cell in row.cells]
						cells_text = [c for c in cells_text if c]
						if cells_text:
							rows_text.append(", ".join(cells_text))
					return "\n".join(rows_text)

				blocks = list(iter_block_items(doc))
				total_blocks = len(blocks)
				report_every = max(1, total_blocks // 20)  # ~20 progress updates max
				for i, block in enumerate(blocks):
					if isinstance(block, Paragraph):
						if block.text.strip():
							text += block.text + "\n"
					elif isinstance(block, Table):
						table_text = read_table(block)
						if table_text.strip():
							text += table_text + "\n"
					if total_blocks > 1 and ((i + 1) % report_every == 0 or (i + 1) == total_blocks):
						percent = int(((i + 1) / total_blocks) * 100)
						ui.message(f"{percent}%")
						try:
							tones.beep(200, 30)
						except Exception:
							pass
			else:
				ui.message(_("Fayl formati qollab-quvvatlanmaydi"))
				return
		except Exception as e:
			ui.message(_("Faylni o'qishda xatolik"))
			return
		
		if not text.strip():
			ui.message(_("Fayl bo'sh yoki matn topilmadi"))
			return
			
		base_filename = os.path.splitext(os.path.basename(filepath))[0]
		self._process_and_save(text, base_filename)

	def _runFileDialog(self):
		import wx
		import threading
		with wx.FileDialog(None, _("O'qitish uchun faylni tanlang"), wildcard="Text & PDF Files (*.txt;*.pdf;*.docx)|*.txt;*.pdf;*.docx",
						   style=wx.FD_OPEN | wx.FD_FILE_MUST_EXIST) as fileDialog:
			if fileDialog.ShowModal() == wx.ID_CANCEL:
				return
			filepath = fileDialog.GetPath()
			# Read/extract text in a background thread - large PDFs/DOCX files can
			# take a while and must not block NVDA's main thread (would trigger
			# another "Core frozen" watchdog warning otherwise).
			threading.Thread(target=self._compile_from_path, args=(filepath,)).start()

	@script(description=_("Compiles clipboard text to MP3."))
	def script_compileLargeText(self, gesture):
		"""Compiles clipboard text to MP3."""
		import api
		try:
			clip_text = api.getClipData()
		except Exception:
			clip_text = None
		if clip_text and clip_text.strip():
			self._process_and_save(clip_text, self._get_translated_prefix("Clipboard"))
		else:
			ui.message(_("Clipboard is empty"))

	@script(description=_("Compiles the currently selected text on screen to MP3."))
	def script_compileSelection(self, gesture):
		"""Compiles the currently selected text on screen to MP3."""
		import api
		import textInfos
		text = ""
		try:
			obj = api.getFocusObject()
			info = obj.makeTextInfo(textInfos.POSITION_SELECTION)
			text = info.text
		except Exception:
			text = ""
		if text and text.strip():
			self._process_and_save(text, self._get_translated_prefix("Selection"))
		else:
			ui.message(_("No selected text found"))

	@script(description=_("Selects a TXT, PDF, or DOCX file and converts it to MP3."))
	def script_compileFile(self, gesture):
		"""Selects a TXT, PDF, or DOCX file and converts it to MP3."""
		# NOTE: must go through wx.CallAfter - calling _runFileDialog() (which
		# opens a modal wx.FileDialog) directly here blocks NVDA's main
		# thread for the whole time the dialog is open, which the watchdog
		# then reports as "Core frozen in stack!".
		wx.CallAfter(self._runFileDialog)

	@script(description=_("Smart compile: converts selection, file, focused text or clipboard to MP3."))
	def script_smartCompile(self, gesture):
		"""Smart compile: converts selection, file, focused text or clipboard to MP3."""
		import api
		import textInfos
		import ui
		import winUser
		import ctypes
		import os
		
		# 1. Selected text
		text = ""
		try:
			obj = api.getFocusObject()
			info = obj.makeTextInfo(textInfos.POSITION_SELECTION)
			text = info.text
		except Exception:
			text = ""
			
		if text and text.strip():
			self._process_and_save(text, self._get_translated_prefix("Selection"))
			return
			
		# 2. File in clipboard using ctypes
		filepaths = None
		CF_HDROP = 15
		try:
			if winUser.OpenClipboard(0):
				hDrop = winUser.GetClipboardData(CF_HDROP)
				if hDrop:
					shell32 = ctypes.windll.shell32
					count = shell32.DragQueryFileW(hDrop, 0xFFFFFFFF, None, 0)
					if count > 0:
						filepaths = []
						for i in range(count):
							length = shell32.DragQueryFileW(hDrop, i, None, 0)
							buf = ctypes.create_unicode_buffer(length + 1)
							shell32.DragQueryFileW(hDrop, i, buf, length + 1)
							filepaths.append(buf.value)
				winUser.CloseClipboard()
		except Exception:
			try: winUser.CloseClipboard()
			except: pass
			
		if filepaths and len(filepaths) > 0:
			filepath = filepaths[0]
			ext = filepath.lower().split('.')[-1]
			if ext in ['txt', 'pdf', 'docx']:
				threading.Thread(target=self._compile_from_path, args=(filepath,)).start()
				return
				
		# 3. Clipboard text
		clip_text = None
		try:
			clip_text = api.getClipData()
		except Exception:
			pass
			
		if clip_text and clip_text.strip():
			self._process_and_save(clip_text, self._get_translated_prefix("Clipboard"))
			return
			
		# 4. Focused object text
		try:
			info = obj.makeTextInfo(textInfos.POSITION_ALL)
			text = info.text
		except Exception:
			text = ""
			
		if text and text.strip():
			self._process_and_save(text, self._get_translated_prefix("Focused"))
			return
			
		ui.message(_("Fayl bo'sh yoki matn topilmadi"))

	@script(description=_("Opens Edge Reader settings."))
	def script_openSettings(self, gesture):
		"""Opens Edge Reader settings."""
		import gui
		try:
			gui.mainFrame.popupSettingsDialog(EdgeReaderSettingsPanel)
		except AttributeError:
			gui.mainFrame._popupSettingsDialog(EdgeReaderSettingsPanel)

	__gestures__ = {
		# Tugmalar belgilanmagan. Foydalanuvchi "Input Gestures" orqali o'zi belgilashi mumkin.
	}
