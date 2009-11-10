
// ==UserScript==
// @name          Scan barcode into Half.com
// @description   Add button to Half.com search box to scan barcode
// @author        Jeffrey Sharkey
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
	helper.value = 'Scan barcode...';
	helper.style.background = '#cfc';
	helper.style.color = '#484';
	helper.style.border = '1px solid #484';
	helper.style.padding = '5px';
	helper.style.marginLeft = '10px';
	helper.addEventListener('click', function(event) {
		var result = window.intentHelper.startActivityForResult(JSON.stringify({
			action:'com.google.zxing.client.android.SCAN',
			category:['CATEGORY_DEFAULT']
		}));
		result = JSON.parse(result);
		item.value = result['extras']['SCAN_RESULT'];
	}, false);
	return helper;
}

var append = [];
var items = document.body.getElementsByTagName('input');
for(i in items) {
	var item = items[i];
	if(item.name == 'query')
		append.push([item,generate(item)]);
}

for(i in append) {
	var target = append[i][0];
	var generated = append[i][1];
	insertAfter(generated, target);
}
