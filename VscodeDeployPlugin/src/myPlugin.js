const vscode = require('vscode')
const dateUtils = require('./dateUtils')
const axios = require('axios')
const fs = require('fs')

/**
 * @description: Get branch object via simpleGit
 * @param {simple-git} simpleGit
 * @returns branch object
 * @Date: 2019-07-03 13:51:18
 */
function getBranch (simpleGit) {
    return new Promise((resolve, reject) => {
        simpleGit.branch((err, branch) => {
            if (err) {
                reject(err)
            } else {
                resolve(branch)
            }
        })
    })
}

function getShowTagInDropDown () {
    // If you wanna get realtime config, must use "vscode.workspace.getConfiguration()"
    return vscode.workspace.getConfiguration().get('zerofinanceGit.showTagInDropDownPreference')
}

/**
 * @description: Get tag object via simpleGit
 * @param {simple-git} simpleGit
 * @returns tag object
 * @Date: 2019-07-03 13:52:04
 */
/* function getTag(simpleGit) {
    return new Promise((resolve, reject) => {
        simpleGit.tags((err, tags) => {
            if (err) {
                reject(err)
            } else {
                resolve(tags)
            }
        })
    })
} */

function getAllReleaseVersion (simpleGit) {
    return new Promise((resolve, reject) => {
        simpleGit.branch((err, branch) => {
            if (err) {
                reject(err)
            } else {
                resolve(branch)
            }
        })
    })
}

/**
 * @description: Show all of your workspaces in the select option.
 * @returns the project you picked
 * @Date: 2019-07-03 13:52:42
 */
async function chooicingFolder () {
    return await vscode.window.showWorkspaceFolderPick()
}

/**
 * @description: Generated the branch version automatically
 * @param {simple-git} simpleGit
 * @returns the branch you have beed inputted.
 * @Date: 2019-07-03 13:54:10
 */
async function chooicingBranch (simpleGit) {
    let branch = await getBranch(simpleGit)
    let currentBranch = getCurrentRemoteBranch(branch)
    // Get next branch
    const currentBranchs = currentBranch.split('.')
    let nextBranch = ''

    if (currentBranch.endsWith('.x') && currentBranchs.length == 3) {
        const [p1, p2, p3] = currentBranchs
        const nextP2 = parseInt(p2) + 1
        nextBranch = p1 + '.' + nextP2 + '.x'
    } else {
        nextBranch = '1.0.x'
    }
    return await vscode.window
        .showInputBox({
            // 这个对象中所有参数都是可选参数
            password: false, // 输入内容是否是密码
            ignoreFocusOut: false, // 默认false，设置为true时鼠标点击别的地方输入框不会消失
            placeHolder: 'Fill in new branch', // 在输入框内的提示信息
            prompt: 'The new branch has been filled in!', // 在输入框下方的提示信息
            value: nextBranch,
            validateInput: function (text) {
                if (text == '' || !text.endsWith('.x')) {
                    return 'Please fill in a correct branch name: similar to 1.0.x and so on.'
                }
                return ''
            } // 对输入内容进行验证并返回
        })
        .then(function (newBranch) {
            if (!newBranch) return
            // resolve(newBranch)
            return newBranch
        })
}

/**
 * @description: Generated the release version automatically
 * @param {string} releaseType
 * @param {simple-git} simpleGit
 * @Date: 2019-07-03 13:55:57
 */
