/**
 * 센서 목록 화면의 체크박스 다중선택 + 위치 일괄변경
 * 체크박스가 <a class="split-row"> 안에 있어서, 클릭이 그대로 버블링되면 상세 페이지로 이동해버림
 * — click을 stopPropagation해서 체크만 토글되게 함
 */
(function () {
    const checkboxes = Array.from(document.querySelectorAll('.sensor-select'));
    if (checkboxes.length === 0) return;

    const countEl = document.getElementById('sensorBulkCount');
    const locationSelect = document.getElementById('sensorBulkLocation');
    const moveBtn = document.getElementById('btnBulkMoveSensors');
    const deleteBtn = document.getElementById('btnBulkDeleteSensors');
    const selectAllCb = document.getElementById('sensorSelectAll');

    function selected() {
        return checkboxes.filter((cb) => cb.checked);
    }

    // 선택 바는 항상 뜨게 해줌
    function refresh() {
        const n = selected().length;
        countEl.textContent = n + '개 선택됨';
        locationSelect.disabled = n === 0;
        moveBtn.disabled = n === 0;
        deleteBtn.disabled = n === 0;
        // 개별 체크박스로 전부/일부만 선택된 상태도 전체선택 체크박스에 반영
        selectAllCb.checked = n > 0 && n === checkboxes.length;
    }

    checkboxes.forEach((cb) => {
        cb.addEventListener('click', (e) => e.stopPropagation());
        cb.addEventListener('change', refresh);
    });

    // 체크박스가 놓인 왼쪽 영역 어디를 클릭해도 체크박스가 토글되게 함
    document.querySelectorAll('.split-row-select-zone').forEach((zone) => {
        zone.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (e.target.classList.contains('sensor-select')) return;
            const cb = zone.querySelector('.sensor-select');
            if (!cb) return;
            cb.checked = !cb.checked;
            cb.dispatchEvent(new Event('change'));
        });
    });

    // 목록 전체를 한 번에 고를 때 씀
    selectAllCb.addEventListener('change', () => {
        checkboxes.forEach((cb) => { cb.checked = selectAllCb.checked; });
        refresh();
    });

    moveBtn.addEventListener('click', () => {
        const sensors = selected().map((cb) => ({
            sensorId: Number(cb.dataset.sensorId),
            sensorName: cb.dataset.sensorName
        }));
        if (sensors.length === 0) return;

        moveBtn.disabled = true;
        fetch('/my-group/sensors/selected-location', {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({sensors, locationId: Number(locationSelect.value)})
        }).then((r) => {
            if (!r.ok) throw new Error('bulk move failed');
            location.reload();
        }).catch(() => {
            alert('위치를 일괄 변경하지 못했어요. 잠시 후 다시 시도해주세요.');
            moveBtn.disabled = false;
        });
    });

    deleteBtn.addEventListener('click', () => {
        const ids = selected().map((cb) => cb.dataset.sensorId);
        if (ids.length === 0) return;
        if (!confirm(`선택한 센서 ${ids.length}개를 삭제할까요?`)) return;

        deleteBtn.disabled = true;
        // 일부만 실패해도 성공한 건 이미 지워졌으니, 결과와 상관없이 새로고침해서 실제 상태를 보여줌
        Promise.allSettled(ids.map((id) => fetch(`/my-group/sensors/${id}`, {method: 'DELETE'})))
            .then((results) => {
                const failed = results.some((r) => r.status === 'rejected' || !r.value.ok);
                if (failed) alert('일부 삭제하지 못했어요.');
                location.reload();
            });
    });
})();
