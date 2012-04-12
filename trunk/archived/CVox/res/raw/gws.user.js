// ==UserScript==
// @name          GWS
// @description   GWS Script
// @author        Charles L. Chen
// @include       http://www.google.com/search?*
// ==/UserScript==

var inputFocused = false;

var resultElems = new Array();
var resultsIdx = -1;



function buildResultElems(){
  resultElems = new Array();
  var resNode = document.getElementById("res");
  var medNodes = resNode.getElementsByClassName("med");
  if (medNodes.length > 0){
//    resultElems.push(medNodes[0]);
  }
  var gNodes = resNode.getElementsByClassName("g");
  for (var i=0, gNode; gNode = gNodes[i]; i++){
    resultElems.push(gNode);
  }
}


function getNextResult(){
  if (resultElems.length < 1){
    return null;
  }
  resultsIdx++;
  if (resultsIdx > resultElems.length - 1){
    resultsIdx = 0;
    return null;
  }
  var elem = resultElems[resultsIdx];
  return elem;
}

function getPrevResult(){
  if (resultElems.length < 1){
    return null;
  }
  resultsIdx--;
  if (resultsIdx < 0){
    resultsIdx = resultElems.length - 1;
    return null;
  }
  var elem = resultElems[resultsIdx];
  return elem;
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

function applyLens(targetNode){
  var lensNode = document.getElementById("CVOX_LENS");
  if (!lensNode){
    lensNode = document.createElement('span');
	lensNode.id = "CVOX_LENS";
    lensNode.style.backgroundColor = '#CCE6FF';
    lensNode.style.borderColor = '#0000CC';
    lensNode.style.borderWidth = 'medium';
    lensNode.style.borderStyle = 'groove';
    lensNode.style.position = 'absolute';
    lensNode.style.display = 'none';
    document.body.appendChild(lensNode);
  }
  while (lensNode.firstChild){
    lensNode.removeChild(lensNode.firstChild);
  }
  if (targetNode === null) {
    lensNode.style.display = 'none';
    return;
  }
  var left = 0;
  var top = 0;
  var obj = targetNode;
  if (obj.offsetParent) {
    left = obj.offsetLeft;
    top = obj.offsetTop;
    obj = obj.offsetParent;
    while (obj !== null) {
      left += obj.offsetLeft;
      top += obj.offsetTop;
      obj = obj.offsetParent;
    }
  }
  lensNode.appendChild(targetNode.cloneNode(true));
  lensNode.style.top = top + 'px';
  lensNode.style.left = left + 'px';
  lensNode.style.zIndex = 999;
  lensNode.style.display = 'block';

  var adjustment = (1.3 * 100) + '%';
  lensNode.style.setProperty('font-size', adjustment, 'important');
  var subnodes = lensNode.getElementsByTagName('*');
  for (var i = 0, node; node = subnodes[i]; i++){
    node.style.setProperty('line-height', 'normal', 'important');
    node.style.setProperty('font-size', '100%', 'important');
  }

  
  scrollToElem(lensNode);
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
    readNextResult();
    return false;
  }
  if (keyCode == 107) { // k
    var resultNode = getPrevResult();
    if (resultNode){
      speak(resultNode.textContent,0,null);
	    applyLens(resultNode);
    }
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
    var elem = resultElems[resultsIdx];
    var links = elem.getElementsByTagName('A');
    document.location = links[0].href;
    return false;
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

function readNextResult(){
  var resultNode = getNextResult();
  if (resultNode){
    speak(resultNode.textContent,0,null);
	  applyLens(resultNode);
  }
}

document.addEventListener('keypress', keyPressHandler, true);
document.addEventListener('keydown', keyDownHandler, true);
document.addEventListener('focus', focusHandler, true);
document.addEventListener('blur', blurHandler, true);


buildResultElems();
readNextResult();