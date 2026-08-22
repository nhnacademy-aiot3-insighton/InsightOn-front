// InsightOn 로그인 - login.js
// 방식 A: 브라우저가 게이트웨이/인증 서버(insightonauth)를 직접 호출한다. 인증은 전부 auth가 한다.
// 프론트 Spring(/login POST)은 이 흐름에서 쓰지 않는다 — auth가 accessToken/refreshToken/userId를
// HttpOnly 쿠키로 브라우저에 직접 내려주므로, front(Java)는 토큰을 전혀 만지지 않는다.
//   로그인  POST /api/v1/auth/login {email,password} -> 200 (쿠키 3종 발급) / 401 / 423

(function () {
    const form = document.getElementById('loginForm');
    if (!form) return;

    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const errorBox = document.getElementById('loginErrorBox');
    const errorText = document.getElementById('loginErrorText');
    const btnSubmit = form.querySelector('button[type="submit"]');

    function showError(message) {
        if (!errorBox || !errorText) return;
        errorText.textContent = message;
        errorBox.style.display = message ? 'flex' : 'none';
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        showError('');
        btnSubmit.disabled = true;
        try {
            const res = await fetch('/api/v1/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: emailInput.value.trim(), password: passwordInput.value })
            });
            if (res.ok) {
                window.location.href = '/';
                return;
            }
            if (res.status === 423) {
                showError('로그인이 일시적으로 잠겼어요. 잠시 후 다시 시도해주세요.');
            } else if (res.status === 401 || res.status === 400) {
                showError('이메일 또는 비밀번호가 올바르지 않아요.');
            } else {
                showError('로그인에 실패했어요. 잠시 후 다시 시도해주세요.');
            }
        } catch (err) {
            showError('로그인 서버에 연결할 수 없어요. 잠시 후 다시 시도해주세요.');
        } finally {
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

    function postForm(path, params) {
        return fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams(params)
        });
    }

    // 이메일(아이디) 찾기 — front 목업
    const btnFindId = document.getElementById('btnFindId');
    if (btnFindId) {
        btnFindId.addEventListener('click', async () => {
            const name = document.getElementById('findIdName').value.trim();
            const phone = document.getElementById('findIdPhone').value.trim();
            const result = document.getElementById('findIdResult');
            if (!name || !phone) return;
            try {
                const res = await postForm('/find-id', { name, phone });
                const data = await res.json();
                result.textContent = `가입하신 이메일: ${data.maskedEmail}`;
            } catch (err) {
                result.textContent = '조회 중 오류가 발생했어요.';
            }
        });
    }

    // 비밀번호 재설정 요청 — front 목업
    const btnResetPassword = document.getElementById('btnResetPassword');
    if (btnResetPassword) {
        btnResetPassword.addEventListener('click', async () => {
            const email = document.getElementById('resetEmail').value.trim();
            const result = document.getElementById('resetPasswordResult');
            if (!email) return;
            try {
                const res = await postForm('/reset-password', { email });
                const data = await res.json();
                result.textContent = data.message;
            } catch (err) {
                result.textContent = '요청 중 오류가 발생했어요.';
            }
        });
    }
})();
