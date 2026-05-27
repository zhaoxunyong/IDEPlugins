const MANUAL_ASSIGNEE_PICK_VALUE = '__manual_git_mr_assignee__'

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
    const configuredItems = dedupeKeepOrder(assignees || []).map(value => ({
        label: value,
        value
    }))

    configuredItems.push({
        label: '$(edit) 手动输入其他 assignee',
        description: '输入不在配置列表中的 GitLab 用户名',
        value: MANUAL_ASSIGNEE_PICK_VALUE
    })

    return configuredItems
}

function normalizeGitMrAssigneeSelection (raw) {
    const value = raw === undefined || raw === null ? '' : String(raw).trim()
    return value || null
}

function getMissingGitMrAssigneeMessage () {
    return '请选择 assignee，或手动填写其他 assignee 后再发起 Merge Request。'
}

module.exports = {
    MANUAL_ASSIGNEE_PICK_VALUE,
    buildGitMrAssigneeQuickPickItems,
    getMissingGitMrAssigneeMessage,
    normalizeGitMrAssigneeSelection,
    parseConfiguredGitMrAssignees
}
