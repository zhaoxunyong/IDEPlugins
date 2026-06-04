package com.zerofinance.zerogit.eclipse.tests;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.junit.Test;

public class ZeroGitPluginSmokeTest {

    @Test
    public void allZeroGitCommandsAreDefined() {
        ICommandService commandService =
                PlatformUI.getWorkbench().getService(ICommandService.class);
        assertNotNull("Workbench command service should be available in PDE tests", commandService);

        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.generateCommitMessage").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.aiCodeReview").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.mavenChange").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.startNewFeature").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.finishFeature").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.rebaseFeature").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.mergeRequest").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.startNewRelease").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.finishRelease").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.startNewHotfix").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.finishHotfix").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.runCiCommand").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.gitflowGuideline").isDefined());
    }
}
