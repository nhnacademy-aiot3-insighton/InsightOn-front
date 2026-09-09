// InsightOn 회원가입 - signup.js
// 방식 A: 브라우저가 게이트웨이/인증 서버(insightonauth)를 직접 호출한다. 인증은 전부 auth 가 한다.
// 프론트 Spring 목업(/signup/email/...)은 이 흐름에서 쓰지 않는다.
// 계약(auth DTO): 전부 JSON.
//   중복확인  POST /api/v1/auth/check-email          {email}          -> {available}
//   코드발송  POST /api/v1/auth/email/verify-request {email}          -> 204
//   코드확인  POST /api/v1/auth/email/verify-confirm {email, code}    -> {verificationToken}
//   회원가입  POST /api/v1/auth/signup {email,password,userName,phoneNumber,token} -> 201
//     (auth 는 리다이렉트를 주지 않으므로 201 을 받으면 JS 가 로그인 페이지로 이동시킨다)

const GATEWAY = ''; // 같은 오리진(insighton.store)의 /api 는 ingress가 게이트웨이로 라우팅해준다

// InsightOn 회원가입 - signup.js

(function () {
    const form = document.getElementById('signupForm');
    if (!form) return;

    const emailInput    = document.getElementById('signupEmail');
    const emailHint     = document.getElementById('emailHint');
    const btnCheckEmail = document.getElementById('btnCheckEmail');
    const btnSendCode   = document.getElementById('btnSendCode');
    const codeStatus    = document.getElementById('codeStatus');
    const codeSection   = document.getElementById('codeSection');
    const verifyCode    = document.getElementById('verifyCode');
    const btnVerifyCode = document.getElementById('btnVerifyCode');
    const passwordInput = document.getElementById('password');
    const passwordHint  = document.getElementById('passwordHint');
    const nameInput     = document.getElementById('name');
    const nameHint      = document.getElementById('nameHint');
    const phoneInput    = document.getElementById('phone');
    const phoneHint     = document.getElementById('phoneHint');
    const btnSubmit     = document.getElementById('btnSubmitSignup');

    // 제출 실패시 서버 메시지를 해당 필드 밑으로 보내기 위한 매핑 — 못 찾으면 codeStatus로 뭉뚱그림
    const fieldHintsByKeyword = [
        { keyword: '비밀번호', hint: passwordHint },
        { keyword: '이름', hint: nameHint },
        { keyword: '전화번호', hint: phoneHint }
    ];

    // 필드별 안내문구를 원래 상태(기본 안내문구, 에러 아님)로 되돌림
    function resetFieldHint(hint) {
        if (!hint) return;
        hint.textContent = hint.dataset.default || '';
        hint.classList.remove('is-error', 'is-success');
    }

    let emailChecked = false;
    let verificationToken = null;

    function setHint(el, text, kind) {
        if (!el) return;
        el.textContent = text || '';
        el.classList.remove('is-error', 'is-success');
        if (kind === 'error') {
            // 같은 오류가 연달아 뜰 때도 흔들림이 다시 재생되도록 강제로 리플로우시킨 뒤 클래스를 붙임
            void el.offsetWidth;
            el.classList.add('is-error');
        }
        if (kind === 'success') el.classList.add('is-success');
    }

    function postJson(path, body) {
        return fetch(path, {   // ★ 프론트 서버 경로 (게이트웨이 아님)
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
    }

    function refreshSubmitState() {
        btnSubmit.disabled = !(emailChecked && verificationToken);
    }

    function resetVerification() {
        emailChecked = false;
        verificationToken = null;
        codeSection.style.display = 'none';
        verifyCode.value = '';
        setHint(codeStatus, '');
        setHint(emailHint, '');
        refreshSubmitState();
    }

    emailInput.addEventListener('input', resetVerification);

    // 1. 이메일 중복 확인
    btnCheckEmail.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        if (!email) { setHint(emailHint, '이메일을 입력하세요.', 'error'); return; }
        try {
            const res = await postJson('/signup/check-email', { email });
            if (!res.ok) { setHint(emailHint, '이메일 형식을 확인해주세요.', 'error'); return; }
            const data = await res.json();
            if (data.available) {
                emailChecked = true;
                setHint(emailHint, '사용 가능한 이메일입니다.', 'success');
            } else {
                emailChecked = false;
                setHint(emailHint, '이미 사용 중인 이메일입니다.', 'error');
            }
        } catch (e) {
            setHint(emailHint, '서버에 연결할 수 없습니다.', 'error');
        }
    });

    // 2. 인증코드 발송
    const btnSendCodeDefaultText = btnSendCode.textContent;

    // 재전송 쿨다운(백엔드 60초와 동일) — 버튼에 남은 시간을 보여주고 그동안 재클릭을 막는다
    function startResendCooldown(seconds) {
        let remaining = seconds;
        btnSendCode.disabled = true;
        const timer = setInterval(() => {
            if (remaining <= 0) {
                clearInterval(timer);
                btnSendCode.textContent = btnSendCodeDefaultText;
                btnSendCode.disabled = false;
                return;
            }
            btnSendCode.textContent = `재전송 (${remaining}초)`;
            remaining -= 1;
        }, 1000);
    }

    btnSendCode.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        if (!emailChecked) { setHint(codeStatus, '이메일 중복 확인을 먼저 하세요.', 'error'); return; }
        btnSendCode.disabled = true;
        try {
            const res = await postJson('/signup/send-code', { email });
            if (res.ok) {
                codeSection.style.display = 'block';
                setHint(codeStatus, '인증코드를 발송했습니다. 메일함을 확인하세요.', 'success');
                startResendCooldown(60);
            } else if (res.status === 429) {
                setHint(codeStatus, '너무 빨리 재요청했어요. 잠시 후 다시 시도해주세요.', 'error');
                btnSendCode.disabled = false;
            } else if (res.status === 423) {
                setHint(codeStatus, '재전송 시도가 초과되어 15분간 잠겼습니다.', 'error');
            } else {
                setHint(codeStatus, '발송에 실패했습니다.', 'error');
                btnSendCode.disabled = false;
            }
        } catch (e) {
            setHint(codeStatus, '서버에 연결할 수 없습니다.', 'error');
            btnSendCode.disabled = false;
        }
    });

    // 3. 인증코드 확인
    btnVerifyCode.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        const code = verifyCode.value.trim();
        if (!code) { setHint(codeStatus, '인증코드를 입력하세요.', 'error'); return; }
        try {
            const res = await postJson('/signup/verify-code', { email, code });
            if (!res.ok) {
                setHint(codeStatus, '인증코드가 올바르지 않습니다.', 'error');
                verificationToken = null;
                refreshSubmitState();
                return;
            }
            const data = await res.json();
            verificationToken = data.verificationToken;
            setHint(codeStatus, '이메일 인증이 완료됐어요.', 'success');
            refreshSubmitState();
        } catch (e) {
            setHint(codeStatus, '서버에 연결할 수 없습니다.', 'error');
        }
    });

    // 4. 회원가입
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = emailInput.value.trim();
        const password = passwordInput.value;
        const userName = nameInput.value.trim();
        // 구분자(-, 공백, 괄호)만 제거 - 숫자 아닌 글자(문자 등)까지 지워버리면 서버 검증을 우회하게 되므로 그대로 둠
        const phoneNumber = phoneInput.value.trim().replace(/[-\s()]/g, "");

        if (!emailChecked)      { setHint(emailHint, '이메일 중복 확인을 해주세요.', 'error'); return; }
        if (!verificationToken) { setHint(codeStatus, '이메일 인증을 완료해주세요.', 'error'); return; }
        if (!password || !userName || !phoneNumber) return;

        btnSubmit.disabled = true;
        try {
            const res = await postJson('/signup/submit', {   // ★ 프론트 서버 경로
                email, password, userName, phoneNumber, token: verificationToken
            });
            if (res.status === 200 || res.status === 201) {
                window.location.href = '/login?registered=1';
            } else {
                const data = await res.json().catch(() => null);
                showSubmitError((data && data.message) || '회원가입에 실패했습니다.');
                btnSubmit.disabled = false;
            }
        } catch (e) {
            showSubmitError('서버에 연결할 수 없습니다.');
            btnSubmit.disabled = false;
        }
    });

    function showSubmitError(message) {
        [passwordHint, nameHint, phoneHint].forEach(resetFieldHint);
        const matched = fieldHintsByKeyword.find(({ keyword }) => message.includes(keyword));
        if (matched) {
            setHint(matched.hint, message, 'error');
        } else {
            setHint(codeStatus, message, 'error');
        }
    }

    const toggle = document.querySelector('.password-toggle');
    if (toggle && passwordInput) {
        toggle.addEventListener('click', () => {
            const isPw = passwordInput.type === 'password';
            passwordInput.type = isPw ? 'text' : 'password';
            const icon = toggle.querySelector('i');
            if (icon) {
                icon.classList.toggle('ti-eye', !isPw);
                icon.classList.toggle('ti-eye-off', isPw);
            }
        });
    }

    refreshSubmitState();
})();