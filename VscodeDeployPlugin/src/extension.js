const path = require('path')
const vscode = require('vscode')
const myPlugin = require('./myPlugin')
const tmp = require('tmp')
const fs = require('fs')

const util = require('util')
const exec = util.promisify(require('child_process').exec)
const { spawn } = require('child_process')

let mdTml = null
let myStatusBarItem = null
let myOutputChannel = null

const CONFIG_ROOT = 'zerofinanceGit'
const CONFIG_SCRIPT_URL = `${CONFIG_ROOT}.gitScriptsUrlPreference`
const CONFIG_CHECK_GIT_VERSION = `${CONFIG_ROOT}.checkGitVersion`
const CONFIG_DEBUG = `${CONFIG_ROOT}.debug`
const CONFIG_COMMIT_MESSAGE_MODEL = `${CONFIG_ROOT}.commitMessageModel`
const CONFIG_GROUP_NAME = `${CONFIG_ROOT}.groupName`
const CONFIG_GIT_BASH = `${CONFIG_ROOT}.gitBash`
const DEFAULT_SCRIPT_ROOT_URL = 'https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/git-flow'
const COMMAND_PREFIX = 'extension.'
const GITFLOW_GUIDELINE_URL = 'https://v04jaasnl45.feishu.cn/wiki/Vg5PwK2smiPxGLk7w4Gc7tZanjb'
const gitCheckFile = 'gitCheck.sh'
const tmpdir = tmp.tmpdir
const gitCheckPath = tmpdir + '/' + gitCheckFile

const gitFlowScriptByCommand = {
    'extension.GenerateCommitMessage': 'GenCommitMessage.sh',
    'extension.StartNewFeature': 'StartNewFeature.sh',
    'extension.FinishFeature': 'FinishFeature.sh',
    'extension.RebaseFeature': 'RebaseFeature.sh',
    'extension.MavenChange': 'MavenChange.sh',
    'extension.StartNewRelease': 'StartNewRelease.sh',
    'extension.FinishRelease': 'FinishRelease.sh',
    'extension.StartNewHotfix': 'StartNewHotfix.sh',
    'extension.FinishHotfix': 'FinishHotfix.sh'
}

function debugLog (message, payload) {
    const debugEnabled = vscode.workspace.getConfiguration().get(CONFIG_DEBUG)
    if (!debugEnabled) {
        return
    }
    if (payload !== undefined) {
        console.log(`[zerofinance-git][debug] ${message}`, payload)
    } else {
        console.log(`[zerofinance-git][debug] ${message}`)
    }
}

function normalizePath (path) {
    return path.replace(/\\/gm, '/')
}

function buildCdCommand (targetPath) {
    const normalizedPath = normalizePath(targetPath)
    if (process.platform === 'win32') {
        return `cd /d "${normalizedPath}"`
    }
    return `cd "${normalizedPath}"`
}

/** When debug is on, bash -x writes trace lines (starting with "+ ") to stderr; strip those and return the rest. */
function getRealStderr (stderr) {
    const debug = vscode.workspace.getConfiguration().get(CONFIG_DEBUG)
    const str = (stderr !== undefined && stderr !== '') ? String(stderr).trim() : ''
    if (!debug) return str
    return str.split('\n').filter(line => !/^\s*\+/.test(line)).join('\n').trim()
}

/** Build user-facing error message from exec() rejection or from our throw: support err.code, err.stderr, err.stdout, err.message. */
function buildExecErrorMessage (err) {
    const exitCode = err && err.code
    const stderrMsg = err && err.stderr ? getRealStderr(err.stderr) : ''
    const stdoutMsg = err && err.stdout ? err.stdout.toString().trim() : ''
    const baseMsg = stderrMsg || stdoutMsg || (err && err.message ? err.message : String(err))
    return exitCode !== undefined ? `${baseMsg} (exit code: ${exitCode})` : baseMsg
}

function buildErrorDetails (err) {
    if (!err) {
        return 'Unknown error'
    }
    const lines = []
    const addLine = (label, value) => {
        if (value !== undefined && value !== null && String(value).trim() !== '') {
            lines.push(`${label}: ${String(value).trim()}`)
        }
    }
    addLine('message', err.message || String(err))
    addLine('exitCode', err.code)
    addLine('stderr', err.stderr ? getRealStderr(err.stderr) : '')
    addLine('stdout', err.stdout)
    addLine('stack', err.stack)
    let cause = err.cause
    let depth = 1
    while (cause && depth <= 5) {
        const prefix = `cause${depth}`
        addLine(`${prefix}.message`, cause.message || String(cause))
        addLine(`${prefix}.stack`, cause.stack)
        cause = cause.cause
        depth += 1
    }
    return lines.join('\n')
}

async function showErrorWithCopy (summary, details) {
    const copyAction = '复制完整错误'
    const picked = await vscode.window.showErrorMessage(summary, copyAction)
    if (picked === copyAction) {
        await vscode.env.clipboard.writeText(details || summary)
    }
}

function getRootUrl () {
    let rootUrl = vscode.workspace.getConfiguration().get(CONFIG_SCRIPT_URL)
    if (!rootUrl) {
        rootUrl = DEFAULT_SCRIPT_ROOT_URL
    }
    return rootUrl.replace(/\/+$/, '')
}

function getValidGroups () {
    const pkg = require(path.join(__dirname, '..', 'package.json'))
    const groupEnum = (pkg && pkg.contributes && pkg.contributes.configuration && pkg.contributes.configuration.properties && pkg.contributes.configuration.properties[CONFIG_GROUP_NAME] && pkg.contributes.configuration.properties[CONFIG_GROUP_NAME].enum) || []
    return groupEnum.filter(g => g !== '')
}

async function ensureGroupNameConfigured () {
    const groupName = vscode.workspace.getConfiguration().get(CONFIG_GROUP_NAME)
    const validGroups = getValidGroups()
    if (validGroups.includes(groupName)) {
        return groupName
    }

    const openSettingsAction = 'Open Settings'
    const groupList = validGroups.join(' or ')
    const message = `Please configure "zerofinanceGit.groupName" to ${groupList} before running tasks.`
    const selectedAction = await vscode.window.showErrorMessage(message, openSettingsAction)
    if (selectedAction === openSettingsAction) {
        await vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_GROUP_NAME)
    }
    return null
}

async function askStartFeatureName (groupName) {
    const branchPrefix = `feature/${groupName}/`
    const featureRule = /^\d+-\S.*$/
    const fullFeatureName = await vscode.window.showInputBox({
        ignoreFocusOut: true,
        placeHolder: 'Please input feature name',
        prompt: 'Please input feature name after prefix, and start with number- (e.g. 001-login).',
        value: branchPrefix,
        validateInput: function (text) {
            const value = (text || '').trim()
            if (!value.startsWith(branchPrefix)) {
                return `Branch name must start with "${branchPrefix}".`
            }
            const featureName = value.slice(branchPrefix.length).trim()
            if (!featureName) {
                return 'Please input feature name after the prefix.'
            }
            if (!featureRule.test(featureName)) {
                return 'Feature name must start with number- (e.g. 001-login).'
            }
            return ''
        }
    })

    if (!fullFeatureName) {
        vscode.window.showErrorMessage('Please input feature name, task aborted.')
        return null
    }
    const normalizedFeatureName = fullFeatureName.trim()
    const featureName = normalizedFeatureName.slice(branchPrefix.length).trim()
    if (!normalizedFeatureName.startsWith(branchPrefix) || !featureName || !featureRule.test(featureName)) {
        vscode.window.showErrorMessage('Please input feature name after the prefix, task aborted.')
        return null
    }
    return `${branchPrefix}${featureName}`
}

