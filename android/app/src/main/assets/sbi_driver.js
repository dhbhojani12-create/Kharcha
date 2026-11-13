/* Kharcha top-up helper for the SBI Prepaid site, run behind Kharcha's own screen.
   window.__KP = {amt, last4, done:{...}} is set by the app before this runs.
   The user types the captcha and OTP in Kharcha; this script fills them in, plus the amount,
   and clicks through SBIePay. Every payment click happens at most once per top-up: the app
   keeps the "done" list across page loads, so nothing is pressed twice. */
(function () {
  if (window.__kdrv) return; window.__kdrv = 1;
  var P = window.__KP || {}, D = P.done || {};
  var K = window.KharchaCap;
  var t0 = Date.now();
  function vis(e) { return e && e.offsetParent !== null; }
  function q(sel) { return Array.prototype.filter.call(document.querySelectorAll(sel), vis); }
  function txt(e) { return ((e.innerText || e.value || '') + '').replace(/\s+/g, ' ').trim(); }
  function byText(sel, re) { var a = q(sel); for (var i = 0; i < a.length; i++) if (re.test(txt(a[i]))) return a[i]; return null; }
  function setVal(el, v) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    try { el.focus(); } catch (e) { }
    el.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v);
    var k = String(v).slice(-1) || 'a';
    el.dispatchEvent(new KeyboardEvent('keydown', { key: k, bubbles: true }));
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new KeyboardEvent('keyup', { key: k, bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
    el.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    el.dispatchEvent(new Event('blur', { bubbles: false }));
    try { el.blur(); } catch (e) { }
  }
  function once(k, f) { if (D[k]) return false; D[k] = 1; K.did(k); f(); return true; }
  var lastSay = '';
  function say(s, step) { var k = step + s; if (k !== lastSay) { lastSay = k; K.status(s, String(step)); } }
  function errText() {
    var a = q('mat-error, .error, .alert, .toast-message, .mat-snack-bar-container, .swal2-html-container, p, span, div, small');
    for (var i = 0; i < a.length; i++) {
      var t = txt(a[i]);
      if (t.length > 4 && t.length < 150 && a[i].children.length === 0 &&
          /invalid|incorrect|wrong|expired|locked|not match|failed|attempt|blocked|try again/i.test(t)) return t;
    }
    return '';
  }
  var onSbi = /prepaid\.sbi/.test(location.host);

  /* ---------- login: captcha image to Kharcha, captcha text back ---------- */
  function capImg() {
    var imgs = q('img'), best = null;
    for (var i = 0; i < imgs.length; i++) {
      var im = imgs[i], tag = (im.src + ' ' + im.alt + ' ' + im.className + ' ' + im.id);
      if (/captcha/i.test(tag) || /^data:image/.test(im.src)) { best = im; if (/captcha/i.test(tag)) break; }
    }
    return best;
  }
  function imgData(im) {
    if (/^data:image/.test(im.src)) return im.src;
    try {
      var c = document.createElement('canvas');
      c.width = im.naturalWidth; c.height = im.naturalHeight;
      c.getContext('2d').drawImage(im, 0, 0);
      return c.toDataURL('image/png');
    } catch (e) { return null; }
  }
  function capInput() { return q('input[placeholder="Captcha"], input[name=text]')[0]; }
  function field(which) {
    if (which === 'username') return q('input[name=username]')[0];
    if (which === 'password') return q('input[name=password]')[0];
    if (which === 'captcha') return capInput();
    if (/^otp\d$/.test(which)) return q('input[name=' + which + ']')[0];
    if (which === 'otp') return q('input[type=password], input[type=tel], input[type=number], input[type=text]')[0];
    return null;
  }
  /* The app types into the focused field with real key presses (SBI ignores script-set text). */
  window.__kFocus = function (which) {
    var el = field(which);
    if (!el) return false;
    var proto = HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, '');
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.focus();
    try { el.setSelectionRange(0, 0); } catch (e) { }
    return document.activeElement === el;
  };
  /* where a field is on screen, in device pixels inside the WebView, for a real tap */
  window.__kRect = function (which) {
    var el = field(which);
    if (!el) return '';
    try { el.scrollIntoView({ block: 'center' }); } catch (e) { }
    var r = el.getBoundingClientRect(), d = window.devicePixelRatio || 1;
    try { el.click(); } catch (e) { }
    return JSON.stringify({ x: (r.left + r.width / 2) * d, y: (r.top + r.height / 2) * d });
  };
  window.__kCapBox = function () { return !!capInput(); };
  window.__kOtpBoxes = function () { var n = 0; for (var i = 1; i <= 6; i++) if (field('otp' + i)) n++; return n; };
  window.__kContinue = function () {
    var b = q('button[name=verify]')[0] || byText('button', /continue|login|submit/i);
    window.__ksub = Date.now();
    if (b) b.click(); else K.needUser('Tap Continue on the SBI page.');
  };
  window.__kLogin = function () {
    var b = byText('button', /^(login|verify|submit|continue)$/i) || q('button[name=verify]')[0];
    window.__kotp = Date.now();
    if (b) b.click(); else K.needUser('Tap Login on the SBI page.');
  };
  var lastCap = '', capSent = '';

  function tick() {
    var h = location.hash || '', title = document.title || '', body = document.body ? document.body.innerText : '';

    if (onSbi && /^#\/login/.test(h)) {
      var im = capImg();
      if (im && im.complete && im.naturalWidth > 0) {
        var key = im.src.length + im.src.slice(-40);
        if (key !== lastCap) {
          var d = imgData(im);
          if (d) {
            var err = window.__ksub ? (errText() || "SBI didn't accept that. Check the captcha – it's case-sensitive.") : '';
            if (window.__ksub || lastCap === '') { K.captcha(d, err); window.__ksub = 0; }
            else K.captcha(d, 'SBI showed a new captcha – type this one.');
            lastCap = key; capSent = d;
          } else if (!window.__kneed) { window.__kneed = 1; K.needUser('Type the captcha on the SBI page'); }
        } else if (window.__ksub && Date.now() - window.__ksub > 7000) {
          // rejected without a new captcha (e.g. wrong password)
          window.__ksub = 0;
          K.captcha(capSent, errText() || "SBI didn't accept the login. Check your User ID and password.");
        }
      } else if (!im && Date.now() - t0 > 12000 && !window.__kneed) {
        window.__kneed = 1; K.needUser('Captcha not found – log in on the SBI page');
      }
      return;
    }
    if (onSbi && /^#\/otp/.test(h)) {
      var ok = byText('button', /^ok$/i);
      if (ok) { ok.click(); return; }
      if (!window.__kotpShown) { window.__kotpShown = 1; K.otpPage(''); }
      if (window.__kotp && Date.now() - window.__kotp > 7000) {
        window.__kotp = 0;
        K.otpPage(errText() || "That OTP didn't work. Check it and try again.");
      }
      return;
    }
    if (onSbi && /^#\/home/.test(h)) {
      // product picker: press Proceed on the transit / NCMC one
      var btns = q('button'), pick = null;
      for (var i = 0; i < btns.length && !pick; i++) {
        if (!/^proceed$/i.test(txt(btns[i]))) continue;
        var box = btns[i];
        for (var up = 0; up < 6 && box.parentElement; up++) {
          box = box.parentElement;
          if (/transit|ncmc|metro|mobility|common/i.test(txt(box)) && txt(box).length < 400) { pick = btns[i]; break; }
        }
      }
      if (pick && !D.home) { once('home', function () { pick.click(); }); say('Opening your transit card…', 2); }
      else if (!D.home && !window.__kneedH) { window.__kneedH = 1; K.needUser('Tap Proceed on your transit card'); }
      return;
    }
    if (onSbi && /^#\/app\/management\/topUp/.test(h)) {
      if (P.last4 && !D.cardok && body.indexOf(P.last4) < 0) {
        say('SBI opened a different card. Switch to ····' + P.last4 + ' on the SBI page, or continue if it is the right one.', 'card');
        return;
      }
      var inp = q('input[type=number]')[0];
      if (!inp) { say('Opening top-up…', 2); return; }
      if (!D.amt) { setVal(inp, String(P.amt)); once('amt', function () {}); say('Filled ₹' + P.amt, 2); return; }
      if (!D.proceed) {
        if (String(inp.value) !== String(P.amt)) { setVal(inp, String(P.amt)); return; }
        var pr = byText('button', /^proceed$/i);
        if (pr) { once('proceed', function () { pr.click(); }); say('Starting payment…', 2); }
        return;
      }
      if (!D.confirm) { var c2 = byText('button', /^(confirm|yes|ok|pay)\b/i); if (c2) once('confirm', function () { c2.click(); }); }
      say('Starting payment…', 2);
      return;
    }
    if (onSbi && /^#\/app/.test(h) && !D.proceed) {
      if (!D.nav) { once('nav', function () { location.hash = '#/app/management/topUp'; }); say('Opening top-up…', 2); }
      else say('Open Top Up for ····' + P.last4 + ' on the SBI page', 'card');
      return;
    }
    if (/SBIePay/i.test(title) && !/UPI QR Wait/i.test(title)) {
      say('Choosing UPI…', 2);
      var r = q('input[name=upiOpt]')[0];
      if (!r) { var up2 = byText('a', /^UPI$/); if (up2) once('upi', function () { up2.click(); }); return; }
      if (!r.checked && !D.radio) { once('radio', function () { r.click(); }); return; }
      var pn = document.getElementById('upiButton');
      if (!vis(pn)) pn = byText('button', /pay now/i);
      if (pn) once('paynow', function () { pn.click(); });
      return;
    }
    if (/UPI QR Wait/i.test(title)) {
      var ua = byText('button, a', /pay by any upi app/i);
      if (ua) once('upiapp', function () { ua.click(); });
      say('Approve the payment in your UPI app', 3);
      return;
    }
    if (onSbi && D.paynow && byText('a, button', /go to dashboard/i)) {
      var res = /fail|declin|unsuccess|cancel/i.test(body) ? 'fail' : 'ok';
      once('fin', function () { K.finished(res); });
    }
  }
  setInterval(tick, 600);
  tick();
})();
