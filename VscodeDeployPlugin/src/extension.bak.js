// https://segmentfault.com/a/1190000008968904
// https://www.cnblogs.com/virde/p/vscode-extension-input-and-output.html
// https://github.com/steveukx/git-js
// https://www.jianshu.com/p/2b096d8ad9b8
// https://github.com/Microsoft/vscode-extension-samples
// https://www.jianshu.com/p/520c575e91c3
// https://segmentfault.com/a/1190000017279102
// https://segmentfault.com/a/1190000014758981
// https://dev.azure.com/it0815/_usersSettings/tokens
// https://www.cnblogs.com/liuxianan/p/vscode-plugin-publish.html
// https://www.cnblogs.com/virde/p/vscode-extension-input-and-output.html
// http://nodejs.cn/api/fs.html#fs_fs_unlinksync_path
// https://www.cnblogs.com/liuxianan/p/vscode-plugin-snippets-and-settings.html
// https://code.visualstudio.com/api/references/contribution-points

// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
const vscode = require('vscode')
const myPlugin = require('./myPlugin')
const simpleGit = require('simple-git')
const tmp = require('tmp')
const fs = require('fs')

const util = require('util')
const exec = util.promisify(require('child_process').exec)

let mdTml = null
let myStatusBarItem = null

// const rootUrl = 'https://raw.githubusercontent.com/zhaoxunyong/vs-code-git-plugin/master/'
// let rootUrl = process.env.GIT_PLUGIN_URL
// const config = vscode.workspace.getConfiguration()
// let rootUrl = vscode.workspace.getConfiguration().get('zerofinanceGit.gitScriptsUrlPreference')
// if (!rootUrl) {
//     rootUrl = 'http://gitlab.aeasycredit.net/dave.zhao/deployPlugin/raw/master'
// }
const newBranchFile = 'newBranch.sh'
const newReleaseFile = 'release.sh'
const newTagFile = 'tag.sh'
const gitCheckFile = 'gitCheck.sh'
const committedLogsFile = "committedLogs.sh";
const tmpdir = tmp.tmpdir
const newBranchPath = tmpdir + '/' + newBranchFile
const newReleasePath = tmpdir + '/' + newReleaseFile
const newTagPath = tmpdir + '/' + newTagFile
const gitCheckPath = tmpdir + '/' + gitCheckFile
const committedLogsPath = tmpdir + '/' + committedLogsFile
// this method is called when your extension is activated
// your extension is activated the very first time the command is executed

function getRootUrl () {
    // If you wanna get realtime config, must use "vscode.workspace.getConfiguration()"
    let rootUrl = vscode.workspace.getConfiguration().get('zerofinanceGit.gitScriptsUrlPreference')
    if (!rootUrl) {
        rootUrl = 'http://gitlab.zerofinance.net/dave.zhao/deployPlugin/raw/master'
    }
    return rootUrl
}

// function getGitHomePath() {
//     return vscode.workspace.getConfiguration().get('zerofinanceGit.gitHomePathPreference')
// }

function getNeedTagWhileBranch () {
    // If you wanna get realtime config, must use "vscode.workspace.getConfiguration()"
    return vscode.workspace.getConfiguration().get('zerofinanceGit.tagWhileBranchPreference')
}

function clearCacheFile () {
    try {
        fs.unlinkSync(newBranchPath)
    } catch (error) { }

    try {
        fs.unlinkSync(newReleasePath)
    } catch (error) { }

    try {
        fs.unlinkSync(newTagPath)
    } catch (error) { }

    try {
        fs.unlinkSync(gitCheckPath)
    } catch (error) { }
}

/**
 * @param {vscode.ExtensionContext} context
 */
function activate (context) {
    context.subscriptions.push(
        vscode.commands.registerCommand('extension.newBranch', function () {
            clearCacheFile()
            newBranch()
        })
    )

    context.subscriptions.push(
        vscode.commands.registerCommand('extension.newRelease', () => {
            clearCacheFile()
            newRelease()
        })
    )

    /*context.subscriptions.push(
        vscode.commands.registerCommand('extension.clearCache', () => {
            try {
                fs.unlinkSync(newBranchPath)
            } catch (error) {}

            try {
                fs.unlinkSync(newReleasePath)
            } catch (error) {}

            try {
                fs.unlinkSync(newTagPath)
            } catch (error) {}
            vscode.window.showInformationMessage('Clear cache sussessfully!')
        })
    )*/

    vscode.window.onDidCloseTerminal(terminal => {
        console.log(`onDidCloseTerminal, name: ${terminal.name}`)
        mdTml = null
    })
}

