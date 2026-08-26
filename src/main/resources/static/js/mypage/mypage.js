// InsightOn 마이페이지(메인) - mypage.js
// 정보수정/비밀번호변경은 별도 화면(폼 제출)에서 처리된다.
// 소셜 연동 해제, 회원 탈퇴는 확인이 필요한 액션이라 fetch로 front 서버를 호출한다.

(function () {

    function postForm(path, params) {
        return fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams(params)
        });
    }

    // ================================================================
    // 소셜 계정 연동 해제
    // ================================================================
    const socialList = document.getElementById('socialList');
    const socialError = document.getElementById('socialError');

    function setSocialError(message) {
        if (!socialError) return;
        if (!message) {
            socialError.style.display = 'none';
            socialError.textContent = '';
            return;
        }
        socialError.style.display = 'block';
        socialError.textContent = message;
        socialError.classList.add('is-error');
    }

    if (socialList) {
        socialList.addEventListener('click', async (e) => {
            const btn = e.target.closest('.btn-unlink');
            if (!btn) return;

            const oauthId = btn.dataset.oauthId;
            if (!confirm('이 계정 연동을 해제할까요?')) return;

            btn.disabled = true;
            setSocialError('');
            try {
                const res = await postForm('/mypage/social/unlink', { oauthId });
                if (res.ok) {
                    btn.closest('.social-account-row').remove();
                } else if (res.status === 409) {
                    setSocialError('마지막 남은 로그인 수단은 해제할 수 없어요.');
                } else {
                    setSocialError('연동 해제에 실패했어요. 잠시 후 다시 시도해주세요.');
                }
            } catch (err) {
                setSocialError('서버에 연결할 수 없습니다.');
            } finally {
                btn.disabled = false;
            }
        });
    }

    // ================================================================
    // 회원 탈퇴
    // ================================================================
    const btnWithdraw = document.getElementById('btnWithdraw');
    if (btnWithdraw) {
        btnWithdraw.addEventListener('click', async () => {
            if (!confirm('정말 탈퇴하시겠어요? 이 작업은 되돌릴 수 없어요.')) return;
            if (!confirm('마지막 확인이에요. 탈퇴를 진행할까요?')) return;

            btnWithdraw.disabled = true;
            try {
                const res = await fetch('/mypage/withdraw', { method: 'POST' });
                if (res.ok) {
                    window.location.href = '/login?withdrawn=1';
                } else {
                    alert('탈퇴 처리에 실패했어요. 잠시 후 다시 시도해주세요.');
                    btnWithdraw.disabled = false;
                }
            } catch (err) {
                alert('서버에 연결할 수 없습니다.');
                btnWithdraw.disabled = false;
            }
        });
    }

})();