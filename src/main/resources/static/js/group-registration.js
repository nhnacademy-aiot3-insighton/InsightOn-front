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
        fetch(`/my-group/registration/cities?state=${encodeURIComponent(state)}`)
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

    if (typeof REGISTRATION_INIT !== 'undefined' && REGISTRATION_INIT.previousState) {
        loadCities(REGISTRATION_INIT.previousState, REGISTRATION_INIT.previousCity);
    }
})();
