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