async function chooicingRlease (releaseType, simpleGit) {
    const currentDate = dateUtils.formatTime('yyyyMMddhhmm', new Date())
    let branch = await getBranch(simpleGit)
    console.log('branch--->', branch)
    /* if (!branch.current.endsWith('.x')) {
                vscode.window.showErrorMessage('Only support ' + releaseType + ' version based on branch version(similar to 1.0.x)!')
                return
            } */
    let currentBranch = getCurrentRemoteBranch(branch)
    // let tags = await getTag(simpleGit)
    let nextRelase = ''
    if (currentBranch == '') {
        vscode.window.showErrorMessage('Please create a branch first.')
        return
    } else {
        let currentBranchs = currentBranch.split('.')
        let [b1, b2] = currentBranchs
        /* 
        // console.log("Latest available tag: %s", tags.latest);
        const latestTag = tags.latest
        if (latestTag != undefined && latestTag.indexOf('-') != -1) {
            const [currentRelease] = latestTag.split('-')
            // console.log("currentRelease="+currentRelease);
            const currentReleases = currentRelease.split('.')
            // Get next relase
            if (currentReleases.length >= 3) {
                const [p1, p2, p3] = currentReleases
                const compareBranch = b1 + b2
                const compareTag = p1 + p2
                // 如果当前tag的版本与当前分支的不一样，以当前分支为主
                if (compareBranch != compareTag) {
                    nextRelase = `${b1}.${b2}.0.${releaseType}`
                } else {
                    const nextP3 = parseInt(p3) + 1
                    nextRelase = p1 + '.' + p2 + '.' + nextP3 + '.' + releaseType
                }
            }
        } else {
            // 没有tag，默认以当前分支创建
            nextRelase = `${b1}.${b2}.0.${releaseType}`
        } */
        let maxRemoteReleaseBranch = getMaxRemoteReleaseBranch(branch)
        if (maxRemoteReleaseBranch) {
            let currentReleases = maxRemoteReleaseBranch.split('.')
            if (currentReleases.length >= 3) {
                const [p1, p2, p3] = currentReleases
                const compareBranch = b1 + b2
                const compareTag = p1 + p2
                // 如果当前tag的版本与当前分支的不一样，以当前分支为主
                if (compareBranch != compareTag) {
                    nextRelase = `${b1}.${b2}.0.${releaseType}`
                } else {
                    const nextP3 = parseInt(p3) + 1
                    nextRelase = p1 + '.' + p2 + '.' + nextP3 + '.' + releaseType
                }
            }
        } else {
            nextRelase = `${b1}.${b2}.0.${releaseType}`
        }
        return await vscode.window
            .showInputBox({
                // 这个对象中所有参数都是可选参数
                password: false, // 输入内容是否是密码
                ignoreFocusOut: false, // 默认false，设置为true时鼠标点击别的地方输入框不会消失
                placeHolder: 'Fill in new ' + releaseType, // 在输入框内的提示信息
                prompt: 'The new ' + releaseType + ' has been filled in!', // 在输入框下方的提示信息
                value: nextRelase,
                validateInput: function (text) {
                    if (text == '' || text.indexOf('.' + releaseType) == -1) {
                        return 'Please fill in a correct relase name: it similar to be 1.0.0.' + releaseType + ' and so on.'
                    }
                    return ''
                } // 对输入内容进行验证并返回
            })
            .then(function (nextRelase) {
                if (!nextRelase) return
                return {
                    nextRelase: nextRelase,
                    currentDate: currentDate,
                    releaseType: releaseType
                }
            })
    }
}

function chooicingTag (selectedRelease) {
    const currentDate = dateUtils.formatTime('yyyyMMddhhmm', new Date())
    return {
        nextRelase: selectedRelease,
        currentDate: currentDate,
        releaseType: 'tag'
    }
}

/* async function chooicingFolder() {
    return await vscode.window.showWorkspaceFolderPick()
} */

/**
 * @description: Which release type you'd like to
 * @returns release type
 * @Date: 2019-07-03 13:56:31
 */
async function chooicingRleaseType () {
    const items = [
        {
            label: 'release',
            description: 'The full version for release.'
            // detail: 'first item details'
        },
        {
            label: 'hotfix',
            description: 'The hotfix version for release.'
        }
    ]
    const showTag = getShowTagInDropDown()
    if (showTag) {
        items.push({
            label: 'tag',
            description: 'Tag the release version after released.'
        })
    }
    return await vscode.window.showQuickPick(items, { placeHolder: 'Which release type would you like to pick?' }).then(value => {
        return value.label
    })
}

/**
 * @description: List all of remote releae versions
 * @param {json} versions
 * @Date: 2019-07-26 10:15:14
 */
async function listAllRemoteReleaseVersions (simpleGit) {
    let branch = await getAllReleaseVersion(simpleGit)
    let releaseBranchs = getAllRemoteReleaseBranchs(branch)
    let items = releaseBranchs.map(ver => {
        return {
            label: ver
        }
    })
    return await vscode.window.showQuickPick(items, { placeHolder: 'Which release type would you like to pick?' }).then(value => {
        return value.label
    })
}

/**
 * 如果version1>version2返回1，如果version<version2返回-1，否则返回0
 */
