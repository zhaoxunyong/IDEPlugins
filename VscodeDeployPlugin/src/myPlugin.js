const vscode = require('vscode')
const axios = require('axios')
const fs = require('fs')

/**
 * @description: Download the script from github
 * @param {string} gitlab url
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
                reject(error)
            })
    })
}

/**
 * @description: Expose objects to the outside
 * @Date: 2019-07-03 13:58:39
 */
module.exports = {
    downloadScripts
}