async function committedLogWarn (rootPath) {
    return new Promise(async (resolve, reject) => {
        rootPath = rootPath.replace(/\\/gm, '/')
        let projectScriptPath = rootPath + '/' + committedLogsFile
        let scriptPath = committedLogsPath
        scriptPath = scriptPath.replace(/\\/gm, '/')

        if (fs.existsSync(projectScriptPath)) {
            scriptPath = projectScriptPath
        } else {
            let committedLogsUrl = getRootUrl() + '/' + committedLogsFile
            try {
                await myPlugin.downloadScripts(committedLogsUrl, committedLogsPath)
                // await myPlugin.downloadScripts(gitCheckUrl, gitCheckPath).catch(err => {
                //     vscode.window.showErrorMessage(`Can't found ${gitCheckUrl}: ${err}`)
                //     throw new Error(err)
                // })
            } catch (err) {
                console.warn('gitCheck.sh not found in remote git!')
            }
        }

        if (fs.existsSync(scriptPath)) {
            try {
                let cmd = `cd ${rootPath} && "${getBashPath()}" ${scriptPath}`
                let isWin = process.platform === 'win32'
                // "D:/Developer/Git/bin/bash.exe" -c "cd d:/Developer/workspace/blog && C:/Users/DAVE~1.ZHA/AppData/Local/Temp/gitCheck.sh"
                if (isWin) {
                    cmd = `"${getBashPath()}" -c "cd ${rootPath} && ${scriptPath}"`
                }
                if (myStatusBarItem == null) {
                    myStatusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left)
                }
                // disposable = vscode.window.setStatusBarMessage('Checking git status, it may take a few seconds...')
                myStatusBarItem.text = `Pre checking, it may take a few seconds...`
                // myStatusBarItem.color = new vscode.ThemeColor('statusBar.background')
                myStatusBarItem.color = 'red'
                myStatusBarItem.show()
                const { stdout, stderr } = await exec(cmd)
                // if (stderr !== undefined && stderr !== '') {
                //     throw new Error(stderr)
                // }
                let msg = stdout.toString()
                vscode.window.showWarningMessage(msg, {
                    modal: true,
                    detail: '确认是否有需要合并的分支，如果有请先合并！'
                }).then(() => {
                    // console.log('You clicked the button!');
                    resolve()
                }).catch(err => {
                    reject(err)
                })
            } catch (err) {
                const { stdout } = err
                let msg = stdout.toString()
                vscode.window.showErrorMessage(msg)
                throw new Error(msg)
            } finally {
                // disposable.dispose()
                myStatusBarItem.hide()
            }
        }
    })

}

