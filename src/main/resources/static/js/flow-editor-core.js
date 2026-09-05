/** Flow 편집기의 그래프 해석과 시간 변환처럼 DOM에 의존하지 않는 규칙을 모은다. */
(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) module.exports = api;
    root.FlowEditorCore = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    const BACKEND_INTEGER_MAX = 2147483647;
    const SECONDS_PER_MINUTE = 60;
    const WEEKDAY_NAME_TO_INDEX = {SUN: 0, MON: 1, TUE: 2, WED: 3, THU: 4, FRI: 5, SAT: 6};

    function minutesToSeconds(minutes) {
        const seconds = Math.round(minutes * SECONDS_PER_MINUTE);
        return Number.isSafeInteger(seconds) && seconds >= 0 && seconds <= BACKEND_INTEGER_MAX
            ? seconds
            : null;
    }

    // 화면에서 선택한 반복 규칙을 Rule Engine의 Spring 6필드 cron으로 변환한다.
    function buildCron(schedule) {
        const minute = String(schedule.minute);
        const hour = String(schedule.hour);
        if (schedule.repeatType === 'WEEKLY') {
            const days = schedule.weekdays.length ? schedule.weekdays.slice().sort().join(',') : '*';
            return `0 ${minute} ${hour} * * ${days}`;
        }
        if (schedule.repeatType === 'MONTHLY') {
            return `0 ${minute} ${hour} ${schedule.day} * *`;
        }
        return `0 ${minute} ${hour} * * *`;
    }

    function weekdayIndex(token) {
        const value = token.trim().toUpperCase();
        if (value === '7') return 0;
        if (/^[0-6]$/.test(value)) return Number(value);
        return Object.prototype.hasOwnProperty.call(WEEKDAY_NAME_TO_INDEX, value)
            ? WEEKDAY_NAME_TO_INDEX[value]
            : null;
    }

    // Spring은 요일 범위의 시작값 7만 0으로 바꾸고 끝값 7은 그대로 둔다.
    // 그래서 0-7과 7-7은 모두 주 7일이며, 이 차이를 잃으면 저장 시 실행일이 바뀐다.
    function weekdayRangeEndIndex(token) {
        const value = token.trim().toUpperCase();
        if (value === '7') return 7;
        return weekdayIndex(value);
    }

    function parseWeekdayField(field) {
        const days = new Set();
        for (const token of field.split(',')) {
            const range = token.split('-');
            if (range.length === 1) {
                const value = weekdayIndex(range[0]);
                if (value === null) return null;
                days.add(value);
            } else if (range.length === 2) {
                const start = weekdayIndex(range[0]);
                const end = weekdayRangeEndIndex(range[1]);
                if (start === null || end === null || start > end) return null;
                for (let i = start; i <= end; i++) {
                    days.add(i === 7 ? 0 : i);
                }
            } else {
                return null;
            }
        }
        return Array.from(days);
    }

    /**
     * 편집 폼이 손실 없이 표현할 수 있는 매일/매주/매월 cron만 구조화한다.
     * null은 cron이 잘못됐다는 뜻이 아니라 이 단순 폼의 표현 범위를 벗어났다는 뜻이다.
     */
    function parseCron(cron) {
        const parts = String(cron || '').trim().split(/\s+/);
        if (parts.length !== 6) return null;
        const [second, minute, hour, day, month, weekday] = parts;
        if (second !== '0' || !/^\d+$/.test(minute) || !/^\d+$/.test(hour) || month !== '*') return null;
        const m = Number(minute);
        const h = Number(hour);
        if (m < 0 || m > 59 || h < 0 || h > 23) return null;
        if (day !== '*' && weekday === '*') {
            if (!/^\d+$/.test(day)) return null;
            const d = Number(day);
            if (d < 1 || d > 31) return null;
            return {repeatType: 'MONTHLY', hour: h, minute: m, day: d, weekdays: []};
        }
        if (day === '*' && weekday !== '*') {
            const weekdays = parseWeekdayField(weekday);
            if (!weekdays || !weekdays.length) return null;
            return {repeatType: 'WEEKLY', hour: h, minute: m, day: null, weekdays};
        }
        if (day === '*' && weekday === '*') {
            return {repeatType: 'DAILY', hour: h, minute: m, day: null, weekdays: []};
        }
        return null;
    }

    function isAction(node) {
        return node && (node.nodeType === 'ALERT' || node.nodeType === 'ACTUATOR_CONTROL');
    }

    function findRangeCommandWidget(rules, actuatorType) {
        const commands = rules[actuatorType] || {};
        return Object.values(commands).find((widget) => widget.kind === 'RANGE') || null;
    }

    function combineEditorActions(actions, actuatorCommandRules) {
        if (!actions.length || actions.some((node) => !isAction(node))) return null;

        const pairedIndexes = new Set();
        const combinedByFirstIndex = new Map();
        actions.forEach((primary, primaryIndex) => {
            if (pairedIndexes.has(primaryIndex) || primary.nodeType !== 'ACTUATOR_CONTROL') return;
            const primaryConfig = primary.configuration || {};
            const temperatureWidget = findRangeCommandWidget(actuatorCommandRules, primaryConfig.actuatorType);
            if (primaryConfig.command !== 'power'
                || String(primaryConfig.commandValue) !== 'ON'
                || !temperatureWidget) {
                return;
            }

            // 저장기는 전원 노드 바로 다음에 보조 온도 노드를 기록한다. 떨어진 노드까지
            // 검색해 합치면 사용자가 지정한 액션 순서가 바뀔 수 있으므로 이 순서만 복원한다.
            const supplementalIndex = primaryIndex + 1;
            const supplemental = actions[supplementalIndex];
            const supplementalConfig = supplemental && supplemental.configuration
                ? supplemental.configuration
                : {};
            if (!supplemental
                || pairedIndexes.has(supplementalIndex)
                || supplemental.nodeType !== 'ACTUATOR_CONTROL'
                || supplementalConfig.actuatorType !== primaryConfig.actuatorType
                || supplementalConfig.command !== temperatureWidget.stateKey) {
                return;
            }

            pairedIndexes.add(primaryIndex);
            pairedIndexes.add(supplementalIndex);
            combinedByFirstIndex.set(primaryIndex, {
                ...primary,
                supplementalTemperatureValue: supplementalConfig.commandValue
            });
        });

        const editorActions = [];
        actions.forEach((action, index) => {
            const combined = combinedByFirstIndex.get(index);
            if (combined) {
                editorActions.push(combined);
            } else if (!pairedIndexes.has(index)) {
                editorActions.push(action);
            }
        });

        // 편집 화면은 같은 기기 종류를 여러 카드로 표현하지 않으므로, 온도 보조 동작으로
        // 정상 병합되지 않은 중복 기기 제어는 저장 손실을 막기 위해 편집을 차단한다.
        const actuatorTypes = new Set();
        for (const action of editorActions) {
            if (action.nodeType !== 'ACTUATOR_CONTROL') continue;
            const actuatorType = (action.configuration || {}).actuatorType;
            if (actuatorTypes.has(actuatorType)) return null;
            actuatorTypes.add(actuatorType);
        }
        return editorActions;
    }

    function outgoingKey(sourceClientNodeKey, sourcePort) {
        return JSON.stringify([sourceClientNodeKey, sourcePort]);
    }

    /**
     * 화면이 손실 없이 표현할 수 있는 단일 경로만 해석한다.
     * Node뿐 아니라 모든 Link가 소비되어야 성공하므로 숨은 분기를 저장 과정에서 지우지 않는다.
     */
    function parseFlow(nodes, links, actuatorCommandRules) {
        const nodeByKey = new Map();
        for (const node of nodes) {
            if (!node || !node.clientNodeKey || nodeByKey.has(node.clientNodeKey)) return null;
            nodeByKey.set(node.clientNodeKey, node);
        }

        const outgoing = new Map();
        links.forEach((link, index) => {
            const key = outgoingKey(link.sourceClientNodeKey, link.sourcePort);
            if (!outgoing.has(key)) outgoing.set(key, []);
            outgoing.get(key).push({link, index});
        });

        const triggers = nodes.filter((node) =>
            node.nodeType === 'SENSOR' || node.nodeType === 'LOCATION' || node.nodeType === 'SCHEDULE');
        if (triggers.length !== 1) return null;

        const usedKeys = new Set();
        const consumedLinkIndexes = new Set();
        const trigger = triggers[0];
        const filters = [];
        let gate = null;
        let actions = null;
        usedKeys.add(trigger.clientNodeKey);
        let sourceKey = trigger.clientNodeKey;
        let sourcePort = 'out';

        for (let guard = 0; guard <= nodes.length; guard++) {
            const indexedLinks = outgoing.get(outgoingKey(sourceKey, sourcePort)) || [];
            if (!indexedLinks.length || indexedLinks.some(({link}) => link.targetPort !== 'in')) return null;

            const targets = indexedLinks.map(({link}) => nodeByKey.get(link.targetClientNodeKey));
            if (targets.some((target) => !target || usedKeys.has(target.clientNodeKey))) return null;
            if (new Set(targets.map((target) => target.clientNodeKey)).size !== targets.length) return null;
            indexedLinks.forEach(({index}) => consumedLinkIndexes.add(index));

            const combinedActions = combineEditorActions(targets, actuatorCommandRules);
            if (combinedActions) {
                targets.forEach((target) => usedKeys.add(target.clientNodeKey));
                actions = combinedActions;
                break;
            }
            if (targets.length !== 1) return null;

            const target = targets[0];
            usedKeys.add(target.clientNodeKey);
            if (target.nodeType === 'THRESHOLD') {
                if (gate) return null;
                filters.push(target);
                sourceKey = target.clientNodeKey;
                sourcePort = 'true';
                continue;
            }
            if (target.nodeType === 'EVENT_GATE') {
                if (gate) return null;
                gate = target;
                sourceKey = target.clientNodeKey;
                sourcePort = 'true';
                continue;
            }
            return null;
        }

        const scheduleShapeSupported = trigger.nodeType !== 'SCHEDULE'
            || (filters.length === 0
                && gate === null
                && actions
                && actions.every((action) => action.nodeType === 'ACTUATOR_CONTROL'));
        return actions
            && scheduleShapeSupported
            && usedKeys.size === nodes.length
            && consumedLinkIndexes.size === links.length
            ? {trigger, filters, gate, actions}
            : null;
    }

    return {
        BACKEND_INTEGER_MAX,
        buildCron,
        minutesToSeconds,
        parseCron,
        parseFlow
    };
});
