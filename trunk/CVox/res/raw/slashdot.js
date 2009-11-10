
// ==UserScript==
// @name          Optimize Slashdot for mobile screen
// @description   Hide navigation bars on Slashdot to optimize for mobile screens
// @author        Jeffrey Sharkey
// @include       http://*slashdot.org*
// ==/UserScript==

function hideById(id) {
	var target = document.getElementById(id);
	if(target == null) return;
	target.style.display = 'none';
}

function flattenById(id) {
	var target = document.getElementById(id);
	if(target == null) return;
	target.style.padding = '0px';
	target.style.margin = '0px';
}

function flattenByClass(parent, tag, className) {
	var items = parent.getElementsByTagName(tag);
	for(i in items) {
		var item = items[i];
		if(typeof item.className !== 'string') continue;
		if(item.className.indexOf(className) != -1) {
			item.style.padding = '0px';
			item.style.margin = '0px';
		}
	}
}


hideById('links');
hideById('fad1');
hideById('fad2');
hideById('fad3');
hideById('fad6');
hideById('fad30');
hideById('fad60');
hideById('slashboxes');

var yuimain = document.getElementById('yui-main');
flattenByClass(yuimain,'div','yui-b');
flattenByClass(yuimain,'div','maincol');
flattenByClass(yuimain,'div','article usermode thumbs');

// these usually only apply when logged in
flattenById('contents');
flattenById('articles');
flattenById('indexhead');
