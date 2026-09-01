/*
 * InsightOn 랜딩 페이지 스크롤 연출 (신규 디자인)
 * - .lp-reveal 요소가 뷰포트에 들어오면 blur-up 페이드인
 * - 히어로 스크린샷 살짝 패럴랙스
 * - prefers-reduced-motion 이면 모두 비활성 (CSS에서 이미 즉시 표시 처리)
 */
(function () {
  var reduce = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ---- reveal on scroll ----
  var reveals = document.querySelectorAll('.lp-reveal');
  if (reduce || !('IntersectionObserver' in window)) {
    reveals.forEach(function (el) { el.classList.add('is-in'); });
  } else {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          e.target.classList.add('is-in');
          io.unobserve(e.target);
        }
      });
    }, { threshold: 0.15, rootMargin: '0px 0px -8% 0px' });
    reveals.forEach(function (el) { io.observe(el); });
  }

  // ---- hero screenshot parallax (very light) ----
  var shot = document.querySelector('.lp-hero-shot');
  if (shot && !reduce) {
    var raf = 0;
    var onScroll = function () {
      if (raf) return;
      raf = requestAnimationFrame(function () {
        raf = 0;
        var y = window.scrollY;
        if (y < window.innerHeight * 1.2) {
          shot.style.transform = 'translateY(' + (y * -0.04).toFixed(1) + 'px) scale(' +
            (1 + Math.min(y / 6000, 0.03)).toFixed(4) + ')';
        }
      });
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
  }

  // ---- smooth anchor scroll for nav links ----
  document.querySelectorAll('.lp-nav a[href^="#"], .lp-cta a[href^="#"]').forEach(function (a) {
    a.addEventListener('click', function (ev) {
      var id = a.getAttribute('href').slice(1);
      var target = id && document.getElementById(id);
      if (!target) return;
      ev.preventDefault();
      target.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' });
    });
  });
})();
