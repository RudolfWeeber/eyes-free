// ==UserScript==
// @name          CVOX
// @description   Generic CVOX Script
// @author        Charles L. Chen
// @include       http://*
// ==/UserScript==

var currentElem = null;
var prevElem = null;
var inputFocused = false;

function getNextLeafNode(){
  prevElem = currentElem;
  if (currentElem == null){
    currentElem = document.body;
  } else {
    while (!currentElem.nextSibling){
    currentElem = currentElem.parentNode;
    if (currentElem == document.body){
      currentElem = null;
      return currentElem;
    }
  }
  currentElem = currentElem.nextSibling;
  }
  while (currentElem.firstChild){
    currentElem = currentElem.firstChild;
  }
  return currentElem;  
}

function getPrevLeafNode(){
  prevElem = currentElem;
  if (currentElem == null){
    currentElem = document.body;
  } else {
    while (!currentElem.previousSibling){
    currentElem = currentElem.parentNode;
    if (currentElem == document.body){
      currentElem = null;
      return currentElem;
    }
  }
  currentElem = currentElem.previousSibling;
  }
  while (currentElem.lastChild){
    currentElem = currentElem.lastChild;
  }
  return currentElem;  
}

function containsText(textStr){
  if (textStr === null){
    return false;
  }
  if (textStr.length < 1){
    return false;
  }
  var myregexp = new RegExp("[a-zA-Z0-9]");
  return myregexp.test(textStr);
}

function getLineage(targetNode){
  var lineage = new Array();
  while (targetNode){
    lineage.push(targetNode);      
    targetNode = targetNode.parentNode;
  }
  lineage.reverse();
  while(lineage.length && !lineage[0].tagName && !lineage[0].nodeValue){
    lineage.shift();
  }
  return lineage;
}

//Compares Lineage A with Lineage B and returns
//the index value in B at which B diverges from A.
//If there is no divergence, the result will be -1.
//Note that if B is the same as A except B has more nodes
//even after A has ended, that is considered a divergence.
//The first node that B has which A does not have will
//be treated as the divergence point.
//
function compareLineages(lina, linb){
  var i = 0;
  while( lina[i] && linb[i] && (lina[i] == linb[i]) ){
    i++;
  }
  if ( !lina[i] && !linb[i] ){
    i = -1;
  }
  return i;
}



function getInfoOnCurrentElem(){
  var currentLineage = getLineage(currentElem);
  var divergence = compareLineages(getLineage(prevElem), currentLineage);
  var infoStr = "";
  for (var i=divergence, elem; elem = currentLineage[i]; i++){
    if (elem.tagName){
    if (elem.tagName == 'H1'){
      infoStr = infoStr + 'H 1. ';
    } else if (elem.tagName == 'H2'){
      infoStr = infoStr + 'H 2. ';
    } else if (elem.tagName == 'H3'){
      infoStr = infoStr + 'H 3. ';
    } else if (elem.tagName == 'H4'){
      infoStr = infoStr + 'H 4. ';
    } else if (elem.tagName == 'H5'){
      infoStr = infoStr + 'H 5. ';
    } else if (elem.tagName == 'H6'){
      infoStr = infoStr + 'H 6. ';
    } else if (elem.tagName == 'A'){
      infoStr = infoStr + 'Link. ';
    }
  }
  }
  return infoStr;
}

function readNext(){
  getNextLeafNode();
  while (!containsText(currentElem.textContent)){
    getNextLeafNode();
    if (currentElem === null){
      speak("End of document", 0, null);
      return;
    }
  }
  speak(getInfoOnCurrentElem() + ' ' + currentElem.textContent, 0, null);
  scrollToElem(currentElem);
}

function readPrev(){
  getPrevLeafNode();
  while (!containsText(currentElem.textContent)){
    getPrevLeafNode();
    if (currentElem === null){
      speak("Beginning of document", 0, null);
      return;
    }
  }
  speak(getInfoOnCurrentElem() + ' ' + currentElem.textContent, 0, null);
  scrollToElem(currentElem);
}

function scrollToElem(targetNode){
  while (!targetNode.offsetParent){
    targetNode = targetNode.parentNode;
  }
  var left = 0;
  var top = 0;
  while (targetNode.offsetParent) { 
    left += targetNode.offsetLeft;
    top += targetNode.offsetTop; 
    targetNode = targetNode.offsetParent;
  }
  left += targetNode.offsetLeft;
  top += targetNode.offsetTop;
  window.scrollTo(left, top);
}

function speak(textStr, queueMode, paramsArray){
  window.ttsHelper.speak(textStr, queueMode);
  //alert(textStr);
}

//TODO: Come up with a working escape sequence!
function keyPressHandler(evt){
  var keyCode = evt.keyCode;
  if (inputFocused){
    return true;
  }
  if (keyCode == 106) { // j
    readNext();
    return false;
  }
  if (keyCode == 107) { // k
    readPrev();
    return false;
  }  
  return true;
}

function keyDownHandler(evt){
  window.ttsHelper.stop();
  var keyCode = evt.keyCode;
  if (inputFocused){
    return true;
  }
  if ((keyCode == 32) || (keyCode == 13)) { // space or Enter (G1 enter key does not work)
    var targetElem = currentElem;
    while (targetElem){
	  if (targetElem.tagName == 'A'){
	    document.location = targetElem.href;
      return false;
	  }
	  targetElem = targetElem.parentNode;
	}
    return true;
  }
  return true;
}

function focusHandler(evt){
  if (evt.target.tagName && 
      evt.target.tagName == 'INPUT'){
      inputFocused = true;
      }
  return true;
}

function blurHandler(evt){
  inputFocused = false;
  return true;
}

if (document.location.toString().indexOf('http://www.google.com/search?') != 0){
  document.addEventListener('keypress', keyPressHandler, true);
  document.addEventListener('keydown', keyDownHandler, true);
  document.addEventListener('focus', focusHandler, true);
  document.addEventListener('blur', blurHandler, true);
  speak(document.title, 0, null);
}