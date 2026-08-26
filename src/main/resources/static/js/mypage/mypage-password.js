// InsightOn 비밀번호 재설정 - mypage-password.js
// 비밀번호 표시/숨김 토글, 새 비밀번호 확인 일치 검증(제출 전 클라이언트 체크).

(function () {

    // ================================================================
    // 비밀번호 표시/숨김 토글
    // ================================================================
    document.querySelectorAll('.password-toggle').forEach((toggle) => {
        toggle.addEventListener('click', () => {
            const input = toggle.previousElementSibling;
            const isPw = input.type === 'password';
            input.type = isPw ? 'text' : 'password';
            const icon = toggle.querySelector('i');
            if (icon) {
                icon.classList.toggle('ti-eye', !isPw);
                icon.classList.toggle('ti-eye-off', isPw);
            }
        });
    });

    // ================================================================
    // 새 비밀번호 확인 일치 검증
    // ================================================================
    const newPassword = document.getElementById('newPassword');
    const newPasswordConfirm = document.getElementById('newPasswordConfirm');
    const matchError = document.getElementById('passwordMatchError');
    const form = newPasswordConfirm ? newPasswordConfirm.closest('form') : null;

    if (form) {
        form.addEventListener('submit', (e) => {
            if (newPassword.value !== newPasswordConfirm.value) {
                e.preventDefault();
                matchError.style.display = 'block';
            } else {
                matchError.style.display = 'none';
            }
        });
    }

})();