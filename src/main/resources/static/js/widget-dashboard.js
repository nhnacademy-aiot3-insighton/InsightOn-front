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
    const BASE_URL = `/groups/${GROUP_ID}/location/${LOCATION_ID}/dashboard`;

    // sensorEui -> display name & sensorId mapping
    const sensorNameByEui = {};
    const sensorIdByEui = {};
    sensorSelect.querySelectorAll('option[data-eui]').forEach((opt) => {
        const eui = opt.dataset.eui;
        sensorNameByEui[eui] = opt.textContent.trim();
        sensorIdByEui[eui] = opt.value; // sensorId
    });

    let uidCounter = 0;
    const state = (DASHBOARD_INIT.widgets || []).map((w) => ({
        uid: 'w' + (w.widgetId ?? ++uidCounter),
        widgetId: w.widgetId,
        xPos: w.xPos,
        yPos: w.yPos,
        width: w.width,
        height: w.height,
        widgetConfig: w.widgetConfig || {
            type: 'GRAPH',
            sensorEui: null,
            range: '-1h',
            aggregateWindow: '1m',
            fields: []
        },
        dirty: false
    }));

    const chartInstances = {};
    const sseConnections = {};
    let editingUid = null;

    // SSE 구독 처리
    function subscribeWidgetSse(w, sensorId) {
        if (!sensorId) return;

        if (sseConnections[w.uid]) {
            sseConnections[w.uid].close();
        }

        console.log(`[SSE Subscribed] 센서 ${sensorId}번 실시간 SSE 연결 개설 완료`);
        const eventSource = new EventSource(`/sse/sensors/${sensorId}`);
        sseConnections[w.uid] = eventSource;

        eventSource.addEventListener('telemetry', (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log(`[SSE Telemetry Received] 센서 ${sensorId}번 데이터 수신:`, data);
                updateWidgetRealtime(w, data);
            } catch (e) {
                console.warn('[SSE Parse Error]', e);
            }
        });

        eventSource.onerror = (err) => {
            console.warn(`[SSE Error] 센서 ${sensorId} SSE 연결 알림`, err);
        };
    }

    // SSE 실시간 데이터 갱신
    function updateWidgetRealtime(w, telemetryData) {
        const chart = chartInstances[w.uid];
        const metrics = telemetryData.metrics || {};

        const timeStr = new Date(telemetryData.timestamp).toLocaleTimeString([], {
            hour: '2-digit', minute: '2-digit', second: '2-digit'
        });

        if (w.widgetConfig.type === 'GRAPH' && chart) {
            chart.data.labels.push(timeStr);

            chart.data.datasets.forEach((ds) => {
                const val = metrics[ds.label] ?? null;
                ds.data.push(val);
            });

            if (chart.data.labels.length > 30) {
                chart.data.labels.shift();
                chart.data.datasets.forEach((ds) => ds.data.shift());
            }

            chart.update('quiet');
        } else {
            const el = contentEl(w.uid);
            if (!el) return;
            const firstField = (w.widgetConfig.fields || [])[0];
            const val = metrics[firstField];
            const valueEl = el.querySelector('.grid-widget-value');
            if (valueEl && val !== undefined) {
                valueEl.textContent = Number(val).toLocaleString(undefined, {
                    maximumFractionDigits: 1
                });
            }
        }
    }

    // GridStack 초기화
    const grid = GridStack.init({
        column: 12,
        cellHeight: 80,
        margin: 8,
        float: true,
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
        const sensorName = sensorNameByEui[w.widgetConfig.sensorEui] || '미설정 센서';
        const fields = (w.widgetConfig.fields || []).join(', ') || '메트릭 미선택';
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
            <div class="card h-100 grid-widget shadow-sm border-1">
                <div class="card-status-top bg-primary"></div>
                <div class="card-header d-flex justify-content-between align-items-center py-2 px-3">
                    <div class="grid-widget-label d-flex align-items-center gap-2 text-truncate" style="max-width: calc(100% - 95px);">
                        <i class="ti ti-chart-line text-primary"></i>
                        <span class="fw-bold text-truncate" title="${sensorName} · ${fields}">${sensorName} · ${fields}</span>
                    </div>
                    <div class="grid-widget-controls d-flex align-items-center gap-1">
                        <button type="button" class="settings btn btn-icon btn-ghost-secondary btn-sm" title="위젯 설정" aria-label="위젯 설정"><i class="ti ti-settings"></i></button>
                        <button type="button" class="remove btn btn-icon btn-ghost-danger btn-sm" title="위젯 삭제" aria-label="위젯 삭제"><i class="ti ti-trash"></i></button>
                    </div>
                </div>
                <div class="card-body grid-widget-body p-2 d-flex flex-column" style="position: relative; flex: 1; min-height: 0;"></div>
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
        if (span) {
            span.textContent = `${sensorName} · ${fields}`;
            span.title = `${sensorName} · ${fields}`;
        }
    }

    function updateDim(w) {
        const el = contentEl(w.uid);
        if (el) {
            const dimEl = el.querySelector('.grid-widget-dim');
            if (dimEl) dimEl.textContent = `${w.width}×${w.height}`;
        }
    }

    function renderWidgetBody(w, el) {
        const body = el.querySelector('.grid-widget-body');
        if (!body) return;

        // 센서가 설정된 경우 (기존 위젯이든 신규로 설정한 위젯이든) 차트/수치 캔버스 렌더링
        if (w.widgetConfig && w.widgetConfig.sensorEui) {
            initEmptyChart(w, el);
            if (w.widgetId && !w.dirty) {
                fetchAndRenderData(w, el);
            }
            return;
        }

        // 미설정 위젯일 때만 안내 문구 출력
        body.className = 'card-body grid-widget-body p-3 d-flex flex-column align-items-center justify-content-center text-center';
        body.innerHTML = `
            <div class="text-secondary">
                <i class="ti ti-settings fs-2 mb-1 text-muted"></i>
                <div class="fw-bold">위젯 설정을 완료해주세요</div>
                <small class="text-muted">⚙️ 버튼을 눌러 센서를 선택하세요</small>
            </div>
        `;
    }

    function initEmptyChart(w, el) {
        const type = w.widgetConfig.type || 'GRAPH';
        const body = el.querySelector('.grid-widget-body');
        if (!body) return;

        if (type === 'GRAPH') {
            body.className = 'card-body grid-widget-chart grid-widget-body p-2 d-flex flex-column';
            body.innerHTML = `<canvas style="width: 100%; height: 100%; flex: 1;"></canvas>`;
            const canvas = body.querySelector('canvas');
            destroyChart(w.uid);

            const fields = (w.widgetConfig.fields && w.widgetConfig.fields.length)
                ? w.widgetConfig.fields
                : ['temperature', 'humidity'];
            const colors = ['#206bc4', '#4263eb', '#5ebea3', '#f59f00', '#d63939'];

            chartInstances[w.uid] = new Chart(canvas, {
                type: 'line',
                data: {
                    labels: [],
                    datasets: fields.map((field, idx) => ({
                        label: field,
                        data: [],
                        borderColor: colors[idx % colors.length],
                        backgroundColor: colors[idx % colors.length] + '20',
                        borderWidth: 1.5,
                        fill: idx === 0,
                        tension: 0.3,
                        pointRadius: 3,
                        pointHoverRadius: 5
                    }))
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    plugins: {legend: {display: fields.length > 1}},
                    scales: {
                        x: {ticks: {font: {size: 10}}, grid: {display: false}},
                        y: {ticks: {font: {size: 10}}, grid: {color: '#eef1f6'}}
                    }
                }
            });
        } else {
            body.className = 'card-body grid-widget-body p-2 d-flex flex-column align-items-center justify-content-center';
            body.innerHTML = `<span class="grid-widget-value fs-1 fw-bold">—</span><span class="grid-widget-unit text-muted"></span>`;
        }
    }

    function fetchAndRenderData(w, el) {
        fetch(`${BASE_URL}/widgets/${w.widgetId}/chart-data`)
            .then((r) => {
                if (!r.ok) throw new Error('chart-data fetch failed');
                return r.json();
            })
            .then((data) => {
                // influxDB에서 가져온 과거 데이터를 차트에 쭉 그려넣음
                paintWidgetData(w, el, data);
                // InfluxDB 시계열 수신 후 SSE 구독 시작
                const sensorId = sensorIdByEui[w.widgetConfig.sensorEui];
                if (sensorId) {
                    subscribeWidgetSse(w, sensorId);
                }
            })
            .catch(() => {
                console.warn('[InfluxDB Data Fetch Failed] 실시간 SSE만 연결 시도:', err);
                const sensorId = sensorIdByEui[w.widgetConfig.sensorEui];
                if (sensorId) {
                    subscribeWidgetSse(w, sensorId);
                }
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
                        borderColor: i === 0 ? '#206bc4' : '#b8752a',
                        backgroundColor: i === 0 ? 'rgba(32, 107, 196, 0.1)' : 'rgba(184, 117, 42, 0.1)',
                        borderWidth: 1.5,
                        tension: 0.3,
                        fill: i === 0,
                        pointRadius: 0,
                        pointHoverRadius: 3
                    }))
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {legend: {display: datasets.length > 1, labels: {font: {size: 11}}}},
                    scales: {
                        x: {ticks: {font: {size: 10}}, grid: {display: false}},
                        y: {ticks: {font: {size: 10}}, grid: {color: '#eef1f6'}}
                    }
                }
            });
            return;
        }

        const firstSeries = datasets[0]?.data || [];
        const latest = firstSeries.length ? firstSeries[firstSeries.length - 1] : null;
        const valueEl = body.querySelector('.grid-widget-value');
        if (valueEl) {
            valueEl.textContent = latest === null || latest === undefined ? '데이터 없음' : Number(latest).toLocaleString(undefined, {maximumFractionDigits: 1});
        }

        if (type === 'GAUGE') {
            const canvas = body.querySelector('.grid-widget-sparkline canvas');
            if (canvas) {
                destroyChart(w.uid);
                chartInstances[w.uid] = new Chart(canvas, {
                    type: 'line',
                    data: {
                        labels,
                        datasets: [{
                            data: firstSeries,
                            borderColor: '#206bc4',
                            borderWidth: 1.5,
                            tension: 0.3,
                            fill: false,
                            pointRadius: 0
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {legend: {display: false}},
                        scales: {x: {display: false}, y: {display: false}}
                    }
                });
            }
        }
    }

    function destroyChart(uid) {
        if (chartInstances[uid]) {
            chartInstances[uid].destroy();
            delete chartInstances[uid];
        }
    }

    // ---------- GridStack layout events ----------

    grid.on('change', (event, items) => {
        (items || []).forEach((item) => {
            const w = state.find((x) => x.uid === String(item.id));
            if (!w) return;
            w.xPos = item.x;
            w.yPos = item.y;
            w.width = item.w;
            w.height = item.h;
            w.dirty = true;
            console.log(`[Widget Layout Changed] 위젯 (${w.uid}) 위치/크기 변경: x=${w.xPos}, y=${w.yPos}, w=${w.width}, h=${w.height}`);
            updateDim(w);
            if (chartInstances[w.uid]) {
                chartInstances[w.uid].resize();
            }
        });
    });

    grid.on('resizestop', (event, el) => {
        const uid = el.getAttribute('gs-id');
        console.log(`[Widget Resize Stopped] 위젯 (${uid}) 리사이즈 완료`);
        if (uid && chartInstances[uid]) {
            chartInstances[uid].resize();
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
                const defaultAttrs = [
                    {metricKey: 'temperature', displayName: '온도', unit: '°C'},
                    {metricKey: 'humidity', displayName: '습도', unit: '%'}
                ];
                metricListEl.innerHTML = defaultAttrs.map((a) => `
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" value="${a.metricKey}" id="metric-${a.metricKey}"
                               ${selectedFields.includes(a.metricKey) || selectedFields.length === 0 ? 'checked' : ''}>
                        <label class="form-check-label" for="metric-${a.metricKey}">${a.displayName} <span class="text-muted">(${a.unit})</span></label>
                    </div>
                `).join('');
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

        const type = modalEl.querySelector('input[name="widgetType"]:checked')?.value || 'GRAPH';
        const sensorOpt = sensorSelect.options[sensorSelect.selectedIndex];
        const sensorEui = sensorOpt ? sensorOpt.dataset.eui : null;

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

        // 설정 적용 시 해당 센서 SSE 새로 구독
        const sensorId = (sensorOpt && sensorOpt.value) ? sensorOpt.value : (sensorIdByEui[sensorEui] || '1');
        if (sensorId) {
            subscribeWidgetSse(w, sensorId);
        }
    });

    // ---------- add / remove ----------

    btnAddWidget.addEventListener('click', () => {
        const w = {
            uid: 'new' + (++uidCounter),
            widgetId: null,
            xPos: 0,
            yPos: nextFreeRow(),
            width: 4,
            height: 4,
            widgetConfig: {type: 'GRAPH', sensorEui: null, range: '-1h', aggregateWindow: '1m', fields: []}
        };
        state.push(w);
        addItemToGrid(w);
        renderAll();
    });

    function removeWidget(uid) {
        if (!confirm('이 위젯을 삭제할까요?')) return;
        destroyChart(uid);

        if (sseConnections[uid]) {
            sseConnections[uid].close();
            delete sseConnections[uid];
        }

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
                if (!r.ok) throw new Error(`save failed with status ${r.status}`);
                location.reload();
            })
            .catch((err) => {
                console.error('[Dashboard Save] 저장 실패 원인:', err);
                saveStatusEl.textContent = '저장에 실패했어요. 잠시 후 다시 시도해주세요.';
                btnSaveLayout.disabled = false;
            });
    });

    // ---------- init ----------

    state.forEach((w) => addItemToGrid(w));
    renderAll();
})();
