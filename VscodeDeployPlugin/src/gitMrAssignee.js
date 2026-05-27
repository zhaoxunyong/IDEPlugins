function splitConfiguredValues (raw) {
    return String(raw || '')
        .trim()
        .split(/\s+/)
        .map(item => item.trim())
        .filter(Boolean)
}

function dedupeKeepOrder (values) {
    const seen = new Set()
    const result = []
    values.forEach(value => {
        if (!seen.has(value)) {
            seen.add(value)
            result.push(value)
        }
    })
    return result
}

function parseConfiguredGitMrAssignees (raw) {
    const configured = splitConfiguredValues(raw)
    return dedupeKeepOrder(configured)
}

function buildGitMrAssigneeQuickPickItems (assignees) {
    return dedupeKeepOrder(assignees || []).map(value => ({
        label: value,
        value
    }))
}

function normalizeGitMrAssigneeSelection (raw) {
    const value = raw === undefined || raw === null ? '' : String(raw).trim()
    return value || null
}

function resolveGitMrAssigneeSelection (selectedItems, typedValue) {
    const selected = Array.isArray(selectedItems) && selectedItems.length > 0
        ? normalizeGitMrAssigneeSelection(selectedItems[0] && selectedItems[0].value)
        : null
    if (selected) {
        return selected
    }
    return normalizeGitMrAssigneeSelection(typedValue)
}

function getMissingGitMrAssigneeMessage () {
    return '请选择 assignee，或手动填写其他 assignee 后再发起 Merge Request。'
}

module.exports = {
    buildGitMrAssigneeQuickPickItems,
    getMissingGitMrAssigneeMessage,
    normalizeGitMrAssigneeSelection,
    parseConfiguredGitMrAssignees,
    resolveGitMrAssigneeSelection
}
