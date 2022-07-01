const vscode = require('vscode');

function chooicingFolder() {
    return new Promise((resolve, reject) => {
        vscode.window.showWorkspaceFolderPick().then(selectedItem => {
            if (selectedItem) {
                // vscode.window.showInformationMessage(`You have selected ${selectedItem.name}`);
                resolve(selectedItem);
            } /* else {
                reject(new Error('At lease you should chooice a folder!'));
            } */
        });
    });
}

function chooicingBranch(simpleGit) {
    return new Promise((resolve, reject) => {
        simpleGit.branch((err, branch)=> {
            let currentBranch = branch.current;
            const currentBranchs = currentBranch.split('.')
            // Get next branch
            let nextBranch = '';
            if(currentBranch.endsWith('.x') && currentBranchs.length == 3) {
                const [p1,p2,p3] = currentBranchs;
                const nextP2 = parseInt(p2)+1;
                nextBranch = p1+'.'+nextP2+".x";
            }
    
            // Set next branch
            // let items = [];
            // if(nextBranch != '') {
            //     items.push({ description: 'Next branch', label: nextBranch, value: nextBranch });
            // } else {
            //     items.push({ description: 'Please ', label: nextBranch, value: nextBranch });
            // }
            // items.push({ description: 'Current branch', label: currentBranch, value: currentBranch });
            vscode.window.showInputBox(
                { // 这个对象中所有参数都是可选参数
                    password:false, // 输入内容是否是密码
                    ignoreFocusOut: false, // 默认false，设置为true时鼠标点击别的地方输入框不会消失
                    placeHolder:'Fill in new branch', // 在输入框内的提示信息
                    prompt:'The new branch has been filled in!', // 在输入框下方的提示信息
                    value: nextBranch,
                    validateInput: function (text) {
                        if(text == '' || !text.endsWith('.x')) {
                            return 'Please fill in a correct branch name: similar to 1.10.x 1.11.x and so on.';
                        } 
                        return ''; 
                    } // 对输入内容进行验证并返回
            }).then(function(newBranch){
                // console.log("用户输入："+msg);
                // vscode.window.showInformationMessage(msg);
                resolve(newBranch);
            });
            // vscode.window.showInformationMessage("请问你现在的心情如何",'你说什么','我不知道','再见！')
            // .then(function(select){
            //     console.log(select);
            // });
    
            /* for(key in branch.branches) {
                if(!key.startsWith("remotes/")) {
                    items.push({ description: key, label: key, value: key });
                }
            } */
            
            // return new Promise((resolve, reject) => {
            //     vscode.window.showQuickPick(items, { matchOnDetail: true, matchOnDescription: true }).then(item => {
            //         if(item) {
            //             vscode.window.showInformationMessage("item--->"+item);
            //             resolve(item);
            //         } /* else {
            //             reject(new Error('At lease you should chooice a item!'));
            //         } */
            //         // let cmdStr = `bash ./test.sh`;
            //         // mdTml.show(false);
            //         // mdTml.sendText(cmdStr);
            //     });
            // });
        });

    });
}

module.exports = {
	chooicingFolder,
	chooicingBranch
}