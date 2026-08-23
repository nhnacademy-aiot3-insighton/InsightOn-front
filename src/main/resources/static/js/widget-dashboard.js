(function () {
    const gridEl = document.getElementById('widgetGrid');
    if (!gridEl) return;

    const emptyStateEl = document.getElementById('widgetEmptyState');
    const saveStatusEl = document.getElementById('saveStatus');
    const btnAddWidget = document.getElementById('btnAddWidget');
    const btnSaveLayout = document.getElementById('btnSaveLayout');

    const modalEl = document.getElementById('widgetConfigModal');
    const modal = new bootstrap.Modal(modalEl);
    const sensorSelect = document.getElementById('widgetSensorSelect');
    const metricListEl = document.getElementById('widgetMetricList');
    const rangeSelect = document.getElementById('widgetRangeSelect');
    const aggSelect = document.getElementById('widgetAggSelect');
    const btnApplyConfig = document.getElementById('btnApplyWidgetConfig');

    const GROUP_ID = DASHBOARD_INIT.groupId;
    const LOCATION_ID = DASHBOARD_INIT.locationId;
    const BASE_URL = `/my-group/location/${LOCATION_ID}/dashboard`;

    // sensorEui -> display name, built once from the server-rendered <select>
    const sensorNameByEui = {};
    sensorSelect.querySelectorAll('option[data-eui]').forEach((opt) => {
        sensorNameByEui[opt.dataset.eui] = opt.textContent.trim();
    });

    let uidCounter = 0;
    const state = (DASHBOARD_INIT.widgets || []).map((w) => ({
        uid: 'w' + (w.widgetId ?? ++uidCounter),
        widgetId: w.widgetId,
        xPos: w.xPos,
        yPos: w.yPos,
        width: w.width,
        height: w.height,
        widgetConfig: w.widgetConfig || {type: 'SINGLE_STAT', sensorEui: null, range: '-1h', aggregateWindow: '1m', fields: []},
        dirty: false // true whenever widgetConfig no longer matches what's persisted server-side
    }));

    const chartInstances = {};
    let editingUid = null;

    // GridStack owns collision/push behavior: resizing or dragging a widget into a
    // neighbor moves that neighbor out of the way, cascading through however many
    // widgets are affected — its 'change' event below is where we hear about all of that.
    const grid = GridStack.init({
        column: 12,
        cellHeight: 80,
        margin: 8,
        float: true,
        handle: '.drag-handle',
        resizable: {handles: 'e, se, s, sw, w'}
    }, gridEl);

    function itemEl(uid) {
        return gridEl.querySelector(`.grid-stack-item[gs-id="${uid}"]`);
    }

    function contentEl(uid) {
        const item = itemEl(uid);
        return item ? item.querySelector('.grid-widget') : null;
    }

    function nextFreeRow() {
        return state.reduce((max, w) => Math.max(max, w.yPos + w.height), 0);
    }

    function widgetTitle(w) {
        const sensorName = sensorNameByEui[w.widgetConfig.sensorEui] || '센서 미지정';
        const fields = (w.widgetConfig.fields || []).join(', ') || '메트릭 미지정';
        return {sensorName, fields};
    }

    function renderAll() {
        emptyStateEl.style.display = state.length === 0 ? 'block' : 'none';
    }

    function buildGridStackItem(w) {
        const item = document.createElement('div');
        item.className = 'grid-stack-item';
        item.setAttribute('gs-id', w.uid);
        item.setAttribute('gs-x', w.xPos);
        item.setAttribute('gs-y', w.yPos);
        item.setAttribute('gs-w', w.width);
        item.setAttribute('gs-h', w.height);

        const {sensorName, fields} = widgetTitle(w);

        item.innerHTML = `
            <div class="grid-stack-item-content grid-widget">
                <div class="grid-widget-head">
                    <div class="grid-widget-label">
                        <i class="ti ti-cpu"></i>
                        <span title="${sensorName} · ${fields}">${sensorName} · ${fields}</span>
                    </div>
                    <div class="grid-widget-controls">
                        <button type="button" class="drag-handle" title="이동" aria-label="위젯 이동"><i class="ti ti-grip-vertical"></i></button>
                        <button type="button" class="settings" title="설정" aria-label="위젯 설정"><i class="ti ti-settings"></i></button>
                        <button type="button" class="remove" title="삭제" aria-label="위젯 삭제"><i class="ti ti-trash"></i></button>
                    </div>
                </div>
                <div class="grid-widget-body"></div>
                <div class="grid-widget-dim">${w.width}×${w.height}</div>
            </div>
        `;

        const content = item.querySelector('.grid-widget');
        content.querySelector('.settings').addEventListener('click', () => openConfigModal(w));
        content.querySelector('.remove').addEventListener('click', () => removeWidget(w.uid));

        return item;
    }

    function addItemToGrid(w) {
        const item = buildGridStackItem(w);
        gridEl.appendChild(item);
        grid.makeWidget(item);
        renderWidgetBody(w, item.querySelector('.grid-widget'));
    }

    function updateLabel(w) {
        const el = contentEl(w.uid);
        if (!el) return;
        const {sensorName, fields} = widgetTitle(w);
        const span = el.querySelector('.grid-widget-label span');
        span.textContent = `${sensorName} · ${fields}`;
        span.title = `${sensorName} · ${fields}`;
    }

    function updateDim(w) {
        const el = contentEl(w.uid);
        if (el) el.querySelector('.grid-widget-dim').textContent = `${w.width}×${w.height}`;
    }

    function renderWidgetBody(w, el) {
        const body = el.querySelector('.grid-widget-body');
        const type = w.widgetConfig.type;

        if (!w.widgetConfig.sensorEui || !(w.widgetConfig.fields || []).length) {
            body.className = 'grid-widget-body d-flex';
            body.innerHTML = `<p class="grid-widget-empty">설정 필요 — <i class="ti ti-settings"></i> 아이콘을 눌러 구성하세요</p>`;
            return;
        }

        // chart-data is looked up by widgetId and reflects the last SAVED config —
        // a brand-new or just-edited widget's data would otherwise show stale/wrong values
        // until it's actually saved, so show that state honestly instead of pretending it's live.
        if (!w.widgetId || w.dirty) {
            body.className = 'grid-widget-body d-flex';
            const msg = !w.widgetId ? '저장하면 데이터가 표시돼요' : '설정이 변경됐어요 — 저장하면 반영돼요';
            body.innerHTML = `<p class="grid-widget-empty"><i class="ti ti-device-floppy"></i> ${msg}</p>`;
            return;
        }

        if (type === 'GRAPH') {
            body.className = 'grid-widget-chart grid-widget-body';
            body.innerHTML = `<canvas></canvas>`;
        } else if (type === 'GAUGE') {
            body.className = 'grid-widget-body';
            body.innerHTML = `<span class="grid-widget-value">—</span><span class="grid-widget-unit"></span><div class="grid-widget-sparkline"><canvas></canvas></div>`;
        } else {
            body.className = 'grid-widget-body';
            body.innerHTML = `<span class="grid-widget-value">—</span><span class="grid-widget-unit"></span>`;
        }

        fetchAndRenderData(w, el);
    }

    function fetchAndRenderData(w, el) {
        fetch(`${BASE_URL}/widgets/${w.widgetId}/chart-data`)
            .then((r) => {
                if (!r.ok) throw new Error('chart-data fetch failed');
                return r.json();
            })
            .then((data) => paintWidgetData(w, el, data))
            .catch(() => {
                const body = el.querySelector('.grid-widget-body');
                const valueEl = body.querySelector('.grid-widget-value');
                if (valueEl) valueEl.textContent = '데이터 없음';
            });
    }

    function paintWidgetData(w, el, data) {
        const type = w.widgetConfig.type;
        const labels = data.labels || [];
        const datasets = data.datasets || [];
        const body = el.querySelector('.grid-widget-body');

        if (type === 'GRAPH') {
            const canvas = body.querySelector('canvas');
            destroyChart(w.uid);
            chartInstances[w.uid] = new Chart(canvas, {
                type: 'line',
                data: {
                    labels,
                    datasets: datasets.map((ds, i) => ({
                        label: ds.label,
                        data: ds.data,
                        borderColor: i === 0 ? '#1c4e80' : '#b8752a',
                        backgroundColor: i === 0 ? 'rgba(28, 78, 128, 0.08)' : 'rgba(184, 117, 42, 0.08)',
                        borderWidth: 1.5,
                        tension: 0.2,
                        fill: i === 0,
                        pointRadius: 0,
                        pointHoverRadius: 3
                    }))
                },
                options: {
                    maintainAspectRatio: false,
                    plugins: {legend: {display: datasets.length > 1, labels: {font: {size: 11}}}},
                    scales: {
                        x: {ticks: {color: '#56697d', font: {family: 'IBM Plex Mono', size: 10}}, grid: {display: false}},
                        y: {ticks: {color: '#56697d', font: {family: 'IBM Plex Mono', size: 10}}, grid: {color: '#ccd5e0'}}
                    }
                }
            });
            return;
        }

        const firstSeries = datasets[0]?.data || [];
        const latest = firstSeries.length ? firstSeries[firstSeries.length - 1] : null;
        const valueEl = body.querySelector('.grid-widget-value');
        valueEl.textContent = latest === null || latest === undefined ? '데이터 없음' : Number(latest).toLocaleString(undefined, {maximumFractionDigits: 1});

        if (type === 'GAUGE') {
            const canvas = body.querySelector('.grid-widget-sparkline canvas');
            destroyChart(w.uid);
            chartInstances[w.uid] = new Chart(canvas, {
                type: 'line',
                data: {
                    labels,
                    datasets: [{
                        data: firstSeries,
                        borderColor: '#1c4e80',
                        borderWidth: 1.5,
                        tension: 0.3,
                        fill: false,
                        pointRadius: 0
                    }]
                },
                options: {
                    maintainAspectRatio: false,
                    plugins: {legend: {display: false}},
                    scales: {x: {display: false}, y: {display: false}}
                }
            });
        }
    }

    function destroyChart(uid) {
        if (chartInstances[uid]) {
            chartInstances[uid].destroy();
            delete chartInstances[uid];
        }
    }

    // ---------- GridStack layout events: this is where cross-widget interaction happens ----------

    grid.on('change', (event, items) => {
        (items || []).forEach((item) => {
            const w = state.find((x) => x.uid === String(item.id));
            if (!w) return;
            w.xPos = item.x;
            w.yPos = item.y;
            w.width = item.w;
            w.height = item.h;
            updateDim(w);
        });
    });

    grid.on('resize', (event, el) => {
        const uid = el.getAttribute('gs-id');
        const w = state.find((x) => x.uid === uid);
        const node = el.gridstackNode;
        if (w && node) {
            w.width = node.w;
            w.height = node.h;
            updateDim(w);
        }
    });

    // ---------- config modal ----------

    function loadMetrics(sensorId, selectedFields) {
        metricListEl.innerHTML = `<p class="metric-check-empty">불러오는 중...</p>`;
        fetch(`${BASE_URL}/sensors/${sensorId}/attributes`)
            .then((r) => {
                if (!r.ok) throw new Error('attributes fetch failed');
                return r.json();
            })
            .then((attrs) => {
                if (!attrs.length) {
                    metricListEl.innerHTML = `<p class="metric-check-empty">이 센서는 등록된 메트릭이 없어요.</p>`;
                    return;
                }
                metricListEl.innerHTML = attrs.map((a) => `
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" value="${a.metricKey}" id="metric-${a.metricKey}"
                               ${selectedFields.includes(a.metricKey) ? 'checked' : ''}>
                        <label class="form-check-label" for="metric-${a.metricKey}">${a.displayName} <span class="text-muted">(${a.unit})</span></label>
                    </div>
                `).join('');
            })
            .catch(() => {
                metricListEl.innerHTML = `<p class="metric-check-empty">메트릭을 불러오지 못했어요.</p>`;
            });
    }

    function openConfigModal(w) {
        editingUid = w.uid;
        modalEl.querySelectorAll('input[name="widgetType"]').forEach((r) => {
            r.checked = r.value === w.widgetConfig.type;
        });
        sensorSelect.value = '';
        Array.from(sensorSelect.options).forEach((opt) => {
            if (opt.dataset.eui === w.widgetConfig.sensorEui) sensorSelect.value = opt.value;
        });
        rangeSelect.value = w.widgetConfig.range || '-1h';
        aggSelect.value = w.widgetConfig.aggregateWindow || '1m';

        if (sensorSelect.value) {
            loadMetrics(sensorSelect.value, w.widgetConfig.fields || []);
        } else {
            metricListEl.innerHTML = `<p class="metric-check-empty">먼저 센서를 선택하세요.</p>`;
        }
        modal.show();
    }

    sensorSelect.addEventListener('change', () => {
        if (sensorSelect.value) {
            loadMetrics(sensorSelect.value, []);
        } else {
            metricListEl.innerHTML = `<p class="metric-check-empty">먼저 센서를 선택하세요.</p>`;
        }
    });

    btnApplyConfig.addEventListener('click', () => {
        const w = state.find((x) => x.uid === editingUid);
        if (!w) return;

        const type = modalEl.querySelector('input[name="widgetType"]:checked')?.value || 'SINGLE_STAT';
        const sensorOpt = sensorSelect.options[sensorSelect.selectedIndex];
        const sensorEui = sensorOpt ? sensorOpt.dataset.eui : null;

        // if the metric checklist never loaded (fetch failed, or still "불러오는 중"), there are no
        // checkboxes to read — treat that as "unknown", not "user unchecked everything", and keep
        // whatever fields were already configured rather than silently wiping them out.
        const checkboxEls = metricListEl.querySelectorAll('input[type="checkbox"]');
        const fields = checkboxEls.length
            ? Array.from(checkboxEls).filter((c) => c.checked).map((c) => c.value)
            : (w.widgetConfig.fields || []);

        w.widgetConfig = {
            type,
            sensorEui: sensorEui || null,
            range: rangeSelect.value,
            aggregateWindow: aggSelect.value,
            fields
        };
        w.dirty = true;

        modal.hide();
        updateLabel(w);
        const el = contentEl(w.uid);
        if (el) renderWidgetBody(w, el);
    });

    // ---------- add / remove ----------

    btnAddWidget.addEventListener('click', () => {
        const w = {
            uid: 'new' + (++uidCounter),
            widgetId: null,
            xPos: 0,
            yPos: nextFreeRow(),
            width: 4,
            height: 2,
            widgetConfig: {type: 'SINGLE_STAT', sensorEui: null, range: '-1h', aggregateWindow: '1m', fields: []}
        };
        state.push(w);
        addItemToGrid(w);
        renderAll();
        openConfigModal(w);
    });

    function removeWidget(uid) {
        if (!confirm('이 위젯을 삭제할까요?')) return;
        destroyChart(uid);
        const el = itemEl(uid);
        if (el) grid.removeWidget(el);
        const idx = state.findIndex((w) => w.uid === uid);
        if (idx >= 0) state.splice(idx, 1);
        renderAll();
    }

    // ---------- save ----------

    btnSaveLayout.addEventListener('click', () => {
        const payload = state.map((w) => ({
            widgetId: w.widgetId,
            xPos: w.xPos,
            yPos: w.yPos,
            width: w.width,
            height: w.height,
            widgetConfig: {...w.widgetConfig, groupId: GROUP_ID, locationId: LOCATION_ID}
        }));

        saveStatusEl.style.display = 'flex';
        saveStatusEl.textContent = '저장 중...';
        btnSaveLayout.disabled = true;

        fetch(`${BASE_URL}/save`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(payload)
        })
            .then((r) => {
                if (!r.ok) throw new Error('save failed');
                location.reload();
            })
            .catch(() => {
                saveStatusEl.textContent = '저장에 실패했어요. 잠시 후 다시 시도해주세요.';
                btnSaveLayout.disabled = false;
            });
    });

    // ---------- init ----------

    state.forEach((w) => addItemToGrid(w));
    renderAll();

    // GRAPH/GAUGE/SINGLE_STAT widgets refresh on a timer, same cadence chart.js polls at
    setInterval(() => {
        state.forEach((w) => {
            if (!w.widgetId || w.dirty) return;
            const el = contentEl(w.uid);
            if (el) fetchAndRenderData(w, el);
        });
    }, 30000);
})();
