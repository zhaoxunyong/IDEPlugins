package com.aeasycredit.deployplugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import com.aeasycredit.deployplugin.exception.Exceptionhelper;

/** 
 * ChangeVersionHandler
 * 
 * <p>
 * <a href="ChangeVersionHandler.java"><i>View Source</i></a>
 * </p>
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0 
*/
public class ChangeVersionHandler extends AbstractDeployPluginHandler {
    /**
     * The constructor.
     */
    public ChangeVersionHandler() {
    }

    /**
     * the command has been executed, so extract extract the needed information
     * from the application context.
     */
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
        this.shell = window.getShell();
        this.selection = window.getSelectionService().getSelection();
        try {
        	this.init();
        	if(this.preCheck()) {
    			changeVersion(event);
        	}
		} catch (Exception e) {
//			MessageDialog.openError(shell, "change version error", e.getMessage());
		    MultiStatus status = Exceptionhelper.createMultiStatus(e.getLocalizedMessage(), e);
            // show error dialog
            ErrorDialog.openError(shell, "change version error", e.getMessage(), status);
		}
        return null;
    }
}