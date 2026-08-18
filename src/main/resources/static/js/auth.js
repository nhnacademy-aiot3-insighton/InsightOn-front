(function () {
    // ---------- password visibility toggle (login, signup, reset-password-confirm, mypage) ----------
    document.querySelectorAll('.password-toggle').forEach((btn) => {
        btn.addEventListener('click', () => {
            const input = btn.parentElement.querySelector('input');
            const showing = input.type === 'text';
            input.type = showing ? 'password' : 'text';
            btn.querySelector('i').className = showing ? 'ti ti-eye' : 'ti ti-eye-off';
            btn.setAttribute('aria-label', showing ? '비밀번호 표시' : '비밀번호 숨기기');
        });
    });

    // ---------- generic countdown-disabled button, used for resend cooldowns ----------
    function startCooldown(btn, seconds, idleLabel) {
        let remaining = seconds;
        btn.disabled = true;
        const tick = () => {
            btn.textContent = remaining + '초 후 재발송';
            remaining -= 1;
            if (remaining < 0) {
                clearInterval(timer);
                btn.disabled = false;
                btn.textContent = idleLabel;
            }
        };
        tick();
        const timer = setInterval(tick, 1000);
    }

    function clearChildren(el) {
        while (el.firstChild) el.removeChild(el.firstChild);
    }

    // ---------- login: find-id / reset-password modals (AJAX, no page nav) ----------
    const findIdBtn = document.getElementById('btnFindId');
    if (findIdBtn) {
        findIdBtn.addEventListener('click', () => {
            const name = document.getElementById('findIdName').value.trim();
            const phone = document.getElementById('findIdPhone').value.trim();
            const resultEl = document.getElementById('findIdResult');
            clearChildren(resultEl);

            if (!name || !phone) {
                const hint = document.createElement('p');
                hint.className = 'field-hint danger';
                hint.textContent = '이름과 전화번호를 입력하세요.';
                resultEl.appendChild(hint);
                return;
            }

            findIdBtn.disabled = true;
            fetch('/find-id', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'name=' + encodeURIComponent(name) + '&phone=' + encodeURIComponent(phone)
            }).then((r) => r.json()).then((data) => {
                const card = document.createElement('div');
                card.className = 'masked-email-result';
                const icon = document.createElement('i');
                icon.className = 'ti ti-mail';
                const textWrap = document.createElement('div');
                const label = document.createElement('div');
                label.style.fontSize = 'var(--fs-meta)';
                label.style.color = 'var(--ink-soft)';
                label.textContent = name + '님의 이메일';
                const email = document.createElement('div');
                email.className = 'num';
                email.textContent = data.maskedEmail;
                textWrap.appendChild(label);
                textWrap.appendChild(email);
                card.appendChild(icon);
                card.appendChild(textWrap);
                resultEl.appendChild(card);
                findIdBtn.disabled = false;
            }).catch(() => {
                const hint = document.createElement('p');
                hint.className = 'field-hint danger';
                hint.textContent = '잠시 후 다시 시도해주세요.';
                resultEl.appendChild(hint);
                findIdBtn.disabled = false;
            });
        });
    }

    const resetBtn = document.getElementById('btnResetPassword');
    if (resetBtn) {
        resetBtn.addEventListener('click', () => {
            const email = document.getElementById('resetEmail').value.trim();
            const resultEl = document.getElementById('resetPasswordResult');
            clearChildren(resultEl);

            if (!email) {
                const hint = document.createElement('p');
                hint.className = 'field-hint danger';
                hint.textContent = '이메일을 입력하세요.';
                resultEl.appendChild(hint);
                return;
            }

            resetBtn.disabled = true;
            fetch('/reset-password', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: 'email=' + encodeURIComponent(email)
            }).then((r) => r.json()).then((data) => {
                const box = document.createElement('div');
                box.className = 'auth-error';
                if (data.ok) {
                    box.style.background = 'var(--success-tint)';
                    box.style.color = 'var(--success)';
                    box.style.borderColor = 'rgba(47,125,92,0.25)';
                }
                const icon = document.createElement('i');
                icon.className = data.ok ? 'ti ti-check' : 'ti ti-alert-circle';
                const span = document.createElement('span');
                span.textContent = data.message;
                box.appendChild(icon);
                box.appendChild(span);
                resultEl.appendChild(box);
                resetBtn.disabled = false;
            }).catch(() => {
                const hint = document.createElement('p');
                hint.className = 'field-hint danger';
                hint.textContent = '잠시 후 다시 시도해주세요.';
                resultEl.appendChild(hint);
                resetBtn.disabled = false;
            });
        });
    }

    // ---------- signup: email duplicate check + verification code send/verify ----------
    const emailInput = document.getElementById('signupEmail');
    if (!emailInput) return;

    const checkBtn = document.getElementById('btnCheckEmail');
    const emailHint = document.getElementById('emailHint');
    const sendCodeBtn = document.getElementById('btnSendCode');
    const sendCodeLabel = sendCodeBtn ? sendCodeBtn.textContent : '';
    const codeSection = document.getElementById('codeSection');
    const codeStatus = document.getElementById('codeStatus');
    const codeInput = document.getElementById('verifyCode');
    const verifyBtn = document.getElementById('btnVerifyCode');
    const verifiedFlag = document.getElementById('emailVerifiedInput');
    const submitBtn = document.getElementById('btnSubmitSignup');

    emailInput.addEventListener('input', () => {
        emailHint.textContent = '';
        emailHint.className = 'field-hint';
    });

    checkBtn.addEventListener('click', () => {
        const email = emailInput.value.trim();
        if (!email) return;
        checkBtn.disabled = true;
        fetch('/signup/email/check-duplicate', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: 'email=' + encodeURIComponent(email)
        }).then((r) => r.json()).then((data) => {
            emailHint.textContent = data.available ? '사용할 수 있는 이메일이에요.' : '이미 가입된 이메일이에요.';
            emailHint.className = 'field-hint ' + (data.available ? 'success' : 'danger');
            checkBtn.disabled = false;
        }).catch(() => {
            emailHint.textContent = '확인하지 못했어요. 잠시 후 다시 시도해주세요.';
            emailHint.className = 'field-hint danger';
            checkBtn.disabled = false;
        });
    });

    sendCodeBtn.addEventListener('click', () => {
        const email = emailInput.value.trim();
        if (!email) {
            codeStatus.textContent = '이메일을 먼저 입력하세요.';
            codeStatus.className = 'verify-status danger';
            return;
        }
        fetch('/signup/email/send-code', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: 'email=' + encodeURIComponent(email)
        }).then((r) => r.json()).then((data) => {
            codeSection.style.display = 'block';
            codeStatus.textContent = data.message;
            codeStatus.className = 'verify-status ' + (data.ok ? 'success' : 'warning');
            if (data.ok) startCooldown(sendCodeBtn, data.cooldownSeconds, sendCodeLabel);
        });
    });

    verifyBtn.addEventListener('click', () => {
        const code = codeInput.value.trim();
        fetch('/signup/email/verify-code', {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: 'code=' + encodeURIComponent(code)
        }).then((r) => r.json()).then((data) => {
            codeStatus.textContent = data.message;
            codeStatus.className = 'verify-status ' + (data.ok ? 'success' : (data.locked ? 'danger' : 'warning'));
            if (data.ok) {
                verifiedFlag.value = 'true';
                codeInput.disabled = true;
                verifyBtn.disabled = true;
                sendCodeBtn.disabled = true;
                submitBtn.disabled = false;
            }
            if (data.locked) {
                verifyBtn.disabled = true;
                codeInput.disabled = true;
                sendCodeBtn.disabled = true;
            }
        });
    });
})();
