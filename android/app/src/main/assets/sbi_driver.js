/* Kharcha top-up helper for the SBI Prepaid site.
   window.__KP = {amt, last4, done:{...}} is set by the app before this runs.
   Every automated click happens at most once per top-up: the app keeps the "done" list
   across page loads, so a payment button is never pressed twice. */
(function () {
  if (window.__kdrv) return; window.__kdrv = 1;
  var P = window.__KP || {}, D = P.done || {};
  var K = window.KharchaCap;
  function vis(e) { return e && e.offsetParent !== null; }
  function q(sel) { return Array.prototype.filter.call(document.querySelectorAll(sel), vis); }
  function txt(e) { return ((e.innerText || e.value || '') + '').replace(/\s+/g, ' ').trim(); }
  function byText(sel, re) { var a = q(sel); for (var i = 0; i < a.length; i++) if (re.test(txt(a[i]))) return a[i]; return null; }
  function setVal(el, v) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v);
    ['input', 'change', 'blur'].forEach(function (t) { el.dispatchEvent(new Event(t, { bubbles: true })); });
  }
  function once(k, f) { if (D[k]) return false; D[k] = 1; K.did(k); f(); return true; }
  var lastSay = '';
  function say(s, step) { var k = step + s; if (k !== lastSay) { lastSay = k; K.status(s, String(step)); } }
  var onSbi = /prepaid\.sbi/.test(location.host);

  // offer to save the login the user types (only read when they tap Continue)
  if (onSbi) document.addEventListener('click', function (ev) {
    var b = ev.target.closest && ev.target.closest('button');
    if (!b || !/^#\/login/.test(location.hash)) return;
    var u = q('input[name=username]')[0], p = q('input[name=password]')[0];
    if (u && p && u.value && p.value) K.offerLogin(u.value, p.value);
  }, true);

  function tick() {
    var h = location.hash || '', title = document.title || '', body = document.body ? document.body.innerText : '';

    if (onSbi && /^#\/login/.test(h)) {
      var u = q('input[name=username]')[0], p = q('input[name=password]')[0];
      var cap = q('input[placeholder="Captcha"], input[name=text]')[0];
      if (u && p && !u.value && !p.value) {
        var c = K.creds();
        if (c) { c = JSON.parse(c); setVal(u, c.u); setVal(p, c.p); }
      }
      if (cap && u && u.value && !cap.value && document.activeElement !== cap && !window.__kcapf) {
        window.__kcapf = 1; cap.focus(); K.keyboard();
      }
      if (cap && cap.value) window.__kcapf = 0;
      say(u && u.value && p && p.value ? 'Type the captcha and tap Continue' : 'Log in to SBI Prepaid', 1);
      return;
    }
    if (onSbi && /^#\/otp/.test(h)) {
      K.reached('otp');
      say('Enter the OTP – your keyboard usually suggests it', 1);
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
      else if (!D.home) say('Tap Proceed on your transit card', 1);
      return;
    }
    if (onSbi && /^#\/app\/management\/topUp/.test(h)) {
      if (P.last4 && !D.cardok && body.indexOf(P.last4) < 0) {
        say('This is another card. Switch to ····' + P.last4 + ' on the page', 'card');
        return;
      }
      var inp = q('input[type=number]')[0];
      if (!inp) return;
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
      else say('Open Top Up for ····' + P.last4 + ' on the page', 'card');
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
  setInterval(tick, 700);
  tick();
})();
