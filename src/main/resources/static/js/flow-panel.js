/**
 * Flow 상세 화면의 활성화/비활성화, 휴지통 이동/복구/영구삭제 버튼 처리.
 * split-detail.js가 목록 페이지 오른쪽 패널에 상세 내용을 오려 붙이는 경우에도 동작하도록
 * document 레벨에서 이벤트 위임으로 처리한다.
 */
(function () {
    const isTrashPage = () => document.body.classList.contains('flow-trash-page');

    function showError(message) {
        const errorEl = isTrashPage()
            ? document.getElementById('flowTrashError')
            : document.getElementById('flowStatusError');
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = 'block';
        }
    }

    function clearError() {
        const errorEl = isTrashPage()
            ? document.getElementById('flowTrashError')
            : document.getElementById('flowStatusError');
        if (errorEl) errorEl.style.display = 'none';
    }

    async function requestOrThrow(response, fallbackMessage) {
        if (!response.ok) {
            const body = await response.json().catch(() => null);
            throw new Error(body && (body.message || body.detail) ? (body.message || body.detail) : fallbackMessage);
        }
        return response;
    }

    function afterArchivedMutation() {
        if (isTrashPage()) {
            location.reload();
        } else {
            location.href = '/my-group/flows/trash';
        }
    }

    function restoreFlow(flowId) {
        clearError();
        return fetch(`/my-group/flows/${flowId}/restore`, {method: 'POST'})
            .then((response) => requestOrThrow(response, '복구하지 못했어요.'))
            .then(afterArchivedMutation)
            .catch((error) => showError(error.message));
    }

    function permanentlyDeleteFlow(flowId, flowName) {
        const label = flowName ? `“${flowName}” 자동화를` : '이 자동화를';
        if (!confirm(`${label} 영구 삭제할까요? 되돌릴 수 없어요.`)) return Promise.resolve();
        clearError();
        return fetch(`/my-group/flows/${flowId}`, {method: 'DELETE'})
            .then((response) => requestOrThrow(response, '삭제하지 못했어요.'))
            .then(afterArchivedMutation)
            .catch((error) => showError(error.message));
    }

    document.addEventListener('click', (e) => {
        const statusBtn = e.target.closest('.btn-flow-status-toggle');
        if (statusBtn) {
            const flowId = statusBtn.dataset.flowId;
            const targetStatus = statusBtn.dataset.targetStatus;
            clearError();

            fetch(`/my-group/flows/${flowId}/status`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({status: targetStatus}),
            })
                .then((r) => requestOrThrow(r, '상태를 변경하지 못했어요.'))
                .then(() => location.reload())
                .catch((err) => showError(err.message));
            return;
        }

        const archiveBtn = e.target.closest('.btn-flow-archive');
        if (archiveBtn) {
            if (!confirm('이 자동화를 휴지통으로 이동할까요?')) return;
            const flowId = archiveBtn.dataset.flowId;
            clearError();

            fetch(`/my-group/flows/${flowId}/archive`, {method: 'POST'})
                .then((r) => requestOrThrow(r, '휴지통으로 이동하지 못했어요.'))
                .then(() => location.reload())
                .catch((err) => showError(err.message));
            return;
        }

        const restoreBtn = e.target.closest('.btn-flow-restore');
        if (restoreBtn) {
            restoreFlow(restoreBtn.dataset.flowId);
            return;
        }

        const deleteBtn = e.target.closest('.btn-flow-delete');
        if (deleteBtn) {
            permanentlyDeleteFlow(deleteBtn.dataset.flowId, deleteBtn.dataset.flowName);
        }
    });

    const emptyTrashButton = document.getElementById('btnEmptyFlowTrash');
    if (emptyTrashButton) {
        emptyTrashButton.addEventListener('click', () => {
            if (!confirm('휴지통의 모든 자동화를 영구 삭제할까요? 되돌릴 수 없어요.')) return;
            emptyTrashButton.disabled = true;
            fetch('/my-group/flows/trash', {method: 'DELETE'})
                .then((response) => requestOrThrow(response, '휴지통을 비우지 못했어요.'))
                .then((response) => response.json())
                .then((result) => {
                    (result.deletedFlowIds || []).forEach((flowId) => {
                        document.querySelector(`.flow-trash-list-item[data-flow-id="${flowId}"]`)?.remove();
                    });
                    const remaining = document.querySelectorAll('.flow-trash-list-item').length;
                    const count = document.querySelector('.flow-trash-count');
                    if (count) count.textContent = `${remaining}개`;
                    if (result.failedFlowIds && result.failedFlowIds.length) {
                        showError(`${result.deletedFlowIds.length}개를 삭제했고 ${result.failedFlowIds.length}개는 삭제하지 못했어요. 잠시 후 다시 시도해주세요.`);
                        emptyTrashButton.disabled = false;
                        return;
                    }
                    location.reload();
                })
                .catch((error) => {
                    const errorEl = document.getElementById('flowTrashError');
                    if (errorEl) {
                        errorEl.textContent = error.message;
                        errorEl.style.display = 'block';
                    }
                    emptyTrashButton.disabled = false;
                });
        });
    }

    const contextMenu = document.getElementById('flowTrashContextMenu');
    if (contextMenu) {
        let contextFlowId = null;
        let contextFlowName = null;

        function closeContextMenu() {
            contextMenu.hidden = true;
            contextFlowId = null;
            contextFlowName = null;
        }

        function openContextMenu(row, left, top) {
            contextFlowId = row.dataset.flowId;
            contextFlowName = row.dataset.flowName;
            contextMenu.hidden = false;
            const menuWidth = contextMenu.offsetWidth;
            const menuHeight = contextMenu.offsetHeight;
            contextMenu.style.left = `${Math.max(8, Math.min(left, window.innerWidth - menuWidth - 8))}px`;
            contextMenu.style.top = `${Math.max(8, Math.min(top, window.innerHeight - menuHeight - 8))}px`;
            contextMenu.querySelector('[role="menuitem"]')?.focus();
        }

        document.addEventListener('contextmenu', (event) => {
            const row = event.target.closest('.flow-trash-row');
            if (!row) {
                closeContextMenu();
                return;
            }
            event.preventDefault();
            openContextMenu(row, event.clientX, event.clientY);
        });

        contextMenu.addEventListener('click', (event) => {
            const action = event.target.closest('[data-action]');
            if (!action || !contextFlowId) return;
            const flowId = contextFlowId;
            const flowName = contextFlowName;
            closeContextMenu();
            if (action.dataset.action === 'restore') restoreFlow(flowId);
            if (action.dataset.action === 'delete') permanentlyDeleteFlow(flowId, flowName);
        });

        document.addEventListener('click', (event) => {
            const menuButton = event.target.closest('.btn-flow-trash-menu');
            if (menuButton) {
                event.preventDefault();
                const rect = menuButton.getBoundingClientRect();
                openContextMenu(menuButton, rect.right, rect.bottom + 4);
                return;
            }
            if (!event.target.closest('#flowTrashContextMenu')) closeContextMenu();
        });
        document.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') closeContextMenu();
        });
        window.addEventListener('resize', closeContextMenu);
        window.addEventListener('scroll', closeContextMenu, true);
    }
})();
