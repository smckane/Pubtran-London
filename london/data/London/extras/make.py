#!/usr/bin/env python
# coding=UTF-8

def findStation(station, stops):
	if station in stops: return station
	if station + ' Underground Station' in stops: return station + ' Underground Station'
	if station.replace(' and ', ' & ') in stops: return station.replace(' and ', ' & ')
	if station + ' (Central Line)' in stops: return station + ' (Central Line)'
	if station.replace(' ', '-') in stops: return station.replace(' ', '-')
	if station.replace(' (H & C)', ' (Circle Line)') in stops: return station.replace(' (H & C)', ' (Circle Line)')
	if station.replace('(District and Picc)', '(Dist & Pic lines)') in stops: return station.replace('(District and Picc)', '(Dist & Pic lines)')
	if station == 'Bromley-by-Bow': return 'Bromley-By-Bow'
	if station == 'Heathrow Terminals 123': return 'Heathrow Terminals 1-2-3 Underground Station'
	if station == 'Harrow and Wealdstone': return 'Harrow & Wealdstone Underground Station'
	if station == 'St John\'s Wood': return 'St.John\'s Wood'
	if station == 'Hammersmith': return 'Hammersmith (H&C Line) Underground Station'
	if station == 'King\'s Cross St Pancras': return 'King\'s Cross St.Pancras'
	if station == 'Edgware Road (Bakerloo)': return 'Edgware Road (Bakerloo Line)'
	return None

lines = ['Bakerloo', 'Central', 'District', 'Hammersmith & City, Circle', 'Jubilee', 'Metropolitan', 'Northern', 'Piccadilly', 'Victoria', 'Waterloo & City']

circle = ['Aldgate', 'Baker Street', 'Barbican', 'Bayswater', 'Blackfriars', 'Cannon Street', 'Edgware Road (H & C)', 'Embankment', 'Euston Square', 'Farringdon', 'Gloucester Road', 'Great Portland Street', 'High Street Kensington', 'King\'s Cross St Pancras', 'Liverpool Street', 'Mansion House', 'Monument', 'Moorgate', 'Notting Hill Gate', 'Paddington', 'Sloane Square', 'South Kensington', 'St. James\'s Park', 'Temple', 'Tower Hill', 'Victoria', 'Westminster']
hammersmith = ['Aldgate East', 'Barbican', 'Barking', 'Bow Road', 'Bromley-by-Bow', 'East Ham', 'Edgware Road (H & C)', 'Euston Square', 'Farringdon', 'Goldhawk Road', 'Great Portland Street', 'Hammersmith', 'King\'s Cross St Pancras', 'Ladbroke Grove', 'Latimer Road', 'Liverpool Street', 'Mile End', 'Moorgate', 'Paddington', 'Plaistow', 'Royal Oak', 'Stepney Green', 'Upton Park', 'West Ham', 'Westbourne Park', 'Whitechapel']

code = ''
stations = {}
for line in open('stationCodes.txt'):
	line = line.strip()
	line = line.replace('’', '\'')
	line = line.replace('Trackernet Data Services Guide Beta 0', '')
	
	if line in lines: code = line[0]
	
	if not (line[3] == ' ' and line[:2].upper() == line[:2]): continue
	if line[4:] == 'Olympia': continue
	
	code2 = code
	if code == 'H':
		code2 = ''
		if line[4:] in circle: code2 = 'c'
		if line[4:] in hammersmith: code2 += 'H'
		if code2 == '': print 'ERROR - ' + line[4:]
	
	stations[line[4:]] = stations.get(line[4:], line[:3] + ':') + code2

stops = []
for line in open('../stops.txt'):
	stops.append(line.strip())

for key in stations.keys():
	if findStation(key, stops) == None: print key
	#and / &, Debden, (Central Line), pomlcky misto mezer, tecka misto mezery, 

f = open('../extras.txt', 'w')
for key in stations.keys():
	station = findStation(key, stops)
	if station != None:
		f.write(station + ';' + stations[key] + '\n')
