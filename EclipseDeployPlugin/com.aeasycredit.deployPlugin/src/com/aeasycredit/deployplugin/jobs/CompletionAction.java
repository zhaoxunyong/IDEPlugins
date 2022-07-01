package com.aeasycredit.deployplugin.jobs;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * CompletionAction
 * 
 * <p>
 * <a href="CompletionAction.java"><i>View Source</i></a>
 * </p>
 * 
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0
 */
public class CompletionAction extends Action {
    private boolean ok;
    private String okTitle;
    private String okMsg;
    private String failMsg;
    private String failTitle;
    private Throwable throwable;

    private Refreshable refreshable;
    private Shell shell;
    private String pluginId;

    private MessageConsoleStream console;

    /**
     * @param pluginId
     * @param shell
     * @param refreshable
     */
    public CompletionAction(String pluginId, Shell shell, Refreshable refreshable, MessageConsoleStream console) {
        this.pluginId = pluginId;
        this.shell = shell;
        this.refreshable = refreshable;
        this.console = console;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public void setOkTitle(String okTitle) {
        this.okTitle = okTitle;
    }

    public void setOkMsg(String okMsg) {
        this.okMsg = okMsg;
    }

    public void setFailMsg(String failMsg) {
        this.failMsg = failMsg;
    }

    public void setFailTitle(String failTitle) {
        this.failTitle = failTitle;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public boolean isOk() {
        return ok;
    }

    public Shell getShell() {
        return shell;
    }

    public void setShell(Shell shell) {
        this.shell = shell;
    }

    public MessageConsoleStream getConsole() {
        return console;
    }

    public void setConsole(MessageConsoleStream console) {
        this.console = console;
    }

    public Refreshable getRefreshable() {
        return refreshable;
    }

    public void setRefreshable(Refreshable refreshable) {
        this.refreshable = refreshable;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getOkTitle() {
        return okTitle;
    }

    public String getOkMsg() {
        return okMsg;
    }

    public String getFailMsg() {
        return failMsg;
    }

    public String getFailTitle() {
        return failTitle;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void run() {
        // refreshable.refresh();
        // then show the dialog
        if (ok) {
            // MessageDialog.openInformation(shell, okTitle, okMsg);
        } else {
            Status status = new Status(IStatus.ERROR, pluginId, throwable.getLocalizedMessage(), throwable);
            ErrorDialog.openError(shell, failTitle, failMsg, status);
        }
    }
}
