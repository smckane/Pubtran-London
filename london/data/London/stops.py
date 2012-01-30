#!/usr/bin/env python

stops = []
for line in open('tfl.txt'):
	stops.append(line.strip())

for line in open('nr.txt'):
	line = line.strip()
	if not line in stops and not line + " Rail Station" in stops:
		stops.append(line + " Rail Station")

for stop in stops:
	if stop.endswith(' DLR Station') and stop[:-12] in stops:
		pass
	elif  stop.endswith(' Underground Station') and stop[:-20] in stops:
		pass
	elif  stop.endswith(' Station') and stop[:-8] in stops:
		pass
	elif  stop.endswith(' Rail Station') and stop[:-13] in stops:
		pass
	else: print stop
