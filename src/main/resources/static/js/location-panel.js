/**
 * 헤더의 "위치 관리" 버튼으로 여는 모달(locationManageModal)의 저장/삭제 처리.
 * 대시보드/액추에이터 등 위치 상세 페이지 어디서든 같은 모달을 공유해서 쓴다.
 */
(function () {
    function showError(modal, message) {
        const status = modal.querySelector('#locationManageError');
        if (status) {
            status.textContent = message;
            status.style.display = 'block';
        }
    }

    document.addEventListener('click', (e) => {
        const modal = document.getElementById('locationManageModal');
        if (!modal) return;

        const saveBtn = e.target.closest('#btnSaveLocation');
        if (saveBtn) {
            const locationId = modal.dataset.locationId;
            const currentMode = modal.dataset.currentMode;
            const newName = modal.querySelector('#locationManageName').value.trim();
            const selectedMode = modal.querySelector('#locationManageMode').value;

            fetch(`/my-group/location/${locationId}/update`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({newLocationName: newName}),
            })
                .then((r) => {
                    if (!r.ok) throw new Error('update failed');
                    if (selectedMode === currentMode) return null;
                    return fetch(`/my-group/location/${locationId}/toggle-mode`, {method: 'POST'})
                        .then((r2) => {
                            if (!r2.ok) throw new Error('toggle failed');
                        });
                })
                .then(() => location.reload())
                .catch(() => showError(modal, '저장하지 못했어요. 잠시 후 다시 시도해주세요.'));
            return;
        }

        const deleteBtn = e.target.closest('#btnDeleteLocation');
        if (deleteBtn) {
            const locationId = modal.dataset.locationId;
            if (!confirm('이 위치를 삭제할까요? 소속된 센서·액추에이터·대시보드가 함께 영향을 받아요.')) return;
            fetch(`/my-group/location/${locationId}/delete`, {method: 'DELETE'})
                .then((r) => {
                    if (!r.ok) throw new Error('delete failed');
                    location.href = '/my-group/location/list';
                })
                .catch(() => showError(modal, '삭제하지 못했어요. 잠시 후 다시 시도해주세요.'));
        }
    });
})();
