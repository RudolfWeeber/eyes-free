// ==UserScript==
// @name          IDEAL Web Access Loader (Custom script)
// @description   Loads the user's own custom script.
// @author        IDEAL Group, Inc.
// @include       http://*
// ==/UserScript==

function load(){
  var scriptNode = document.createElement("script");
  scriptNode.src = "content://com.ideal.webaccess.localjs/ideal-loader_custom.js";
  document.body.appendChild(scriptNode);
}

window.setTimeout(load, 1000);