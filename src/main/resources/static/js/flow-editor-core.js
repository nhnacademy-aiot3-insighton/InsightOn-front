/** Flow 편집기의 그래프 해석과 시간 변환처럼 DOM에 의존하지 않는 규칙을 모은다. */
(function (root, factory) {
    const api = factory();
    if (typeof module === 'object' && module.exports) module.exports = api;
    root.FlowEditorCore = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
    'use strict';

    const BACKEND_INTEGER_MAX = 2147483647;
    const SECONDS_PER_MINUTE = 60;

    function minutesToSeconds(minutes) {
        const seconds = Math.round(minutes * SECONDS_PER_MINUTE);
        return Number.isSafeInteger(seconds) && seconds >= 0 && seconds <= BACKEND_INTEGER_MAX
            ? seconds
            : null;
    }

    function isAction(node) {
        return node && (node.nodeType === 'ALERT' || node.nodeType === 'ACTUATOR_CONTROL');
    }

    function findRangeCommandWidget(rules, actuatorType) {
        const commands = rules[actuatorType] || {};
        return Object.values(commands).find((widget) => widget.kind === 'RANGE') || null;
    }

    function combineEditorActions(actions, actuatorCommandRules) {
        if (actions.length === 1) return isAction(actions[0]) ? actions[0] : null;
        if (actions.length !== 2 || actions.some((node) => node.nodeType !== 'ACTUATOR_CONTROL')) {
            return null;
        }

        for (const primary of actions) {
            const supplemental = actions.find((candidate) => candidate !== primary);
            const primaryConfig = primary.configuration || {};
            const supplementalConfig = supplemental.configuration || {};
            const temperatureWidget = findRangeCommandWidget(actuatorCommandRules, primaryConfig.actuatorType);
            if (primaryConfig.command === 'power'
                && String(primaryConfig.commandValue) === 'ON'
                && temperatureWidget
                && supplementalConfig.actuatorType === primaryConfig.actuatorType
                && supplementalConfig.command === temperatureWidget.stateKey) {
                return {
                    ...primary,
                    supplementalTemperatureValue: supplementalConfig.commandValue
                };
            }
        }
        return null;
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
        let action = null;
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

            const combinedAction = combineEditorActions(targets, actuatorCommandRules);
            if (combinedAction) {
                targets.forEach((target) => usedKeys.add(target.clientNodeKey));
                action = combinedAction;
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

        return action
            && usedKeys.size === nodes.length
            && consumedLinkIndexes.size === links.length
            ? {trigger, filters, gate, action}
            : null;
    }

    return {
        BACKEND_INTEGER_MAX,
        minutesToSeconds,
        parseFlow
    };
});
