/**
 * 센서 상세 카드(저장/삭제/필드 삭제)의 클릭을 document 레벨에서 위임 처리한다 — 이 카드는
 * 독립 페이지(sensor/detail.html)로도 열리고, 목록 화면 오른쪽 패널에 innerHTML로 통째로
 * 옮겨 붙는 방식(split-detail.js)으로도 나타나기 때문에, 카드가 나중에 주입돼도 그대로 동작하는
 * 위임 방식이 아니면 두 번째 경우에 이벤트가 안 걸린다.
 */
(function () {
    function showError(card, message) {
        const status = card.querySelector('#saveStatus');
        if (status) {
            status.textContent = message;
            status.style.display = 'block';
        }
    }

    document.body.addEventListener('click', (e) => {
        const saveBtn = e.target.closest('#btnSaveSensor');
        if (saveBtn) {
            const card = saveBtn.closest('#detailContent');
            const sensorId = card.dataset.sensorId;
            const name = card.querySelector('#sensorName').value.trim();
            const locationId = card.querySelector('#sensorLocation').value;
            if (!name) {
                showError(card, '센서 이름을 입력하세요.');
                return;
            }
            saveBtn.disabled = true;
            fetch(`/sensors/${sensorId}`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({locationId: Number(locationId), sensorName: name})
            }).then((r) => {
                if (!r.ok) throw new Error('save failed');
                location.reload();
            }).catch(() => {
                showError(card, '저장하지 못했어요. 잠시 후 다시 시도해주세요.');
                saveBtn.disabled = false;
            });
            return;
        }

        const deleteBtn = e.target.closest('#btnDeleteSensor');
        if (deleteBtn) {
            const card = deleteBtn.closest('#detailContent');
            if (!confirm('이 센서를 삭제할까요? 참조하는 위젯·Flow 조건이 있다면 함께 끊겨요.')) return;
            fetch(`/sensors/${card.dataset.sensorId}`, {method: 'DELETE'})
                .then((r) => {
                    if (!r.ok) throw new Error('delete failed');
                    location.href = '/sensors';
                })
                .catch(() => showError(card, '삭제하지 못했어요. 잠시 후 다시 시도해주세요.'));
            return;
        }

        const removeAttrBtn = e.target.closest('.btn-remove-attribute');
        if (removeAttrBtn) {
            const card = removeAttrBtn.closest('#detailContent');
            const metricKey = removeAttrBtn.dataset.metricKey;
            fetch(`/sensors/${card.dataset.sensorId}/attributes/${metricKey}`, {method: 'DELETE'})
                .then((r) => {
                    if (!r.ok) throw new Error('delete failed');
                    removeAttrBtn.closest('.chip-option').remove();
                })
                .catch(() => alert('삭제하지 못했어요. 잠시 후 다시 시도해주세요.'));
        }
    });
})();
