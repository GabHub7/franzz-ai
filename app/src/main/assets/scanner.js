(function () {
  if (window.__orbit && window.__orbit.v === 1) return;
  // Elemen yang TIDAK PERNAH disentuh: tombol, link, submit (aturan: tidak ada auto-next/submit).
  var BTN = 'button,[type=submit],[type=button],[type=reset],[type=image],[role=button],a[href]';
  var reg = {};

  function vis(e) {
    var r = e.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    var s = getComputedStyle(e);
    return s.visibility !== 'hidden' && s.display !== 'none';
  }
  function txt(e) { return (e.innerText || e.textContent || '').replace(/\s+/g, ' ').trim(); }
  function byIds(ids) {
    return ids.split(/\s+/).map(function (i) { return document.getElementById(i); })
      .filter(Boolean).map(txt).join(' ').trim();
  }
  function optLabel(el) {
    var a = el.getAttribute('aria-label'); if (a && a.trim()) return a.trim();
    var l = el.getAttribute('aria-labelledby'); if (l) { var t = byIds(l); if (t) return t; }
    if (el.id) {
      var id = (window.CSS && CSS.escape) ? CSS.escape(el.id) : el.id;
      var f = document.querySelector('label[for="' + id + '"]'); if (f) return txt(f);
    }
    var p = el.closest('label'); if (p) return txt(p);
    var d = el.getAttribute('data-value'); if (d) return d.trim();
    return txt(el);
  }
  function titleOf(root) {
    var h = root.querySelector('[role=heading],legend');
    if (h && txt(h)) return txt(h).replace(/\s*\*$/, '');
    var l = root.getAttribute('aria-labelledby'); if (l) { var t = byIds(l); if (t) return t; }
    return txt(root).slice(0, 120);
  }
  function checked(el) { return el.checked === true || el.getAttribute('aria-checked') === 'true'; }
  function guard(el) { return el.matches(BTN); }
  function setNative(el, v) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function scan() {
    reg = {};
    var found = [], used = new Set(), unsupported = 0;
    var roots = Array.prototype.slice.call(
      document.querySelectorAll('[role=listitem],fieldset,[role=radiogroup],[role=group],form')).filter(vis);
    // proses dari yang terdalam agar tiap kontrol diklaim oleh wadah soal terdekat
    for (var ri = roots.length - 1; ri >= 0; ri--) {
      (function (root, order) {
        var q = function (sel) {
          return Array.prototype.slice.call(root.querySelectorAll(sel))
            .filter(function (e) { return vis(e) && !used.has(e) && !e.disabled; });
        };
        var radios = q('input[type=radio],[role=radio]');
        var checks = q('input[type=checkbox],[role=checkbox]');
        var texts = q('input[type=text],input:not([type]),input[type=email],input[type=number],input[type=tel],textarea')
          .filter(function (e) { return !e.readOnly; });
        var sels = q('select');
        var all = radios.concat(checks, texts, sels);
        var kind = null, els = [];
        if (radios.length >= 2) { kind = 'single'; els = radios; }
        else if (checks.length >= 1) { kind = 'multi'; els = checks; }
        else if (texts.length === 1 && !radios.length && !sels.length) { kind = 'text'; els = texts; }
        else if (sels.length === 1 && !texts.length) { kind = 'select'; els = sels; }
        all.forEach(function (e) { used.add(e); });
        if (!kind) { if (all.length) unsupported++; return; }

        var labels = [], oel = [], idx = [];
        if (kind === 'select') {
          var s = els[0];
          Array.prototype.forEach.call(s.options, function (o, i) {
            if (o.disabled || !o.value || !txt(o)) return;
            labels.push(txt(o)); idx.push(i);
          });
          if (labels.length < 2) { unsupported++; return; }
        } else if (kind !== 'text') {
          els.forEach(function (e) {
            var l = optLabel(e);
            if (!l || /^(other|lainnya)\b/i.test(l)) return; // opsi "Other" dilewati (butuh teks bebas)
            labels.push(l); oel.push(e);
          });
          if (labels.length < (kind === 'single' ? 2 : 1)) { unsupported++; return; }
        }
        found.push({
          order: order, kind: kind, els: kind === 'text' ? els : oel, sel: kind === 'select' ? els[0] : null, idx: idx,
          out: {
            title: titleOf(root), type: kind, options: labels,
            required: !!root.querySelector('[aria-required=true],[required]')
          }
        });
      })(roots[ri], ri);
    }
    found.sort(function (a, b) { return a.order - b.order; });
    var qs = found.map(function (f, i) {
      var id = 'q' + (i + 1);
      reg[id] = { kind: f.kind, els: f.els, sel: f.sel, idx: f.idx, root: f.els[0] || f.sel };
      f.out.id = id;
      return f.out;
    });
    return JSON.stringify({ questions: qs, unsupported: unsupported });
  }

  function fill(id, a) {
    var r = reg[id];
    if (!r) return 'missing';
    var first = r.root; if (!first || !first.isConnected) return 'stale';
    if (r.kind === 'text') {
      if (typeof a.text !== 'string' || !a.text || guard(r.els[0])) return 'skip';
      setNative(r.els[0], a.text); return 'ok';
    }
    var want = a.choices || [];
    if (r.kind === 'select') {
      var o = r.idx[want[0]]; if (o == null) return 'skip';
      r.sel.selectedIndex = o;
      r.sel.dispatchEvent(new Event('input', { bubbles: true }));
      r.sel.dispatchEvent(new Event('change', { bubbles: true }));
      return 'ok';
    }
    want.forEach(function (i) {
      var el = r.els[i];
      if (!el || guard(el)) return;            // tidak pernah klik tombol/link
      if (!checked(el)) el.click();            // tidak pernah membatalkan pilihan yang sudah ada
    });
    return 'ok';
  }

  function check(id) {
    var r = reg[id]; if (!r) return JSON.stringify({ choices: [], text: '' });
    if (r.kind === 'text') return JSON.stringify({ choices: [], text: r.els[0].value || '' });
    if (r.kind === 'select') {
      var c = r.idx.indexOf(r.sel.selectedIndex);
      return JSON.stringify({ choices: c < 0 ? [] : [c], text: '' });
    }
    var got = [];
    r.els.forEach(function (e, i) { if (checked(e)) got.push(i); });
    return JSON.stringify({ choices: got, text: '' });
  }

  window.__orbit = { v: 1, scan: scan, fill: fill, check: check };
})();
