/*
 * 다크 모드 적용기 — <head> 에서 app.css 보다 먼저 동기 실행되어 첫 페인트 전에
 * <html data-theme> 를 세팅한다(플래시 방지).
 *
 * 사용자 설정은 'theme' 쿠키에 저장: 'system' | 'light' | 'dark'
 * - 쿠키가 없거나 'system' 이면 OS 설정(prefers-color-scheme)을 따른다.
 * 마이페이지의 화면 모드 UI 는 window.InsightTheme.set(...) 로 값을 바꾼다.
 */
(function () {
  function readPref() {
    var m = document.cookie.match(/(?:^|;\s*)theme=(system|light|dark)/);
    return m ? m[1] : 'system';
  }

  function systemDark() {
    return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches);
  }

  function resolve(pref) {
    if (pref === 'dark' || pref === 'light') return pref;
    return systemDark() ? 'dark' : 'light';
  }

  function apply(theme) {
    var el = document.documentElement;
    el.setAttribute('data-theme', theme);
    el.setAttribute('data-bs-theme', theme);
  }

  apply(resolve(readPref()));

  window.InsightTheme = {
    getPref: function () {
      return readPref();
    },
    getResolved: function () {
      return resolve(readPref());
    },
    set: function (pref) {
      if (pref !== 'system' && pref !== 'light' && pref !== 'dark') return;
      document.cookie = 'theme=' + pref + '; path=/; max-age=31536000; samesite=lax';
      apply(resolve(pref));
      try {
        window.dispatchEvent(new CustomEvent('themechange', {
          detail: { pref: pref, theme: resolve(pref) }
        }));
      } catch (e) { /* 구형 브라우저 무시 */ }
    }
  };

  // 'system' 상태에서 OS 테마가 바뀌면 즉시 반영
  if (window.matchMedia) {
    try {
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
        if (readPref() === 'system') {
          apply(resolve('system'));
          try {
            window.dispatchEvent(new CustomEvent('themechange', {
              detail: { pref: 'system', theme: resolve('system') }
            }));
          } catch (e) {}
        }
      });
    } catch (e) { /* Safari < 14 등 */ }
  }
})();
