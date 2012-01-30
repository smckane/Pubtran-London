#!/usr/bin/env python

from xml.sax.handler import ContentHandler
from xml.sax import make_parser
import re
import sys

stops = []

def modifyStopName(stop):
	stop = stop.replace("  ", " / ")
	stop = stop.replace("  ", " ").replace("  ", " ")
	m = re.match(r".*([a-z][A-Z]).*", stop)
	if m != None:
		index = stop.find(m.group(1))
		#addStop(stop[:index + 1] + " / " + stop[index + 1:])
	while re.match(r".* [A-Z]$", stop):
		stop = stop[:-2]
	return stop

class Stop:
	def __init__(self):
		self.name = ''
		self.lat = 10000
		self.lot = 10000

class NaptanHandler(ContentHandler):
	inStopPoint = False
	inCommonName = False
	inLatitude = False
	inLongitude = False
	
	def startElement(self, name, attrs):
		if name == "CommonName": self.inCommonName = True
		elif name == "StopPoint":
			self.inStopPoint = True
			self.stop = Stop()
		elif name == "Latitude": self.inLatitude = True
		elif name == "Longitude": self.inLongitude = True

	def endElement(self,name):
		if name == "CommonName": self.inCommonName = False
		elif name == "Latitude": self.inLatitude = False
		elif name == "Longitude": self.inLongitude = False
		elif name == "StopPoint":
			self.inStopPoint = False
			self.stop.name =  modifyStopName(self.stop.name)
			stops.append(self.stop)
	
	def characters(self, data):
		if self.inStopPoint:
			if self.inCommonName:
				self.stop.name += data
			elif self.inLatitude:
				self.stop.lat = float(data)
			elif self.inLongitude:
				self.stop.lon = float(data)

saxparser = make_parser()
saxparser.setContentHandler(NaptanHandler())
saxparser.parse(open("naptan.xml"))

for stop in stops:
	print stop.name + '#' + str(stop.lat) + '#' + str(stop.lon)
