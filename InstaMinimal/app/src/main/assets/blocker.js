(function () {
  if (window.__igBlocker) return;
  window.__igBlocker = true;

  const USERNAME = 'idkaprik';

  // The app sets this to true while Reel time is running
  const allowed = () => window.__igReelsAllowed === true;

  // Explore is always blocked. The Reels feed is blocked unless Reel time is running.
  function guard() {
    const p = location.pathname;
    if (/^\/explore\/?$/.test(p) || (!allowed() && /^\/reels\/?$/.test(p))) {
      location.replace('/direct/inbox/');
    }
  }
  guard();

  // Bottom icons: found once, then reused (redone if Instagram changes the bar or Reel time changes)
  let navRow = null, navCount = 0, lastAllowed = null;
  function fixNav() {
    const a = allowed();
    if (navRow && navRow.isConnected && navRow.children.length === navCount &&
        navRow.style.display === 'flex' && lastAllowed === a) return;

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
    // Only touch a small bar; a big container means the wrong element was found
    if (row.getBoundingClientRect().height > 150 || row.querySelectorAll('a').length > 8) return;

    const reels = q('a[href="/reels/"]');
    const explore = q('a[href="/explore/"]');

    row.style.display = 'flex';
    row.style.justifyContent = 'space-evenly';
    row.style.alignItems = 'center';

    for (const kid of row.children) {
      const hide = (!a && reels && kid.contains(reels)) || (explore && kid.contains(explore));
      if (hide) { kid.style.display = 'none'; }
      else { kid.style.flex = '1'; kid.style.display = 'flex'; kid.style.justifyContent = 'center'; }
    }
    navRow = row;
    navCount = row.children.length;
    lastAllowed = a;
  }

  // Is a single Reel open right now? (the app uses this to lock swiping to the next Reel)
  const REEL_PAGE = /^\/reels?\/([^\/]+)/;
  function visibleArea(el) {
    const r = el.getBoundingClientRect();
    const w = Math.min(r.right, innerWidth) - Math.max(r.left, 0);
    const h = Math.min(r.bottom, innerHeight) - Math.max(r.top, 0);
    return (w > 0 && h > 0) ? w * h : 0;
  }
  function reelOpen() {
    if (allowed()) return false;                    // Reel time is running: no lock
    const p = location.pathname;
    if (REEL_PAGE.test(p)) return true;
    if (p.startsWith('/stories/')) return false;
    const videos = document.querySelectorAll('video');
    if (!videos.length) return false;
    const d = document.querySelector('div[role="dialog"]');
    if (d && d.querySelector('video')) return true;
    const big = innerWidth * innerHeight * 0.4;
    for (const v of videos) if (visibleArea(v) > big) return true;
    return false;
  }

  // Backup: if the address jumps to a different Reel, go back (at most once per 1.5 s)
  let lockedReel = null, lastBack = 0;
  function lockSingleReel() {
    if (allowed()) { lockedReel = null; return; }
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
    if (document.hidden) return;                    // do nothing while the app is in the background
    guard();
    document.documentElement.classList.toggle('ig-home', location.pathname === '/');
    fixNav();
    lockSingleReel();
    try { if (window.IFNative) IFNative.setReelOpen(reelOpen()); } catch (e) {}
  }, 1500);
})();
