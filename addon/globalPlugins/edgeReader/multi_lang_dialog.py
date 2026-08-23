# -*- coding: utf-8 -*-
import wx
import config
import addonHandler
from .voice_map import VOICE_MAP, get_lang_display_name, get_voices_for_lang

addonHandler.initTranslation()


class MultiLangDialog(wx.Dialog):
	def __init__(self, parent, languages_found):
		super(MultiLangDialog, self).__init__(parent, title=_("O'qitish sozlamalari (Ko'p tilli)"), size=(480, 400), style=wx.DEFAULT_DIALOG_STYLE | wx.RESIZE_BORDER)
		self.languages_found = languages_found
		self.voice_selections = {}
		self.is_track_by_track = False
		self.is_skipped = False

		main_panel = wx.Panel(self)
		main_vbox = wx.BoxSizer(wx.VERTICAL)

		self.chk_multi = wx.CheckBox(main_panel, label=_("Tillarni avtomatik aniqlash va alohida ovozda o'qish"))
		self.chk_multi.SetValue(True)
		self.chk_multi.Bind(wx.EVT_CHECKBOX, self.on_check_multi)
		main_vbox.Add(self.chk_multi, flag=wx.ALL, border=10)

		self.lbl = wx.StaticText(main_panel, label=_("Matnda quyidagi tillar aniqlandi. Mos ovozni tanlang:"))
		main_vbox.Add(self.lbl, flag=wx.LEFT | wx.RIGHT | wx.BOTTOM, border=10)

		# Scrolled window for languages
		self.scroll_win = wx.ScrolledWindow(main_panel, style=wx.VSCROLL)
		self.scroll_win.SetScrollRate(0, 20)
		scroll_sizer = wx.BoxSizer(wx.VERTICAL)

		favs = config.conf["edgeReader"]["favorite_voices"]

		self.combos = {}
		self.lang_panels = []
		for lang in languages_found:
			hbox = wx.BoxSizer(wx.HORIZONTAL)
			display_name = get_lang_display_name(lang)
			lang_lbl = wx.StaticText(self.scroll_win, label=f"{display_name}:", size=(150, -1))
			hbox.Add(lang_lbl, flag=wx.RIGHT | wx.ALIGN_CENTER_VERTICAL, border=8)

			# Sozlamalarda shu til uchun belgilangan ovozlar (bo'lmasa - VOICE_DB standart ro'yxati)
			multi_voices_raw = config.conf["edgeReader"]["multi_lang_voices"]
			lang_voices = get_voices_for_lang(lang, multi_voices_raw)

			combo_choices = []
			for v in lang_voices:
				if v not in combo_choices:
					combo_choices.append(v)
			for v in favs:
				if v not in combo_choices and v.split('-')[0].lower() == lang.split('-')[0].lower():
					combo_choices.append(v)

			combo = wx.ComboBox(self.scroll_win, choices=combo_choices, style=wx.CB_DROPDOWN)

			default_voice = VOICE_MAP.get(lang, config.conf["edgeReader"]["edge_voice"])
			if default_voice in combo_choices:
				combo.SetValue(default_voice)
			elif combo_choices:
				combo.SetValue(combo_choices[0])

			hbox.Add(combo, proportion=1)
			scroll_sizer.Add(hbox, flag=wx.EXPAND | wx.ALL, border=5)
			self.combos[lang] = combo
			self.lang_panels.append(lang_lbl)
			self.lang_panels.append(combo)

		self.scroll_win.SetSizer(scroll_sizer)

		# Min height for scrolled window
		min_h = min(len(languages_found) * 40, 200)
		self.scroll_win.SetMinSize((-1, min_h))

		main_vbox.Add(self.scroll_win, proportion=1, flag=wx.EXPAND | wx.LEFT | wx.RIGHT | wx.BOTTOM, border=10)

		self.radio_box = wx.RadioBox(
			main_panel, label=_("Saqlash usuli"),
			choices=[_("1 ta yaxlit MP3"), _("Treklarga bo'lib saqlash (Papka)")],
			majorDimension=1, style=wx.RA_SPECIFY_COLS
		)
		main_vbox.Add(self.radio_box, flag=wx.EXPAND | wx.ALL, border=10)

		hbox_btn = wx.BoxSizer(wx.HORIZONTAL)
		btn_ok = wx.Button(main_panel, id=wx.ID_OK, label=_("Boshlash"))
		btn_skip = wx.Button(main_panel, id=wx.ID_CANCEL, label=_("Avtomatik (Skip)"))

		hbox_btn.Add(btn_ok, flag=wx.RIGHT, border=10)
		hbox_btn.Add(btn_skip)

		main_vbox.Add(hbox_btn, flag=wx.ALIGN_CENTER | wx.BOTTOM, border=10)

		main_panel.SetSizer(main_vbox)
		main_vbox.Fit(self)

		self.Bind(wx.EVT_BUTTON, self.on_ok, id=wx.ID_OK)
		self.Bind(wx.EVT_BUTTON, self.on_skip, id=wx.ID_CANCEL)

	def on_check_multi(self, event):
		is_checked = self.chk_multi.GetValue()
		for item in self.lang_panels:
			item.Enable(is_checked)
		self.lbl.Enable(is_checked)

	def on_ok(self, event):
		self.use_multi_lang = self.chk_multi.GetValue()
		if self.use_multi_lang:
			for lang, combo in self.combos.items():
				self.voice_selections[lang] = combo.GetValue()
		self.is_track_by_track = (self.radio_box.GetSelection() == 1)
		self.EndModal(wx.ID_OK)

	def on_skip(self, event):
		self.use_multi_lang = False
		self.EndModal(wx.ID_CANCEL)
