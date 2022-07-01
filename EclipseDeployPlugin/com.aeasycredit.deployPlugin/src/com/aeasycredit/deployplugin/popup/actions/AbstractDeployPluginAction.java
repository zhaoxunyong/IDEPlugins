package com.aeasycredit.deployplugin.popup.actions;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.internal.registry.IWorkbenchRegistryConstants;

import com.aeasycredit.deployplugin.exception.Exceptionhelper;

/**
 * AbstractDeployPluginAction
 * 
 * <p>
 * <a href="AbstractSlicePluginAction.java"><i>View Source</i></a>
 * </p>
 * 
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0
 */
@SuppressWarnings("restriction")
public abstract class AbstractDeployPluginAction implements IObjectActionDelegate, IExecutableExtension {
    private static final String PARM_COMMAND_ID = "commandId";
    
    private IWorkbenchPart targetPart;
    private IHandlerService handlerService = null;
    private String commandId = null;
    @SuppressWarnings("rawtypes")
    private Map parameterMap = null;


    /**
     * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
     */
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        this.targetPart = targetPart;
    }

    /**
     * @see IActionDelegate#run(IAction)
     */
    public void run(IAction action) {
        if(handlerService==null){
            handlerService =(IHandlerService) targetPart.getSite().getService(IHandlerService.class);
        }
        Shell shell = targetPart.getSite().getShell();
        try {
            handlerService.executeCommand(commandId, null);
        } catch (Exception e) {
//            MessageDialog.openError(shell, "DeployPlugin", e.getMessage());
            MultiStatus status = Exceptionhelper.createMultiStatus(e.getLocalizedMessage(), e);
            // show error dialog
            ErrorDialog.openError(shell, "DeployPlugin", e.getMessage(), status);
        }
    }
    
    /**
     * @see IActionDelegate#selectionChanged(IAction, ISelection)
     */
    public void selectionChanged(IAction action, ISelection selection) {
    }

    /* (non-Javadoc)
     * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
     */
    @SuppressWarnings("rawtypes")
	@Override
    public void setInitializationData(IConfigurationElement config,
            String propertyName, Object data) throws CoreException {
        String id = config.getAttribute(IWorkbenchRegistryConstants.ATT_ID);
        // save the data until our init(*) call, where we can get
        // the services.
        if (data instanceof String) {
            commandId = (String) data;
        } else if (data instanceof Map) {
            parameterMap = (Map) data;
            if (parameterMap.get(PARM_COMMAND_ID) == null) {
                Status status = new Status(IStatus.ERROR,
                        "org.eclipse.ui.tests", "The '" + id
                                + "' action won't work without a commandId");
                throw new CoreException(status);
            }
        } else {
            Status status = new Status(
                    IStatus.ERROR,
                    "org.eclipse.ui.tests",
                    "The '"
                            + id
                            + "' action won't work without some initialization parameters");
            throw new CoreException(status);
        }
    }
}
