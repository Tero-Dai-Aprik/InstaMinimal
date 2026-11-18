(function () {
  if (window.__igBlocker) return;
  window.__igBlocker = true;

  const USERNAME = 'idkaprik';

  const BLOCKED = /^\/(reels|explore)\/?$/;
  function guard() {
    if (BLOCKED.test(location.pathname)) location.replace('/direct/inbox/');
  }
  guard();

  function fixNav() {
    const q = s => document.querySelector(s);
    const home = q('a[href="/"]');
    const inbox = q('a[href="/direct/inbox/"]');
    if (!home || !inbox) return;
    const profile = q('a[href="/' + USERNAME + '/"]');

    let row = inbox.parentElement;
    while (row && row !== document.body &&
           !(row.contains(home) && (!profile || row.contains(profile)))) {
      row = row.parentElement;
    }
    if (!row || row === document.body) return;
    if (row.getBoundingClientRect().height > 150 || row.querySelectorAll('a').length > 8) return;

    const reels = q('a[href="/reels/"]');
    const explore = q('a[href="/explore/"]');

    row.style.display = 'flex';
    row.style.justifyContent = 'space-evenly';
    row.style.alignItems = 'center';

    for (const kid of row.children) {
      const hide = (reels && kid.contains(reels)) || (explore && kid.contains(explore));
      if (hide) { kid.style.display = 'none'; }
      else { kid.style.flex = '1'; kid.style.display = 'flex'; kid.style.justifyContent = 'center'; }
    }
  }

  // Is a single Reel open right now?
  const REEL_PAGE = /^\/reels?\/([^\/]+)/;
  function visibleArea(el) {
    const r = el.getBoundingClientRect();
    const w = Math.min(r.right, innerWidth) - Math.max(r.left, 0);
    const h = Math.min(r.bottom, innerHeight) - Math.max(r.top, 0);
    return (w > 0 && h > 0) ? w * h : 0;
  }
  function reelOpen() {
    const p = location.pathname;
    if (REEL_PAGE.test(p)) return true;               // its own page
    if (p.startsWith('/stories/')) return false;      // never lock stories
    const d = document.querySelector('div[role="dialog"]');
    if (d && d.querySelector('video')) return true;   // a video in a pop-up
    const big = innerWidth * innerHeight * 0.4;       // a large video on screen
    return Array.from(document.querySelectorAll('video')).some(v => visibleArea(v) > big);
  }

  // Backup: if the address jumps to a different Reel, go back (at most once per 1.5 s)
  let lockedReel = null, lastBack = 0;
  function lockSingleReel() {
    const m = location.pathname.match(REEL_PAGE);
    if (!m) { lockedReel = null; return; }
    if (!lockedReel) { lockedReel = m[1]; return; }
    if (m[1] !== lockedReel && Date.now() - lastBack > 1500) {
      lastBack = Date.now();
      history.back();
    }
  }

  const style = document.createElement('style');
  style.textContent = 'html.ig-home article { display: none !important; }';
  document.head.appendChild(style);

  setInterval(() => {
    guard();
    document.documentElement.classList.toggle('ig-home', location.pathname === '/');
    fixNav();
    lockSingleReel();
    // Tell the Android app whether a Reel is open, so it can lock vertical swipes
    try { if (window.IFNative) IFNative.setReelOpen(reelOpen()); } catch (e) {}
  }, 1000);
})();
