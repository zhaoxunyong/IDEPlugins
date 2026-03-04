const vscode = require('vscode')
const myPlugin = require('./myPlugin')
const tmp = require('tmp')
const fs = require('fs')

const util = require('util')
const exec = util.promisify(require('child_process').exec)

let mdTml = null
let myStatusBarItem = null

const CONFIG_ROOT = 'zerofinanceGit'
const CONFIG_SCRIPT_URL = `${CONFIG_ROOT}.gitScriptsUrlPreference`
const CONFIG_CHECK_GIT_VERSION = `${CONFIG_ROOT}.checkGitVersion`
const CONFIG_DEBUG = `${CONFIG_ROOT}.debug`
const CONFIG_GROUP_NAME = `${CONFIG_ROOT}.groupName`
const DEFAULT_SCRIPT_ROOT_URL = 'https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/git-flow'
const COMMAND_PREFIX = 'extension.'
const gitCheckFile = 'gitCheck.sh'
const tmpdir = tmp.tmpdir
const gitCheckPath = tmpdir + '/' + gitCheckFile

const gitFlowScriptByCommand = {
    'extension.StartNewFeature': 'StartNewFeature.sh',
    'extension.FinishFeature': 'FinishFeature.sh',
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

function getRootUrl () {
    let rootUrl = vscode.workspace.getConfiguration().get(CONFIG_SCRIPT_URL)
    if (!rootUrl) {
        rootUrl = DEFAULT_SCRIPT_ROOT_URL
    }
    return rootUrl.replace(/\/+$/, '')
}

async function ensureGroupNameConfigured () {
    const groupName = vscode.workspace.getConfiguration().get(CONFIG_GROUP_NAME)
    const validGroups = ['a', 'b']
    if (validGroups.includes(groupName)) {
        return groupName
    }

    const openSettingsAction = 'Open Settings'
    const message = 'Please configure "zerofinanceGit.groupName" to "a" or "b" before running tasks.'
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
        vscode.window.showErrorMessage(`Can't found ${scriptUrl}: ${err}`)
        throw new Error(err && err.message ? err.message : String(err))
    }
}

function getOrCreateStatusBarItem () {
    if (myStatusBarItem == null) {
        myStatusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left)
    }
    return myStatusBarItem
}

function buildBashCommand (rootPath, scriptPath) {
    const normalizedRootPath = normalizePath(rootPath)
    const normalizedScriptPath = normalizePath(scriptPath)
    if (process.platform === 'win32') {
        return `"${getBashPath()}" -c "cd ${normalizedRootPath} && ${normalizedScriptPath}"`
    }
    return `cd "${normalizedRootPath}" && "${getBashPath()}" "${normalizedScriptPath}"`
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
    Object.keys(gitFlowScriptByCommand).forEach(commandId => {
        context.subscriptions.push(
            vscode.commands.registerCommand(commandId, async () => {
                clearCacheFile()
                try {
                    debugLog('command triggered', commandId)
                    const executed = await executeGitFlowCommand(commandId)
                    if (executed) {
                        const commandName = commandId.replace(COMMAND_PREFIX, '')
                        vscode.window.showInformationMessage(`${commandName} executed done, please check the logs in terminal.`)
                    }
                } catch (err) {
                    const msg = err && err.message ? err.message : String(err)
                    debugLog('command failed', { commandId, msg })
                    vscode.window.showErrorMessage(msg)
                }
            })
        )
    })

    context.subscriptions.push(vscode.window.onDidCloseTerminal(terminal => {
        debugLog('terminal closed', terminal.name)
        mdTml = null
    }))
}

async function gitCheck (rootPath) {
    rootPath = normalizePath(rootPath)
    debugLog('gitCheck start', rootPath)
    const gitConfigPath = rootPath + '/.git'
    if (!fs.existsSync(gitConfigPath)) {
        const errMsg = `${rootPath} is not a git project, make sure you are opening the project root folder.`
        vscode.window.showErrorMessage(errMsg)
        throw new Error(errMsg)
    }
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
            if (stderr !== undefined && stderr !== '') {
                throw new Error(stderr)
            }
            debugLog('gitCheck finished successfully')
        } catch (err) {
            let msg = err && err.stdout ? err.stdout.toString() : (err && err.message ? err.message : String(err))
            vscode.window.showErrorMessage(msg)
            throw new Error(msg)
        } finally {
            getOrCreateStatusBarItem().hide()
        }
    }
}

async function executeGitFlowCommand (commandId) {
    const scriptName = gitFlowScriptByCommand[commandId]
    if (!scriptName) {
        throw new Error(`Unsupported command: ${commandId}`)
    }
    debugLog('resolve command script', { commandId, scriptName })
    const groupName = await ensureGroupNameConfigured()
    if (!groupName) {
        return false
    }

    let selectedItem = await myPlugin.chooicingFolder()
    if (!selectedItem) {
        debugLog('workspace pick cancelled')
        return false
    }
    const rootPath = selectedItem.uri.fsPath
    debugLog('workspace selected', rootPath)

    await gitCheck(rootPath)
    const scriptPath = await resolveScriptPath(rootPath, scriptName)
    debugLog('ready to run script', scriptPath)
    const scriptArgs = [groupName]
    if (commandId === 'extension.StartNewFeature') {
        const featureName = await askStartFeatureName(groupName)
        if (!featureName) {
            return false
        }
        scriptArgs.push(featureName)
    }
    runScriptInTerminal(rootPath, scriptPath, scriptArgs)
    return true
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
    const argsText = buildBashArgs(scriptArgs)
    const cmdStr = argsText ? `${buildBashCommand(rootPath, scriptPath)} ${argsText}` : buildBashCommand(rootPath, scriptPath)
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

function getBashPath () {
    let gitBash = 'bash'
    if (process.platform === 'win32') {
        gitBash = vscode.workspace.getConfiguration().get('terminal.integrated.shell.windows')
        if (gitBash === null || !gitBash.includes('bash.exe')) {
            const errMsg = 'Please set "git bash" for the terminal at "settings.json": "terminal.integrated.shell.windows": "YourGitPath\\\\bin\\\\bash.exe"'
            vscode.window.showErrorMessage(errMsg)
            throw new Error(errMsg)
        }
        gitBash = normalizePath(gitBash)
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
