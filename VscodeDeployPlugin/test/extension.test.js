/* global suite, test */

//
// Note: This example test is leveraging the Mocha test framework.
// Please refer to their documentation on https://mochajs.org/ for help.
//

// The module 'assert' provides assertion methods from node
const assert = require('assert');
const Module = require('module');
const originalLoad = Module._load;
Module._load = function(request) {
	if (request === 'vscode') {
		return {};
	}
	return originalLoad.apply(this, arguments);
};
const extension = require('../src/extension');
Module._load = originalLoad;

// You can import and use all API from the 'vscode' module
// as well as import your extension to test it
// const vscode = require('vscode');
// const myExtension = require('../extension');

// Defines a Mocha test suite to group tests of similar kind together
suite("Extension Tests", function() {

	// Defines a Mocha unit test
	test("Something 1", function() {
		assert.equal(-1, [1, 2, 3].indexOf(5));
		assert.equal(-1, [1, 2, 3].indexOf(0));
	});

	test("AI Code Review 未输入提交范围时不传脚本参数", function() {
		assert.deepEqual([], extension.buildAiCodeReviewScriptArgs('   '));
	});

	test("AI Code Review 仅将提交范围传给脚本", function() {
		assert.deepEqual(['HEAD~2 HEAD'], extension.buildAiCodeReviewScriptArgs(' HEAD~2 HEAD '));
	});
});
