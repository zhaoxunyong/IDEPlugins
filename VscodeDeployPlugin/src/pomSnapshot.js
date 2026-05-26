const fs = require('fs')
const path = require('path')

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
        return fs.readFileSync(filePath, 'utf8').includes('-SNAPSHOT')
    } catch (_) {
        return false
    }
}

module.exports = {
    pomXmlContainsSnapshot
}
