(function () {
    const stateSelect = document.getElementById('state');
    if (!stateSelect) return;

    const citySelect = document.getElementById('city');

    function loadCities(state, preselectCity) {
        if (!state) {
            citySelect.innerHTML = '<option value="">먼저 시/도를 선택하세요</option>';
            return;
        }
        citySelect.innerHTML = '<option value="">불러오는 중...</option>';
        fetch(`/group-registration/cities?state=${encodeURIComponent(state)}`)
            .then((r) => {
                if (!r.ok) throw new Error('cities fetch failed');
                return r.json();
            })
            .then((cities) => {
                if (!cities.length) {
                    citySelect.innerHTML = '<option value="">등록된 시/군/구가 없어요</option>';
                    return;
                }
                citySelect.innerHTML = '<option value="">선택하세요</option>'
                    + cities.map((c) => `<option value="${c}">${c}</option>`).join('');
                if (preselectCity) citySelect.value = preselectCity;
            })
            .catch(() => {
                citySelect.innerHTML = '<option value="">불러오지 못했어요</option>';
            });
    }

    stateSelect.addEventListener('change', () => loadCities(stateSelect.value, null));

    // city select에 이미 실제 옵션이 서버 렌더링돼 있으면(그룹 정보 화면) 다시 안 불러온다 -
    // 안 그러면 화면 진입 직후 잠깐 깜빡이며 같은 목록을 또 fetch하게 된다. 옵션이 플레이스홀더
    // 1개뿐인 경우(그룹 신청 화면)에만 JS로 채운다.
    if (typeof REGISTRATION_INIT !== 'undefined' && REGISTRATION_INIT.previousState && citySelect.options.length <= 1) {
        loadCities(REGISTRATION_INIT.previousState, REGISTRATION_INIT.previousCity);
    }
})();

function previewGroup(inputId, resultId) {
    const input = document.getElementById(inputId);
    const resultDiv = document.getElementById(resultId);
    if (!input || !resultDiv) return;

    const token = input.value.trim();
    if (!token) {
        resultDiv.style.display = 'block';
        resultDiv.className = 'alert alert-warning py-2 px-3 mb-0 text-dark';
        resultDiv.innerHTML = '<i class="ti ti-alert-circle"></i> 초대 코드를 입력해주세요.';
        return;
    }

    resultDiv.style.display = 'block';
    resultDiv.className = 'p-3 rounded border bg-light text-secondary';
    resultDiv.innerHTML = '<div class="spinner-border spinner-border-sm text-primary" role="status"></div> 그룹 정보를 조회하고 있어요...';

    fetch('/my-group/preview?inviteToken=' + encodeURIComponent(token))
        .then(res => {
            if (!res.ok) {
                return res.json().then(data => { throw new Error(data.message || '초대 코드를 찾을 수 없습니다.'); });
            }
            return res.json();
        })
        .then(data => {
            resultDiv.className = 'p-3 rounded border border-success-subtle bg-success-subtle text-dark';
            resultDiv.innerHTML = `
                <div class="d-flex align-items-center justify-content-between mb-2">
                    <strong class="fs-6 text-success d-flex align-items-center gap-1"><i class="ti ti-circle-check"></i> 초대 그룹 확인됨</strong>
                    <span class="badge bg-success text-white">가입 가능</span>
                </div>
                <div class="mb-1"><strong>🏢 그룹명:</strong> ${escapeHtml(data.name || '-')}</div>
                <div class="mb-1"><strong>📍 소재지:</strong> ${escapeHtml(data.groupRegion || '-')}</div>
                ${data.description ? `<div class="mb-2"><strong>📝 설명:</strong> ${escapeHtml(data.description)}</div>` : ''}
                <div class="mt-2 text-end">
                    <button class="btn btn-sm btn-success d-inline-flex align-items-center gap-1" type="button" onclick="submitInviteForm('${inputId}')">
                        이 그룹으로 가입하기 <i class="ti ti-arrow-right"></i>
                    </button>
                </div>
            `;
        })
        .catch(err => {
            resultDiv.className = 'p-3 rounded border border-danger-subtle bg-danger-subtle text-danger';
            resultDiv.innerHTML = '<i class="ti ti-circle-x"></i> ' + escapeHtml(err.message);
        });
}

function submitInviteForm(inputId) {
    const input = document.getElementById(inputId);
    if (input && input.form) {
        input.form.submit();
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
