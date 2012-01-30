#!/usr/bin/env python
# coding=UTF-8

import os

def processDir(d):
	d += '/src/cz/fhejl/pubtran/data/'
	names =  os.listdir(d)
	for name in names:
		if not name.endswith('.java'):
			os.rename(d + name, d + name + '.java')

processDir('czsk')
processDir('london')
