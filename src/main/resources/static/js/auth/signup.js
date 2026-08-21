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
    const nameInput     = document.getElementById('name');
    const phoneInput    = document.getElementById('phone');
    const btnSubmit     = document.getElementById('btnSubmitSignup');

    let emailChecked = false;
    let verificationToken = null;   // 코드확인 성공 시 auth 가 주는 토큰. 가입 때 실어보낸다.

    function setHint(el, text, kind) {
        if (!el) return;
        el.textContent = text || '';
        el.classList.remove('is-error', 'is-success');
        if (kind === 'error')   el.classList.add('is-error');
        if (kind === 'success') el.classList.add('is-success');
    }

    function postJson(path, body) {
        return fetch(`${GATEWAY}${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
    }

    // 이메일 인증(토큰)까지 끝나야 가입 버튼 활성화
    function refreshSubmitState() {
        btnSubmit.disabled = !(emailChecked && verificationToken);
    }

    // 이메일을 바꾸면 이전 확인/인증 무효화 (auth 는 verify 한 email 로 토큰을 발급하므로)
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
            const res = await postJson('/api/v1/auth/check-email', { email });
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

    // 2. 인증코드 발송 (-> 204)
    btnSendCode.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        if (!emailChecked) { setHint(codeStatus, '이메일 중복 확인을 먼저 하세요.', 'error'); return; }
        try {
            const res = await postJson('/api/v1/auth/email/verify-request', { email });
            if (res.ok) {   // 204 No Content
                codeSection.style.display = 'block';
                setHint(codeStatus, '인증코드를 발송했습니다. 메일함을 확인하세요.', 'success');
            } else {
                setHint(codeStatus, '발송에 실패했습니다.', 'error');
            }
        } catch (e) {
            setHint(codeStatus, '서버에 연결할 수 없습니다.', 'error');
        }
    });

    // 3. 인증코드 확인 (-> {verificationToken})
    btnVerifyCode.addEventListener('click', async () => {
        const email = emailInput.value.trim();
        const code = verifyCode.value.trim();
        if (!code) { setHint(codeStatus, '인증코드를 입력하세요.', 'error'); return; }
        try {
            const res = await postJson('/api/v1/auth/email/verify-confirm', { email, code });
            if (!res.ok) {
                setHint(codeStatus, '인증코드가 올바르지 않습니다.', 'error');
                verificationToken = null;
                refreshSubmitState();
                return;
            }
            const data = await res.json();
            verificationToken = data.verificationToken;   // ★ 가입 때 쓸 토큰
            setHint(codeStatus, '이메일 인증이 완료됐어요.', 'success');
            refreshSubmitState();
        } catch (e) {
            setHint(codeStatus, '서버에 연결할 수 없습니다.', 'error');
        }
    });

    // 4. 회원가입 (-> 201). auth 는 리다이렉트를 안 주므로 JS 가 로그인 페이지로 이동.
    form.addEventListener('submit', async (e) => {
        e.preventDefault();   // 프론트 /signup 으로 가지 않고 auth 로 직접 보낸다
        const email = emailInput.value.trim();
        const password = passwordInput.value;
        const userName = nameInput.value.trim();
        const phoneNumber = phoneInput.value.trim();

        if (!emailChecked)      { setHint(emailHint, '이메일 중복 확인을 해주세요.', 'error'); return; }
        if (!verificationToken) { setHint(codeStatus, '이메일 인증을 완료해주세요.', 'error'); return; }
        if (!password || !userName || !phoneNumber) return;

        btnSubmit.disabled = true;
        try {
            const res = await postJson('/api/v1/auth/signup', {
                email, password, userName, phoneNumber, token: verificationToken
            });
            if (res.status === 201) {
                window.location.href = '/login?registered=1';   // 로그인 페이지 + "가입 완료" 배너
            } else if (res.status === 400) {
                // auth 검증 실패 (비밀번호는 영문+숫자+특수문자 8~64자)
                setHint(codeStatus, '입력값을 확인해주세요. 비밀번호는 영문·숫자·특수문자를 포함해 8자 이상이어야 합니다.', 'error');
                btnSubmit.disabled = false;
            } else {
                setHint(codeStatus, '회원가입에 실패했습니다.', 'error');
                btnSubmit.disabled = false;
            }
        } catch (e) {
            setHint(codeStatus, '서버에 연결할 수 없습니다.', 'error');
            btnSubmit.disabled = false;
        }
    });

    // 비밀번호 표시/숨김 토글
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