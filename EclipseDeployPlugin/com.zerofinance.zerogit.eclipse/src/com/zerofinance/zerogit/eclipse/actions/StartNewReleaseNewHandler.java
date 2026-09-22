package com.zerofinance.zerogit.eclipse.actions;

public class StartNewReleaseNewHandler extends StartNewReleaseHandler {
    @Override
    protected String commandTitle() {
        return "Start New Release(New)";
    }

    @Override
    protected String scriptFileName() {
        return "ReadyToRelease.sh";
    }
}