async function getSuggestedReleaseVersion (rootPath, groupName) {
    const branchPrefix = `release/${groupName}/`
    const conflictPrefix = `hotfix/${groupName}/`
    const defaultBase = '1.0.0'
    try {
        const releaseBranches = await getReleaseBranches(rootPath, groupName, { includeLocal: false })
        const hotfixBranches = await getHotfixBranches(rootPath, groupName, { includeLocal: false })
        const releaseVersions = extractBranchVersions(releaseBranches, branchPrefix)
        const hotfixVersions = extractBranchVersions(hotfixBranches, conflictPrefix)
        const releaseVersionSet = new Set(releaseVersions)
        const rcVersions = releaseVersions.filter(v => {
            const p = parseReleaseVersionRC(v)
            return p && p.rc > 0
        })
        let suggested
        if (rcVersions.length > 0) {
            rcVersions.sort((a, b) => compareReleaseVersionDesc(b, a))
            const top = parseReleaseVersionRC(rcVersions[0])
            if (top) {
                suggested = `${top.base}-RC${top.rc + 1}`
            }
        }
        if (!suggested) {
            const allBases = [...releaseVersions, ...hotfixVersions]
            const maxBase = getMaxBaseVersionFromVersionStrings(allBases) || defaultBase
            suggested = `${maxBase}-RC1`
        }
        while (releaseVersionSet.has(suggested)) {
            const p = parseReleaseVersionRC(suggested)
            if (!p) break
            suggested = `${p.base}-RC${p.rc + 1}`
        }
        return suggested
    } catch (err) {
        debugLog('failed to resolve suggested release version', err && err.message ? err.message : String(err))
    }
    return `${defaultBase}-RC1`
}

async function getLatestRemoteReleaseVersion (rootPath, groupName) {
    const releasePrefix = `release/${groupName}/`
    const releaseBranches = await getReleaseBranches(rootPath, groupName, { includeLocal: false })
    for (const branchName of releaseBranches) {
        if (!branchName.startsWith(releasePrefix)) {
            continue
        }
        const version = branchName.slice(releasePrefix.length)
        if (parseSemverVersion(version)) {
            return version
        }
    }
    return null
}

async function getSuggestedHotfixVersion (rootPath, groupName) {
    return getSuggestedReleaseOrHotfixVersion(rootPath, groupName, 'hotfix')
}

async function getSuggestedReleaseOrHotfixVersion (rootPath, groupName, branchType) {
    const defaultVersion = '1.0.0'
    try {
        const latestReleaseVersion = await getLatestRemoteReleaseVersion(rootPath, groupName)
        const latestHotfixVersion = await getLatestRemoteHotfixVersion(rootPath, groupName)
        const latestTagVersion = await getLatestRemoteTagVersion(rootPath)
        const candidates = [latestReleaseVersion, latestHotfixVersion, latestTagVersion].filter(Boolean)
        if (candidates.length > 0) {
            candidates.sort((left, right) => compareSemverVersionDesc(left, right))
            const [major, minor, patch] = candidates[0].split('.').map(item => parseInt(item, 10))
            return `${major}.${minor}.${patch + 1}`
        }
    } catch (err) {
        debugLog(`failed to resolve latest ${branchType} version`, err && err.message ? err.message : String(err))
    }
    return defaultVersion
}

async function getLatestRemoteHotfixVersion (rootPath, groupName) {
    const hotfixPrefix = `hotfix/${groupName}/`
    const hotfixBranches = await getHotfixBranches(rootPath, groupName, { includeLocal: false })
    for (const branchName of hotfixBranches) {
        if (!branchName.startsWith(hotfixPrefix)) {
            continue
        }
        const version = branchName.slice(hotfixPrefix.length)
        if (parseSemverVersion(version)) {
            return version
        }
    }
    return null
}

async function getLatestRemoteTagVersion (rootPath) {
    const semverTagRule = /^v?(\d+)\.(\d+)\.(\d+)$/
    const cmd = `${buildCdCommand(rootPath)} && git ls-remote --tags --refs origin`
    const { stdout } = await exec(cmd, { maxBuffer: 1024 * 1024 * 10 })
    const versions = []
    stdout.split(/\r?\n/).forEach(line => {
        const raw = (line || '').trim()
        if (!raw) {
            return
        }
        const segments = raw.split(/\s+/)
        const refName = segments.length > 1 ? segments[1] : ''
        if (!refName.startsWith('refs/tags/')) {
            return
        }
        const tagName = refName.slice('refs/tags/'.length).trim()
        const matched = tagName.match(semverTagRule)
        if (!matched) {
            return
        }
        versions.push({
            value: `${parseInt(matched[1], 10)}.${parseInt(matched[2], 10)}.${parseInt(matched[3], 10)}`,
            parts: matched.slice(1).map(item => parseInt(item, 10))
        })
    })

    if (versions.length === 0) {
        return null
    }

    versions.sort((left, right) => compareSemverPartsDesc(left.parts, right.parts))
    return versions[0].value
}

async function askStartReleaseName (rootPath, groupName) {
    const branchPrefix = `release/${groupName}/`
    const conflictPrefix = `hotfix/${groupName}/`
    const releaseVersionRule = /^\d+\.\d+\.\d+-RC\d+$/
    const suggestedVersion = await getSuggestedReleaseVersion(rootPath, groupName)
    const releaseBranches = await getReleaseBranches(rootPath, groupName)
    const hotfixBranches = await getHotfixBranches(rootPath, groupName)
    const releaseVersions = new Set(extractBranchVersions(releaseBranches, branchPrefix))
    const hotfixVersions = new Set(extractBranchVersions(hotfixBranches, conflictPrefix))
    const fullReleaseName = await vscode.window.showInputBox({
        ignoreFocusOut: true,
        placeHolder: 'Please input release name',
        prompt: 'Please input release version after prefix (e.g. 1.0.0-RC1).',
        value: `${branchPrefix}${suggestedVersion}`,
        validateInput: function (text) {
            const value = (text || '').trim()
            if (!value.startsWith(branchPrefix)) {
                return `Branch name must start with "${branchPrefix}".`
            }
            const releaseVersion = value.slice(branchPrefix.length).trim()
            if (!releaseVersion) {
                return 'Please input release version after the prefix.'
            }
            if (!releaseVersionRule.test(releaseVersion)) {
                return 'Release version must follow format X.Y.Z-RCN (e.g. 1.0.0-RC1).'
            }
            if (releaseVersions.has(releaseVersion)) {
                return `Release version already exists: ${releaseVersion}`
            }
            if (hotfixVersions.has(releaseVersion)) {
                return `Version conflict: hotfix/${groupName}/${releaseVersion} already exists`
            }
            return ''
        }
    })

    if (!fullReleaseName) {
        vscode.window.showErrorMessage('Please input release name, task aborted.')
        return null
    }
    const normalizedReleaseName = fullReleaseName.trim()
    const releaseVersion = normalizedReleaseName.slice(branchPrefix.length).trim()
    if (!normalizedReleaseName.startsWith(branchPrefix) || !releaseVersion || !releaseVersionRule.test(releaseVersion)) {
        vscode.window.showErrorMessage('Please input valid release name (X.Y.Z-RCN) after the prefix, task aborted.')
        return null
    }
    return `${branchPrefix}${releaseVersion}`
}

