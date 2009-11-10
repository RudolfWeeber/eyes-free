
// ==UserScript==
// @name          Pick E-mail address
// @description   Fill forms by picking an E-mail address from Android contacts
// @author        Jeffrey Sharkey
// @include       http://*digg.com/register*
// @include       http://*facebook.com*
// @include       http://*m.yahoo.com/p/mail/compose*
// @include       http://*m.half.com*
// ==/UserScript==

function insertAfter(newElement,targetElement) {
	var parent = targetElement.parentNode;
	if(parent.lastchild == targetElement) {
		parent.appendChild(newElement);
	} else {
		parent.insertBefore(newElement, targetElement.nextSibling);
	}
}

function generate(item) {
	var helper = document.createElement('input');
	helper.type = 'button';
	helper.value = 'Pick Android contact...';
	helper.style.background = '#cfc';
	helper.style.color = '#484';
	helper.style.border = '1px solid #484';
	helper.style.padding = '5px';
	helper.style.marginLeft = '10px';
	helper.addEventListener('click', function(event) {
		var result = window.intentHelper.startActivityForResult(JSON.stringify({
			action:'ACTION_GET_CONTENT',
			type:'vnd.android.cursor.item/email'
		}));
		result = JSON.parse(result);
		item.value = result['data']['data'];
	}, false);
	return helper;
}

var append = [];
var items = document.body.getElementsByTagName('input');
for(i in items) {
	var item = items[i];
	var digg = (item.name == 'email' || item.name == 'emailverify');
	var facebook = (item.name == 'reg_email__');
	var yahoo = (item.className == 'd' && (item.name.substr(0,2) == 'to' || item.name.substr(0,2) == 'cc'));
	var half = (item.name == 'email');
	if(digg || facebook || yahoo || half)
		append.push([item,generate(item)]);
}

for(i in append) {
	var target = append[i][0];
	var generated = append[i][1];
	insertAfter(generated, target);
}
