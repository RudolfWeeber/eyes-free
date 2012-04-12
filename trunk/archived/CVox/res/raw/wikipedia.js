
// ==UserScript==
// @name          Optimize Wikipedia and view coordinates in Maps and Radar
// @description   Hide navigation bars to optimize for mobile screens and link coordinates from articles to Maps and Radar apps.
// @author        Jeffrey Sharkey
// @include       http://*wikipedia.org/wiki/*
// ==/UserScript==


function hideByClass(tag, className) {
	var items = document.body.getElementsByTagName(tag);
	for(i in items) {
		var item = items[i];
		if(typeof item.className !== 'string') continue;
		if(item.className.indexOf(className) != -1)
			item.style.display = 'none';
	}
}

function hideById(id) {
	var target = document.getElementById(id);
	target.style.display = 'none';
}

var content = document.getElementById('content');
content.style.padding = '0px';
content.style.margin = '0px';

hideByClass('table','ambox');

hideById('column-one');
hideById('siteNotice');
hideById('toc');





function getElementByClass(parent, tag, className) {
	var items = parent.getElementsByTagName(tag);
	var answer = [];
	for(i in items) {
		var item = items[i];
		if(typeof item.className !== 'string') continue;
		if(item.className.indexOf(className) != -1)
			answer.push(item);
	}
	return answer;
}

function createButton(title) {
	var button = document.createElement('input');
	button.type = 'button';
	button.value = title;
	button.style.background = '#cfc';
	button.style.color = '#484';
	button.style.border = '1px solid #484';
	button.style.padding = '5px';
	button.style.marginLeft = '10px';
	return button;
}


var coords = document.getElementById('coordinates');
var dec = getElementByClass(coords, 'span', 'geo-dec')[0];
var lat = parseFloat(getElementByClass(dec, 'span', 'latitude')[0].textContent);
var lon = parseFloat(getElementByClass(dec, 'span', 'longitude')[0].textContent);

coords.appendChild(document.createElement('br'));

var maps = createButton('View in Maps');
maps.addEventListener('click', function(event) {
	window.intentHelper.startActivity(JSON.stringify({
		action:'ACTION_VIEW',
		data:'geo:'+lat+','+lon+'?z=14'
	}));
}, false);
coords.appendChild(maps);


var radar = createButton('Find using Radar');
radar.addEventListener('click', function(event) {
	window.intentHelper.startActivity(JSON.stringify({
		action:'com.google.android.radar.SHOW_RADAR',
		category:['CATEGORY_DEFAULT'],
		'latitude':lat,
		'longitude':lon
	}));
}, false);
coords.appendChild(radar);