function compareVersion (version1, version2) {
    if (version1 == undefined || version2 == undefined) {
        return 0
    }

    let str1 = version1.split('.')
    let str2 = version2.split('.')

    for (let i = 0; i < str1.length || i < str2.length;) {
        let n1 = i < str1.length ? parseInt(str1[i]) : 0
        let n2 = i < str2.length ? parseInt(str2[i]) : 0
        if (n1 > n2) return 1
        else if (n1 < n2) return -1
        else i++
    }
    return 0
}

/**
 * @description: Get the current version from the remote branch
 * @param {Branch Object} branch
 * @returns the maximum version
 * @Date: 2019-07-03 13:57:44
 */
function getCurrentRemoteBranch (branch) {
    let currentBranch = ''
    let version2 = '0.0.0'
    for (let version in branch.branches) {
        if (version.startsWith('remotes/origin/') && version.endsWith('.x')) {
            const remoteBranchVersion = version.split('/')[2]
            let version1 = remoteBranchVersion
            // 如果version1>version2返回1，如果version<version2返回-1，否则返回0
            if (compareVersion(version1, version2) == 1) {
                version2 = version1
                currentBranch = remoteBranchVersion
            }
        }
    }
    // console.log('currentBranch--->', currentBranch)
    return currentBranch
}

/**
 * @description: Get the maximum version from the remote branch
 * @param {Object} branch
 * @returns the maximum version
 * @Date: 2019-07-03 13:57:44
 */
function getMaxRemoteReleaseBranch (branch) {
    let currentBranch = ''
    let tempBranch = 0
    let version2 = '0.0.0'
    for (let version in branch.branches) {
        if (version.startsWith('remotes/origin/') && (version.endsWith('.release') || version.endsWith('.hotfix'))) {
            const remoteBranchVersion = version.split('/')[2]
            let version1 = remoteBranchVersion.replace(/\.release|\.hotfix/, '')
            // 如果version1>version2返回1，如果version<version2返回-1，否则返回0
            if (compareVersion(version1, version2) == 1) {
                version2 = version1
                currentBranch = remoteBranchVersion
            }
            /*let temp = parseInt(remoteBranchVersion.replace(/\.|release|hotfix/gm, ''))
            if (temp >= tempBranch) {
                tempBranch = temp
                currentBranch = remoteBranchVersion
            }*/
        }
    }
    console.log('tempBranch--->', tempBranch)
    console.log('currentBranch--->', currentBranch)
    return currentBranch
}

/**
 * @description: Get all of the remote release branch versions
 * @param {type}
 * @Date: 2019-07-09 09:27:57
 */
function getAllRemoteReleaseBranchs (branch) {
    let releaseBranchs = []
    for (let version in branch.branches) {
        if (version.startsWith('remotes/origin/') && (version.indexOf('.release') != -1 || version.indexOf('.hotfix') != -1)) {
            const remoteBranchVersion = version.split('/')[2]
            releaseBranchs.push(remoteBranchVersion)
        }
    }
    // console.log('currentBranch--->', currentBranch)
    return releaseBranchs.reverse()
}

async function getDesc (branch) {
    let initialValue = ''
    if (branch != undefined && branch != '') {
        initialValue = "Mod Change version to " + branch + "."
    }
    return await vscode.window.showInputBox({
        value: initialValue,
        ignoreFocusOut: true,
        placeHolder: 'Add a message for git description',
        prompt: 'Add a message for git description',
        validateInput: function (text) {
            if (text == '') {
                return 'Please add a message for git description.'
            }
            return ''
        }
    })
}

/**
 * @description: Download the script from github
 * @param {string} github url
 * @Date: 2019-07-03 14:06:21
 */
function downloadScripts (url, file) {
    return new Promise((resolve, reject) => {
        axios({
            url: url,
            method: 'GET',
            responseType: 'blob', // important
            headers: {
                'Cache-Control': 'no-cache'
            }
        })
            .then(response => {
                fs.writeFile(file, response.data, err => {
                    if (err) {
                        throw err
                    } else {
                        resolve(file)
                    }
                })
            })
            .catch(function (error) {
                // handle error
                // console.log("error->", error);
                reject(error)
                // throw new Error(error);
            })
    })
}

/**
 * @description: Expose objects to the outside
 * @Date: 2019-07-03 13:58:39
 */
module.exports = {
    chooicingFolder,
    chooicingBranch,
    chooicingRlease,
    chooicingRleaseType,
    listAllRemoteReleaseVersions,
    chooicingTag,
    downloadScripts,
    getDesc
}
