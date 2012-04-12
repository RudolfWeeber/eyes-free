
// ==UserScript==
// @name          Share Digg story
// @description   One-click to share any Digg Mobile story through an E-mail 
// @author        Jeffrey Sharkey
// @include       http://m.digg.com/*
// ==/UserScript==

function generate(item) {
	var header = item.getElementsByTagName('h3')[0];
	var link = header.getElementsByTagName('a')[0];
	var body = item.getElementsByTagName('p')[0];
	
	var helper = document.createElement('input');
	helper.type = 'button';
	helper.value = 'Share in E-mail';
	helper.style.background = '#cfc';
	helper.style.color = '#484';
	helper.style.border = '1px solid #484';
	helper.style.padding = '5px';
	helper.style.marginLeft = '10px';
	helper.addEventListener('click', function(event) {
		window.intentHelper.startActivity(JSON.stringify({
			action:'ACTION_SEND',
			type:'plain/html',
			'EXTRA_SUBJECT':link.textContent,
			'EXTRA_TEXT':link.href+'\n\n'+body.textContent
		}));
	}, false);
	return helper;
}

var items = document.body.getElementsByTagName('div');
for(i in items) {
	var item = items[i];
	if(item.className == 'news-summary')
		item.appendChild(generate(item));
}



