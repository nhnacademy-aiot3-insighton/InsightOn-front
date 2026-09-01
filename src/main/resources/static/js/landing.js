/*
 * InsightOn 랜딩 페이지 — React 18 (CDN, 무빌드) + htm
 * Spring Boot + Thymeleaf 유지: templates/index.html 이 window.__LP_STATE__ 주입 후 이 파일 로드.
 * 원본 시안: Claude Design "InsightOn (Standalone)" 번들.
 */
(function () {
  var React = window.React;
  var ReactDOM = window.ReactDOM;
  if (!React || !ReactDOM || !window.htm) {
    console.error('[landing] React/htm CDN 로드 실패');
    return;
  }
  var html = window.htm.bind(React.createElement);
  var useState = React.useState;
  var useEffect = React.useEffect;
  var useRef = React.useRef;

  var S = window.__LP_STATE__ || { state: 'GUEST', userName: null };

  function prefersReduce() {
    return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  }
  function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }
  function lerp(a, b, t) { return a + (b - a) * t; }

  /* ---- 스크롤 진입 시 blur-up 리빌 ----
     기본은 "보이는 상태". 화면 아래에 있는 요소만 armed(숨김) → 진입 시 표시.
     IntersectionObserver가 안 먹는 환경 대비 타임아웃 폴백 포함. */
  function Reveal(props) {
    var ref = useRef(null);
    useEffect(function () {
      var el = ref.current;
      if (!el || prefersReduce() || !('IntersectionObserver' in window)) return;
      var rect = el.getBoundingClientRect();
      if (rect.top < window.innerHeight * 0.85) return; // 이미 보이면 그대로 둠
      el.classList.add('lp-armed');
      var done = false;
      function show() {
        if (done) return;
        done = true;
        el.classList.add('is-in');
        io.disconnect();
        clearTimeout(t);
      }
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) { if (e.isIntersecting) show(); });
      }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
      io.observe(el);
      var t = setTimeout(show, 900);
      return function () { io.disconnect(); clearTimeout(t); };
    }, []);
    var Tag = props.as || 'div';
    return html`<${Tag} ref=${ref}
      class=${'lp-reveal ' + (props.class || '')}
      id=${props.id} style=${props.style}>${props.children}</${Tag}>`;
  }

  /* ---- 스크롤 진행도 훅 (요소 기준 0..1)
     trigger point = 요소 top - startVh*100vh, span 만큼 스크롤하는 동안 0→1 ---- */
  function useScrollProgress(ref, spanVh, startVh) {
    var st = useState(prefersReduce() ? 1 : 0);
    var p = st[0], setP = st[1];
    useEffect(function () {
      if (prefersReduce()) { setP(1); return; }
      var raf = 0;
      function update() {
        raf = 0;
        var el = ref.current;
        if (!el) return;
        var vh = window.innerHeight;
        var top = el.getBoundingClientRect().top + window.scrollY;
        var start = top - vh * (startVh != null ? startVh : 0);
        var span = vh * (spanVh || 1.4);
        setP(clamp((window.scrollY - start) / span, 0, 1));
      }
      function onScroll() { if (!raf) raf = requestAnimationFrame(update); }
      window.addEventListener('scroll', onScroll, { passive: true });
      window.addEventListener('resize', onScroll);
      update();
      return function () {
        window.removeEventListener('scroll', onScroll);
        window.removeEventListener('resize', onScroll);
      };
    }, []);
    return p;
  }

  /* ---- 스크린샷: 이미지 + 자리표시자 폴백 (파일 없으면 자리표시자 노출) ---- */
  function Shot(props) {
    return html`
      <div class="lp-shot-ph" style=${props.style}>${props.children}</div>
      ${props.src && html`<img class="lp-shot-img" src=${props.src} alt=${props.alt || ''}
        loading="lazy" onError=${function (e) { e.target.remove(); }} />`}`;
  }

  /* ---- Apple mac-studio 식: 스크롤에 따라 단어가 blur→선명 + 페이드인 ---- */
  function ScrollText(props) {
    var ref = useRef(null);
    useEffect(function () {
      var el = ref.current;
      if (!el || prefersReduce() || !('IntersectionObserver' in window)) return;
      if (el.getBoundingClientRect().top < window.innerHeight * 1.15) return;
      el.classList.add('lp-armed');
      var done = false;
      function show() { if (done) return; done = true; el.classList.add('is-in'); io.disconnect(); clearTimeout(t); }
      var io = new IntersectionObserver(function (entries) {
        entries.forEach(function (e) { if (e.isIntersecting) show(); });
      }, { threshold: 0.2, rootMargin: '0px 0px -10% 0px' });
      io.observe(el);
      var t = setTimeout(show, 900);
      return function () { io.disconnect(); clearTimeout(t); };
    }, []);
    var lines = props.lines || [String(props.children || '')];
    var total = 0;
    lines.forEach(function (l) { total += l.split(' ').filter(Boolean).length; });
    var counter = { i: 0 };
    var Tag = props.as || 'h2';
    return html`<${Tag} ref=${ref} class=${'lp-scrolltext ' + (props.class || '')} style=${props.style}>
      ${lines.map(function (line, li) {
        return html`<span class="lp-st-line" key=${li}>${
          line.split(' ').filter(Boolean).map(function (w, wi) {
            var delay = (counter.i++ / Math.max(total - 1, 1)) * 0.5;
            return html`<span class="lp-st-word" style=${{ transitionDelay: delay.toFixed(2) + 's' }} key=${wi}>${w + ' '}</span>`;
          })
        }</span>`;
      })}
    </${Tag}>`;
  }

  /* ================= NAV ================= */
  function Nav() {
    var isGuest = S.state === 'GUEST';
    return html`
      <nav class="lp-nav">
        <div class="lp-nav-left">
          <a href="/" class="lp-brand"><span class="lp-mark" aria-hidden="true"></span>InsightOn</a>
          <div class="lp-nav-links">
            <a href="#automation">Automation</a>
            <a href="#context">Context AI</a>
            <a href="#platform">Architecture</a>
          </div>
        </div>
        <div class="lp-nav-right">
          ${isGuest && html`<a href="/login">로그인</a>`}
          ${isGuest && html`<a href="/signup" class="lp-btn">무료로 시작하기</a>`}
          ${!isGuest && S.userName && html`<a href="/mypage">${S.userName}님</a>`}
          ${!isGuest && S.state === 'NO_GROUP' && html`<a href="/group-registration" class="lp-btn">그룹 생성 신청</a>`}
          ${!isGuest && S.state === 'HAS_GROUP' && html`<a href="/my-group" class="lp-btn">대시보드로</a>`}
        </div>
      </nav>`;
  }

  /* ================= HERO (핀-줌) ================= */
  function Hero() {
    var pinRef = useRef(null);
    var p = useScrollProgress(pinRef, 0.8);
    var reduce = prefersReduce();

    var titleStyle = reduce ? {} : {
      opacity: clamp(1 - p * 1.7, 0, 1),
      transform: 'translateY(' + (-p * 60) + 'px)'
    };
    var shotScale = reduce ? 1 : lerp(0.62, 1, clamp(p / 0.9, 0, 1));
    var shotStyle = reduce ? {} : {
      transform: 'scale(' + shotScale.toFixed(4) + ')',
      borderRadius: lerp(24, 0, clamp(p / 0.9, 0, 1)).toFixed(1) + 'px',
      opacity: clamp(0.45 + p * 0.7, 0, 1)
    };
    var calloutIn = clamp((p - 0.45) / 0.25, 0, 1);
    var calloutStyle = reduce ? {} : {
      opacity: calloutIn,
      transform: 'translateY(' + ((1 - calloutIn) * 18) + 'px)'
    };
    var cueStyle = reduce ? {} : { opacity: clamp(1 - p * 4, 0, 1) };

    return html`
      <header class="lp-hero-pin" ref=${pinRef}>
        <div class="lp-hero-stage">
          <div class="lp-hero-glow" aria-hidden="true"></div>

          <div class="lp-hero-shot" style=${shotStyle}>
            <${Shot} src="/img/landing/hero-dashboard.jpg" alt="실시간 IoT 관제 대시보드">
              실시간 IoT 관제 대시보드 스크린샷<br/>(센서 상태 · 공기질 · 에너지 그래프)
            </${Shot}>
          </div>

          <div class="lp-hero-copy" style=${titleStyle}>
            <p class="lp-eyebrow">멀티테넌트 오피스 IoT 관제 · AI 자동 제어</p>
            <h1>센서 데이터에서 판단까지,<br/>사람 없이 이어집니다</h1>
            <p class="lp-hero-sub">규칙 기반 자동화와 AI 자동 제어가, 사람이 개입하지 않아도 최적 상태를 유지하는 오피스를 만듭니다.</p>
          </div>

          <div class="lp-hero-callout" style=${calloutStyle}>
            <p class="lp-eyebrow">실시간 관제</p>
            <p>센서 상태 · 공기질 · 에너지 데이터를 한 화면에서 확인합니다.</p>
          </div>

          <div class="lp-scroll-cue" aria-hidden="true" style=${cueStyle}>SCROLL ↓</div>
        </div>
      </header>`;
  }

  /* ================= 본문 섹션들 ================= */
  function Intro() {
    return html`
      <section class="lp-section lp-intro">
        <${Reveal} as="p" class="lp-eyebrow">WHO IT'S FOR</${Reveal}>
        <${ScrollText} as="p" class="lp-lead"
          lines=${['여러 층, 여러 회의실의 공기질과 에너지를', '하나의 화면에서 관리하고 싶은 팀에게 필요합니다.']} />
      </section>`;
  }

  function Automation() {
    return html`
      <section class="lp-section" id="automation">
        <div class="lp-section-head">
          <${Reveal} as="p" class="lp-eyebrow">TWO-STAGE AUTOMATION</${Reveal}>
          <${ScrollText} lines=${['관제부터 판단까지, 두 층위로 자동화합니다']} />
        </div>

        <${Reveal} class="lp-two">
          <div class="lp-card">
            <div class="lp-card-icon sq"><span></span></div>
            <h3>규칙 기반 자동화</h3>
            <p class="lp-p">사람이 정한 명시적 규칙에 따라 동작합니다. 노코드로 조건과 액션을 구성해, 예측 가능한 상황을 즉시 처리합니다.</p>
          </div>
          <div class="lp-plus" aria-hidden="true">+</div>
          <div class="lp-card">
            <div class="lp-card-icon circ"><span></span></div>
            <h3>AI 자동 제어</h3>
            <p class="lp-p">LLM이 상황을 스스로 해석해 선제적으로 판단합니다. 규칙으로 정의되지 않은 상황에서도 최적의 조치를 제안하고 실행합니다.</p>
          </div>
        </${Reveal}>

        <${Reveal} class="lp-flow">
          <div class="lp-flow-cols">
            <div class="lp-flow-col">
              <div class="lp-flow-caption">센서 인입</div>
              <div class="lp-chip">온도 센서</div>
              <div class="lp-chip">재실 센서</div>
              <div class="lp-chip">실내 공기질</div>
            </div>
            <div class="lp-flow-col">
              <div class="lp-flow-caption">조건 평가</div>
              <div class="lp-chip">규칙 엔진</div>
              <div class="lp-chip dim">AI 컨텍스트 판단</div>
              <div class="lp-chip dim">날씨·미세먼지 결합</div>
            </div>
            <div class="lp-flow-col">
              <div class="lp-flow-caption">액션 실행</div>
              <div class="lp-chip out">냉방 강도 조정</div>
              <div class="lp-chip out">환기량 증대</div>
              <div class="lp-chip out">공기청정 ON</div>
            </div>
          </div>
        </${Reveal}>
      </section>`;
  }

  function ContextAI() {
    return html`
      <section id="context" class="lp-context">
        <div class="lp-section lp-split">
          <div>
            <${Reveal} as="p" class="lp-eyebrow">CONTEXTUAL JUDGMENT</${Reveal}>
            <${ScrollText} class="lp-h2-sm"
              lines=${['실내 데이터뿐 아니라', '날씨·미세먼지까지 결합합니다']} />
            <${Reveal}>
              <p class="lp-p" style=${{ fontSize: '16px', marginTop: '20px' }}>실시간 날씨·미세먼지 데이터를 실내 센서 값과 함께 해석해, 왜 이 조치가 필요한지를 자연어로 설명합니다.</p>
            </${Reveal}>
            <${Reveal} class="lp-quote">
              <p class="lp-eyebrow">AI 판단 예시</p>
              <p>"외부 미세먼지 농도가 '나쁨' 수준으로 예보되어, 3층 사무공간 환기를 중단하고 공기청정 모드로 전환합니다."</p>
            </${Reveal}>
          </div>
          <${Reveal} class="lp-shot-square">
            <${Shot} src="/img/landing/context-sensor.jpg" alt="실내 센서 + 외부 날씨·미세먼지 데이터 결합">
              실내 센서 + 외부 날씨/미세먼지<br/>데이터를 결합한 판단 화면
            </${Shot}>
          </${Reveal}>
        </div>
      </section>`;
  }

  function Architecture() {
    return html`
      <section class="lp-section" id="platform" style=${{ paddingTop: '140px' }}>
        <div class="lp-section-head">
          <${Reveal} as="p" class="lp-eyebrow">ARCHITECTURE</${Reveal}>
          <${ScrollText} lines=${['여러 고객사가 안전하게 함께 씁니다']} />
        </div>

        <${Reveal} class="lp-grid-2">
          <div class="lp-card">
            <h3>멀티테넌트 격리</h3>
            <p class="lp-p" style=${{ marginBottom: '28px' }}>고객사별 데이터와 인프라가 완전히 분리되어, 한 회사의 장애·트래픽 폭증이 다른 회사에 영향을 주지 않습니다.</p>
            <div class="lp-tenants">
              <div class="lp-tenant on"><b>테넌트 A</b><span>격리된 환경</span></div>
              <div class="lp-tenant"><b>테넌트 B</b><span>격리된 환경</span></div>
              <div class="lp-tenant"><b>테넌트 C</b><span>격리된 환경</span></div>
            </div>
          </div>
          <div class="lp-card">
            <h3>규칙 엔진 이중화</h3>
            <p class="lp-p" style=${{ marginBottom: '28px' }}>Rule Engine은 고정 2인스턴스 active-active로 운영되어, 한 인스턴스 장애 시에도 자동화 처리가 끊기지 않습니다.</p>
            <div class="lp-ha">
              <div class="lp-ha-node">ACTIVE</div>
              <div class="lp-ha-swap" aria-hidden="true">⇄</div>
              <div class="lp-ha-node">ACTIVE</div>
            </div>
          </div>
        </${Reveal}>

        <${Reveal} class="lp-pipeline">
          <div>
            <h3>초저지연 실시간성</h3>
            <p class="lp-p" style=${{ maxWidth: '420px' }}>센서 패킷 인입부터 화면 반영까지 초저지연으로 처리되어, 현장 상태가 지연 없이 화면에 나타납니다.</p>
          </div>
          <div class="lp-pipe-track">
            <div class="lp-pipe-node"><i></i><span>센서 인입</span></div>
            <div class="lp-pipe-line"></div>
            <div class="lp-pipe-node"><i></i><span>규칙 처리 · 40ms</span></div>
            <div class="lp-pipe-line"></div>
            <div class="lp-pipe-node"><i></i><span>화면 반영</span></div>
          </div>
        </${Reveal}>
      </section>`;
  }

  function CTA() {
    var s = S.state;
    return html`
      <section class="lp-cta" id="cta">
        <${ScrollText} lines=${['지금 InsightOn을 경험해보세요']} />
        <${Reveal} class="lp-cta-actions">
          ${s === 'GUEST' && html`<a href="/signup" class="lp-btn lp-btn-lg">무료로 시작하기</a>`}
          ${s === 'GUEST' && html`<a href="/login" class="lp-btn lp-btn-lg lp-btn-ghost">로그인</a>`}
          ${s === 'NO_GROUP' && html`<a href="/group-registration" class="lp-btn lp-btn-lg">그룹 생성 신청하기</a>`}
          ${s === 'NO_GROUP' && html`<a href="/my-group/join" class="lp-btn lp-btn-lg lp-btn-ghost">초대 코드로 가입</a>`}
          ${s === 'HAS_GROUP' && html`<a href="/my-group" class="lp-btn lp-btn-lg">대시보드로 이동</a>`}
        </${Reveal}>
      </section>`;
  }

  function Footer() {
    return html`
      <footer class="lp-footer">
        <span class="lp-brand"><span class="lp-mark" aria-hidden="true"></span>InsightOn</span>
        <span>© 2026 InsightOn · 멀티테넌트 오피스 IoT 관제</span>
      </footer>`;
  }

  function App() {
    /* 앵커 스무스 스크롤 */
    useEffect(function () {
      function onClick(ev) {
        var a = ev.target.closest && ev.target.closest('a[href^="#"]');
        if (!a) return;
        var el = document.getElementById(a.getAttribute('href').slice(1));
        if (!el) return;
        ev.preventDefault();
        el.scrollIntoView({ behavior: prefersReduce() ? 'auto' : 'smooth', block: 'start' });
      }
      document.addEventListener('click', onClick);
      return function () { document.removeEventListener('click', onClick); };
    }, []);

    return html`<div class="lp">
      <${Nav}/>
      <${Hero}/>
      <${Intro}/>
      <${Automation}/>
      <${ContextAI}/>
      <${Architecture}/>
      <${CTA}/>
      <${Footer}/>
    </div>`;
  }

  var root = document.getElementById('lp-root');
  ReactDOM.createRoot(root).render(html`<${App}/>`);
})();
