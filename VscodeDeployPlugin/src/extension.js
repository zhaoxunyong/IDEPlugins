const vscode = require('vscode')
const myPlugin = require('./myPlugin')
const tmp = require('tmp')
const fs = require('fs')

const util = require('util')
const exec = util.promisify(require('child_process').exec)

let mdTml = null
let myStatusBarItem = null

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
    const debugEnabled = vscode.workspace.getConfiguration().get('zerofinanceGit.debug')
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
    let rootUrl = vscode.workspace.getConfiguration().get('zerofinanceGit.gitScriptsUrlPreference')
    if (!rootUrl) {
        // rootUrl = 'http://gitlab.zerofinance.net/dave.zhao/deployPlugin/raw/master'
        rootUrl = 'https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/git-flow/git-flow'
    }
    return rootUrl
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
                    await executeGitFlowCommand(commandId)
                    const commandName = commandId.replace('extension.', '')
                    vscode.window.showInformationMessage(`${commandName} executed done, please check the logs in terminal.`)
                } catch (err) {
                    const msg = err && err.message ? err.message : String(err)
                    debugLog('command failed', { commandId, msg })
                    vscode.window.showErrorMessage(msg)
                }
            })
        )
    })

    vscode.window.onDidCloseTerminal(terminal => {
        console.log(`onDidCloseTerminal, name: ${terminal.name}`)
        mdTml = null
    })
}

async function gitCheck (rootPath) {
    rootPath = normalizePath(rootPath)
    debugLog('gitCheck start', rootPath)
    const gitConfigPath = rootPath + '/.git'
    if (!fs.existsSync(gitConfigPath)) {
        const errMsg = `${rootPath} is nott a git project, make sure you are opening the root folder of project!`
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
    const checkGitVersion = vscode.workspace.getConfiguration().get('zerofinanceGit.checkGitVersion')
    debugLog('checkGitVersion enabled', checkGitVersion)
    if (checkGitVersion) {
        try {
            const { stdout } = await exec('git version')
            console.log("1--->" + stdout)
            // git version 2.29.2.windows.2
            let versions = stdout.split(' ')
            let [v1, v2, v3] = versions
            let gitversion = v3.split('.')
            // 2.29.2
            let [g1, g2, g3] = gitversion
            debugLog('detected git version', `${g1}.${g2}.${g3}`)
            if (parseInt(g1) < 2 || (parseInt(g1) === 2 && parseInt(g2) < 29)) {
                const msg = "Making sure git version >= 2.29.x. "
                throw new Error(msg)
            }
        } catch (err) {
            const { message } = err
            let msg = message.toString() + "Please download from here: https://mirrors.huaweicloud.com/git-for-windows/v2.51.0.windows.2/Git-2.51.0.2-64-bit.exe"
            vscode.window.showErrorMessage(msg)
            throw new Error(msg)

        }
    }

    if (fs.existsSync(scriptPath)) {
        try {
            let cmd = `cd "${rootPath}" && "${getBashPath()}" "${scriptPath}"`
            let isWin = process.platform === 'win32'
            // "D:/Developer/Git/bin/bash.exe" -c "cd d:/Developer/workspace/blog && C:/Users/DAVE~1.ZHA/AppData/Local/Temp/gitCheck.sh"
            if (isWin) {
                cmd = `"${getBashPath()}" -c "cd ${rootPath} && ${scriptPath}"`
            }
            if (myStatusBarItem == null) {
                myStatusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left)
            }
            // disposable = vscode.window.setStatusBarMessage('Checking git status, it may take a few seconds...')
            myStatusBarItem.text = `Checking git status, it may take a few seconds...`
            // myStatusBarItem.color = new vscode.ThemeColor('statusBar.background')
            myStatusBarItem.color = 'red'
            myStatusBarItem.show()
            debugLog('execute gitCheck command', cmd)
            const { stderr } = await exec(cmd)
            if (stderr !== undefined && stderr !== '') {
                throw new Error(stderr)
            }
            debugLog('gitCheck finished successfully')
            // getTerminal().sendText(cmd)
        } catch (err) {
            let msg = err && err.stdout ? err.stdout.toString() : (err && err.message ? err.message : String(err))
            vscode.window.showErrorMessage(msg)
            throw new Error(msg)
        } finally {
            // disposable.dispose()
            myStatusBarItem.hide()
        }
    }
}

async function executeGitFlowCommand (commandId) {
    const scriptName = gitFlowScriptByCommand[commandId]
    if (!scriptName) {
        throw new Error(`Unsupported command: ${commandId}`)
    }
    debugLog('resolve command script', { commandId, scriptName })

    let selectedItem = await myPlugin.chooicingFolder()
    if (!selectedItem) {
        debugLog('workspace pick cancelled')
        return
    }
    const rootPath = selectedItem.uri.fsPath
    debugLog('workspace selected', rootPath)

    await gitCheck(rootPath)
    const scriptPath = await resolveScriptPath(rootPath, scriptName)
    debugLog('ready to run script', scriptPath)
    runScriptInTerminal(rootPath, scriptPath)
}

function runScriptInTerminal (rootPath, scriptPath) {
    const cmdStr = `cd "${normalizePath(rootPath)}" && "${getBashPath()}" "${normalizePath(scriptPath)}"`
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
        if (gitBash === null || gitBash.indexOf('bash.exe') === -1) {
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
