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

  const style = document.createElement('style');
  style.textContent = 'html.ig-home article { display: none !important; }';
  document.head.appendChild(style);

  setInterval(() => {
    guard();
    document.documentElement.classList.toggle('ig-home', location.pathname === '/');
    fixNav();
  }, 1000);
})();
