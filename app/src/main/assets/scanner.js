(function () {
  if (window.__orbit && window.__orbit.v === 2) return;
  // Elemen yang TIDAK PERNAH disentuh: tombol, link, submit (aturan: tidak ada auto-submit).
  var BTN = 'button,[type=submit],[type=button],[type=reset],[type=image],[role=button],a[href]';
  var SENSITIVE = /(pass|pwd|card|cvv|cvc|otp|pin|ssn|iban|account.?num)/i;
  var reg = {};

  function arr(l) { return Array.prototype.slice.call(l); }
  function vis(e) {
    var r = e.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) return false;
    var s = getComputedStyle(e);
    return s.visibility !== 'hidden' && s.display !== 'none';
  }
  function txt(e) { return (e.innerText || e.textContent || '').replace(/\s+/g, ' ').trim(); }
  function esc(id) { return (window.CSS && CSS.escape) ? CSS.escape(id) : id; }
  function byIds(ids) {
    return ids.split(/\s+/).map(function (i) { return document.getElementById(i); })
      .filter(Boolean).map(txt).join(' ').trim();
  }
  // Label sebuah kontrol: aria-label, aria-labelledby, label[for], label pembungkus, data-value.
  function optLabel(el) {
    var a = el.getAttribute('aria-label'); if (a && a.trim()) return a.trim();
    var l = el.getAttribute('aria-labelledby'); if (l) { var t = byIds(l); if (t) return t; }
    if (el.id) { var f = document.querySelector('label[for="' + esc(el.id) + '"]'); if (f) return txt(f); }
    var p = el.closest('label'); if (p) return txt(p);
    var d = el.getAttribute('data-value'); if (d) return d.trim();
    return txt(el);
  }
  function fieldLabel(el) {
    var l = optLabel(el);
    if (l && !/^(input|textarea|select)$/i.test(l)) return l;
    return (el.getAttribute('placeholder') || '').trim();
  }
  // Judul soal: teks terdekat di atas kontrol (saudara sebelumnya pada tingkat induk mana pun).
  function titleNear(el) {
    var n = el;
    for (var d = 0; d < 7 && n && n !== document.body; d++) {
      var p = n.previousElementSibling, hops = 0;
      while (p && hops < 4) {
        var t = txt(p);
        if (t && t.length >= 4 && !p.querySelector('input,select,textarea')) return t.slice(0, 160);
        p = p.previousElementSibling; hops++;
      }
      n = n.parentElement;
    }
    return '';
  }
  function titleOf(root) {
    var h = root.querySelector('[role=heading],legend,h1,h2,h3,h4');
    if (h && txt(h)) return txt(h).replace(/\s*\*$/, '');
    var l = root.getAttribute('aria-labelledby'); if (l) { var t = byIds(l); if (t) return t; }
    return '';
  }
  function checked(el) { return el.checked === true || el.getAttribute('aria-checked') === 'true'; }
  function guard(el) { return el.matches(BTN); }
  function setNative(el, v) {
    var proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  function sensitive(e) {
    var s = (e.name || '') + ' ' + (e.id || '') + ' ' + (e.getAttribute('autocomplete') || '');
    return SENSITIVE.test(s);
  }
  function isRequired(root) { return !!(root.querySelector && root.querySelector('[aria-required=true],[required]')); }
  function cmp(a, b) { return (a.compareDocumentPosition(b) & 4) ? -1 : 1; } // 4 = b setelah a

  function scan() {
    reg = {};
    var found = [], used = new Set(), unsupported = 0;

    function free(root, sel) {
      return arr(root.querySelectorAll(sel)).filter(function (e) { return vis(e) && !used.has(e) && !e.disabled; });
    }
    function labelsOf(els, kind) {
      var labels = [], keep = [];
      els.forEach(function (e) {
        var l = optLabel(e);
        if (!l || /^(other|lainnya)\b/i.test(l)) return; // opsi "Other" dilewati (butuh teks bebas)
        labels.push(l); keep.push(e);
      });
      return { labels: labels, els: keep };
    }
    function add(kind, els, sel, idx, title, required, anchor) {
      found.push({ anchor: anchor, kind: kind, els: els, sel: sel, idx: idx,
        out: { title: title, type: kind, options: [], required: required } });
    }

    // ---- Tahap 1: wadah soal yang jelas (Google Forms, Microsoft Forms, fieldset, ARIA group)
    var roots = arr(document.querySelectorAll('[role=listitem],fieldset,[role=radiogroup],[role=group]')).filter(vis);
    for (var ri = roots.length - 1; ri >= 0; ri--) {
      (function (root) {
        var radios = free(root, 'input[type=radio],[role=radio]');
        var checks = free(root, 'input[type=checkbox],[role=checkbox]');
        var texts = free(root, 'input[type=text],input:not([type]),input[type=email],input[type=number],input[type=tel],textarea')
          .filter(function (e) { return !e.readOnly && !sensitive(e); });
        var sels = free(root, 'select');
        var all = radios.concat(checks, texts, sels);
        if (!all.length) return;
        var kind = null, els = [];
        if (radios.length >= 2) { kind = 'single'; els = radios; }
        else if (checks.length >= 1) { kind = 'multi'; els = checks; }
        else if (texts.length === 1 && !radios.length && !sels.length) { kind = 'text'; els = texts; }
        else if (sels.length === 1 && !texts.length) { kind = 'select'; els = sels; }
        // Beberapa kelompok radio (name berbeda) dalam satu wadah = grid. Tidak didukung.
        if (kind === 'single') {
          var names = {}; els.forEach(function (e) { if (e.name) names[e.name] = 1; });
          if (Object.keys(names).length > 1) { all.forEach(function (e) { used.add(e); }); unsupported++; return; }
        }
        if (!kind) { all.forEach(function (e) { used.add(e); }); unsupported++; return; }
        var title = titleOf(root) || (kind === 'text' || kind === 'select' ? fieldLabel(els[0]) : '') || titleNear(els[0]);
        if (!title) return; // tanpa judul: biarkan tahap berikut mencoba
        all.forEach(function (e) { used.add(e); });
        if (kind === 'select') {
          var s = els[0], labels = [], idx = [];
          arr(s.options).forEach(function (o, i) { if (!o.disabled && o.value && txt(o)) { labels.push(txt(o)); idx.push(i); } });
          if (labels.length < 2) { unsupported++; return; }
          add('select', [], s, idx, title, isRequired(root), s); found[found.length - 1].out.options = labels;
        } else if (kind === 'text') {
          add('text', els, null, [], title, isRequired(root), els[0]);
        } else {
          var lo = labelsOf(els, kind);
          if (lo.labels.length < (kind === 'single' ? 2 : 1)) { unsupported++; return; }
          add(kind, lo.els, null, [], title, isRequired(root), lo.els[0]); found[found.length - 1].out.options = lo.labels;
        }
      })(roots[ri]);
    }

    // ---- Tahap 2: halaman HTML biasa tanpa pembungkus. Radio dikelompokkan menurut name,
    //      checkbox menurut wadah terdekat, lalu isian teks dan dropdown satu per satu.
    var rest = function (sel) { return free(document, sel); };
    var radiosByName = {};
    rest('input[type=radio]').forEach(function (e) {
      var k = e.name || ('#anon' + (e.parentElement ? arr(document.querySelectorAll('*')).indexOf(e.parentElement) : 0));
      (radiosByName[k] = radiosByName[k] || []).push(e);
    });
    Object.keys(radiosByName).forEach(function (k) {
      var els = radiosByName[k]; els.forEach(function (e) { used.add(e); });
      var lo = labelsOf(els, 'single'), title = titleNear(els[0]);
      if (lo.labels.length >= 2 && title) { add('single', lo.els, null, [], title, isRequired(els[0].parentElement || document), lo.els[0]); found[found.length - 1].out.options = lo.labels; }
      else unsupported++;
    });
    var cbs = rest('input[type=checkbox]'), done = new Set();
    cbs.forEach(function (e) {
      if (done.has(e)) return;
      var a = e.parentElement, grp = null;
      while (a && a !== document.documentElement) {
        var g = cbs.filter(function (x) { return !done.has(x) && a.contains(x); });
        if (g.length >= 2) { grp = g; break; }
        a = a.parentElement;
      }
      if (!grp) { done.add(e); used.add(e); unsupported++; return; } // satu checkbox sendirian (mis. persetujuan) dilewati
      grp.forEach(function (x) { done.add(x); used.add(x); });
      var lo = labelsOf(grp, 'multi'), title = titleNear(grp[0]);
      if (lo.labels.length >= 1 && title) { add('multi', lo.els, null, [], title, isRequired(a), lo.els[0]); found[found.length - 1].out.options = lo.labels; }
      else unsupported++;
    });
    rest('input[type=text],input:not([type]),input[type=email],input[type=number],input[type=tel],textarea').forEach(function (e) {
      used.add(e);
      if (e.readOnly || sensitive(e) || e.type === 'search') return;
      var title = fieldLabel(e) || titleNear(e);
      if (title) add('text', [e], null, [], title, e.required || e.getAttribute('aria-required') === 'true', e); else unsupported++;
    });
    rest('select').forEach(function (s) {
      used.add(s);
      var labels = [], idx = [];
      arr(s.options).forEach(function (o, i) { if (!o.disabled && o.value && txt(o)) { labels.push(txt(o)); idx.push(i); } });
      var title = fieldLabel(s) || titleNear(s);
      if (labels.length >= 2 && title) { add('select', [], s, idx, title, s.required, s); found[found.length - 1].out.options = labels; }
      else unsupported++;
    });

    found.sort(function (a, b) { return cmp(a.anchor, b.anchor); });
    var qs = found.map(function (f, i) {
      var id = 'q' + (i + 1);
      reg[id] = { kind: f.kind, els: f.els, sel: f.sel, idx: f.idx, root: f.els[0] || f.sel };
      f.out.id = id; f.out.title = f.out.title.replace(/\s*\*+\s*$/, '').slice(0, 200);
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

  // Satu-satunya tempat yang mengklik tombol: hanya tombol "Lanjut/Berikutnya" (whitelist eksak).
  // Tombol Kirim/Selesai/Konfirmasi (dan apa pun yang tidak ada di whitelist) TIDAK PERNAH diklik.
  function next() {
    var NEXT = /^(next|berikutnya|selanjutnya|lanjut|lanjutkan|continue)$/i;
    var SUB = /(submit|send|kirim|selesai|finish|done|konfirmasi|confirm|complete|simpan|save)/i;
    var btns = arr(document.querySelectorAll('button,[role=button],input[type=button],input[type=submit]')).filter(vis);
    var nextBtn = null, sub = false;
    btns.forEach(function (b) {
      var t = (b.tagName === 'INPUT' ? b.value : txt(b)).trim();
      var a = (b.getAttribute('aria-label') || '').trim();
      if (SUB.test(t) || SUB.test(a)) { sub = true; }
      else if (NEXT.test(t) || NEXT.test(a)) {
        if (!b.disabled && b.getAttribute('aria-disabled') !== 'true') nextBtn = nextBtn || b;
      }
    });
    if (nextBtn && sub) return JSON.stringify({ state: 'ambiguous' });
    if (nextBtn) { nextBtn.click(); return JSON.stringify({ state: 'clicked' }); }
    return JSON.stringify({ state: sub ? 'submit' : 'none' });
  }

  window.__orbit = { v: 2, scan: scan, fill: fill, check: check, next: next };
})();
