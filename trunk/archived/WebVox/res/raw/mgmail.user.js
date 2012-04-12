// ==UserScript==
// @name          Gmail
// @description   Gmail (Mobile) Script
// @author        Charles L. Chen
// @include       https://mail.google.com/mail/s/*
// ==/UserScript==

var inputFocused = false;

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
    window.setTimeout(speakCurrentMessage,0);
    return true;
  }
  if (keyCode == 107) { // k
    window.setTimeout(speakCurrentMessage,0);
    return true;
  }
  return true;
}

function findCurrentMessage(){
  var messageNodes = document.getElementsByClassName("a H Id");
  var message;
  for (var i=0; message = messageNodes[i]; i++){
    if (message.style.cssText.indexOf("visibility: visible;") != -1){
      return message.parentNode;
    }
  }
}

function speakCurrentMessage(){
  var message = findCurrentMessage();
  speak(message.textContent, 2, null);
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


document.addEventListener('keypress', keyPressHandler, true);
document.addEventListener('focus', focusHandler, true);
document.addEventListener('blur', blurHandler, true);