package com.aeasycredit.deployplugin.jobs;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.progress.IProgressConstants;

import com.aeasycredit.deployplugin.DeployPluginHelper;
import com.aeasycredit.deployplugin.DeployPluginLauncherPlugin;
import com.aeasycredit.deployplugin.handlers.ANSIColor;

/**
 *  http://blog.eitchnet.ch/archives/46
 * 
 * <p>
 * <a href="ListenerJob.java"><i>View Source</i></a>
 * </p>
 * 
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0
 */
public class ListenerJob extends Job {

    // private MessageConsoleStream console;
    // private Shell shell;
    // private IProject project;
    private CompletionAction completionAction;
    
    private ListenerHandler listenerHandler;

    /**
     * @param name
     * @param completedAction
     */
    public ListenerJob(String name, CompletionAction completionAction, ListenerHandler listenerHandler) {
        super(name);
        // this.console = console;
        // this.shell = shell;
        // this.project = project;
        this.completionAction = completionAction;
        this.listenerHandler = listenerHandler;
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
        IStatus status = null;
        completionAction.getConsole().println("loading...");
        // activate the progress bar with an unknown amount of task work
        monitor.beginTask("Loading " + getName(), IProgressMonitor.UNKNOWN);
        // perform the job
        try {
            // execute task work...
            boolean ok = true;
            
            try {
//            	completionAction.getConsole().println(ANSIColor.RED_BRIGHT.toString());
                listenerHandler.process();
//                completionAction.getConsole().println(ANSIColor.RESET.toString());
            } catch (Exception e) {
                ok = false;
                throw new Exception("Listener Execute Failed: "+e.getMessage());
            }
            completionAction.setOk(ok);
            completionAction.getConsole().println(this.getName()+(ok?" OK.":" ERROR."));
            // refresh workspace
            completionAction.getRefreshable().refresh();
            status = Status.OK_STATUS;
            // at the end of the successfully ended work, set the completion
            // task to be ok
        } catch (Exception e) {
            e.printStackTrace();
            // logger.error(e1, e1);
            // if the work failed then set the completion
            // task to be NOT ok and set the exception so it
            // can be shown to the user
            completionAction.setThrowable(e);
            completionAction.getConsole().println("ERROR.");
            completionAction.setOk(false);
            status = new Status(IStatus.ERROR, DeployPluginHelper.PLUGIN_ID, e.getLocalizedMessage(), e);
        }
        // stop the monitor
        monitor.done();
        // execute the completion task
        complete();
        return status;
    }

    /**
     * completes the task by showing the user a dialog about the execution state
     * of the job
     */
    protected void complete() {
        setProperty(IProgressConstants.ICON_PROPERTY, DeployPluginLauncherPlugin.getImageDescriptor("/icons/sample.gif"));
        Boolean isModal = (Boolean) this.getProperty(IProgressConstants.PROPERTY_IN_DIALOG);
        if (isModal != null && isModal.booleanValue()) {
            // The progress dialog is still open so
            // just open the message
            showResults(completionAction);
        } else {
            setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
            setProperty(IProgressConstants.ACTION_PROPERTY, completionAction);
        }
    }

    /**
     * Asynchronous execution of an {@link Action}
     * 
     * @param action
     */
    protected static void showResults(final Action action) {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                action.run();
            }
        });
    }
}