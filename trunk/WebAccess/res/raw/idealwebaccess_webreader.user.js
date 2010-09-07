// ==UserScript==
// @name          IDEAL Web Access Loader
// @description   Loads the IDEAL Web Access web reader.
// @author        IDEAL Group, Inc.
// @include       http://*
// ==/UserScript==

function load(){
  var scriptNode = document.createElement("script");
  scriptNode.src = "content://com.ideal.webaccess.localjs/ideal-loader_webreader.js";
  document.body.appendChild(scriptNode);
}

window.setTimeout(load, 1000);