async function askStartHotfixName (rootPath, groupName) {
    const branchPrefix = `hotfix/${groupName}/`
    const conflictPrefix = `release/${groupName}/`
    const semverRule = /^\d+\.\d+\.\d+$/
    const suggestedVersion = await getSuggestedHotfixVersion(rootPath, groupName)
    const hotfixBranches = await getHotfixBranches(rootPath, groupName)
    const releaseBranches = await getReleaseBranches(rootPath, groupName)
    const hotfixVersions = new Set(extractBranchVersions(hotfixBranches, branchPrefix))
    const releaseVersions = new Set(extractBranchVersions(releaseBranches, conflictPrefix))
    const fullHotfixName = await vscode.window.showInputBox({
        ignoreFocusOut: true,
        placeHolder: 'Please input hotfix name',
        prompt: 'Please input hotfix version after prefix (e.g. 1.0.0).',
        value: `${branchPrefix}${suggestedVersion}`,
        validateInput: function (text) {
            const value = (text || '').trim()
            if (!value.startsWith(branchPrefix)) {
                return `Branch name must start with "${branchPrefix}".`
            }
            const hotfixVersion = value.slice(branchPrefix.length).trim()
            if (!hotfixVersion) {
                return 'Please input hotfix version after the prefix.'
            }
            if (!semverRule.test(hotfixVersion)) {
                return 'Hotfix version must follow SemVer format (e.g. 1.0.0).'
            }
            if (hotfixVersions.has(hotfixVersion)) {
                return `Hotfix version already exists: ${hotfixVersion}`
            }
            if (releaseVersions.has(hotfixVersion)) {
                return `Version conflict: release/${groupName}/${hotfixVersion} already exists`
            }
            return ''
        }
    })

    if (!fullHotfixName) {
        vscode.window.showErrorMessage('Please input hotfix name, task aborted.')
        return null
    }
    const normalizedHotfixName = fullHotfixName.trim()
    const hotfixVersion = normalizedHotfixName.slice(branchPrefix.length).trim()
    if (!normalizedHotfixName.startsWith(branchPrefix) || !hotfixVersion || !semverRule.test(hotfixVersion)) {
        vscode.window.showErrorMessage('Please input valid hotfix name after the prefix, task aborted.')
        return null
    }
    return `${branchPrefix}${hotfixVersion}`
}

async function confirmFinishFeature (groupName) {
    return showModalYesNoDialog(`是否已在gitlab中MR到develop-${groupName}，并完成了Merge操作？继续流程只会删除本地的feature分支。`)
}

async function confirmFinishFeatureForRelease (groupName) {
    return showModalConfirmDialog('是否已执行Finish Feature操作？')
}

async function confirmOpsReleaseDone () {
    return showModalYesNoDialog('运维是否已完成上线？')
}

async function confirmMaintainerPermission () {
    return showModalYesNoDialog('只有Maintainer角色才有权限操作，请确认你对该项目是否有Maintainer权限？')
}

async function showModalConfirmDialog (message) {
    const confirmAction = 'OK'
    const selectedAction = await vscode.window.showWarningMessage(message, { modal: true }, confirmAction)
    return selectedAction === confirmAction
}

async function showModalYesNoDialog (message) {
    const yesAction = { title: 'Yes' }
    const noAction = { title: 'No', isCloseAffordance: true }
    const selectedAction = await vscode.window.showWarningMessage(message, { modal: true }, yesAction, noAction)
    return !!selectedAction && selectedAction.title === yesAction.title
}

async function confirmRunScript (commandId, rootPath, scriptPath, scriptArgs) {
    const scriptName = path.basename(scriptPath)
    const argsText = Array.isArray(scriptArgs) && scriptArgs.length > 0 ? scriptArgs.join(' ') : '(无)'
    const messageLines = [
        '即将在终端中执行以下脚本：',
        '',
        `命令：${commandId.replace(COMMAND_PREFIX, '')}`,
        `工作目录：${normalizePath(rootPath)}`,
        `脚本：${scriptName}`,
        `参数：${argsText}`,
        '',
        '请确认已经理解脚本功能与影响，是否继续执行？'
    ]
    const yesAction = { title: 'Yes' }
    const noAction = { title: 'No', isCloseAffordance: true }
    const selectedAction = await vscode.window.showWarningMessage(messageLines.join('\n'), { modal: true }, yesAction, noAction)
    return !!selectedAction && selectedAction.title === yesAction.title
}

async function getReleaseBranches (rootPath, groupName, options = {}) {
    const { includeLocal = true } = options
    const releasePrefix = `release/${groupName}/`
    const refs = includeLocal
        ? `refs/heads/${releasePrefix}* refs/remotes/origin/${releasePrefix}*`
        : `refs/remotes/origin/${releasePrefix}*`
    const cmd = `${buildCdCommand(rootPath)} && git fetch origin --prune && git for-each-ref --sort=-committerdate --format="%(refname:short)" ${refs}`
    const { stdout } = await exec(cmd, { maxBuffer: 1024 * 1024 * 10 })
    const branchSet = new Set()
    stdout.split(/\r?\n/).forEach(line => {
        const raw = (line || '').trim()
        if (!raw) {
            return
        }
        const normalized = raw.startsWith('origin/') ? raw.slice('origin/'.length) : raw
        if (normalized.startsWith(releasePrefix)) {
            branchSet.add(normalized)
        }
    })
    const branches = Array.from(branchSet)
    branches.sort((left, right) => compareReleaseBranchVersionDesc(left, right, releasePrefix))
    return branches
}

async function getHotfixBranches (rootPath, groupName, options = {}) {
    const { includeLocal = true } = options
    const hotfixPrefix = `hotfix/${groupName}/`
    const refs = includeLocal
        ? `refs/heads/${hotfixPrefix}* refs/remotes/origin/${hotfixPrefix}*`
        : `refs/remotes/origin/${hotfixPrefix}*`
    const cmd = `${buildCdCommand(rootPath)} && git fetch origin --prune && git for-each-ref --sort=-committerdate --format="%(refname:short)" ${refs}`
    const { stdout } = await exec(cmd, { maxBuffer: 1024 * 1024 * 10 })
    const branchSet = new Set()
    stdout.split(/\r?\n/).forEach(line => {
        const raw = (line || '').trim()
        if (!raw) {
            return
        }
        const normalized = raw.startsWith('origin/') ? raw.slice('origin/'.length) : raw
        if (normalized.startsWith(hotfixPrefix)) {
            branchSet.add(normalized)
        }
    })
    const branches = Array.from(branchSet)
    branches.sort((left, right) => compareReleaseBranchVersionDesc(left, right, hotfixPrefix))
    return branches
}

async function getCurrentBranch (rootPath) {
    const cmd = `${buildCdCommand(rootPath)} && git rev-parse --abbrev-ref HEAD`
    const { stdout } = await exec(cmd, { maxBuffer: 1024 * 1024 })
    return (stdout || '').trim()
}

async function getLocalFeatureBranches (rootPath, groupName) {
    const featurePrefix = `feature/${groupName}/`
    const refs = `refs/heads/${featurePrefix}*`
    const cmd = `${buildCdCommand(rootPath)} && git for-each-ref --format="%(refname:short)" ${refs}`
    const { stdout } = await exec(cmd, { maxBuffer: 1024 * 1024 * 10 })
    const branches = stdout
        .split(/\r?\n/)
        .map(line => (line || '').trim())
        .filter(Boolean)
        .filter(branchName => branchName.startsWith(featurePrefix))
    branches.sort((left, right) => compareFeatureBranchByNumericPrefixDesc(left, right, featurePrefix))
    return branches
}

function compareReleaseBranchVersionDesc (leftBranch, rightBranch, releasePrefix) {
    const leftVersion = leftBranch.startsWith(releasePrefix) ? leftBranch.slice(releasePrefix.length) : leftBranch
    const rightVersion = rightBranch.startsWith(releasePrefix) ? rightBranch.slice(releasePrefix.length) : rightBranch
    return compareReleaseVersionDesc(rightVersion, leftVersion)
}

