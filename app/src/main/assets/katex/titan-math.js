// Rev 36: draws the maths in Codeforces statements and editorials with KaTeX, offline.
// Each formula is <span class="math" data-tex="…">text</span> (or a <div> for display maths);
// the text is the app's own conversion and stays if KaTeX can't draw the formula.
(function () {
  if (typeof katex === 'undefined') return;
  var els = document.querySelectorAll('[data-tex]');
  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    try {
      var out = document.createElement(el.tagName === 'DIV' ? 'div' : 'span');
      katex.render(el.getAttribute('data-tex'), out, {
        displayMode: el.tagName === 'DIV', throwOnError: true, strict: 'ignore', trust: false, maxExpand: 1000
      });
      el.textContent = '';
      el.appendChild(out);
      el.className += ' drawn';
    } catch (e) {
      el.className += ' undrawn';
    }
  }
})();
