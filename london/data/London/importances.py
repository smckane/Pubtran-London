#!/usr/bin/env python

import re

stops = []
for line in open('tfl.txt'):
	stops.append(line.strip())

for stop in stops:
	if stop.endswith(' DLR Station') or stop.endswith(' Underground Station') or stop.endswith(' Rail Station'):
		print stop + ";1"
	elif stop.endswith(' Underground Station'):
		n = 20
	elif (stop + ' DLR Station' in stops) or (stop + ' Underground Station' in stops) or (stop + ' Rail Station' in stops):
		print stop + ";1"