function extractBranchVersions (branches, branchPrefix) {
    return branches
        .filter(branchName => branchName.startsWith(branchPrefix))
        .map(branchName => branchName.slice(branchPrefix.length).trim())
        .filter(Boolean)
}

function compareSemverPartsDesc (leftParts, rightParts) {
    for (let index = 0; index < 3; index += 1) {
        if (leftParts[index] !== rightParts[index]) {
            return rightParts[index] - leftParts[index]
        }
    }
    return 0
}

function parseSemverVersion (versionText) {
    const semverRule = /^(\d+)\.(\d+)\.(\d+)$/
    const matched = String(versionText || '').match(semverRule)
    if (!matched) {
        return null
    }
    return matched.slice(1).map(item => parseInt(item, 10))
}

/** Release 版本格式：X.Y.Z 或 X.Y.Z-RCN。返回 { base, baseParts, rc } 或 null。 */
function parseReleaseVersionRC (versionText) {
    const s = String(versionText || '').trim()
    const rcRule = /^(\d+)\.(\d+)\.(\d+)-RC(\d+)$/
    const plainRule = /^(\d+)\.(\d+)\.(\d+)$/
    let matched = s.match(rcRule)
    if (matched) {
        const base = `${matched[1]}.${matched[2]}.${matched[3]}`
        const baseParts = matched.slice(1, 4).map(item => parseInt(item, 10))
        return { base, baseParts, rc: parseInt(matched[4], 10) }
    }
    matched = s.match(plainRule)
    if (matched) {
        const base = `${matched[1]}.${matched[2]}.${matched[3]}`
        const baseParts = matched.slice(1, 4).map(item => parseInt(item, 10))
        return { base, baseParts, rc: 0 }
    }
    return null
}

/** 从一组 release/hotfix 版本字符串中取最大 base 版本 (X.Y.Z)。 */
function getMaxBaseVersionFromVersionStrings (versionStrings) {
    let maxBase = null
    let maxParts = null
    for (const v of versionStrings) {
        const parsed = parseReleaseVersionRC(v)
        if (!parsed) continue
        if (!maxParts || compareSemverPartsDesc(parsed.baseParts, maxParts) < 0) {
            maxBase = parsed.base
            maxParts = parsed.baseParts
        }
    }
    return maxBase
}

/** 比较 release 版本 (X.Y.Z 或 X.Y.Z-RCN)，降序：先 base 再 rc。 */
function compareReleaseVersionDesc (leftVersion, rightVersion) {
    const left = parseReleaseVersionRC(leftVersion)
    const right = parseReleaseVersionRC(rightVersion)
    if (!left && !right) return String(rightVersion || '').localeCompare(String(leftVersion || ''))
    if (left && !right) return -1
    if (!left && right) return 1
    const baseCmp = compareSemverPartsDesc(left.baseParts, right.baseParts)
    if (baseCmp !== 0) return baseCmp
    return right.rc - left.rc
}

function compareSemverVersionDesc (leftVersion, rightVersion) {
    const leftParts = parseSemverVersion(leftVersion)
    const rightParts = parseSemverVersion(rightVersion)
    if (!leftParts && !rightParts) {
        return String(rightVersion || '').localeCompare(String(leftVersion || ''))
    }
    if (leftParts && !rightParts) {
        return -1
    }
    if (!leftParts && rightParts) {
        return 1
    }
    return compareSemverPartsDesc(leftParts, rightParts)
}

function isMavenProject (rootPath) {
    const pomPath = path.join(rootPath, 'pom.xml')
    if (!fs.existsSync(pomPath)) {
        return false
    }
    try {
        const pomContent = fs.readFileSync(pomPath, 'utf8')
        return /<project[\s>]/.test(pomContent)
    } catch (err) {
        debugLog('read pom.xml failed', err && err.message ? err.message : String(err))
        return false
    }
}

/** 从 gitRoot 往下递归收集所有含有效 pom.xml 的 Maven 项目根目录。 */
function collectMavenRootsUnder (dirPath, result) {
    if (!dirPath || !fs.existsSync(dirPath) || !fs.statSync(dirPath).isDirectory()) {
        return
    }
    if (isMavenProject(dirPath)) {
        result.push(normalizePath(dirPath))
    }
    try {
        const names = fs.readdirSync(dirPath)
        for (const name of names) {
            if (name === '.git') continue
            const childPath = path.join(dirPath, name)
            if (fs.statSync(childPath).isDirectory()) {
                collectMavenRootsUnder(childPath, result)
            }
        }
    } catch (err) {
        debugLog('collectMavenRootsUnder readdir failed', err && err.message ? err.message : String(err))
    }
}

/**
 * 从 git 根往下找包含 pathContained 的 Maven 项目根，返回路径最短的（最外层）。
 * @param {string} gitRootPath - git 根目录
 * @param {string} pathContained - 当前选中的路径（工作区目录或文件所在目录）
 * @returns {string|null} 最外层的 Maven 项目根路径，未找到返回 null
 */
function getMavenProjectRootPath (gitRootPath, pathContained) {
    const roots = []
    collectMavenRootsUnder(gitRootPath, roots)
    const normalizedContained = normalizePath(path.resolve(pathContained))
    let bestRoot = null
    let bestPathLength = Number.MAX_SAFE_INTEGER
    const sep = '/'
    for (const root of roots) {
        const normalizedRoot = normalizePath(path.resolve(root))
        const underThisRoot = normalizedContained === normalizedRoot ||
            normalizedContained.startsWith(normalizedRoot + sep)
        if (underThisRoot && normalizedRoot.length < bestPathLength) {
            bestRoot = normalizedRoot
            bestPathLength = normalizedRoot.length
        }
    }
    return bestRoot
}

function getMavenVersionFromPom (rootPath) {
    const pomPath = path.join(rootPath, 'pom.xml')
    if (!fs.existsSync(pomPath)) {
        return null
    }

    try {
        const pomContent = fs.readFileSync(pomPath, 'utf8')
        const contentWithoutComments = pomContent.replace(/<!--[\s\S]*?-->/g, '')
        const contentWithoutParent = contentWithoutComments.replace(/<parent>[\s\S]*?<\/parent>/g, '')
        const matched = contentWithoutParent.match(/<version>\s*([^<\s]+)\s*<\/version>/)
        return matched ? matched[1].trim() : null
    } catch (err) {
        debugLog('parse pom version failed', err && err.message ? err.message : String(err))
        return null
    }
}

function buildSuggestedMavenVersion (currentVersion, changeType) {
    const baseVersion = String(currentVersion || '')
        .trim()
        .replace(/-SNAPSHOT$/i, '')

    const semverParts = parseSemverVersion(baseVersion)
    const nextVersion = semverParts
        ? `${semverParts[0]}.${semverParts[1]}.${semverParts[2] + 1}`
        : '1.0.1'

    return changeType === 'snapshot' ? `${nextVersion}-SNAPSHOT` : nextVersion
}

function isValidMavenVersionText (versionText) {
    const mavenVersionRule = /^\d+\.\d+\.\d+(?:-SNAPSHOT)?$/
    return mavenVersionRule.test(String(versionText || '').trim())
}

async function askMavenChangeType () {
    const options = [
        {
            label: 'release',
            description: '默认选项',
            value: 'release'
        },
        {
            label: 'snapshot',
            value: 'snapshot'
        }
    ]

    const selected = await vscode.window.showQuickPick(options, {
        ignoreFocusOut: true,
        canPickMany: false,
        title: 'Select Maven change type',
        placeHolder: 'Choose snapshot or release'
    })

    if (!selected) {
        vscode.window.showErrorMessage('Please select maven change type, task aborted.')
        return null
    }
    return selected.value
}

