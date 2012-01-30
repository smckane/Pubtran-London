#!/usr/bin/env python

import httplib, urllib, re, sys

def tfl_request(stop):
	params = urllib.urlencode({'type_origin': 'stop', 'name_origin': stop, 'type_destination': 'stop',  'name_destination': 'cyprus', 'language': 'en', 'place_origin': 'London', 'place_destination': 'London'})
	headers = {"User-Agent": "Opera/9.70 (Linux ppc64 ; U; en) Presto/2.2.1", "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", 'Accept-Language': 'cs,en-us;q=0.7,en;q=0.3', 'Accept-Charset': 'ISO-8859-2,utf-8;q=0.7,*;q=0.7', 'Keep-Alive': '115', 'Connection': 'keep-alive', 'Referer': 'http://journeyplanner.tfl.gov.uk/user/XSLT_TRIP_REQUEST2?language=en', 'Content-Type': 'application/x-www-form-urlencoded', '': '', '': ''}
	conn = httplib.HTTPConnection("journeyplanner.tfl.gov.uk:80")
	conn.request("POST", "/user/XML_TRIP_REQUEST2", params, headers)
	response = conn.getresponse()
	data = response.read()
	found = re.findall(r'>([^<]*?)</odvNameElem', data)
	for name in found[:-2]:
		print name
	conn.close()

def expand(s, index, stops):
	count = 0
	for i in range(index, len(stops)):
		if stops[i].startswith(s):
			count += 1
		else: break
	return count

def how_many(s, stops):
	count = 0
	for stop in stops:
		if stop.startswith(s):
			count += 1
		elif ' ' + s in stop:
			count += 1
	return count

stops = []
for line in open('naptan.txt'):
	stops.append(line.strip().lower())
stops.sort()

i = 0
previous = ''
while True:
	if i >= len(stops): break
	stop = stops[i]
	
	lastPossible = stop
	for j in range(len(stop), 1, -1):
		current = stop[0:j]
		if (j < len(stop) and stop[j] ==  ' ') or stop[j - 1] == ' ': continue
		count = how_many(current, stops)
		if count > 90: break
		if current in previous: break
		lastPossible = current
	
	sys.stderr.write(lastPossible + '\n')
	if lastPossible == 'zzzzzzzzzzz':
		tfl_request(lastPossible)

	previous = lastPossible
	i += expand(lastPossible, i, stops)
