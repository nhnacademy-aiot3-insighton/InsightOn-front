(function () {
    const gridEl = document.getElementById('widgetGrid');
    if (!gridEl) return;

    // 차트 색을 현재 테마(app.css 토큰)에서 읽어온다 — theme.js가 <head>에서
    // <html data-theme>를 이미 세팅하므로 페이지 로드 시점의 라이트/다크가 반영됨.
    function cssVar(name, fallback) {
        const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
        return v || fallback;
    }

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
    const CAN_MANAGE = DASHBOARD_INIT.canManage === true;
    const BASE_URL = `/my-group/location/${LOCATION_ID}/dashboard`;

    // sensorEui -> display name & sensorId mapping
    const sensorNameByEui = {};
    const sensorIdByEui = {};
    if (sensorSelect) {
        sensorSelect.querySelectorAll('option[data-eui]').forEach((opt) => {
            const eui = opt.dataset.eui;
            sensorNameByEui[eui] = opt.textContent.trim();
            sensorIdByEui[eui] = opt.value; // sensorId
        });
    }

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
            aggregateWindow: '15m',
            fields: []
        },
        layoutDirty: false,  // 위치·크기 변경 여부 (드래그, 리사이즈)
        configDirty: false   // 설정 변경 여부 (위젯 설정 모달 "적용")
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

    function getAggregateWindowMs(aggStr) {
        if (!aggStr) return 15 * 60 * 1000;
        const unit = aggStr.slice(-1);
        const val = parseInt(aggStr.slice(0, -1), 10) || 15;
        if (unit === 'm') return val * 60 * 1000;
        if (unit === 'h') return val * 60 * 60 * 1000;
        if (unit === 's') return val * 1000;
        return 15 * 60 * 1000;
    }

    function formatTelemetryTime(ts) {
        const d = ts ? new Date(ts) : new Date();
        const now = new Date();
        const sameDay = d.getFullYear() === now.getFullYear()
            && d.getMonth() === now.getMonth()
            && d.getDate() === now.getDate();
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        if (sameDay) {
            return `${hours}:${minutes}`;
        }
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${month}-${day} ${hours}:${minutes}`;
    }

    // SSE 실시간 데이터 갱신 (widgetConfig.aggregateWindow 버킷 주기에 맞춘 실시간 갱신)
    function updateWidgetRealtime(w, telemetryData) {
        const chart = chartInstances[w.uid];
        const metrics = telemetryData.metrics || {};
        const type = w.widgetConfig.type || 'GRAPH';

        // 미래 타임스탬프 방지: 서버 시각이 현재보다 앞서면 현재 시각으로 cap
        const rawMs = telemetryData.timestamp ? new Date(telemetryData.timestamp).getTime() : Date.now();
        const incomingMs = Math.min(rawMs, Date.now());
        const windowMs = getAggregateWindowMs(w.widgetConfig.aggregateWindow);
        const bucketMs = Math.floor(incomingMs / windowMs) * windowMs;
        const bucketTimeStr = formatTelemetryTime(bucketMs);

        if ((type === 'GRAPH' || type === 'BAR') && chart) {
            const labels = chart.data.labels;
            const lastLabel = labels.length > 0 ? labels[labels.length - 1] : null;

            if (lastLabel === bucketTimeStr) {
                // 같은 집계 주기(예: 동일한 15분 구간) 내 수신 데이터는 기존 마지막 포인트를 갱신
                chart.data.datasets.forEach((ds) => {
                    const key = ds.fieldKey || ds.label;
                    const val = metrics[key] ?? metrics[ds.label] ?? null;
                    if (val !== null && ds.data.length > 0) {
                        ds.data[ds.data.length - 1] = Number(val);
                    }
                });
            } else {
                // 새로운 집계 주기 구간이 시작되면 신규 라벨 및 데이터 포인트 추가
                labels.push(bucketTimeStr);
                chart.data.datasets.forEach((ds) => {
                    const key = ds.fieldKey || ds.label;
                    const val = metrics[key] ?? metrics[ds.label] ?? null;
                    ds.data.push(val !== null ? Number(val) : null);
                });
            }

            // 데이터 개수가 많아지면 좌우 스크롤 폭 확장
            adjustChartScroll(w, chart.data.labels.length);

            // Y축 수치 범위: 듀얼 모드는 paintWidgetData에서 고정된 값 재사용, 싱글은 syncYAxis
            const datasets = chart.data.datasets;
            const isDual = (datasets.length === 2);
            if (isDual) {
                // yFixed가 없으면(히스토리 없이 SSE만 수신) 지금 데이터로 한 번만 고정
                if (!w.yFixed0 || !w.yFixed1) {
                    const d0 = (datasets[0]?.data || []).filter(v => v !== null && v !== undefined);
                    const d1 = (datasets[1]?.data || []).filter(v => v !== null && v !== undefined);
                    const mn0 = d0.length ? Math.min(...d0) : 0;
                    const mx0 = d0.length ? Math.max(...d0) : 100;
                    const mn1 = d1.length ? Math.min(...d1) : 0;
                    const mx1 = d1.length ? Math.max(...d1) : 100;
                    const sp0 = (mx0 - mn0) || 10;
                    const sp1 = (mx1 - mn1) || 10;
                    w.yFixed0 = { min: Math.floor(mn0 - sp0 * 0.05), max: Math.ceil(mx0 + sp0 * 0.05) };
                    w.yFixed1 = { min: Math.floor(mn1 - sp1 * 0.05), max: Math.ceil(mx1 + sp1 * 0.05) };
                }

                if (chart.options.scales.y) {
                    chart.options.scales.y.min = w.yFixed0.min;
                    chart.options.scales.y.max = w.yFixed0.max;
                }
                if (chart.options.scales.y1) {
                    chart.options.scales.y1.min = w.yFixed1.min;
                    chart.options.scales.y1.max = w.yFixed1.max;
                }
            } else {
                const allDataPoints = datasets.flatMap(d => d.data || []).filter(v => v !== null && v !== undefined);
                const minVal = allDataPoints.length ? Math.min(...allDataPoints) : 0;
                const maxVal = allDataPoints.length ? Math.max(...allDataPoints) : 100;
                syncYAxis(w, minVal, maxVal, null, null, false);
            }

            chart.update('quiet');
        } else if (type === 'GAUGE' || type === 'SINGLE_STAT') {
            const el = contentEl(w.uid);
            const firstField = (w.widgetConfig.fields || [])[0];
            if (!firstField) return;
            const val = metrics[firstField] ?? 0;
            const numericVal = Number(val);

            const valueEl = el ? el.querySelector('.grid-widget-value') : null;
            if (valueEl) {
                valueEl.textContent = numericVal.toLocaleString(undefined, { maximumFractionDigits: 1 });
            }

            if (chart) {
                const maxVal = 100;
                const fillVal = Math.min(Math.max(numericVal, 0), maxVal);
                chart.data.datasets[0].data = [fillVal, maxVal - fillVal];
                chart.update('quiet');
            }
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

    // GridStack 초기화 (위젯 카드 이동은 헤더 바를 잡았을 때만 가능하도록 한정)
    const grid = GridStack.init({
        column: 12,
        cellHeight: 80,
        margin: 8,
        float: true,
        handle: '.grid-widget-header',
        resizable: {handles: 'e, se, s, sw, w'},
        disableDrag: !CAN_MANAGE,
        disableResize: !CAN_MANAGE
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

    // 위젯에 쓰인 센서들의 메트릭 정의(표시명/단위)를 페이지 로드 시 미리 모아 캐싱한다.
    // metric_definitions는 전역 테이블(센서 무관하게 같은 키는 같은 정의)이라 위젯마다 다시 안 물어봐도 됨
    const metricDefsByKey = {};

    function metricLabel(key) {
        return (metricDefsByKey[key] && metricDefsByKey[key].displayName) || key;
    }

    function metricUnit(key) {
        return (metricDefsByKey[key] && metricDefsByKey[key].unit) || '';
    }

    function metricLabelWithUnit(key) {
        const unit = metricUnit(key);
        return unit ? `${metricLabel(key)} (${unit})` : metricLabel(key);
    }

    function loadMetricDefs(sensorIds) {
        const uniqueIds = [...new Set(sensorIds.filter(Boolean))];
        return Promise.all(uniqueIds.map((id) =>
            fetch(`/my-group/sensors/${id}/attributes`)
                .then((r) => r.ok ? r.json() : [])
                .then((attrs) => attrs.forEach((a) => {
                    metricDefsByKey[a.metricKey] = {displayName: a.displayName, unit: a.unit};
                }))
                .catch(() => {})
        ));
    }

    function widgetTitle(w) {
        const sensorName = sensorNameByEui[w.widgetConfig.sensorEui] || '미설정 센서';
        const fields = (w.widgetConfig.fields || []).map(metricLabel).join(', ') || '메트릭 미선택';
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

        // 카드 상단 액센트 바 색 — 첫 메트릭 색(온도=레드, 습도=민트 …). 미설정이면 primary
        const firstField = (w.widgetConfig.fields || [])[0];
        const accent = firstField ? getFieldColor(firstField, 0) : 'var(--primary)';

        const cursorStyle = CAN_MANAGE ? 'cursor: move;' : '';
        const dragIcon = CAN_MANAGE ? '<i class="ti ti-drag-drop text-muted fs-3" title="드래그하여 위젯 이동"></i>' : '';
        const controlsHtml = CAN_MANAGE ? `
            <div class="grid-widget-controls d-flex align-items-center gap-1">
                <button type="button" class="settings btn btn-icon btn-ghost-secondary btn-sm" title="위젯 설정" aria-label="위젯 설정"><i class="ti ti-settings"></i></button>
                <button type="button" class="remove btn btn-icon btn-ghost-danger btn-sm" title="위젯 삭제" aria-label="위젯 삭제"><i class="ti ti-trash"></i></button>
            </div>
        ` : '';

        item.innerHTML = `
            <div class="card grid-widget h-100 border shadow-sm rounded-3" style="--w-accent: ${accent};">
                <div class="card-header grid-widget-header px-3 py-2 border-bottom d-flex align-items-center justify-content-between" style="${cursorStyle}">
                    <div class="grid-widget-label d-flex align-items-center gap-2 text-truncate" style="max-width: ${CAN_MANAGE ? 'calc(100% - 95px)' : '100%'};">
                        ${dragIcon}
                        <span class="fw-bold text-truncate" title="${sensorName} · ${fields}">${fields}</span>
                    </div>
                    ${controlsHtml}
                </div>
                <div class="card-body grid-widget-body p-2 d-flex flex-column" style="position: relative; flex: 1; min-height: 0;"></div>
                <div class="grid-widget-dim">${w.width}×${w.height}</div>
            </div>
        `;

        const content = item.querySelector('.grid-widget');
        if (CAN_MANAGE) {
            const settingsBtn = content.querySelector('.settings');
            if (settingsBtn) settingsBtn.addEventListener('click', () => openConfigModal(w));
            const removeBtn = content.querySelector('.remove');
            if (removeBtn) removeBtn.addEventListener('click', () => removeWidget(w.uid));
        }

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
            span.textContent = fields;                       // 헤더엔 메트릭만
            span.title = `${sensorName} · ${fields}`;        // 센서 이름은 hover 툴팁으로
        }
        updateAccent(w);
    }

    // 카드 상단 액센트 바를 현재 첫 메트릭 색으로 갱신 (설정 변경 후)
    function updateAccent(w) {
        const el = contentEl(w.uid);
        if (!el) return;
        const f = (w.widgetConfig.fields || [])[0];
        el.style.setProperty('--w-accent', f ? getFieldColor(f, 0) : 'var(--primary)');
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

        // 센서가 설정된 경우 차트/수치 캔버스 렌더링
        // configDirty(설정 변경)일 때는 btnApplyConfig에서 직접 fetchAndRenderData를 호출하므로 여기서 중복 호출하지 않음
        // layoutDirty(드래그·리사이즈)일 때는 renderWidgetBody 자체가 호출되지 않으므로 고려 불필요
        if (w.widgetConfig && w.widgetConfig.sensorEui) {
            initEmptyChart(w, el);
            if (w.widgetId && !w.configDirty) {
                // 초기 로드(페이지 리로드) 시에만 여기서 InfluxDB 조회
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

    function adjustChartScroll(w, labelCount, forceScroll = false) {
        const el = contentEl(w.uid);
        if (!el) return;
        const inner = el.querySelector('.chart-inner-canvas');
        const wrapper = el.querySelector('.chart-scroll-wrapper');
        if (inner && wrapper) {
            const displayMode = (w.widgetConfig && w.widgetConfig.displayMode) || 'SCROLL';
            if (displayMode === 'FIT') {
                inner.style.width = '100%';
                wrapper.style.overflowX = 'hidden';
                return;
            }

            wrapper.style.overflowX = 'auto';
            const wrapperWidth = wrapper.getBoundingClientRect().width || wrapper.clientWidth || 300;
            const contentWidth = labelCount > 0 ? labelCount * 50 : wrapperWidth;
            const minWidth = Math.max(wrapperWidth, contentWidth);
            inner.style.width = minWidth + 'px';

            const isNearRight = (wrapper.scrollWidth - wrapper.clientWidth - wrapper.scrollLeft) < 80;
            if (forceScroll || isNearRight) {
                wrapper.scrollLeft = wrapper.scrollWidth;
            }

            if (!wrapper.dataset.dragBound) {
                wrapper.dataset.dragBound = 'true';
                let isDown = false;
                let startX, scrollLeftPos;

                wrapper.addEventListener('mousedown', (e) => {
                    isDown = true;
                    wrapper.style.cursor = 'grabbing';
                    startX = e.pageX - wrapper.offsetLeft;
                    scrollLeftPos = wrapper.scrollLeft;
                });
                wrapper.addEventListener('mouseleave', () => {
                    isDown = false;
                    wrapper.style.cursor = 'grab';
                });
                wrapper.addEventListener('mouseup', () => {
                    isDown = false;
                    wrapper.style.cursor = 'grab';
                });
                wrapper.addEventListener('mousemove', (e) => {
                    if (!isDown) return;
                    e.preventDefault();
                    const x = e.pageX - wrapper.offsetLeft;
                    const walk = (x - startX) * 1.5;
                    wrapper.scrollLeft = scrollLeftPos - walk;
                });
            }
        }
    }

    function getFieldColor(field, index) {
        const name = String(field || '').toLowerCase();
        if (name.includes('temp') || name.includes('온도')) {
            return '#ff6b6b'; // 모던 코랄 소프트 레드 (온도)
        }
        if (name.includes('humid') || name.includes('습도')) {
            return '#4ecdc4'; // 세련된 틸 민트 블루 (습도)
        }
        if (name.includes('co2') || name.includes('co')) {
            return '#00b894'; // 에메랄드 그린 (CO2)
        }
        if (name.includes('press') || name.includes('기압')) {
            return '#fdcb6e'; // 앰버 옐로우 (기압)
        }
        if (name.includes('light') || name.includes('illumi') || name.includes('조도')) {
            return '#e17055'; // 테라코타 오렌지 (조도)
        }
        if (name.includes('battery') || name.includes('배터리')) {
            return '#a29bfe'; // 소프트 라벤더 바이올렛 (배터리)
        }

        const fallbackColors = ['#ff6b6b', '#4ecdc4', '#00b894', '#fdcb6e', '#a29bfe', '#e17055', '#0984e3'];
        return fallbackColors[index % fallbackColors.length];
    }

    function yTickFmt(val) {
        if (Math.abs(val) >= 1000000) return (val / 1000000).toFixed(1) + 'M';
        if (Math.abs(val) >= 10000) return (val / 1000).toFixed(0) + 'k';
        return Number(val).toLocaleString();
    }

    function syncYAxis(w, minVal, maxVal, minVal1 = null, maxVal1 = null, isDual = false) {
        const el = contentEl(w.uid);
        if (!el) return;

        destroyChart(w.uid + '_y');
        destroyChart(w.uid + '_y1');

        if (isDual) return; // 듀얼 축일 때는 Chart.js 자체 캔버스에 직접 좌/우 Y축을 그리므로 더미 캔버스가 필요없음

        const yCanvas = el.querySelector('.y-axis-canvas');
        if (!yCanvas) return;

        chartInstances[w.uid + '_y'] = new Chart(yCanvas, {
            type: 'line',
            data: { labels: [''], datasets: [] },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                plugins: { legend: { display: false }, tooltip: { enabled: false } },
                scales: {
                    x: { display: false },
                    y: {
                        min: (minVal !== undefined && minVal !== null) ? Math.floor(minVal) : 0,
                        max: (maxVal !== undefined && maxVal !== null) ? Math.ceil(maxVal) : 100,
                        ticks: {
                            font: { size: 10, weight: '600' },
                            color: cssVar('--ink-soft', '#475569'),
                            precision: 0,
                            callback: yTickFmt
                        },
                        grid: { color: 'transparent' }
                    }
                }
            }
        });
    }

    function initEmptyChart(w, el) {
        const type = w.widgetConfig.type || 'GRAPH';
        const body = el.querySelector('.grid-widget-body');
        if (!body) return;

        if (type === 'GRAPH' || type === 'BAR') {
            const fields = (w.widgetConfig.fields && w.widgetConfig.fields.length)
                ? w.widgetConfig.fields
                : [];
            const isDual = (fields.length === 2);
            destroyChart(w.uid);

            let legendHtml = '';
            let scrollWrapperHtml = '';

            if (isDual) {
                const color0 = getFieldColor(fields[0], 0);
                const color1 = getFieldColor(fields[1], 1);
                legendHtml = `
                    <div class="d-flex align-items-center gap-1 small fw-bold" style="color: ${color0};">
                        <span style="display: inline-block; width: 10px; height: 10px; border-radius: 2px; background-color: ${color0};"></span>
                        <span>← ${metricLabelWithUnit(fields[0])}</span>
                    </div>
                    <div class="d-flex align-items-center gap-1 small fw-bold" style="color: ${color1};">
                        <span>${metricLabelWithUnit(fields[1])} →</span>
                        <span style="display: inline-block; width: 10px; height: 10px; border-radius: 2px; background-color: ${color1};"></span>
                    </div>
                `;

                scrollWrapperHtml = `
                    <div class="chart-inner-canvas" style="min-width: 100%; height: 100%; position: relative;">
                        <canvas class="main-canvas" style="width: 100%; height: 100%;"></canvas>
                    </div>
                `;
            } else {
                legendHtml = fields.map((field, idx) => {
                    const color = getFieldColor(field, idx);
                    return `<div class="d-flex align-items-center gap-1 small fw-bold" style="color: ${cssVar('--ink', '#334155')};">
                        <span style="display: inline-block; width: 10px; height: 10px; border-radius: 2px; background-color: ${color};"></span>
                        <span>${metricLabelWithUnit(field)}</span>
                    </div>`;
                }).join('');

                scrollWrapperHtml = `
                    <div class="sticky-y-axis" style="position: sticky; left: 0; top: 0; width: 68px; height: 100%; z-index: 20; background: transparent; float: left; margin-right: -68px; pointer-events: none;">
                        <canvas class="y-axis-canvas" style="width: 100%; height: 100%;"></canvas>
                    </div>
                    <div class="chart-inner-canvas" style="min-width: 100%; height: 100%; position: relative; padding-left: 68px;">
                        <canvas class="main-canvas" style="width: 100%; height: 100%;"></canvas>
                    </div>
                `;
            }

            body.className = 'card-body grid-widget-chart grid-widget-body p-2 d-flex flex-column';
            body.innerHTML = `
                <div class="chart-legend-header px-2 pb-1 d-flex ${isDual ? 'justify-content-between' : 'flex-wrap gap-3'} align-items-center border-bottom mb-1" style="flex-shrink: 0; background: ${cssVar('--surface', '#ffffff')};">
                    ${legendHtml}
                </div>
                <div class="chart-scroll-wrapper" style="width: 100%; height: 100%; overflow-x: auto; overflow-y: hidden; cursor: grab; scrollbar-width: none; -ms-overflow-style: none; flex: 1; position: relative;">
                    ${scrollWrapperHtml}
                </div>
            `;
            const canvas = body.querySelector('.main-canvas');
            const chartType = (type === 'BAR') ? 'bar' : 'line';

            const scales = isDual ? {
                x: { ticks: { font: { size: 10 } }, grid: { display: false } },
                y: {
                    type: 'linear',
                    display: true,
                    position: 'left',
                    min: 0,
                    max: 100,
                    ticks: {
                        font: { size: 10, weight: '700' },
                        color: getFieldColor(fields[0], 0),
                        precision: 0,
                        callback: yTickFmt
                    },
                    grid: { color: cssVar('--line', '#e2e8f0') }
                },
                y1: {
                    type: 'linear',
                    display: true,
                    position: 'right',
                    min: 0,
                    max: 100,
                    ticks: {
                        font: { size: 10, weight: '700' },
                        color: getFieldColor(fields[1], 1),
                        precision: 0,
                        callback: yTickFmt
                    },
                    grid: { drawOnChartArea: false }
                }
            } : {
                x: { ticks: { font: { size: 10 } }, grid: { display: false } },
                y: { ticks: { display: false }, grid: { color: cssVar('--line', '#e2e8f0') } }
            };

            chartInstances[w.uid] = new Chart(canvas, {
                type: chartType,
                data: {
                    labels: [],
                    datasets: fields.map((field, idx) => {
                        const color = getFieldColor(field, idx);
                        return {
                            fieldKey: field,
                            label: metricLabelWithUnit(field),
                            data: [],
                            borderColor: color,
                            backgroundColor: (type === 'BAR') ? color + 'b0' : color + '20',
                            borderWidth: (type === 'BAR') ? 1 : 1.5,
                            fill: idx === 0,
                            tension: 0.3,
                            pointRadius: 3,
                            pointHoverRadius: 5,
                            yAxisID: isDual ? (idx === 0 ? 'y' : 'y1') : 'y'
                        };
                    })
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    plugins: {
                        legend: { display: false }
                    },
                    scales
                }
            });

            if (!isDual) {
                syncYAxis(w, 0, 100, null, null, false);
            }
        } else if (type === 'GAUGE' || type === 'SINGLE_STAT') {
            const gaugeUnit = metricUnit((w.widgetConfig.fields || [])[0]);
            body.className = 'card-body grid-widget-body p-2 d-flex flex-column align-items-center justify-content-center position-relative';
            body.innerHTML = `
                <div style="width: 100%; height: 75%; position: relative;">
                    <canvas style="width: 100%; height: 100%;"></canvas>
                </div>
                <div style="position: absolute; bottom: 15px; text-align: center;">
                    <span class="grid-widget-value fs-2 fw-bold text-primary">0</span>
                    <span class="grid-widget-unit text-muted">${gaugeUnit}</span>
                </div>
            `;
            const canvas = body.querySelector('canvas');
            destroyChart(w.uid);

            chartInstances[w.uid] = new Chart(canvas, {
                type: 'doughnut',
                data: {
                    labels: ['현재값', '잔여'],
                    datasets: [{
                        data: [0, 100],
                        backgroundColor: [cssVar('--primary', '#206bc4'), cssVar('--surface-alt', '#eef1f6')],
                        borderWidth: 0,
                        cutout: '75%'
                    }]
                },
                options: {
                    rotation: 270,
                    circumference: 180,
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: false,
                    plugins: { legend: { display: false }, tooltip: { enabled: false } }
                }
            });
        } else {
            const defaultUnit = metricUnit((w.widgetConfig.fields || [])[0]);
            body.className = 'card-body grid-widget-body p-2 d-flex flex-column align-items-center justify-content-center';
            body.innerHTML = `<span class="grid-widget-value fs-1 fw-bold">—</span><span class="grid-widget-unit text-muted">${defaultUnit}</span>`;
        }
    }

    function fetchAndRenderData(w, el) {
        fetch(`/groups/location/${LOCATION_ID}/dashboard/widgets/${w.widgetId}/chart-data`)
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
                console.warn('[InfluxDB Data Fetch Failed] 실시간 SSE만 연결 시도:');
                const sensorId = sensorIdByEui[w.widgetConfig.sensorEui];
                if (sensorId) {
                    subscribeWidgetSse(w, sensorId);
                }
            });
    }

    function paintWidgetData(w, el, data) {
        const type = w.widgetConfig.type || 'GRAPH';
        const labels = data.labels || [];
        const datasets = data.datasets || [];
        const body = el.querySelector('.grid-widget-body');

        if (type === 'GRAPH' || type === 'BAR') {
            const isDual = (datasets.length === 2);
            destroyChart(w.uid);

            let legendHtml = '';
            let scrollWrapperHtml = '';

            if (isDual) {
                const color0 = getFieldColor(datasets[0].label, 0);
                const color1 = getFieldColor(datasets[1].label, 1);
                legendHtml = `
                    <div class="d-flex align-items-center gap-1 small fw-bold" style="color: ${color0};">
                        <span style="display: inline-block; width: 10px; height: 10px; border-radius: 2px; background-color: ${color0};"></span>
                        <span>← ${metricLabelWithUnit(datasets[0].label)}</span>
                    </div>
                    <div class="d-flex align-items-center gap-1 small fw-bold" style="color: ${color1};">
                        <span>${metricLabelWithUnit(datasets[1].label)} →</span>
                        <span style="display: inline-block; width: 10px; height: 10px; border-radius: 2px; background-color: ${color1};"></span>
                    </div>
                `;

                scrollWrapperHtml = `
                    <div class="chart-inner-canvas" style="min-width: 100%; height: 100%; position: relative;">
                        <canvas class="main-canvas" style="width: 100%; height: 100%;"></canvas>
                    </div>
                `;
            } else {
                legendHtml = datasets.map((ds, i) => {
                    const color = getFieldColor(ds.label, i);
                    return `<div class="d-flex align-items-center gap-1 small fw-bold" style="color: ${cssVar('--ink', '#334155')};">
                        <span style="display: inline-block; width: 10px; height: 10px; border-radius: 2px; background-color: ${color};"></span>
                        <span>${metricLabelWithUnit(ds.label)}</span>
                    </div>`;
                }).join('');

                scrollWrapperHtml = `
                    <div class="sticky-y-axis" style="position: sticky; left: 0; top: 0; width: 68px; height: 100%; z-index: 20; background: transparent; float: left; margin-right: -68px; pointer-events: none;">
                        <canvas class="y-axis-canvas" style="width: 100%; height: 100%;"></canvas>
                    </div>
                    <div class="chart-inner-canvas" style="min-width: 100%; height: 100%; position: relative; padding-left: 68px;">
                        <canvas class="main-canvas" style="width: 100%; height: 100%;"></canvas>
                    </div>
                `;
            }

            body.className = 'card-body grid-widget-chart grid-widget-body p-2 d-flex flex-column';
            body.innerHTML = `
                <div class="chart-legend-header px-2 pb-1 d-flex ${isDual ? 'justify-content-between' : 'flex-wrap gap-3'} align-items-center border-bottom mb-1" style="flex-shrink: 0; background: ${cssVar('--surface', '#ffffff')};">
                    ${legendHtml}
                </div>
                <div class="chart-scroll-wrapper" style="width: 100%; height: 100%; overflow-x: auto; overflow-y: hidden; cursor: grab; scrollbar-width: none; -ms-overflow-style: none; flex: 1; position: relative;">
                    ${scrollWrapperHtml}
                </div>
            `;
            const canvas = body.querySelector('.main-canvas');
            if (!canvas) return;
            const chartType = (type === 'BAR') ? 'bar' : 'line';

            let scales = {};
            if (isDual) {
                const d0Points = (datasets[0]?.data || []).filter(v => v !== null && v !== undefined);
                const minVal0 = d0Points.length ? Math.min(...d0Points) : 0;
                const maxVal0 = d0Points.length ? Math.max(...d0Points) : 100;

                const d1Points = (datasets[1]?.data || []).filter(v => v !== null && v !== undefined);
                const minVal1 = d1Points.length ? Math.min(...d1Points) : 0;
                const maxVal1 = d1Points.length ? Math.max(...d1Points) : 100;

                const span0 = (maxVal0 - minVal0) || 10;
                const padMin0 = Math.floor(minVal0 - span0 * 0.05);
                const padMax0 = Math.ceil(maxVal0 + span0 * 0.05);

                const span1 = (maxVal1 - minVal1) || 10;
                const padMin1 = Math.floor(minVal1 - span1 * 0.05);
                const padMax1 = Math.ceil(maxVal1 + span1 * 0.05);

                // 초기 데이터 기반으로 Y축 고정값 저장 (실시간 업데이트 시 재사용)
                w.yFixed0 = { min: padMin0, max: padMax0 };
                w.yFixed1 = { min: padMin1, max: padMax1 };

                scales = {
                    x: { ticks: { font: { size: 10 } }, grid: { display: false } },
                    y: {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        min: padMin0,
                        max: padMax0,
                        ticks: {
                            font: { size: 10, weight: '700' },
                            color: getFieldColor(datasets[0].label, 0),
                            precision: 0,
                            callback: yTickFmt
                        },
                        grid: { color: cssVar('--line', '#e2e8f0') }
                    },
                    y1: {
                        type: 'linear',
                        display: true,
                        position: 'right',
                        min: padMin1,
                        max: padMax1,
                        ticks: {
                            font: { size: 10, weight: '700' },
                            color: getFieldColor(datasets[1].label, 1),
                            precision: 0,
                            callback: yTickFmt
                        },
                        grid: { drawOnChartArea: false }
                    }
                };
            } else {
                scales = {
                    x: { ticks: { font: { size: 10 } }, grid: { display: false } },
                    y: { ticks: { display: false }, grid: { color: cssVar('--line', '#e2e8f0') } }
                };
            }

            chartInstances[w.uid] = new Chart(canvas, {
                type: chartType,
                data: {
                    labels,
                    datasets: datasets.map((ds, i) => {
                        const rawKey = ds.label;
                        const color = getFieldColor(rawKey, i);
                        return {
                            fieldKey: rawKey,
                            label: metricLabelWithUnit(rawKey),
                            data: ds.data,
                            borderColor: color,
                            backgroundColor: (type === 'BAR') ? color + 'b0' : color + '20',
                            borderWidth: 1.5,
                            tension: 0.3,
                            fill: i === 0,
                            pointRadius: 3,
                            pointHoverRadius: 5,
                            yAxisID: isDual ? (i === 0 ? 'y' : 'y1') : 'y'
                        };
                    })
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                        legend: { display: false }
                    },
                    scales
                }
            });

            if (!isDual) {
                const allDataPoints = datasets.flatMap(d => d.data || []).filter(v => v !== null && v !== undefined);
                const minVal = allDataPoints.length ? Math.min(...allDataPoints) : 0;
                const maxVal = allDataPoints.length ? Math.max(...allDataPoints) : 100;
                syncYAxis(w, minVal, maxVal, null, null, false);
            }

            w.lastLabelCount = labels.length;
            adjustChartScroll(w, labels.length, true);
            return;
        }

        const firstSeries = datasets[0]?.data || [];
        const latest = firstSeries.length ? firstSeries[firstSeries.length - 1] : null;
        const valueEl = body.querySelector('.grid-widget-value');
        if (valueEl) {
            valueEl.textContent = latest === null || latest === undefined ? '0' : Number(latest).toLocaleString(undefined, {maximumFractionDigits: 1});
        }

        if ((type === 'GAUGE' || type === 'SINGLE_STAT') && chartInstances[w.uid] && latest !== null && latest !== undefined) {
            const numericVal = Number(latest);
            const maxVal = 100;
            const fillVal = Math.min(Math.max(numericVal, 0), maxVal);
            chartInstances[w.uid].data.datasets[0].data = [fillVal, maxVal - fillVal];
            chartInstances[w.uid].update('quiet');
        }
    }

    function destroyChart(uid) {
        if (chartInstances[uid]) {
            chartInstances[uid].destroy();
            delete chartInstances[uid];
        }
        if (chartInstances[uid + '_y']) {
            chartInstances[uid + '_y'].destroy();
            delete chartInstances[uid + '_y'];
        }
        if (chartInstances[uid + '_y1']) {
            chartInstances[uid + '_y1'].destroy();
            delete chartInstances[uid + '_y1'];
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
            w.layoutDirty = true;  // 위치·크기 변경 — 데이터 재조회 불필요
            console.log(`[Widget Layout Changed] 위젯 (${w.uid}) 위치/크기 변경: x=${w.xPos}, y=${w.yPos}, w=${w.width}, h=${w.height}`);
            updateDim(w);
            adjustChartScroll(w, w.lastLabelCount || 0);
            if (chartInstances[w.uid]) {
                chartInstances[w.uid].resize();
            }
        });
    });

    grid.on('resizestop', (event, el) => {
        const uid = el.getAttribute('gs-id');
        console.log(`[Widget Resize Stopped] 위젯 (${uid}) 리사이즈 완료`);
        const w = state.find((x) => x.uid === String(uid));
        if (w) {
            adjustChartScroll(w, w.lastLabelCount || 0);
        }
        if (uid && chartInstances[uid]) {
            chartInstances[uid].resize();
        }
    });

    // ---------- config modal ----------

    function loadMetrics(sensorId, selectedFields) {
        metricListEl.innerHTML = `<p class="metric-check-empty">불러오는 중...</p>`;
        fetch(`/my-group/sensors/${sensorId}/attributes`)
            .then((r) => {
                if (!r.ok) throw new Error('attributes fetch failed');
                return r.json();
            })
            .then((attrs) => {
                // 새로 고른 센서의 정의도 캐시에 넣어둔다 - 위젯 적용 직후 카드/차트가 raw 키로
                // 잠깐이라도 안 보이고 바로 표시명/단위로 나오게
                (attrs || []).forEach((a) => {
                    metricDefsByKey[a.metricKey] = {displayName: a.displayName, unit: a.unit};
                });
                if (!attrs || !attrs.length) {
                    metricListEl.innerHTML = `<p class="metric-check-empty">이 센서에 등록된 메트릭이 없습니다.</p>`;
                    return;
                }
                metricListEl.innerHTML = attrs.map((a) => `
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" value="${a.metricKey}" id="metric-${a.metricKey}"
                               ${selectedFields.includes(a.metricKey) ? 'checked' : ''}>
                        <label class="form-check-label" for="metric-${a.metricKey}">${a.displayName || a.metricKey} ${a.unit ? `<span class="text-muted">(${a.unit})</span>` : ''}</label>
                    </div>
                `).join('');
                enforceMetricLimit();
            })
            .catch((err) => {
                console.warn('[loadMetrics] 센서 메트릭 조회 실패:', err);
                metricListEl.innerHTML = `<p class="metric-check-empty text-danger">메트릭 정보를 불러올 수 없습니다.</p>`;
            });
    }

    function enforceMetricLimit() {
        if (!metricListEl) return;
        const checkboxes = metricListEl.querySelectorAll('input[type="checkbox"]');
        const checked = Array.from(checkboxes).filter(cb => cb.checked);
        if (checked.length >= 2) {
            checkboxes.forEach(cb => {
                if (!cb.checked) cb.disabled = true;
            });
        } else {
            checkboxes.forEach(cb => cb.disabled = false);
        }
    }

    if (metricListEl) {
        metricListEl.addEventListener('change', (e) => {
            if (e.target && e.target.type === 'checkbox') {
                const checked = metricListEl.querySelectorAll('input[type="checkbox"]:checked');
                if (checked.length > 2) {
                    e.target.checked = false;
                    alert('메트릭은 최대 2개까지만 선택할 수 있습니다.');
                }
                enforceMetricLimit();
            }
        });
    }


    const AGG_OPTIONS_BY_RANGE = {
        '-1h': [
            {value: '1m', label: '1분'},
            {value: '15m', label: '15분'}
        ],
        '-6h': [
            {value: '15m', label: '15분'},
            {value: '1h', label: '1시간'}
        ],
        '-24h': [
            {value: '15m', label: '15분'},
            {value: '1h', label: '1시간'}
        ],
        '-7d': [
            {value: '1h', label: '1시간'}
        ]
    };

    function updateAggOptions(selectedRange, currentAgg) {
        if (!aggSelect) return;
        const allowed = AGG_OPTIONS_BY_RANGE[selectedRange] || AGG_OPTIONS_BY_RANGE['-1h'];
        aggSelect.innerHTML = allowed.map(opt =>
            `<option value="${opt.value}" ${opt.value === currentAgg ? 'selected' : ''}>${opt.label}</option>`
        ).join('');

        if (!allowed.some(opt => opt.value === aggSelect.value)) {
            aggSelect.value = allowed[0].value;
        }
    }

    function openConfigModal(w) {
        editingUid = w.uid;
        const displayMode = (w.widgetConfig && w.widgetConfig.displayMode) || 'SCROLL';
        modalEl.querySelectorAll('input[name="widgetDisplayMode"]').forEach((r) => {
            r.checked = r.value === displayMode;
        });
        modalEl.querySelectorAll('input[name="widgetType"]').forEach((r) => {
            r.checked = r.value === w.widgetConfig.type;
        });
        sensorSelect.value = '';
        Array.from(sensorSelect.options).forEach((opt) => {
            if (opt.dataset.eui === w.widgetConfig.sensorEui) sensorSelect.value = opt.value;
        });
        rangeSelect.value = w.widgetConfig.range || '-1h';
        updateAggOptions(rangeSelect.value, w.widgetConfig.aggregateWindow || '15m');

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

    if (rangeSelect) {
        rangeSelect.addEventListener('change', () => {
            updateAggOptions(rangeSelect.value, aggSelect.value);
        });
    }

    btnApplyConfig.addEventListener('click', () => {
        const w = state.find((x) => x.uid === editingUid);
        if (!w) return;

        const displayMode = modalEl.querySelector('input[name="widgetDisplayMode"]:checked')?.value || 'SCROLL';
        const type = modalEl.querySelector('input[name="widgetType"]:checked')?.value || 'GRAPH';
        const sensorOpt = sensorSelect.options[sensorSelect.selectedIndex];
        const sensorEui = sensorOpt ? sensorOpt.dataset.eui : null;

        const checkboxEls = metricListEl.querySelectorAll('input[type="checkbox"]');
        const fields = checkboxEls.length
            ? Array.from(checkboxEls).filter((c) => c.checked).map((c) => c.value)
            : (w.widgetConfig.fields || []);

        if (!sensorEui) {
            alert('센서를 선택해주세요.');
            return;
        }
        if (!fields || fields.length === 0) {
            alert('최소 1개 이상의 메트릭을 선택해 주세요.');
            return;
        }
        if (fields.length > 2) {
            alert('메트릭은 최대 2개까지만 선택할 수 있습니다.');
            return;
        }

        w.widgetConfig = {
            type,
            sensorEui: sensorEui || null,
            range: rangeSelect.value,
            aggregateWindow: aggSelect.value,
            fields,
            displayMode
        };
        w.configDirty = true;  // 설정 변경 — 저장 필요 + InfluxDB 재조회 필요

        modal.hide();
        updateLabel(w);
        const el = contentEl(w.uid);

        // renderWidgetBody는 configDirty=true일 때 fetchAndRenderData를 건너뛰므로
        // 여기서 명시적으로 호출해 InfluxDB에서 최신 데이터를 가져옴
        if (el) renderWidgetBody(w, el);
        if (w.widgetId && el) {
            fetchAndRenderData(w, el);
        }

        // 설정 적용 시 해당 센서 SSE 새로 구독
        const sensorId = (sensorOpt && sensorOpt.value) ? sensorOpt.value : (sensorIdByEui[sensorEui] || '1');
        if (sensorId) {
            subscribeWidgetSse(w, sensorId);
        }
    });

    // ---------- add / remove ----------

    if (btnAddWidget) {
        btnAddWidget.addEventListener('click', () => {
            const w = {
                uid: 'new' + (++uidCounter),
                widgetId: null,
                xPos: 0,
                yPos: nextFreeRow(),
                width: 4,
                height: 4,
                widgetConfig: {type: 'GRAPH', sensorEui: null, range: '-1h', aggregateWindow: '15m', fields: [], displayMode: 'SCROLL'}
            };
            state.push(w);
            addItemToGrid(w);
            renderAll();
        });
    }

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

    if (btnSaveLayout) {
        btnSaveLayout.addEventListener('click', () => {
            // 미설정 위젯(센서/메트릭 미선택) 검증
            const unconfiguredWidget = state.find((w) =>
                !w.widgetConfig || !w.widgetConfig.sensorEui || !w.widgetConfig.fields || w.widgetConfig.fields.length === 0
            );

            if (unconfiguredWidget) {
                alert('설정되지 않은 위젯이 있습니다.\n위젯 카드의 ⚙️(설정) 버튼을 눌러 센서와 메트릭 항목을 선택한 후 저장해 주세요.');
                const el = contentEl(unconfiguredWidget.uid);
                if (el) {
                    el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    const card = el.querySelector('.grid-widget');
                    if (card) {
                        card.classList.add('border-danger', 'shadow-lg');
                        setTimeout(() => card.classList.remove('border-danger', 'shadow-lg'), 3000);
                    }
                }
                return;
            }

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
                return r.json();
            })
            .then((savedWidgetIds) => {
                // 저장 성공: 서버에서 반환된 widgetId를 state에 반영 (신규 위젯 ID 확정)
                if (Array.isArray(savedWidgetIds)) {
                    state.forEach((w, idx) => {
                        if (savedWidgetIds[idx] != null) {
                            w.widgetId = savedWidgetIds[idx];
                            // uid도 widgetId 기반으로 갱신 (gridstack gs-id는 유지)
                        }
                    });
                }

                // dirty 플래그 초기화
                state.forEach((w) => {
                    const wasDirty = w.layoutDirty || w.configDirty;
                    w.layoutDirty = false;
                    w.configDirty = false;

                    // 설정이 변경됐던 위젯은 InfluxDB 데이터 즉시 재조회
                    if (wasDirty && w.widgetId) {
                        const el = contentEl(w.uid);
                        if (el) {
                            console.log(`[Save] 위젯 (${w.uid}) 저장 완료, InfluxDB 데이터 재조회`);
                            fetchAndRenderData(w, el);
                        }
                    }
                });

                saveStatusEl.textContent = '저장 완료 ✓';
                btnSaveLayout.disabled = false;
                setTimeout(() => { saveStatusEl.style.display = 'none'; }, 2000);
                console.log('[Dashboard Save] 저장 완료, widgetIds:', savedWidgetIds);
            })
            .catch((err) => {
                console.error('[Dashboard Save] 저장 실패 원인:', err);
                saveStatusEl.textContent = '저장에 실패했어요. 잠시 후 다시 시도해주세요.';
                btnSaveLayout.disabled = false;
            });
        });
    }

    // ---------- init ----------

    // 위젯 카드/차트를 그리기 전에 메트릭 표시명·단위부터 채워둬야, 첫 렌더부터 raw 키(temperature)가
    // 아니라 "온도 (°C)"로 바로 보인다
    const widgetSensorIds = state.map((w) => sensorIdByEui[w.widgetConfig.sensorEui]);
    loadMetricDefs(widgetSensorIds).then(() => {
        state.forEach((w) => addItemToGrid(w));
        renderAll();
    });
})();
