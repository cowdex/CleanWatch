/*
 * CleanWatch page script. Runs before the page's own code, in every frame.
 *
 *  - every frame: hides common ad containers, refuses pop-up windows
 *  - top frame:   lays the page out at a TV-friendly width and turns D-pad
 *                 presses (sent by the app as __cw.key(name)) into moves of an
 *                 invisible mouse, taps and video controls
 *
 * The remote "selects" an element by parking the invisible mouse on it, so the
 * site shows its own hover effect; nothing is drawn by this script.
 *
 * __cw.key(name) returns '' when handled here, 'tap:x,y' (fractions of the
 * viewport) when the app should touch that point, or 'back' when the app
 * should go back in history.
 */
(function () {
  'use strict';
  if (window.__cw) return;

  var CFG = window.__cwConfig || {};
  var TOP = false;
  try { TOP = window.top === window; } catch (e) {}

  function whenRoot(fn) {
    if (document.documentElement) { fn(); return; }
    var mo = new MutationObserver(function () {
      if (document.documentElement) { mo.disconnect(); fn(); }
    });
    mo.observe(document, { childList: true });
  }

  // ---------------------------------------------------------------- ads

  // Element hiding. Add a selector here if an ad slot slips past the host list.
  var HIDE = [
    'ins.adsbygoogle', '.adsbygoogle', '[data-ad-slot]', '[data-ad-client]',
    'iframe[id^="google_ads_iframe"]', 'div[id^="google_ads_"]', 'div[id^="div-gpt-ad"]',
    'iframe[src*="doubleclick.net"]', 'iframe[src*="googlesyndication.com"]',
    '[class~="ad-banner"]', '[class~="ad-container"]', '[class~="ad-slot"]',
    '[class~="ad-wrapper"]', '[class~="ads-container"]', '[class~="banner-ad"]',
    '[class~="adsbox"]', '[class~="popunder"]', '[id="ad-container"]', '[id="ads-container"]'
  ];

  function addStyle() {
    if (document.getElementById('__cw-style')) return;
    var st = document.createElement('style');
    st.id = '__cw-style';
    st.textContent = HIDE.join(',') + '{display:none!important}' +
      (TOP ? '::-webkit-scrollbar{display:none}*{-webkit-tap-highlight-color:transparent}' +
        '[data-cw-sel]{outline:3px solid #95FF50!important;outline-offset:3px!important}' : '') +
      (TOP && CFG.lite ? '*,*::before,*::after{-webkit-backdrop-filter:none!important;' +
        'backdrop-filter:none!important}:not(video){filter:none!important}' : '');
    (document.head || document.documentElement).appendChild(st);
  }
  whenRoot(addStyle);
  document.addEventListener('DOMContentLoaded', addStyle);

  // Pop-ups: a same-site window.open becomes a normal navigation, anything else is dropped.
  window.open = function (url) {
    try {
      var u = new URL(String(url), location.href);
      if (TOP && u.hostname === location.hostname) location.href = u.href;
    } catch (e) {}
    return null;
  };

  // Tell the app when sound-on video is playing, so the TV doesn't start its screensaver.
  function reportPlaying() {
    var vs = document.querySelectorAll('video'), playing = false;
    for (var i = 0; i < vs.length; i++) {
      if (!vs[i].paused && !vs[i].ended && !vs[i].muted && vs[i].readyState > 2) playing = true;
    }
    try { window.CleanWatchBridge.setPlaying(playing); } catch (e) {}
  }
  ['playing', 'pause', 'ended', 'emptied', 'volumechange'].forEach(function (t) {
    document.addEventListener(t, reportPlaying, true);
  });

  if (!TOP) { window.__cw = { key: function () { return ''; } }; return; }

  // ----------------------------------------------------------- viewport

  // A 1080p TV reports a 960px-wide page, which gets the phone/tablet layout.
  // Lay the page out wider and let the WebView scale it to the screen.
  if (CFG.viewportWidth) {
    var want = 'width=' + CFG.viewportWidth + ', user-scalable=no';
    var fixViewport = function () {
      var ms = document.querySelectorAll('meta[name="viewport"]');
      for (var i = 0; i < ms.length; i++) {
        if (ms[i].getAttribute('content') !== want) ms[i].setAttribute('content', want);
      }
      if (!ms.length && document.head) {
        var m = document.createElement('meta');
        m.setAttribute('name', 'viewport');
        m.setAttribute('content', want);
        document.head.appendChild(m);
      }
    };
    whenRoot(function () {
      fixViewport();
      var mo = new MutationObserver(fixViewport);
      mo.observe(document.documentElement, { childList: true, subtree: true });
      window.addEventListener('load', function () { fixViewport(); mo.disconnect(); });
    });
  }

  // ----------------------------------------------------------- subtitles

  // cinejoy keeps its player settings in localStorage. Before its code reads them, turn on
  // "subtitles on start" in the language the app chose for this title (English for English
  // titles, French for French ones, Arabic otherwise).
  function setSubtitleSettings(lang) {
    try {
      var SETTINGS = 'cinejoy_settings';
      var st = JSON.parse(localStorage.getItem(SETTINGS) || '{}') || {};
      st.autoSubtitles = true;
      st.defaultSubtitleLanguage = lang;
      localStorage.setItem(SETTINGS, JSON.stringify(st));
    } catch (e) {}
  }
  if (CFG.subLang) {
    setSubtitleSettings(CFG.subLang);
  } else if (CFG.warmPath && location.pathname === CFG.warmPath) {
    // The background page: cinejoy reads its settings only once, here, so its own "subtitles on
    // start" would use a stale language for every film opened later. Leave the choice to play().
    try {
      var st0 = JSON.parse(localStorage.getItem('cinejoy_settings') || '{}') || {};
      st0.autoSubtitles = false;
      localStorage.setItem('cinejoy_settings', JSON.stringify(st0));
    } catch (e) {}
  }

  // Those settings are only read when cinejoy starts. When the app moves to a film inside an
  // already-running page, it picks the language in the player's subtitle menu instead, once
  // the film plays. (Also checked after a normal start: then it is already right.)
  var wantSubs = CFG.subLang || '', wantSubsFor = location.pathname, choosingSubs = false;

  // Sets the subtitle delay in the player's own subtitle panel: 'shift' (by arg seconds) or
  // 'reset'. done(status, value): 'ok' + the new delay, 'noplayer' or 'nopanel'.
  // (The panel's "Sync subtitle to audio" never finishes on the TV it was tested on, so the
  // app doesn't use it.)
  function subs(cmd, arg, done) {
    var t0 = Date.now();
    function waitFor(test, ms, cb) {
      var r = test();
      if (r || Date.now() - t0 > ms) { cb(r); return; }
      setTimeout(function () { waitFor(test, ms, cb); }, 50);
    }
    function panel() { return document.querySelector('.settings-popover'); }
    function button() { return document.querySelector('[aria-label="Subtitles"]'); }
    var btn = button();
    if (!btn) {
      // The player removes its buttons while they are hidden: move the mouse to bring them back.
      var v = document.querySelector('video'), n = 0;
      (function nudge() {
        btn = button();
        if (btn) { subs(cmd, arg, done); return; }
        if (!v || ++n > 15) { done('noplayer', ''); return; }
        var r = v.getBoundingClientRect();
        hoverPoint(r.left + r.width / 2 + (n % 2) * 9, r.top + r.height / 2);
        setTimeout(nudge, 120);
      })();
      return;
    }
    var openedHere = !panel();
    if (openedHere) btn.click();
    function close() { if (openedHere && panel()) btn.click(); }
    // The panel may be on a sub-page (a language's file list): step back to its first page.
    function toMain(cb, n) {
      var p = panel();
      if (!p) { cb(null); return; }
      if (p.querySelector('.menu-title-action')) { cb(p); return; }
      var back = p.querySelector('.menu-back-btn');
      if (!back || n > 4) { cb(null); return; }
      back.click();
      t0 = Date.now();
      waitFor(function () {
        var q = panel();
        return q && q.querySelector('.menu-title-action') ? q : null;
      }, 1200, function () { toMain(cb, n + 1); });
    }
    waitFor(panel, 1500, function (p) {
      if (!p) { done('nopanel', ''); return; }
      toMain(function (main) {
        if (!main) { close(); done('nopanel', ''); return; }
        if (cmd === 'lang') {
          var links = main.querySelectorAll('.menu-link'), row = null, i;
          for (i = 0; i < links.length; i++) {
            var img = links[i].querySelector('img');
            if (img && img.alt === arg) { row = links[i]; break; }
          }
          if (!row) { close(); done('nolang', ''); return; }            // not offered for this title
          if (/\bselected\b/.test(row.className)) { close(); done('ok', arg); return; }
          row.click();
          // The TV can take over a second to react: wait until the language shows as chosen,
          // or until its list of files opens (languages with many files), whichever comes.
          t0 = Date.now();
          waitFor(function () {
            var q = panel();
            if (!q) return 'gone';
            if (!q.querySelector('.menu-title-action')) return 'files';
            var links2 = q.querySelectorAll('.menu-link');
            for (var j = 0; j < links2.length; j++) {
              var im = links2[j].querySelector('img');
              if (im && im.alt === arg && /\bselected\b/.test(links2[j].className)) return 'chosen';
            }
            return null;
          }, 4000, function (state) {
            if (state !== 'files') { close(); done(state ? 'ok' : 'nopanel', arg); return; }
            // The list starts with the language's name, then its files: take the first file.
            var entries = panel().querySelectorAll('.menu-link');
            var file = entries.length > 1 ? entries[1] : entries[0];
            if (file) file.click();
            t0 = Date.now();
            waitFor(function () {
              var q = panel();
              return !q || q.querySelector('.menu-title-action') ? 'back' : null;
            }, 2500, function () { close(); done('ok', arg); });
          });
          return;
        }
        main.querySelector('.menu-title-action').click();   // "Customize"
        t0 = Date.now();
        waitFor(function () { return document.querySelector('.settings-popover input[type=number]'); }, 1500, function (num) {
          if (!num) { close(); done('nopanel', ''); return; }
          var cur = parseFloat(num.value) || 0;
          var next = cmd === 'reset' ? 0 : Math.max(-20, Math.min(20, Math.round((cur + (arg || 0)) * 10) / 10));
          // Set it the way typing would, so the player's own code picks it up.
          var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
          setter.call(num, String(next));
          num.dispatchEvent(new Event('input', { bubbles: true }));
          num.dispatchEvent(new Event('change', { bubbles: true }));
          var shown = num.value;
          close();
          done('ok', shown);
        });
      }, 0);
    });
  }

  function reportSubs(status, value) {
    try { window.CleanWatchBridge.subtitleStatus(status, value || ''); } catch (e) {}
  }

  // Hand cinejoy's watch progress to the app, for its Continue Watching row: when a film
  // pauses or ends, when the page goes away, and every 15 s while something plays.
  function sendProgress() {
    try {
      var p = localStorage.getItem('Cinejoy::Progress');
      if (p) window.CleanWatchBridge.saveProgress(p);
    } catch (e) {}
  }
  ['pause', 'ended'].forEach(function (t) { document.addEventListener(t, sendProgress, true); });
  window.addEventListener('pagehide', sendProgress);
  document.addEventListener('visibilitychange', function () { if (document.hidden) sendProgress(); });
  setInterval(function () {
    var v = document.querySelector('video');
    if (v && !v.paused) sendProgress();
  }, 15000);
  window.addEventListener('load', sendProgress);

  // ------------------------------------------------ moving between films

  // Navigate the way a click on cinejoy's own link does: its router swaps the page in place,
  // without reloading the site.
  function go(path) {
    if (!document.body) { location.href = path; return; }
    var a = document.createElement('a');
    a.href = path;
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    a.remove();
  }

  function play(path, lang) {
    if (lang) setSubtitleSettings(lang);
    wantSubs = lang || '';
    wantSubsFor = path;
    choosingSubs = false;
    toldNoFilm = '';
    try { deselect(); } catch (e) {}
    go(path);
    return 'ok';
  }

  function leave() {
    var vs = document.querySelectorAll('video');
    for (var i = 0; i < vs.length; i++) { try { vs[i].pause(); } catch (e) {} }
    wantSubs = '';
    try { deselect(); } catch (e) {}
    go(CFG.warmPath || '/settings/player');
  }

  // Waiting in the background: have cinejoy fetch its player pages' code now.
  var warmed = false;
  function warm() {
    if (warmed || !document.body) return;
    warmed = true;

    ['/watch/movie/1', '/watch/tv/1/1/1'].forEach(function (path) {
      var a = document.createElement('a');
      a.href = path;
      a.setAttribute('data-sveltekit-preload-code', 'eager');
      a.style.cssText = 'position:fixed;left:0;top:0;width:1px;height:1px;opacity:0;pointer-events:none';
      document.body.appendChild(a);
      try {
        a.dispatchEvent(new MouseEvent('mousemove', { bubbles: true }));
        a.dispatchEvent(new TouchEvent('touchstart', { bubbles: true }));
      } catch (e) {}
      setTimeout(function () { a.remove(); }, 10000);
    });
  }

  // A film page that shows a message instead of a player (not released yet, unavailable):
  // tell the app, so its loading screen steps aside and the message can be read.
  var toldNoFilm = '';
  setInterval(function () {
    if (!/\/watch\//.test(location.pathname) || toldNoFilm === location.pathname) return;
    if (document.querySelector('video')) return;
    var t = (document.body && document.body.innerText) || '';
    if (/Hasn.t Released|Releases on|not available|Something went wrong|Go Back/i.test(t)) {
      toldNoFilm = location.pathname;
      try { window.CleanWatchBridge.noFilm(); } catch (e) {}
    }
  }, 1000);

  setInterval(function () {
    if (!wantSubs || choosingSubs || location.pathname !== wantSubsFor) return;
    if (document.querySelector('.settings-popover')) return;   // the viewer is in a menu
    var v = document.querySelector('video');
    if (!v || v.paused || v.currentTime < 1) return;
    choosingSubs = true;
    subs('lang', wantSubs, function (status) {
      choosingSubs = false;
      if (status === 'ok' || status === 'nolang') wantSubs = '';
    });
  }, 800);

  // ----------------------------------------------------- lighter effects

  // Blur and glass are the most expensive things this site draws, and a TV chip
  // feels it. The site has its own flat look for slow devices
  // (html[data-flat-ui=on]): solid panels, no blur, no shadows. Switch it on and
  // hold it on. The style rule in addStyle() covers the blur even if the site
  // ever drops that mode.
  var LITE = !!CFG.lite;
  var FLAT_ATTR = 'data-flat-ui';
  var FLAT_RULE = /\[data-flat-ui=["']?on["']?\]/;
  if (LITE) {
    whenRoot(function () {
      var html = document.documentElement;
      var keep = function () {
        if (html.getAttribute(FLAT_ATTR) !== 'on') html.setAttribute(FLAT_ATTR, 'on');
      };
      keep();
      new MutationObserver(keep).observe(html, { attributes: true, attributeFilter: [FLAT_ATTR] });
    });
  }

  // ------------------------------------------------------- hover styles

  // Sites built with current Tailwind wrap every hover effect in
  // "@media (hover: hover)", which is false on a TV, so none of them would ever
  // show. The remote does hover (see the invisible mouse below), so lift those
  // rules out of the condition, keeping their place in the cascade.
  var HOVER_ONLY = /^\s*(\(\s*(any-)?(hover\s*:\s*hover|pointer\s*:\s*fine)\s*\)\s*(and\s*)?)+$/;
  var patchedSheets = new WeakSet();

  function liftHoverRules(group) {
    var rules;
    try { rules = group.cssRules; } catch (e) { return; }   // cross-origin sheet
    if (!rules) return;
    for (var i = rules.length - 1; i >= 0; i--) {
      var rule = rules[i];
      if (rule.type === 4 /* @media */ && HOVER_ONLY.test(rule.conditionText || '')) {
        var inner = rule.cssRules, texts = [];
        for (var k = 0; k < inner.length; k++) texts.push(inner[k].cssText);
        try {
          group.deleteRule(i);
          for (var j = texts.length - 1; j >= 0; j--) group.insertRule(texts[j], i);
        } catch (e) {}
      } else if (LITE && rule.type === 1 && FLAT_RULE.test(rule.selectorText || '') &&
                 rule.style.getPropertyValue('transition') === 'none') {
        // The site's flat look also freezes all motion. Keep the motion.
        rule.style.removeProperty('transition');
        rule.style.removeProperty('animation');
      } else if (rule.cssRules) {
        liftHoverRules(rule);   // @layer, @supports, other @media
      }
    }
  }

  function patchSheets() {
    var sheets = document.styleSheets;
    for (var i = 0; i < sheets.length; i++) {
      if (patchedSheets.has(sheets[i])) continue;
      try { if (!sheets[i].cssRules) continue; } catch (e) { continue; }
      patchedSheets.add(sheets[i]);
      liftHoverRules(sheets[i]);
    }
  }
  // Stylesheets arrive over time (each page of the site brings its own).
  document.addEventListener('load', function (e) {
    if (e.target && e.target.tagName === 'LINK') patchSheets();
  }, true);
  document.addEventListener('DOMContentLoaded', patchSheets);
  window.addEventListener('load', patchSheets);

  // ------------------------------------------------------ selection state

  var cur = null;          // element the remote is on
  var lastRect = null;     // where it was, in case the element goes away
  var pointerMode = false;
  var hovering = false;
  var hud = null, hudTimer = 0;
  var lastDialogBack = 0;
  var wakeFlip = 0;

  // The invisible mouse. The app turns these into real mouse-hover events, so the
  // site's :hover styles and mouseenter handlers run exactly as they do on a computer.
  function hoverPoint(x, y) {
    if (pointerMode) return;
    hovering = true;
    try { window.CleanWatchBridge.hover(x / innerWidth, y / innerHeight); } catch (e) {}
  }

  function hoverEl(el) {
    var r = el.getBoundingClientRect();
    var x = (Math.max(r.left, 0) + Math.min(r.right, innerWidth)) / 2;
    var y = (Math.max(r.top, 0) + Math.min(r.bottom, innerHeight)) / 2;
    hoverPoint(x, y);
  }

  function unhover() {
    if (!hovering) return;
    hovering = false;
    try { window.CleanWatchBridge.unhover(); } catch (e) {}
  }

  // Optional green outline (options menu). It is a CSS outline on the selected
  // element itself, so it moves, scrolls and zooms with it at no cost per frame.
  var RING = !!CFG.ring;
  var RING_ATTR = 'data-cw-sel';
  var marked = null;

  function mark() {
    var want = RING && !pointerMode ? cur : null;
    if (marked === want) return;
    if (marked) { try { marked.removeAttribute(RING_ATTR); } catch (e) {} }
    marked = want;
    if (marked) marked.setAttribute(RING_ATTR, '');
  }

  function deselect() {
    cur = null;
    lastRect = null;
    unhover();
    mark();
  }

  // A new page: forget the old selection instead of hovering whatever is now under it.
  ['pushState', 'replaceState'].forEach(function (name) {
    var orig = history[name];
    history[name] = function () {
      var before = location.href, result = orig.apply(this, arguments);
      if (location.href !== before) deselect();
      return result;
    };
  });
  window.addEventListener('popstate', deselect);

  function visible(el) {
    if (el.checkVisibility) {
      try {
        return el.checkVisibility({
          checkOpacity: true, checkVisibilityCSS: true,
          opacityProperty: true, visibilityProperty: true
        });
      } catch (e) {}
    }
    for (var n = el; n && n.nodeType === 1; n = n.parentElement) {
      var s = getComputedStyle(n);
      if (s.display === 'none' || s.visibility === 'hidden' || parseFloat(s.opacity) < 0.05) return false;
    }
    return true;
  }

  function curOk() {
    if (!cur || !cur.isConnected) return false;
    var r = cur.getBoundingClientRect();
    return r.width > 1 && r.height > 1 && visible(cur);
  }

  // With nothing selected, carry on from whatever the page still shows as hovered
  // (after going back, the button that was pressed is under the last tap again).
  function adoptHovered() {
    if (curOk()) return true;
    cur = null;
    var hs = document.querySelectorAll(':hover');
    if (!hs.length) return false;
    refresh();
    for (var n = hs[hs.length - 1]; n && n.nodeType === 1; n = n.parentElement) {
      if (allEls.indexOf(n) >= 0) {
        cur = n;
        if (curOk()) return true;
        cur = null;
        return false;
      }
    }
    return false;
  }

  function showHud(text) {
    if (!hud) {
      hud = document.createElement('div');
      hud.style.cssText = 'position:fixed;left:50%;top:10%;transform:translateX(-50%);z-index:2147483647;' +
        'pointer-events:none;padding:10px 24px;border-radius:999px;background:rgba(0,0,0,.75);color:#fff;' +
        'font:600 22px/1.3 system-ui,sans-serif;white-space:nowrap;transition:opacity .25s;opacity:0;';
    }
    var h = document.fullscreenElement || document.documentElement;
    if (hud.parentNode !== h) h.appendChild(hud);
    hud.textContent = text;
    hud.style.opacity = '1';
    clearTimeout(hudTimer);
    hudTimer = setTimeout(function () { hud.style.opacity = '0'; }, 1100);
  }

  // ---------------------------------------------------------- candidates

  // Finding every link and button is the slow part on a TV chip (the home page has
  // thousands of elements), so the list is kept until the page actually changes.
  var SEL = 'a[href],button,input,select,textarea,summary,[tabindex],[role]';
  var DIALOG_SEL = '[role="dialog"],[role="alertdialog"],[aria-modal="true"],dialog[open]';
  var ROLES = { button: 1, link: 1, tab: 1, menuitem: 1, option: 1, 'switch': 1, checkbox: 1, radio: 1 };
  var NATIVE = { A: 1, BUTTON: 1, INPUT: 1, SELECT: 1, TEXTAREA: 1, SUMMARY: 1 };
  var NATIVE_SEL = 'a[href],button,input,select,textarea';
  var dirty = true, built = false;
  var allSet = new Set(), dialogSet = new Set();   // kept up to date from page changes
  var allEls = [], dialogEls = [];                 // what the moves use
  var added = [], changed = [];                    // page changes not yet looked at
  var PENDING_MAX = 3000;

  whenRoot(function () {
    new MutationObserver(function (records) {
      dirty = true;
      if (!built) return;
      for (var i = 0; i < records.length; i++) {
        var rec = records[i];
        if (rec.type === 'attributes') {
          changed.push(rec.target);
        } else {
          for (var k = 0; k < rec.addedNodes.length; k++) {
            if (rec.addedNodes[k].nodeType === 1) added.push(rec.addedNodes[k]);
          }
        }
      }
      // A long film adds thousands of nodes (subtitle lines); start over rather than hoard them.
      if (added.length + changed.length > PENDING_MAX) { built = false; added = []; changed = []; }
    }).observe(document.documentElement, {
      childList: true, subtree: true, attributes: true,
      attributeFilter: ['role', 'tabindex', 'href', 'disabled', 'open', 'aria-modal', 'aria-disabled', 'type']
    });
  });

  // Which candidates are on or near the screen. The browser keeps this up to date
  // itself, so a key press only has to measure those few instead of every link.
  var near = new Set();
  var unreported = new Set();   // watched, but the browser has not said where they are yet
  var io = 'IntersectionObserver' in window ? new IntersectionObserver(function (entries) {
    for (var i = 0; i < entries.length; i++) {
      unreported.delete(entries[i].target);
      if (entries[i].isIntersecting) near.add(entries[i].target); else near.delete(entries[i].target);
    }
  }, { rootMargin: '150% 100% 150% 100%' }) : null;

  var wrappers = new Set();   // clickable containers of real links/buttons: skipped, their contents are used
  var videos = new Set();

  function drop(el) {
    allSet.delete(el);
    wrappers.delete(el);
    near.delete(el);
    unreported.delete(el);
    if (io) io.unobserve(el);
  }

  function consider(el) {
    var native = NATIVE[el.tagName];
    var okEl = (native ? (el.tagName !== 'A' || el.hasAttribute('href'))
        : (el.hasAttribute('tabindex') || ROLES[el.getAttribute('role')])) &&
      !el.disabled && el.type !== 'hidden' &&
      el.getAttribute('tabindex') !== '-1' && el.getAttribute('aria-disabled') !== 'true';
    if (!okEl) { if (allSet.has(el)) drop(el); return; }
    if (allSet.has(el)) return;
    allSet.add(el);
    if (io) { unreported.add(el); io.observe(el); }
    if (!native) {
      if (el.querySelector(NATIVE_SEL)) wrappers.add(el);
    } else {
      for (var n = el.parentElement; n && n !== document.body; n = n.parentElement) {
        if (allSet.has(n) && !NATIVE[n.tagName]) wrappers.add(n);
      }
    }
  }

  function scan(root) {
    var i, list = root.querySelectorAll(SEL);
    for (i = 0; i < list.length; i++) consider(list[i]);
    list = root.querySelectorAll(DIALOG_SEL);
    for (i = 0; i < list.length; i++) dialogSet.add(list[i]);
    list = root.querySelectorAll('video');
    for (i = 0; i < list.length; i++) videos.add(list[i]);
  }

  // Scans the whole page once, then only what was added or changed since.
  function refresh() {
    if (!dirty) return;
    dirty = false;
    patchSheets();
    var i;
    if (!built) {
      if (io) io.disconnect();
      near.clear();
      unreported.clear();
      allSet = new Set();
      dialogSet = new Set();
      wrappers = new Set();
      videos = new Set();
      added = [];
      changed = [];
      scan(document);
      built = true;
    } else {
      for (i = 0; i < added.length; i++) {
        var n = added[i];
        if (!n.isConnected) continue;
        if (n.matches(SEL)) consider(n);
        if (n.matches(DIALOG_SEL)) dialogSet.add(n);
        if (n.tagName === 'VIDEO') videos.add(n);
        scan(n);
      }
      for (i = 0; i < changed.length; i++) {
        var t = changed[i];
        if (!t.isConnected) continue;
        consider(t);
        if (t.matches(DIALOG_SEL)) dialogSet.add(t); else dialogSet.delete(t);
      }
      added = [];
      changed = [];
    }
    allEls = [];
    allSet.forEach(function (el) {
      if (!el.isConnected) { drop(el); return; }
      if (!wrappers.has(el)) allEls.push(el);
    });
    dialogEls = [];
    dialogSet.forEach(function (d) {
      if (d.isConnected) dialogEls.push(d); else dialogSet.delete(d);
    });
    videos.forEach(function (v) { if (!v.isConnected) videos.delete(v); });
  }

  // An open dialog traps the remote, like it traps a mouse.
  function scope() {
    refresh();
    for (var i = dialogEls.length - 1; i >= 0; i--) {
      var d = dialogEls[i];
      if (!d.isConnected) continue;
      var r = d.getBoundingClientRect();
      if (r.width > 20 && r.height > 20 && visible(d)) return d;
    }
    return document.fullscreenElement || document;
  }

  // everything=true measures every candidate on the page, not only those near the screen.
  function candidates(root, everything) {
    refresh();
    var out = [], nearOnly = io && root === document && !everything;
    for (var i = 0; i < allEls.length; i++) {
      var el = allEls[i];
      if (el === cur || (nearOnly && !near.has(el) && !unreported.has(el))) continue;
      if (root !== document && !root.contains(el)) continue;
      var r = el.getBoundingClientRect();
      if (r.width < 8 || r.height < 8) continue;
      out.push({ el: el, r: r });
    }
    return out;
  }

  // The sideways-scrolling row an element sits in, if any.
  var rowCache = new WeakMap();
  function rowOf(el) {
    if (rowCache.has(el)) return rowCache.get(el);
    var row = null;
    for (var n = el.parentElement; n && n !== document.body; n = n.parentElement) {
      if (n.scrollWidth > n.clientWidth + 40) {
        var o = getComputedStyle(n).overflowX;
        if (o === 'auto' || o === 'scroll') { row = n; break; }
      }
    }
    rowCache.set(el, row);
    return row;
  }

  // Checked only for the best-scoring candidates, so it can afford to be thorough.
  function usable(c) {
    var el = c.el, r = c.r, s = getComputedStyle(el);
    if (s.visibility === 'hidden' || s.pointerEvents === 'none' || !visible(el)) return false;
    if (el.closest('[aria-hidden="true"],[inert]')) return false;
    var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    if (cx < 0 || cy < 0 || cx >= innerWidth || cy >= innerHeight) return true;
    var top = document.elementFromPoint(cx, cy);
    if (!top || top === el || el.contains(top) || top.contains(el)) return true;
    // Something else is on top. That only matters when it is a fixed layer
    // (header, backdrop) that this element is not part of.
    for (var n = top; n && n.nodeType === 1 && n !== document.documentElement; n = n.parentElement) {
      var p = getComputedStyle(n).position;
      if (p === 'fixed' || p === 'sticky') return n.contains(el);
    }
    return true;
  }

  function gap(a1, a2, b1, b2) { return b1 > a2 ? b1 - a2 : (a1 > b2 ? a1 - b2 : 0); }

  function isFixed(el) {
    for (var n = el; n && n.nodeType === 1; n = n.parentElement) {
      if (getComputedStyle(n).position === 'fixed') return true;
    }
    return false;
  }

  // ------------------------------------------------------- spatial moves

  function select(el) {
    cur = el;
    // No el.focus(): keyboard focus would draw the browser's own outline. Only make
    // sure a text box that was being typed in lets go.
    var a = document.activeElement;
    if (a && a !== el && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA')) {
      try { a.blur(); } catch (e) {}
    }
    var r = el.getBoundingClientRect(), top = 120, edge = 60;
    var comfy = r.top >= top && r.bottom <= innerHeight - edge && r.left >= edge && r.right <= innerWidth - edge;
    if (!comfy && !isFixed(el)) {
      // Jump, don't glide: an animated scroll per press is what feels slow on a TV chip.
      try { el.scrollIntoView({ block: 'center', inline: 'center', behavior: 'instant' }); }
      catch (e) { el.scrollIntoView(); }
      r = el.getBoundingClientRect();
    }
    lastRect = { left: r.left, top: r.top, right: r.right, bottom: r.bottom, width: r.width, height: r.height };
    hoverEl(el);
    mark();
  }

  function selectFirst(cs, prefer) {
    var best = null, bs = Infinity;
    for (var i = 0; i < cs.length; i++) {
      var r = cs[i].r;
      if (r.bottom < 0 || r.top > innerHeight || r.right < 0 || r.left > innerWidth) continue;
      // Default: reading order, page content before the fixed header.
      var s = prefer ? prefer(r) : r.top * 3 + r.left;
      if (s >= bs) continue;
      if (!prefer && isFixed(cs[i].el)) s += 1e6;
      if (s < bs && usable(cs[i])) { best = cs[i]; bs = s; }
    }
    if (best) select(best.el);
    return !!best;
  }

  function best(dir, o, cs) {
    var horiz = dir === 'left' || dir === 'right';
    var ocx = o.left + o.width / 2, ocy = o.top + o.height / 2, scored = [];
    for (var i = 0; i < cs.length; i++) {
      var r = cs[i].r, cx = r.left + r.width / 2, cy = r.top + r.height / 2, main, cross;
      if (dir === 'left') {
        if (cx >= ocx || r.right > o.left + o.width * 0.25) continue;
        main = Math.max(0, o.left - r.right);
      } else if (dir === 'right') {
        if (cx <= ocx || r.left < o.right - o.width * 0.25) continue;
        main = Math.max(0, r.left - o.right);
      } else if (dir === 'up') {
        if (cy >= ocy || r.bottom > o.top + o.height * 0.25) continue;
        main = Math.max(0, o.top - r.bottom);
      } else {
        if (cy <= ocy || r.top < o.bottom - o.height * 0.25) continue;
        main = Math.max(0, r.top - o.bottom);
      }
      if (horiz) {
        cross = gap(o.top, o.bottom, r.top, r.bottom);
        // Left/right stays in the same band; it never jumps to another row.
        if (cross > Math.max(o.height, r.height) * 0.75) continue;
        scored.push({ c: cs[i], s: main + cross * 4 + Math.abs(cy - ocy) * 0.25 });
      } else {
        cross = gap(o.left, o.right, r.left, r.right);
        scored.push({ c: cs[i], s: main + cross * 4 + Math.abs(cx - ocx) * 0.25 });
      }
    }
    scored.sort(function (a, b) { return a.s - b.s; });
    for (var k = 0; k < scored.length && k < 40; k++) {
      if (usable(scored[k].c)) return scored[k].c.el;
    }
    return null;
  }

  function move(dir) {
    var root = scope();
    var ok = adoptHovered();
    var o = ok ? cur.getBoundingClientRect() : lastRect;
    if (!o) { selectFirst(candidates(root)); return; }

    var horiz = dir === 'left' || dir === 'right', target = null;
    // Along a row, only that row's items need measuring.
    var row = horiz && ok ? rowOf(cur) : null;
    if (row && (root === document || root.contains(row))) target = best(dir, o, candidates(row));
    if (!target) target = best(dir, o, candidates(root));
    if (!target && !horiz && io && root === document) target = best(dir, o, candidates(root, true));
    if (target) { select(target); return; }

    // Nothing that way: scroll, so content that loads on scroll can appear
    // (but never the page behind an open dialog).
    if (!horiz && root === document) {
      window.scrollBy(0, (dir === 'down' ? 1 : -1) * innerHeight * 0.6);
      if (ok) hoverEl(cur);
    }
  }

  function ok() {
    if (!adoptHovered()) { selectFirst(candidates(scope())); return ''; }
    var r = cur.getBoundingClientRect();
    var l = Math.max(r.left, 0), t = Math.max(r.top, 0);
    var rt = Math.min(r.right, innerWidth), b = Math.min(r.bottom, innerHeight);
    if (rt - l >= 2 && b - t >= 2) {
      var x = (l + rt) / 2, y = (t + b) / 2, top = document.elementFromPoint(x, y);
      // A real touch from the app: works for hold-to-confirm buttons and opens the keyboard.
      if (top && (top === cur || cur.contains(top))) {
        return 'tap:' + (x / innerWidth).toFixed(5) + ',' + (y / innerHeight).toFixed(5);
      }
    }
    cur.click();
    return '';
  }

  // A spot outside the dialog that is safe to tap: nothing clickable under it.
  function outsidePoint(d) {
    var r = d.getBoundingClientRect(), m = 16;
    var pts = [[0.5, 0.5], [0.12, 0.5], [0.88, 0.5], [0.5, 0.22], [0.5, 0.78],
      [0.12, 0.22], [0.88, 0.22], [0.12, 0.78], [0.88, 0.78]];
    for (var i = 0; i < pts.length; i++) {
      var x = pts[i][0] * innerWidth, y = pts[i][1] * innerHeight;
      if (x > r.left - m && x < r.right + m && y > r.top - m && y < r.bottom + m) continue;
      var t = document.elementFromPoint(x, y);
      if (!t || d.contains(t)) continue;
      if (t.closest('a[href],button,input,select,textarea,[role="button"],[role="link"]')) continue;
      return pts[i];
    }
    return null;
  }

  function back() {
    if (document.fullscreenElement) {
      try { document.exitFullscreen(); } catch (e) {}
      return '';
    }
    var sc = scope(), now = Date.now();
    // Close an open dialog or pop-up menu first. If that did not work, a second BACK goes back.
    if (sc !== document && now - lastDialogBack > 2500) {
      lastDialogBack = now;
      var inPlayer = !!playerVideo();
      var c = sc.querySelector('[aria-label*="close" i],[aria-label*="dismiss" i],[data-dismiss],[data-close]');
      var p = c ? null : outsidePoint(sc);
      // Closing a player menu means "back to the film", so drop the selection too.
      if (inPlayer) deselect();
      if (c) { c.click(); return ''; }
      // Pop-up menus close on a real tap beside them; most ignore a scripted Escape key.
      if (p) return 'tap:' + p[0] + ',' + p[1];
      ['keydown', 'keyup'].forEach(function (t) {
        (document.activeElement || document.body).dispatchEvent(
          new KeyboardEvent(t, { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true }));
      });
      return '';
    }
    return 'back';
  }

  // -------------------------------------------------------------- player

  var PLAYER_PATH = /\/watch(\/|$)/;

  // The film being watched, not a muted trailer playing behind a page.
  function playerVideo() {
    refresh();
    var found = null, bestArea = 0, onPlayerPage = PLAYER_PATH.test(location.pathname);
    videos.forEach(function (v) {
      if (v.readyState < 1 || !(onPlayerPage || (v.duration > 600 && !v.muted))) return;
      var r = v.getBoundingClientRect(), area = r.width * r.height;
      if (area >= innerWidth * innerHeight * 0.6 && area > bestArea) { found = v; bestArea = area; }
    });
    return found;
  }

  function anyVideo() {
    var v = playerVideo();
    if (v) return v;
    var vs = document.querySelectorAll('video');
    for (var i = 0; i < vs.length; i++) if (!vs[i].paused) return vs[i];
    return vs[0] || null;
  }

  function fmt(s) {
    if (!isFinite(s)) return '';
    s = Math.max(0, Math.floor(s));
    var h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (sec < 10 ? '0' : '') + sec;
  }

  function toggle(v) {
    if (v.paused) {
      var p = v.play();
      if (p && p.catch) p.catch(function () {});
      showHud('▶  ' + fmt(v.currentTime));
    } else {
      v.pause();
      showHud('❚❚  ' + fmt(v.currentTime));
    }
  }

  function seek(v, d) {
    var end = isFinite(v.duration) ? v.duration - 1 : Infinity;
    v.currentTime = Math.max(0, Math.min(end, v.currentTime + d));
    showHud((d > 0 ? '+' : '−') + Math.abs(d) + 's   ' + fmt(v.currentTime) +
      (isFinite(v.duration) ? ' / ' + fmt(v.duration) : ''));
  }

  // Players show their controls when the mouse moves over the picture.
  function wake(v) {
    var r = v.getBoundingClientRect();
    wakeFlip = (wakeFlip + 7) % 40;
    hoverPoint(r.left + r.width / 2 + wakeFlip, r.top + r.height / 2 + wakeFlip);
  }

  function selectControls() {
    if (curOk()) return;
    cur = null;
    // Bottom-left first: that is where the play button lives.
    selectFirst(candidates(scope()), function (r) {
      return (innerHeight - (r.top + r.height / 2)) * 3 + r.left;
    });
  }

  // ---------------------------------------------------------------- keys

  function key(name) {
    var v;
    switch (name) {
      case 'playpause': v = anyVideo(); if (v) toggle(v); return '';
      case 'play': v = anyVideo(); if (v && v.paused) toggle(v); return '';
      case 'pause': v = anyVideo(); if (v && !v.paused) toggle(v); return '';
      case 'ff': v = anyVideo(); if (v) seek(v, 30); return '';
      case 'rw': v = anyVideo(); if (v) seek(v, -30); return '';
    }

    v = playerVideo();
    if (v) {
      if (!curOk()) {
        // Watching, nothing selected: the remote drives the film directly.
        if (cur) deselect();
        if (name === 'ok') { toggle(v); return ''; }
        if (name === 'left') { seek(v, -10); return ''; }
        if (name === 'right') { seek(v, 10); return ''; }
        if (name === 'up' || name === 'down') { wake(v); setTimeout(selectControls, 250); return ''; }
      } else if (name === 'back' && !document.fullscreenElement && scope() === document) {
        // On the player's buttons: BACK returns to the film.
        deselect();
        return '';
      }
    }

    if (name === 'ok') return ok();
    if (name === 'back') return back();
    move(name);
    return '';
  }

  Object.defineProperty(window, '__cw', {
    value: {
      key: function (name) {
        try { var result = key(name) || ''; mark(); return result; }
        catch (e) { return name === 'back' ? 'back' : ''; }
      },
      setPointerMode: function (on) {
        if (on) deselect();
        pointerMode = !!on;
      },
      setRing: function (on) {
        RING = !!on;
        mark();
      },
      play: function (path, lang) { return play(String(path), String(lang || '')); },
      leave: leave,
      warm: warm,
      subtitles: function (cmd, arg) {
        subs(cmd, arg, reportSubs);
      },
      pauseAll: function () {
        var vs = document.querySelectorAll('video');
        for (var i = 0; i < vs.length; i++) { try { vs[i].pause(); } catch (e) {} }
      }
    }
  });
})();