async function gitCheck (rootPath) {
    rootPath = rootPath.replace(/\\/gm, '/')
    const gitConfigPath = rootPath + '/.git'
    if (!fs.existsSync(gitConfigPath)) {
        const errMsg = `${rootPath} isn't a git project, make sure you are opening the root folder of project!`
        vscode.window.showErrorMessage(errMsg)
        throw new Error(errMsg)
    }
    let projectScriptPath = rootPath + '/' + gitCheckFile
    let scriptPath = gitCheckPath
    scriptPath = scriptPath.replace(/\\/gm, '/')

    if (fs.existsSync(projectScriptPath)) {
        scriptPath = projectScriptPath
    } else {
        let gitCheckUrl = getRootUrl() + '/' + gitCheckFile
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
    checkGitVersion = vscode.workspace.getConfiguration().get('zerofinanceGit.checkGitVersion')
    if (checkGitVersion) {
        try {
            const { stdout, stderr } = await exec("git version")
            console.log("1--->" + stdout)
            // git version 2.29.2.windows.2
            let versions = stdout.split(' ')
            let [v1, v2, v3] = versions
            let gitversion = v3.split('.')
            // 2.29.2
            let [g1, g2, g3] = gitversion
            if (parseInt(g1) < 2 || (parseInt(g1) === 2 && parseInt(g2) < 29)) {
                const msg = "Making sure git version >= 2.29.x. "
                throw new Error(msg)
            }
        } catch (err) {
            const { message } = err
            let msg = message.toString() + "Please download from here: https://npm.taobao.org/mirrors/git-for-windows/v2.29.2.windows.2/Git-2.29.2.2-64-bit.exe"
            vscode.window.showErrorMessage(msg)
            throw new Error(msg)

        }
    }

    if (fs.existsSync(scriptPath)) {
        try {
            let cmd = `cd ${rootPath} && "${getBashPath()}" ${scriptPath}`
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
            const { stdout, stderr } = await exec(cmd)
            if (stderr !== undefined && stderr !== '') {
                throw new Error(stderr)
            }
            // getTerminal().sendText(cmd)
        } catch (err) {
            const { stdout } = err
            let msg = stdout.toString()
            vscode.window.showErrorMessage(msg)
            throw new Error(msg)
        } finally {
            // disposable.dispose()
            myStatusBarItem.hide()
        }
    } else {
        // Skipping check when gitCheck.sh is existing.
        // vscode.window.showErrorMessage(`Can't found ${newTagFile}`)
    }
}

/**
 * @description: Create branch
 * @Date: 2019-07-03 14:05:21
 */
async function newBranch () {
    let selectedItem = await myPlugin.chooicingFolder()
    const rootPath = selectedItem.uri.fsPath
    await gitCheck(rootPath)
    let newBranch = await myPlugin.chooicingBranch(simpleGit(rootPath))
    // vscode.window.showInformationMessage(newBranch);

    // The path of the root project, if exist, using it, otherwise downloaded from gitlab
    let projectScriptPath = rootPath + '/' + newBranchFile
    let scriptPath = newBranchPath
    if (fs.existsSync(projectScriptPath)) {
        scriptPath = projectScriptPath
    } else {
        let newBranchUrl = getRootUrl() + '/' + newBranchFile
        await myPlugin.downloadScripts(newBranchUrl, newBranchPath).catch(err => {
            vscode.window.showErrorMessage(`Can't found ${newBranchUrl}: ${err}`)
            throw new Error(err)
        })
    }
    if (fs.existsSync(scriptPath)) {
        try {
            let desc = await myPlugin.getDesc(newBranch)
            if (desc === '' || desc === undefined) {
                let err = 'The message for git description must not be empty!'
                vscode.window.showErrorMessage(err)
                throw new Error(err)
            }
            let cmdStr = `cd "${rootPath}" && "${getBashPath()}" "${scriptPath}" ${newBranch} "${desc}"`
            // console.log('cmdStr======>'+cmdStr);
            getTerminal().sendText(cmdStr)
        } catch (err) {
            vscode.window.showErrorMessage(err)
        }
    } else {
        vscode.window.showErrorMessage(`Can't found ${newBranchFile}`)
    }
}

/**
 * @description: Create release
 * @Date: 2019-07-03 14:05:43
 */
async function newRelease () {
    let selectedItem = await myPlugin.chooicingFolder()
    const rootPath = selectedItem.uri.fsPath
    await gitCheck(rootPath)
    await committedLogWarn(rootPath)
    let git = simpleGit(rootPath)

    let releaseType = await myPlugin.chooicingRleaseType()
    let release = {}
    let selectedRelease = ''
    // Only for tag the release version
    if ('tag' === releaseType) {
        selectedRelease = await myPlugin.listAllRemoteReleaseVersions(git)
        release = myPlugin.chooicingTag(selectedRelease)
        // vscode.window.showInformationMessage(newBranch);

        let projectScriptPath = rootPath + '/' + newTagFile
        let scriptPath = newTagPath

        if (fs.existsSync(projectScriptPath)) {
            scriptPath = projectScriptPath
        } else {
            let newTagUrl = getRootUrl() + '/' + newTagFile
            await myPlugin.downloadScripts(newTagUrl, newTagPath).catch(err => {
                vscode.window.showErrorMessage(`Can't found ${newTagUrl}: ${err}`)
                throw new Error(err)
            })
        }
        if (fs.existsSync(scriptPath)) {
            try {
                let desc = await myPlugin.getDesc(release.nextRelase)
                if (desc === '' || desc === undefined) {
                    let err = 'The message for git description must not be empty!'
                    vscode.window.showErrorMessage(err)
                    throw new Error(err)
                }
                let cmdStr = `cd "${rootPath}" && "${getBashPath()}" "${scriptPath}" ${release.nextRelase} ${release.currentDate} "${desc}"`
                console.log('cmdStr======>' + cmdStr)
                getTerminal().sendText(cmdStr)
            } catch (err) {
                vscode.window.showErrorMessage(err)
            }
        } else {
            vscode.window.showErrorMessage(`Can't found ${newTagFile}`)
        }
    } else {
        release = await myPlugin.chooicingRlease(releaseType, git)
        console.log('release----->', release)
        // vscode.window.showInformationMessage(newBranch);

        let projectScriptPath = rootPath + '/' + newReleaseFile
        let scriptPath = newReleasePath

        if (fs.existsSync(projectScriptPath)) {
            scriptPath = projectScriptPath
        } else {
            let newReleaseUrl = getRootUrl() + '/' + newReleaseFile
            await myPlugin.downloadScripts(newReleaseUrl, newReleasePath).catch(err => {
                vscode.window.showErrorMessage(`Can't found ${newReleaseUrl}: ${err}`)
                throw new Error(err)
            })
        }
        if (fs.existsSync(scriptPath)) {
            try {
                let needTagWhileBranch = getNeedTagWhileBranch()
                console.log('needTagWhileBranch------------', needTagWhileBranch)
                if (needTagWhileBranch) {
                    let tooltips = `It will tag ${release.nextRelase}-${release.currentDate} for ${release.nextRelase} automatically. Are you sure to tag?`
                    needTagWhileBranch = await vscode.window.showInformationMessage(tooltips, 'Yes', 'No').then(function (select) {
                        if (select === 'No') {
                            return false
                        } else {
                            return true
                        }
                    })
                }
                let desc = await myPlugin.getDesc(release.nextRelase)
                if (desc === '' || desc === undefined) {
                    let err = 'The message for git description must not be empty!'
                    vscode.window.showErrorMessage(err)
                    throw new Error(err)
                }
                let cmdStr = `cd "${rootPath}" && "${getBashPath()}" "${scriptPath}" ${release.nextRelase} ${release.currentDate} ${needTagWhileBranch} "${desc}"`
                console.log('cmdStr======>' + cmdStr)
                getTerminal().sendText(cmdStr)
            } catch (err) {
                vscode.window.showErrorMessage(err)
            }
        } else {
            vscode.window.showErrorMessage(`Can't found ${newReleaseFile}`)
        }
    }
}

/**
 * @description: Get the terminal of vscode
 * @returns terminal
 * @Date: 2019-07-03 14:05:53
 */
function getTerminal () {
    if (mdTml == null) {
        mdTml = vscode.window.createTerminal('zerofinance')
    }
    mdTml.show(true)

    return mdTml
}

function getBashPath () {
    let gitBash = "bash"
    // let isWin = process.platform === 'win32'
    if (process.platform === 'win32') {
        // If you wanna get realtime config, must use "vscode.workspace.getConfiguration()"
        // let gitBash = vscode.workspace.getConfiguration().get('terminal.external.windowsExec')
        gitBash = vscode.workspace.getConfiguration().get('terminal.integrated.shell.windows')
        if (gitBash === null || gitBash.indexOf("bash.exe") == -1) {
            // Searching "terminal.external.windowsExec"
            // gitBash = vscode.workspace.getConfiguration().get('terminal.external.windowsExec')
            // if (gitBash === null || gitBash.indexOf("bash.exe") == -1) {
            const errMsg = "Please set \"git bash\" for the terminal at \"settings.json\": \"terminal.integrated.shell.windows\": \"YourGitPath\\\\bin\\\\bash.exe\"";
            vscode.window.showErrorMessage(errMsg)
            throw new Error(errMsg)
            // }
        }
        gitBash = gitBash.replace(/\\/gm, '/')
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
