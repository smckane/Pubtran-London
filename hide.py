#!/usr/bin/env python
# coding=UTF-8

import os

def processDir(d, f):
	d += '/src/cz/fhejl/pubtran/data/'
	names =  os.listdir(d)
	for name in names:
		if not name == f:
			os.rename(d + name, d + name[:-5])

processDir('czsk', 'Praha1.java')
processDir('london', 'London1.java')
