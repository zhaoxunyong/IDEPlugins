const fs = require('fs')
const path = require('path')

const XML_COMMENT_PATTERN = /<!--[\s\S]*?-->/g
const PROPERTY_REFERENCE_PATTERN = /\$\{([^}]+)\}/g

function pomXmlContainsSnapshot (rootPath) {
    if (!rootPath || !fs.existsSync(rootPath) || !fs.statSync(rootPath).isDirectory()) {
        return false
    }
    return scanDirectoryForSnapshot(rootPath)
}

function scanDirectoryForSnapshot (dirPath) {
    try {
        const entries = fs.readdirSync(dirPath, { withFileTypes: true })
        for (const entry of entries) {
            if (entry.name === '.git') {
                continue
            }
            const entryPath = path.join(dirPath, entry.name)
            if (entry.isFile() && entry.name === 'pom.xml' && fileContainsSnapshot(entryPath)) {
                return true
            }
            if (entry.isDirectory() && scanDirectoryForSnapshot(entryPath)) {
                return true
            }
        }
    } catch (_) {
        return false
    }
    return false
}

function fileContainsSnapshot (filePath) {
    try {
        return pomContentContainsSnapshot(fs.readFileSync(filePath, 'utf8'))
    } catch (_) {
        return false
    }
}

function pomContentContainsSnapshot (content) {
    const normalizedContent = stripXmlComments(content)
    const properties = extractPomProperties(normalizedContent)
    return blockVersionsContainSnapshot(normalizedContent, 'dependency', properties) ||
        blockVersionsContainSnapshot(normalizedContent, 'plugin', properties)
}

function stripXmlComments (content) {
    return String(content || '').replace(XML_COMMENT_PATTERN, '')
}

function extractPomProperties (content) {
    const properties = new Map()
    for (const block of extractTagBlocks(content, 'properties')) {
        const propertyPattern = /<([A-Za-z0-9_.-]+)>([\s\S]*?)<\/\1>/g
        let match
        while ((match = propertyPattern.exec(block)) !== null) {
            properties.set(match[1], normalizeXmlText(match[2]))
        }
    }
    return properties
}

function blockVersionsContainSnapshot (content, tagName, properties) {
    for (const block of extractTagBlocks(content, tagName)) {
        const version = extractFirstTagValue(block, 'version')
        if (version && versionContainsSnapshot(version, properties)) {
            return true
        }
    }
    return false
}

function extractTagBlocks (content, tagName) {
    const pattern = new RegExp(`<${tagName}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${tagName}>`, 'gi')
    const blocks = []
    let match
    while ((match = pattern.exec(content)) !== null) {
        blocks.push(match[1])
    }
    return blocks
}

function extractFirstTagValue (content, tagName) {
    const pattern = new RegExp(`<${tagName}(?:\\s[^>]*)?>([\\s\\S]*?)<\\/${tagName}>`, 'i')
    const match = pattern.exec(content)
    return match ? normalizeXmlText(match[1]) : ''
}

function normalizeXmlText (value) {
    return String(value || '').trim()
}

function versionContainsSnapshot (version, properties) {
    return resolvePropertyReferences(version, properties).includes('-SNAPSHOT')
}

function resolvePropertyReferences (value, properties, seen = new Set()) {
    const normalizedValue = normalizeXmlText(value)
    if (!normalizedValue) {
        return ''
    }
    return normalizedValue.replace(PROPERTY_REFERENCE_PATTERN, (fullMatch, propertyName) => {
        const normalizedPropertyName = String(propertyName || '').trim()
        if (!normalizedPropertyName || seen.has(normalizedPropertyName) || !properties.has(normalizedPropertyName)) {
            return fullMatch
        }
        const nextSeen = new Set(seen)
        nextSeen.add(normalizedPropertyName)
        return resolvePropertyReferences(properties.get(normalizedPropertyName), properties, nextSeen)
    })
}

module.exports = {
    pomXmlContainsSnapshot
}
