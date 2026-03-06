package com.zerofinance.zerogitdeploy.action;

import com.zerofinance.zerogitdeploy.handler.ZeroGitFlowHandler;

public class FinishFeatureAction extends BaseZeroGitAction {
    @Override
    protected void execute(ZeroGitFlowHandler handler) throws Exception {
        handler.finishFeature();
    }
}
