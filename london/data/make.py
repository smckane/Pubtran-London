#!/usr/bin/env python
# coding=UTF-8

import os, string

def simplify(s):
	s = s.replace('ě', 'e')
	s = s.replace('š', 's')
	s = s.replace('č', 'c')
	s = s.replace('ř', 'r')
	s = s.replace('ž', 'z')
	s = s.replace('ý', 'y')
	s = s.replace('á', 'a')
	s = s.replace('í', 'i')
	s = s.replace('é', 'e')
	s = s.replace('ť', 't')
	s = s.replace('ú', 'u')
	s = s.replace('ů', 'u')
	s = s.replace('ň', 'n')
	s = s.replace('ď', 'd')
	s = s.replace('ó', 'o')
	s = s.replace('ö', 'o')
	s = s.replace('ü', 'u')
	
	s = s.replace('Ě', 'e')
	s = s.replace('Š', 's')
	s = s.replace('Č', 'c')
	s = s.replace('Ř', 'r')
	s = s.replace('Ž', 'z')
	s = s.replace('Ý', 'y')
	s = s.replace('Á', 'a')
	s = s.replace('Í', 'i')
	s = s.replace('É', 'e')
	s = s.replace('Ť', 't')
	s = s.replace('Ú', 'u')
	s = s.replace('Ů', 'u')
	s = s.replace('Ň', 'n')
	s = s.replace('Ď', 'd')
	s = s.replace('ó', 'o')
	
	s = s.replace('A', 'a')
	s = s.replace('B', 'b')
	s = s.replace('C', 'c')
	s = s.replace('D', 'd')
	s = s.replace('E', 'e')
	s = s.replace('F', 'f')
	s = s.replace('G', 'g')
	s = s.replace('H', 'h')
	s = s.replace('I', 'i')
	s = s.replace('J', 'j')
	s = s.replace('K', 'k')
	s = s.replace('L', 'l')
	s = s.replace('M', 'm')
	s = s.replace('N', 'n')
	s = s.replace('O', 'o')
	s = s.replace('P', 'p')
	s = s.replace('Q', 'q')
	s = s.replace('R', 'r')
	s = s.replace('S', 's')
	s = s.replace('T', 't')
	s = s.replace('U', 'u')
	s = s.replace('V', 'v')
	s = s.replace('W', 'w')
	s = s.replace('X', 'x')
	s = s.replace('Y', 'y')
	s = s.replace('Z', 'z')
	
	s = s.replace(')', ' ')
	s = s.replace('(', ' ')
	s = s.replace(',', ' ')
	s = s.replace('.', ' ')
	s = s.replace('-', ' ')
	s = s.replace('  ', ' ')
	s = s.replace('  ', ' ')
	
	return s

def create_java(area, extras, names, simplenames, lats, lons, imps):
	extrasS = ''
	namesS = ''
	simplenamesS = ''
	latsS = ''
	lonsS = ''
	impsS = ''
	for i in range(len(names)):
		if namesS != '':
			extrasS += ', '
			namesS += ', '
			simplenamesS += ', '
			latsS += ', '
			lonsS += ', '
			impsS += ', '
		if extras[i] == None: extrasS += 'null'
		else: extrasS += '"' + extras[i] + '"'
		namesS += '"' + names[i] + '"'
		simplenamesS += '"' + simplenames[i] + '"'
		latsS += lats[i]
		lonsS += lons[i]
		impsS += imps[i]
		
	output = '''package cz.fhejl.pubtran.data;

	public class ''' + area + ''' {

		public String[] extras = { '''
	output += extrasS
	output += ''' };

		public double[] importances = { '''

	output += impsS
	output += ''' };

		public double[] latitudes = { '''

	output += latsS
	output += ''' };

		public double[] longitudes = { '''
	output += lonsS
	output += ''' };

		public String[] names = { '''
	output += namesS
	output += ''' };

		public String[] simpleNames = { '''
	output += simplenamesS
	output += ''' };

	}
	'''
	
	open('../src/cz/fhejl/pubtran/data/' + area + '.java', 'w').write(output)

def process_area(area):
	print 'Processing area ' + area
	d = './' + area + '/'
	extras = []
	names = []
	simplenames = []
	lats = []
	lons = []
	imps = []
	f = open(d + 'stops.txt')
	for line in f:
		extras.append(None)
		names.append(line.strip())
		simplenames.append(simplify(line.strip()))
		lats.append('0')
		lons.append('0')
		imps.append('0')
	
	if os.path.exists(d + 'gps.txt'):
		for line in open(d + 'gps.txt'):
			parts =  line.strip().split(';')
			name = parts[0]
			lat = parts[1]
			lon = parts[2]
			if name in names:
				index = names.index(name)
				lats[index] = lat
				lons[index] = lon
			else: print '  ' + name + ' from gps.txt not found in stops.txt'
	
	if os.path.exists(d + 'importances.txt'):
		for line in open(d + 'importances.txt'):
			parts =  line.strip().split(';')
			name = parts[0]
			imp = parts[1]
			if name in names:
				index = names.index(name)
				imps[index] = imp
			else: print '  ' + name + ' from importances.txt not found in stops.txt'
	
	if os.path.exists(d + 'extras.txt'):
		for line in open(d + 'extras.txt'):
			parts =  line.strip().split(';')
			name = parts[0]
			extra = parts[1]
			if name in names:
				index = names.index(name)
				extras[index] = extra
			else: print '  ' + name + ' from extras.txt not found in stops.txt'
	
	i = 1
	while True:
		suffix = ''
		if len(names) > 1500:
			suffix = str(i)
		j = (i - 1) * 1500
		create_java(area + suffix, extras[j:j + 1500], names[j:j + 1500], simplenames[j:j + 1500], lats[j:j + 1500], lons[j:j + 1500], imps[j:j + 1500])
		if len(names) <= i * 1500: break
		i += 1

print 'Sorting all stops.txt files...',
os.system('java Sort')
print ' done'

for f in os.listdir('../src/cz/fhejl/pubtran/data'):
	os.remove('../src/cz/fhejl/pubtran/data/' + f)

d = '.'
for f in os.listdir(d):
	if os.path.isdir(d + '/' + f) and f[0] in string.uppercase:
		process_area(f)

