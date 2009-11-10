
// ==UserScript==
// @name          Optimize userscripts.org for mobile screen
// @description   Hide right-hand navigation bar on userscripts.org to optimize for mobile screens
// @author        Jeffrey Sharkey
// @include       http://*userscripts.org*
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


flattenById('content');
hideById('right');

document.body.style.background = "#ffffff";