async function askMavenChangeVersion (rootPath, changeType) {
    const currentPomVersion = getMavenVersionFromPom(rootPath)
    const suggestedVersion = buildSuggestedMavenVersion(currentPomVersion, changeType)
    const inputVersion = await vscode.window.showInputBox({
        ignoreFocusOut: true,
        placeHolder: 'Please input maven version',
        prompt: `Please input maven version (${changeType}).`,
        value: suggestedVersion,
        validateInput: function (text) {
            const value = String(text || '').trim()
            if (!value) {
                return 'Please input maven version.'
            }
            if (!isValidMavenVersionText(value)) {
                return 'Maven version must be x.y.z or x.y.z-SNAPSHOT, and x/y/z must be numbers.'
            }
            if (changeType === 'release' && /-SNAPSHOT$/i.test(value)) {
                return 'Release version cannot end with -SNAPSHOT.'
            }
            if (changeType === 'snapshot' && !/-SNAPSHOT$/i.test(value)) {
                return 'Snapshot version must end with -SNAPSHOT.'
            }
            return ''
        }
    })

    if (!inputVersion) {
        vscode.window.showErrorMessage('Please input maven version, task aborted.')
        return null
    }

    const normalizedVersion = String(inputVersion).trim()
    if (!isValidMavenVersionText(normalizedVersion)) {
        vscode.window.showErrorMessage('Invalid maven version format, task aborted.')
        return null
    }
    if (changeType === 'release' && /-SNAPSHOT$/i.test(normalizedVersion)) {
        vscode.window.showErrorMessage('Release version cannot end with -SNAPSHOT, task aborted.')
        return null
    }
    if (changeType === 'snapshot' && !/-SNAPSHOT$/i.test(normalizedVersion)) {
        vscode.window.showErrorMessage('Snapshot version must end with -SNAPSHOT, task aborted.')
        return null
    }
    return normalizedVersion
}

async function askFinishReleaseBranch (rootPath, groupName) {
    const releaseBranches = await getReleaseBranches(rootPath, groupName)
    if (releaseBranches.length === 0) {
        vscode.window.showErrorMessage(`No remote release branch found for group "${groupName}".`)
        return null
    }
    const selectedBranch = await vscode.window.showQuickPick(releaseBranches, {
        ignoreFocusOut: true,
        canPickMany: false,
        title: 'Select release branch to finish',
        placeHolder: 'Latest release branches are listed first'
    })
    if (!selectedBranch) {
        vscode.window.showErrorMessage('Please select a release branch, task aborted.')
        return null
    }
    return selectedBranch
}

async function askFinishHotfixBranch (rootPath, groupName) {
    const hotfixBranches = await getHotfixBranches(rootPath, groupName)
    if (hotfixBranches.length === 0) {
        vscode.window.showErrorMessage(`No remote hotfix branch found for group "${groupName}".`)
        return null
    }
    const selectedBranch = await vscode.window.showQuickPick(hotfixBranches, {
        ignoreFocusOut: true,
        canPickMany: false,
        title: 'Select hotfix branch to finish',
        placeHolder: 'Latest hotfix branches are listed first'
    })
    if (!selectedBranch) {
        vscode.window.showErrorMessage('Please select a hotfix branch, task aborted.')
        return null
    }
    return selectedBranch
}

function compareFeatureBranchByNumericPrefixDesc (leftBranch, rightBranch, featurePrefix) {
    const leftFeatureName = leftBranch.startsWith(featurePrefix) ? leftBranch.slice(featurePrefix.length) : leftBranch
    const rightFeatureName = rightBranch.startsWith(featurePrefix) ? rightBranch.slice(featurePrefix.length) : rightBranch
    const numberPrefixRule = /^(\d+)-/
    const leftMatch = leftFeatureName.match(numberPrefixRule)
    const rightMatch = rightFeatureName.match(numberPrefixRule)

    if (leftMatch && rightMatch) {
        const leftNumber = parseInt(leftMatch[1], 10)
        const rightNumber = parseInt(rightMatch[1], 10)
        if (leftNumber !== rightNumber) {
            return rightNumber - leftNumber
        }
    }

    if (leftMatch && !rightMatch) {
        return -1
    }
    if (!leftMatch && rightMatch) {
        return 1
    }
    return rightFeatureName.localeCompare(leftFeatureName)
}

async function askFinishFeatureBranch (rootPath, groupName) {
    const localFeatureBranches = await getLocalFeatureBranches(rootPath, groupName)
    if (localFeatureBranches.length === 0) {
        vscode.window.showErrorMessage(`No local feature branch found for group "${groupName}".`)
        return null
    }
    const selectedBranch = await vscode.window.showQuickPick(localFeatureBranches, {
        ignoreFocusOut: true,
        canPickMany: false,
        title: 'Select local feature branch to finish',
        placeHolder: 'Branches are sorted by leading number in descending order'
    })
    if (!selectedBranch) {
        vscode.window.showErrorMessage('Please select a feature branch, task aborted.')
        return null
    }
    return selectedBranch
}

