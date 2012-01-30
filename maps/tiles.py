#!/usr/bin/env python

import os, json, Image

name = 'metroABusy'

os.system('mkdir ' + name)

image = name + '.png'

w = Image.open(image).size[0]
h = Image.open(image).size[1]
maxHardZoom = 2

for i in range(maxHardZoom + 1):
	os.system('mkdir ' + name + '/' + str(i))
	scale = float(2 ** i)
	w2 = int(w / scale)
	h2 = int(h / scale)
	w3 = (((w2 - 1) / 256) + 1) * 256
	h3 = (((h2 - 1) / 256) + 1) * 256
	for j in range(w3 / 256):
		os.system('mkdir ' + name + '/' + str(i) + '/' + str(j))
	os.system('convert ' + image + ' -scale ' + str(w2) + 'x' + str(h2) + ' scaled.png')
	os.system('convert -size ' + str(w3) + 'x' + str(h3) + ' xc:white scaled.png -gravity northwest -composite prepared.png')
	os.system('convert prepared.png -crop 256x256 -set filename:tile "%[fx:page.x/256]/%[fx:page.y/256]" "' + name + '/' + str(i) + '/%[filename:tile].png"')
	os.system('rm scaled.png prepared.png')

f = open('./' + name + '/mapInfo.json', 'w')
f.write(json.dumps({'width': w, 'height': h, 'maxHardZoom': maxHardZoom, 'minZoomMdpi': 1.5}))
f.close()

os.system('zip -r ' + name + ' ' + name)
os.system('rm -r ' + name)
