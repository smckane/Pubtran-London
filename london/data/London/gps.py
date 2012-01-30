#!/usr/bin/env python

import re

def average(nums):
	if len(nums) == 0: return 1000
	else: return sum(nums) / len(nums)

naptan = {}
for line in open('naptan.txt'):
	parts = line.strip().split('#')
	stop = parts[0]
	if stop in naptan:
		naptan[stop][0].append(float(parts[1]))
		naptan[stop][1].append(float(parts[2]))
	else:
		naptan[stop] = ([float(parts[1])], [float(parts[2])])

for (stop, locations) in naptan.iteritems():
	lat = average(locations[0])
	lon = average(locations[1])
	naptan[stop] = (lat, lon)

for stop in open('stops.txt'):
	stop = stop.strip()
	lat = 0
	lon = 0
	if not stop in naptan:
		found = False
		for stop2 in naptan:
			if stop.startswith(stop2[:10]):
				found = True
				lat = naptan[stop2][0]
				lon = naptan[stop2][1]
				break
		if not found: continue
	else:
		(lat, lon) = naptan[stop]
	print stop + ';' + str(lat) + ';' + str(lon)
