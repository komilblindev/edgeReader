# -*- coding: UTF-8 -*-

import addonHandler
import gui
import wx
import os

addonHandler.initTranslation()

def onInstall():
	# No legacy cleanup required for this release.
	# (Previously this removed any other installed copy of "edgeReader"
	# whose version was not exactly "1.0.0" — that check could also match
	# the very version being installed and cause it to remove itself.)
	pass