function parseRemainingReleaseVersions (outputText) {
    const output = String(outputText || '')
    const markerMatches = [...output.matchAll(/REMAINING_RELEASES:\s*([^\r\n]*)/g)]
    if (markerMatches.length > 0) {
        const lastMatchedText = (markerMatches[markerMatches.length - 1][1] || '').trim()
        if (!lastMatchedText) {
            return []
        }

        // 1）优先用正则直接提取符合模式的分支名（兼容被错误用 `/` 串在一起的情况）
        const branchMatches = [...lastMatchedText.matchAll(/\b(?:release|hotfix)\/[^\s/]+\/[^\s/]+/g)].map(m => m[0])
        if (branchMatches.length > 0) {
            return branchMatches
        }

        // 2）否则按空格分隔（正常情况）
        return lastMatchedText
            .split(/\s+/)
            .map(item => item.trim())
            .filter(Boolean)
    }

    const fallbackMatches = [...output.matchAll(/Remaining release branches:\s*([^\r\n]*)/g)]
    if (fallbackMatches.length > 0) {
        const lastBranchesText = (fallbackMatches[fallbackMatches.length - 1][1] || '').trim()
        if (!lastBranchesText) {
            return []
        }
        return lastBranchesText
            .split(/\s+/)
            .map(item => item.trim())
            .filter(Boolean)
            .map(item => item.replace(/^origin\//, ''))
            .map(item => item.split('/').pop())
            .filter(Boolean)
    }

    return []
}

async function runFinishReleaseScript (rootPath, scriptPath, scriptArgs) {
    const cmd = buildBashCommand(rootPath, scriptPath, scriptArgs)
    const statusBarItem = getOrCreateStatusBarItem()
    const outputChannel = getOrCreateOutputChannel()
    outputChannel.clear()
    outputChannel.appendLine(`$ ${cmd}`)
    outputChannel.show(true)
    statusBarItem.text = 'Finishing release, it may take a while...'
    statusBarItem.color = 'red'
    statusBarItem.show()
    debugLog('execute finish release command', cmd)
    try {
        const { stdout, stderr } = await runScriptBySpawn(rootPath, scriptPath, scriptArgs, outputChannel)
        debugLog('finish release stdout', stdout)
        if (stderr) {
            debugLog('finish release stderr', stderr)
        }
        // Use real stderr (trace stripped when debug) for parsing so bash -x output is not included
        const realStderr = getRealStderr(stderr)
        const combinedOutput = `${String(stdout || '')}\n${String(realStderr || '')}`
        const remainingVersions = parseRemainingReleaseVersions(combinedOutput)
        if (remainingVersions.length > 0) {
            await showModalConfirmDialog(`目前有进行中的${remainingVersions.join('、')}分支，请项目经理评估是否需要重新测试相关的功能点？`)
        }
    } catch (err) {
        const stdout = err && err.stdout ? String(err.stdout) : ''
        const stderr = err && err.stderr ? String(err.stderr) : ''
        if (stdout) {
            outputChannel.appendLine(stdout)
        }
        if (stderr) {
            outputChannel.appendLine(stderr)
        }
        const message = buildExecErrorMessage(err)
        throw new Error(message)
    } finally {
        statusBarItem.hide()
    }
}

function runScriptBySpawn (rootPath, scriptPath, scriptArgs, outputChannel) {
    const normalizedRootPath = normalizePath(rootPath)
    const normalizedScriptPath = normalizePath(scriptPath)
    const bashPath = getBashPath()
    const debug = vscode.workspace.getConfiguration().get(CONFIG_DEBUG)
    const spawnArgs = []
    if (debug) {
        spawnArgs.push('-x')
    }
    spawnArgs.push(normalizedScriptPath, ...(Array.isArray(scriptArgs) ? scriptArgs : []))

    return new Promise((resolve, reject) => {
        const child = spawn(bashPath, spawnArgs, {
            cwd: normalizedRootPath,
            windowsHide: true
        })

        let stdout = ''
        let stderr = ''

        child.stdout.on('data', (chunk) => {
            const text = chunk.toString()
            stdout += text
            outputChannel.append(text)
        })

        child.stderr.on('data', (chunk) => {
            const text = chunk.toString()
            stderr += text
            outputChannel.append(text)
        })

        child.on('error', (err) => {
            reject(err)
        })

        child.on('close', (code) => {
            if (code === 0) {
                resolve({ stdout, stderr, code })
                return
            }
            const err = new Error(`Script exited with code ${code}`)
            err.code = code
            err.stdout = stdout
            err.stderr = stderr
            reject(err)
        })
    })
}

function clearCacheFile () {
    const allCacheFiles = [gitCheckPath]
    for (let command in gitFlowScriptByCommand) {
        allCacheFiles.push(tmpdir + '/' + gitFlowScriptByCommand[command])
    }

    allCacheFiles.forEach(file => {
        try {
            fs.unlinkSync(file)
        } catch (error) { }
    })
    debugLog('cache cleaned', allCacheFiles)
}

async function resolveScriptPath (rootPath, scriptName) {
    rootPath = normalizePath(rootPath)
    let scriptPath = normalizePath(tmpdir + '/' + scriptName)
    let projectScriptPath = rootPath + '/' + scriptName

    if (fs.existsSync(projectScriptPath)) {
        debugLog('use project local script', projectScriptPath)
        return projectScriptPath
    }

    let scriptUrl = getRootUrl() + '/' + scriptName
    debugLog('download script from remote', scriptUrl)
    try {
        await myPlugin.downloadScripts(scriptUrl, scriptPath)
        debugLog('script downloaded to temp path', scriptPath)
        return scriptPath
    } catch (err) {
        const msg = `Can't found ${scriptUrl}: ${err}`
        await showErrorWithCopy(msg, buildErrorDetails(err))
        throw new Error(err && err.message ? err.message : String(err))
    }
}

function getOrCreateStatusBarItem () {
    if (myStatusBarItem == null) {
        myStatusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left)
    }
    return myStatusBarItem
}

function getOrCreateOutputChannel () {
    if (myOutputChannel == null) {
        myOutputChannel = vscode.window.createOutputChannel('zerofinance-git-flow')
    }
    return myOutputChannel
}

function buildBashCommand (rootPath, scriptPath, scriptArgs = []) {
    const normalizedRootPath = normalizePath(rootPath)
    const normalizedScriptPath = normalizePath(scriptPath)
    const argsText = buildBashArgs(scriptArgs)
    const debug = vscode.workspace.getConfiguration().get(CONFIG_DEBUG)
    const bashPath = getBashPath()
    const traceOpt = debug ? ' -x' : ''
    if (process.platform === 'win32') {
        const scriptCommand = argsText ? `"${normalizedScriptPath}" ${argsText}` : `"${normalizedScriptPath}"`
        return `"${bashPath}"${traceOpt} -c "cd ${quoteBashArg(normalizedRootPath)} && ${scriptCommand}"`
    }
    return argsText
        ? `cd "${normalizedRootPath}" && "${bashPath}"${traceOpt} "${normalizedScriptPath}" ${argsText}`
        : `cd "${normalizedRootPath}" && "${bashPath}"${traceOpt} "${normalizedScriptPath}"`
}

function parseGitVersion (stdout) {
    // Examples:
    // - git version 2.29.2
    // - git version 2.29.2.windows.2
    const match = stdout.match(/(\d+)\.(\d+)\.(\d+)/)
    if (!match) {
        return null
    }
    return {
        major: parseInt(match[1], 10),
        minor: parseInt(match[2], 10),
        patch: parseInt(match[3], 10)
    }
}

/**
 * @param {vscode.ExtensionContext} context
 */
function activate (context) {
    debugLog('extension activated')

    context.subscriptions.push(
        vscode.commands.registerCommand('extension.GitFlowGuideline', async () => {
            try {
                debugLog('open GitFlow guideline', GITFLOW_GUIDELINE_URL)
                const opened = await vscode.env.openExternal(vscode.Uri.parse(GITFLOW_GUIDELINE_URL))
                if (!opened) {
                    await showErrorWithCopy('无法打开 GitFlow Guideline 链接。', GITFLOW_GUIDELINE_URL)
                }
            } catch (err) {
                const msg = err && err.message ? err.message : String(err)
                await showErrorWithCopy(`打开链接失败：${msg}`, buildErrorDetails(err))
            }
        })
    )

    Object.keys(gitFlowScriptByCommand).forEach(commandId => {
        context.subscriptions.push(
            vscode.commands.registerCommand(commandId, async (resourceUri) => {
                clearCacheFile()
                try {
                    debugLog('command triggered', { commandId, resourceUri: resourceUri ? resourceUri.fsPath : null })
                    const executionResult = await executeGitFlowCommand(commandId, resourceUri)
                    if (executionResult.executed) {
                        vscode.window.showInformationMessage(getCommandSuccessMessage(commandId, executionResult.groupName))
                    }
                } catch (err) {
                    const msg = err && err.message ? err.message : String(err)
                    debugLog('command failed', { commandId, msg })
                    await showErrorWithCopy(msg, buildErrorDetails(err))
                    if (commandId === 'extension.FinishRelease') {
                        await showErrorWithCopy('FinishRelease失败，请通过日志查看具体的失败原因。', buildErrorDetails(err))
                    }
                    if (commandId === 'extension.FinishHotfix') {
                        await showErrorWithCopy('FinishHotfix失败，请通过日志查看具体的失败原因。', buildErrorDetails(err))
                    }
                }
            })
        )
    })

    context.subscriptions.push(vscode.window.onDidCloseTerminal(terminal => {
        debugLog('terminal closed', terminal.name)
        mdTml = null
    }))
}

function getCommandSuccessMessage (commandId, groupName) {
    const commandName = commandId.replace(COMMAND_PREFIX, '')
    return `${commandName} executed done, please check the logs in terminal.`
}

async function gitCheck (rootPath) {
    rootPath = normalizePath(rootPath)
    debugLog('gitCheck start', rootPath)
    const gitRootPath = await resolveGitRootPath(rootPath)
    if (!gitRootPath) {
        const errMsg = `${rootPath} is not inside a git repository.`
        vscode.window.showErrorMessage(errMsg)
        throw new Error(errMsg)
    }
    rootPath = normalizePath(gitRootPath)
    debugLog('git root resolved', rootPath)
    let projectScriptPath = rootPath + '/' + gitCheckFile
    let scriptPath = normalizePath(gitCheckPath)

    if (fs.existsSync(projectScriptPath)) {
        scriptPath = projectScriptPath
        debugLog('use local gitCheck script', scriptPath)
    } else {
        let gitCheckUrl = getRootUrl() + '/' + gitCheckFile
        debugLog('download gitCheck script', gitCheckUrl)
        try {
            await myPlugin.downloadScripts(gitCheckUrl, gitCheckPath)
            // await myPlugin.downloadScripts(gitCheckUrl, gitCheckPath).catch(err => {
            //     vscode.window.showErrorMessage(`Can't found ${gitCheckUrl}: ${err}`)
            //     throw new Error(err)
            // })
        } catch (err) {
            console.warn('gitCheck.sh not found in remote git!')
        }
    }

    // git version check
    const checkGitVersion = vscode.workspace.getConfiguration().get(CONFIG_CHECK_GIT_VERSION)
    debugLog('checkGitVersion enabled', checkGitVersion)
    if (checkGitVersion) {
        try {
            const { stdout } = await exec('git version')
            const parsedVersion = parseGitVersion(stdout)
            if (!parsedVersion) {
                throw new Error(`Unable to parse git version output: ${stdout}`)
            }
            const { major, minor, patch } = parsedVersion
            debugLog('detected git version', `${major}.${minor}.${patch}`)
            if (major < 2 || (major === 2 && minor < 29)) {
                const msg = 'Make sure git version is >= 2.29.x. '
                throw new Error(msg)
            }
        } catch (err) {
            const message = err && err.message ? err.message : String(err)
            let msg = `${message} Please download from here: https://mirrors.huaweicloud.com/git-for-windows/v2.51.0.windows.2/Git-2.51.0.2-64-bit.exe`
            vscode.window.showErrorMessage(msg)
            throw new Error(msg)
        }
    }

    if (fs.existsSync(scriptPath)) {
        try {
            const cmd = buildBashCommand(rootPath, scriptPath)
            const statusBarItem = getOrCreateStatusBarItem()
            statusBarItem.text = 'Checking git status, it may take a few seconds...'
            statusBarItem.color = 'red'
            statusBarItem.show()
            debugLog('execute gitCheck command', cmd)
            const { stderr } = await exec(cmd)
            // Failure: (1) exec() rejects on non-zero exit; (2) stderr has real error content (trace stripped when debug).
            const realStderr = getRealStderr(stderr)
            if (realStderr !== '') {
                throw new Error(realStderr)
            }
            debugLog('gitCheck finished successfully')
        } catch (err) {
            // Two sources: (1) exec() rejected (non-zero exit); (2) we threw due to real stderr content.
            const msg = buildExecErrorMessage(err)
            await showErrorWithCopy(msg, buildErrorDetails(err))
            throw new Error(msg)
        } finally {
            getOrCreateStatusBarItem().hide()
        }
    }
}

async function resolveGitRootPath (candidatePath) {
    const normalizedPath = normalizePath(candidatePath)
    const cmd = `${buildCdCommand(normalizedPath)} && git rev-parse --show-toplevel`
    try {
        const { stdout } = await exec(cmd, { maxBuffer: 1024 * 1024 })
        const topLevel = String(stdout || '').trim().split(/\r?\n/)[0]
        if (!topLevel) {
            return null
        }
        return normalizePath(topLevel.trim())
    } catch (err) {
        debugLog('resolve git root failed', err && err.message ? err.message : String(err))
        return null
    }
}

async function pickWorkspaceFolder () {
    const workspaceFolders = vscode.workspace.workspaceFolders || []
    if (workspaceFolders.length === 0) {
        vscode.window.showErrorMessage('No workspace folder found, task aborted.')
        return null
    }
    if (workspaceFolders.length === 1) {
        return workspaceFolders[0]
    }
    return vscode.window.showWorkspaceFolderPick()
}

async function executeGitFlowCommand (commandId, resourceUri) {
    const scriptName = gitFlowScriptByCommand[commandId]
    if (!scriptName) {
        throw new Error(`Unsupported command: ${commandId}`)
    }
    debugLog('resolve command script', { commandId, scriptName })
    const commandRequiresGroup = commandId !== 'extension.GenerateCommitMessage'
    const groupName = commandRequiresGroup ? await ensureGroupNameConfigured() : null
    if (commandRequiresGroup && !groupName) {
        return { executed: false, groupName: null }
    }

    const scriptArgs = commandRequiresGroup ? [groupName] : []
    if (commandId === 'extension.FinishFeature') {
        const confirmed = await confirmFinishFeature(groupName)
        if (!confirmed) {
            debugLog('finish feature aborted by user')
            return { executed: false, groupName }
        }
    }
    if (commandId === 'extension.StartNewFeature') {
        const featureName = await askStartFeatureName(groupName)
        if (!featureName) {
            return { executed: false, groupName }
        }
        scriptArgs.push(featureName)
    }
    if (commandId === 'extension.StartNewRelease') {
        const confirmed = await confirmFinishFeatureForRelease(groupName)
        if (!confirmed) {
            debugLog('start release aborted by user')
            return { executed: false, groupName }
        }
    }
    if (commandId === 'extension.FinishRelease' || commandId === 'extension.FinishHotfix') {
        const hasMaintainer = await confirmMaintainerPermission()
        if (!hasMaintainer) {
            debugLog('finish release/hotfix aborted: user has no Maintainer permission')
            return { executed: false, groupName }
        }
        const confirmed = await confirmOpsReleaseDone()
        if (!confirmed) {
            debugLog('finish release/hotfix aborted by deployment confirmation')
            return { executed: false, groupName }
        }
    }

    let selectedPath
    if (resourceUri && resourceUri.fsPath) {
        const p = normalizePath(resourceUri.fsPath)
        try {
            selectedPath = fs.existsSync(p) && fs.statSync(p).isFile() ? path.dirname(p) : p
        } catch (_) {
            selectedPath = path.dirname(p)
        }
        debugLog('use resource path as selected', selectedPath)
    }
    if (!selectedPath) {
        const selectedItem = await pickWorkspaceFolder()
        if (!selectedItem) {
            debugLog('workspace pick cancelled')
            return { executed: false, groupName }
        }
        selectedPath = selectedItem.uri.fsPath
    }
    debugLog('workspace selected', selectedPath)
    const rootPath = await resolveGitRootPath(selectedPath)
    if (!rootPath) {
        const errMsg = `${normalizePath(selectedPath)} is not inside a git repository.`
        await showErrorWithCopy(errMsg, errMsg)
        return { executed: false, groupName }
    }
    debugLog('workspace git root', rootPath)

    if (commandId !== 'extension.GenerateCommitMessage') {
        await gitCheck(rootPath)
    }
    const scriptPath = await resolveScriptPath(rootPath, scriptName)
    debugLog('ready to run script', scriptPath)
    if (commandId === 'extension.GenerateCommitMessage') {
        const commitMessageModel = String(vscode.workspace.getConfiguration().get(CONFIG_COMMIT_MESSAGE_MODEL) || 'new-api/GLM-5').trim() || 'new-api/GLM-5'
        scriptArgs.push(commitMessageModel)
    }
    if (commandId === 'extension.FinishFeature') {
        const selectedFeatureBranch = await askFinishFeatureBranch(rootPath, groupName)
        if (!selectedFeatureBranch) {
            return { executed: false, groupName }
        }
        scriptArgs.push(selectedFeatureBranch)
    }
    if (commandId === 'extension.RebaseFeature') {
        const currentBranch = await getCurrentBranch(rootPath)
        const featurePrefix = `feature/${groupName}/`
        if (!currentBranch || !currentBranch.startsWith(featurePrefix)) {
            vscode.window.showErrorMessage(`当前分支 "${currentBranch || '(无)'}" 不是 feature 分支（应以 ${featurePrefix} 开头），操作已取消。`)
            return { executed: false, groupName }
        }
        scriptArgs.push(currentBranch)
    }
    if (commandId === 'extension.StartNewRelease') {
        const releaseName = await askStartReleaseName(rootPath, groupName)
        if (!releaseName) {
            return { executed: false, groupName }
        }
        scriptArgs.push(releaseName)
    }
    if (commandId === 'extension.StartNewHotfix') {
        const hotfixName = await askStartHotfixName(rootPath, groupName)
        if (!hotfixName) {
            return { executed: false, groupName }
        }
        scriptArgs.push(hotfixName)
    }
    if (commandId === 'extension.MavenChange') {
        const mavenRootPath = getMavenProjectRootPath(rootPath, selectedPath)
        if (!mavenRootPath) {
            vscode.window.showErrorMessage(`在当前选择目录及其上级目录中未找到有效的 Maven 项目（缺少可用 pom.xml）。请先选择子项目目录后重试。`)
            return { executed: false, groupName }
        }
        if (!isMavenProject(mavenRootPath)) {
            vscode.window.showErrorMessage(`${normalizePath(mavenRootPath)} is not a maven project, task aborted.`)
            return { executed: false, groupName }
        }
        const changeType = await askMavenChangeType()
        if (!changeType) {
            return { executed: false, groupName }
        }
        const mavenVersion = await askMavenChangeVersion(mavenRootPath, changeType)
        if (!mavenVersion) {
            return { executed: false, groupName }
        }
        scriptArgs.push(mavenVersion)
        const confirmedToRun = await confirmRunScript(commandId, mavenRootPath, scriptPath, scriptArgs)
        if (!confirmedToRun) {
            debugLog('script execution cancelled by user', { commandId, scriptPath, scriptArgs })
            return { executed: false, groupName }
        }
        runScriptInTerminal(mavenRootPath, scriptPath, scriptArgs)
        return { executed: true, groupName }
    }
    if (commandId === 'extension.FinishRelease') {
        const selectedReleaseBranch = await askFinishReleaseBranch(rootPath, groupName)
        if (!selectedReleaseBranch) {
            return { executed: false, groupName }
        }
        scriptArgs.push(selectedReleaseBranch)
        const groupsStr = getValidGroups().join(',')
        scriptArgs.push(groupsStr)
        const confirmedToRun = await confirmRunScript(commandId, rootPath, scriptPath, scriptArgs)
        if (!confirmedToRun) {
            debugLog('script execution cancelled by user', { commandId, scriptPath, scriptArgs })
            return { executed: false, groupName }
        }
        await runFinishReleaseScript(rootPath, scriptPath, scriptArgs)
        return { executed: true, groupName }
    }
    if (commandId === 'extension.FinishHotfix') {
        const selectedHotfixBranch = await askFinishHotfixBranch(rootPath, groupName)
        if (!selectedHotfixBranch) {
            return { executed: false, groupName }
        }
        scriptArgs.push(selectedHotfixBranch)
        const groupsStr = getValidGroups().join(',')
        scriptArgs.push(groupsStr)
        const confirmedToRun = await confirmRunScript(commandId, rootPath, scriptPath, scriptArgs)
        if (!confirmedToRun) {
            debugLog('script execution cancelled by user', { commandId, scriptPath, scriptArgs })
            return { executed: false, groupName }
        }
        await runFinishReleaseScript(rootPath, scriptPath, scriptArgs)
        return { executed: true, groupName }
    }
    const confirmedToRun = await confirmRunScript(commandId, rootPath, scriptPath, scriptArgs)
    if (!confirmedToRun) {
        debugLog('script execution cancelled by user', { commandId, scriptPath, scriptArgs })
        return { executed: false, groupName }
    }
    runScriptInTerminal(rootPath, scriptPath, scriptArgs)
    return { executed: true, groupName }
}

function quoteBashArg (value) {
    const str = String(value)
    return `'${str.replace(/'/g, `'\\''`)}'`
}

function buildBashArgs (args) {
    if (!Array.isArray(args) || args.length === 0) {
        return ''
    }
    return args.map(arg => quoteBashArg(arg)).join(' ')
}

function runScriptInTerminal (rootPath, scriptPath, scriptArgs) {
    const cmdStr = buildBashCommand(rootPath, scriptPath, scriptArgs)
    // sendText defaults to addNewLine=true, so this command is executed immediately.
    debugLog('send command to terminal', cmdStr)
    getTerminal().sendText(cmdStr)
}

function getTerminal () {
    if (mdTml == null) {
        mdTml = vscode.window.createTerminal('zerofinance')
    }
    mdTml.show(true)

    return mdTml
}

function buildGitBashPathFromConfig (configuredGitBash) {
    const normalizedInput = normalizePath(String(configuredGitBash || '').trim()).replace(/\/+$/, '')
    if (!normalizedInput) {
        return ''
    }
    if (/bash\.exe$/i.test(normalizedInput)) {
        return normalizedInput
    }
    return `${normalizedInput}/bin/bash.exe`
}

function promptConfigureGitBashPath () {
    const selectGitDirAction = 'Select Git Directory'
    const openSettingsAction = 'Open Settings'
    const errMsg = `Please configure "${CONFIG_GIT_BASH}" before running tasks. You can set a Git directory (extension will append bin/bash.exe) or a full bash.exe path.`
    vscode.window.showErrorMessage(errMsg, selectGitDirAction, openSettingsAction).then(async selectedAction => {
        if (selectedAction === selectGitDirAction) {
            const selected = await vscode.window.showOpenDialog({
                canSelectFiles: false,
                canSelectFolders: true,
                canSelectMany: false,
                openLabel: 'Select Git Directory'
            })
            if (!selected || selected.length === 0) {
                return
            }
            const selectedGitDir = normalizePath(selected[0].fsPath).replace(/\/+$/, '')
            const resolvedBashPath = `${selectedGitDir}/bin/bash.exe`
            if (!fs.existsSync(resolvedBashPath)) {
                vscode.window.showErrorMessage(`Cannot find "${resolvedBashPath}". Please select your Git install directory.`)
                return
            }
            await vscode.workspace.getConfiguration().update(CONFIG_GIT_BASH, selectedGitDir, vscode.ConfigurationTarget.Global)
            vscode.window.showInformationMessage(`Configured ${CONFIG_GIT_BASH}: ${selectedGitDir}`)
            return
        }
        if (selectedAction === openSettingsAction) {
            await vscode.commands.executeCommand('workbench.action.openSettings', CONFIG_GIT_BASH)
        }
    })
}

function getBashPath () {
    let gitBash = 'bash'
    if (process.platform === 'win32') {
        const configuredGitBash = String(vscode.workspace.getConfiguration().get(CONFIG_GIT_BASH) || '').trim()
        if (!configuredGitBash) {
            promptConfigureGitBashPath()
            throw new Error(`Please configure "${CONFIG_GIT_BASH}" before running tasks.`)
        }
        gitBash = buildGitBashPathFromConfig(configuredGitBash)
        if (!fs.existsSync(gitBash)) {
            promptConfigureGitBashPath()
            throw new Error(`Configured "${CONFIG_GIT_BASH}" is invalid. Expected "${gitBash}" to exist.`)
        }
    }
    return gitBash
}

exports.activate = activate

// this method is called when your extension is deactivated
function deactivate () { }

/**
 * @description: Expose objects to the outside
 * @Date: 2019-07-03 13:58:39
 */
module.exports = {
    activate,
    deactivate
}
