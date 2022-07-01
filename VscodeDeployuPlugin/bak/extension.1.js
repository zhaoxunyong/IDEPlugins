// https://segmentfault.com/a/1190000008968904
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
const vscode = require('vscode');
// const {chooicingFolder, chooicingBranch} = require("./MyPlugin");
const myPlugin = require("./MyPlugin");
const simpleGit = require('simple-git')
let mdTml = vscode.window.createTerminal("MyOpen");

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed

/**
 * @param {vscode.ExtensionContext} context
 */
function activate(context) {

	// Use the console to output diagnostic information (console.log) and errors (console.error)
	// This line of code will only be executed once when your extension is activated
	console.log('Congratulations, your extension "my-first-plugin" is now active!');

	// The command has been defined in the package.json file
	// Now provide the implementation of the command with  registerCommand
	// The commandId parameter must match the command field in package.json
	let disposable = vscode.commands.registerCommand('extension.sayHello', function () {
		// The code you place here will be executed every time your command is executed

		// Display a message box to the user
		// vscode.window.showInformationMessage('Hello World!');

		/*let editor = vscode.window.activeTextEditor;
		if (!editor) {
			return; // No open text editor
		}
	
		let selection = editor.selection;
		let text = editor.document.getText(selection);
	
		// Display a message box to the user
		vscode.window.showInformationMessage('Selected characters: ' + text.length);*/
		
		/*var files = vscode.workspace.getWorkspaceFolder;
		vscode.window.showInformationMessage(files)*/
		

		// vscode.window.showWorkspaceFolderPick().then(selectedItem => {
		// 	vscode.window.showInformationMessage("selectedItem--->"+selectedItem.name+"/"+selectedItem.uri);

		// 	vscode.window.showQuickPick(items, { matchOnDetail: true, matchOnDescription: true }).then(item => {
		// 		vscode.window.showInformationMessage("item--->"+item);
		// 		let cmdStr = `bash ./test.sh`;
		// 		mdTml.show(false);
		// 		mdTml.sendText(cmdStr);
		// 	});
		// });
		myPlugin.chooicingFolder().then(selectedItem => {
			console.log("selectedItem=>"+selectedItem.name+"/"+selectedItem.uri);
			myPlugin.chooicingBranch(simpleGit(selectedItem.uri.fsPath)).then(item => {
				/* vscode.commands.getCommands().then(command => {
					command.forEach(c => {
						console.log("c--->"+c);
					});
				}); */
			});
			// vscode.commands.executeCommand("git.branch")
		});
	});

	context.subscriptions.push(disposable);
}
exports.activate = activate;

// this method is called when your extension is deactivated
function deactivate() {}

module.exports = {
	activate,
	deactivate
}
