# -*- coding: utf-8 -*-
import os
import zipfile

base_dir = os.path.dirname(os.path.abspath(__file__))
addon_dir = os.path.join(base_dir, 'addon')
dist_dir = os.path.join(base_dir, 'dist')
os.makedirs(dist_dir, exist_ok=True)

addon_name = 'edgeReader-1.2.7.nvda-addon'
output_path = os.path.join(dist_dir, addon_name)

print(f'Building {addon_name} from {addon_dir}...')

with zipfile.ZipFile(output_path, 'w', zipfile.ZIP_DEFLATED) as z:
	for root, dirs, files in os.walk(addon_dir):
		if '__pycache__' in root:
			continue
		for file in files:
			if file.endswith('.pyc') or file.endswith('.pyo'):
				continue
			full_path = os.path.join(root, file)
			rel_path = os.path.relpath(full_path, addon_dir)
			z.write(full_path, rel_path)

print(f'\nAdd-on successfully built at: {output_path}')
