const DATED_RELEASE_OR_HOTFIX_TAG_RULE = /^(release|hotfix)\/[^/]+\/(\d+\.\d+\.\d+)-(\d{12})$/

function pickLatestDatedRemoteTag (sortedTagRefs) {
    const lines = Array.isArray(sortedTagRefs) ? sortedTagRefs : []
    for (const line of lines) {
        const tagName = String(line || '').split('|')[0].trim()
        if (!tagName) {
            continue
        }
        const matched = tagName.match(DATED_RELEASE_OR_HOTFIX_TAG_RULE)
        if (!matched) {
            continue
        }
        return {
            tagName,
            version: matched[2],
            timestamp: matched[3]
        }
    }
    return null
}

function extractDatedRemoteTagVersions (tagRefs) {
    return (Array.isArray(tagRefs) ? tagRefs : [])
        .map(tagRef => pickLatestDatedRemoteTag([tagRef]))
        .filter(Boolean)
        .map(tag => tag.version)
}

module.exports = {
    extractDatedRemoteTagVersions,
    pickLatestDatedRemoteTag
}
