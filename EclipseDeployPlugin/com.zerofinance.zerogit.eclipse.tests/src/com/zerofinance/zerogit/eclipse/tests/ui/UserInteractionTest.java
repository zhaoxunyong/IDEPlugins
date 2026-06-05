package com.zerofinance.zerogit.eclipse.tests.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.junit.Test;

import com.zerofinance.zerogit.eclipse.ui.UserInteraction;

public class UserInteractionTest {

    @Test
    public void promptAssigneeUsesEditableSelectionDialog() {
        RecordingUserInteraction ui = new RecordingUserInteraction(" faker.zhou ");

        String result = ui.promptAssignee(null, Arrays.asList("faker.zhou", "justin.wang"));

        assertEquals("faker.zhou", result);
        assertTrue(ui.editableSelectionDialogInvoked);
        assertFalse(ui.textInputDialogInvoked);
        assertEquals("ZeroGit: Merge Request", ui.lastTitle);
        assertEquals(Arrays.asList("faker.zhou", "justin.wang"), ui.lastValues);
        assertEquals("faker.zhou", ui.lastDefaultValue);
        assertTrue(ui.lastMessage.contains("assignee"));
        assertTrue(ui.lastMessage.contains("faker.zhou, justin.wang"));
    }

    @Test
    public void promptAssigneeAllowsManualEntryWithoutConfiguredCandidates() {
        RecordingUserInteraction ui = new RecordingUserInteraction(" external.user ");

        String result = ui.promptAssignee(null, Collections.<String>emptyList());

        assertEquals("external.user", result);
        assertTrue(ui.editableSelectionDialogInvoked);
        assertEquals(Collections.emptyList(), ui.lastValues);
        assertEquals("", ui.lastDefaultValue);
    }

    @Test
    public void textInputDialogUsesTopmostModalShellStyle() {
        ExposedUserInteraction ui = new ExposedUserInteraction();
        InputDialog dialog = ui.exposeTextInputDialog(null, "ZeroGit", "message", "value");

        assertTrue((dialog.getShellStyle() & SWT.ON_TOP) != 0);
        assertTrue((dialog.getShellStyle() & SWT.APPLICATION_MODAL) != 0);
    }

    @Test
    public void listSelectionDialogUsesTopmostModalShellStyle() {
        ExposedUserInteraction ui = new ExposedUserInteraction();
        ElementListSelectionDialog dialog = ui.exposeListSelectionDialog(null);

        assertTrue((dialog.getShellStyle() & SWT.ON_TOP) != 0);
        assertTrue((dialog.getShellStyle() & SWT.APPLICATION_MODAL) != 0);
    }

    @Test
    public void editableSelectionDialogUsesTopmostModalShellStyle() {
        ExposedUserInteraction ui = new ExposedUserInteraction();
        Dialog dialog =
                ui.exposeEditableSelectionDialog(null, "ZeroGit", "message", Arrays.asList("a"), "a");

        assertTrue((dialog.getShellStyle() & SWT.ON_TOP) != 0);
        assertTrue((dialog.getShellStyle() & SWT.APPLICATION_MODAL) != 0);
    }

    @Test
    public void confirmDialogUsesTopmostModalShellStyle() {
        ExposedUserInteraction ui = new ExposedUserInteraction();
        MessageDialog dialog = ui.exposeConfirmDialog(null, "ZeroGit", "message");

        assertTrue((dialog.getShellStyle() & SWT.ON_TOP) != 0);
        assertTrue((dialog.getShellStyle() & SWT.APPLICATION_MODAL) != 0);
    }

    @Test
    public void informationDialogUsesTopmostModalShellStyle() {
        ExposedUserInteraction ui = new ExposedUserInteraction();
        MessageDialog dialog = ui.exposeInformationDialog(null, "ZeroGit", "message");

        assertTrue((dialog.getShellStyle() & SWT.ON_TOP) != 0);
        assertTrue((dialog.getShellStyle() & SWT.APPLICATION_MODAL) != 0);
    }

    @Test
    public void warningDialogUsesTopmostModalShellStyle() {
        ExposedUserInteraction ui = new ExposedUserInteraction();
        MessageDialog dialog = ui.exposeWarningDialog(null, "ZeroGit", "message");

        assertTrue((dialog.getShellStyle() & SWT.ON_TOP) != 0);
        assertTrue((dialog.getShellStyle() & SWT.APPLICATION_MODAL) != 0);
    }

    @Test
    public void errorDialogUsesTopmostModalShellStyle() {
        ExposedUserInteraction ui = new ExposedUserInteraction();
        MessageDialog dialog = ui.exposeErrorDialog(null, "ZeroGit", "message");

        assertTrue((dialog.getShellStyle() & SWT.ON_TOP) != 0);
        assertTrue((dialog.getShellStyle() & SWT.APPLICATION_MODAL) != 0);
    }

    private static final class RecordingUserInteraction extends UserInteraction {
        private final String dialogResult;
        private boolean editableSelectionDialogInvoked;
        private boolean textInputDialogInvoked;
        private String lastTitle;
        private String lastMessage;
        private List<String> lastValues;
        private String lastDefaultValue;

        private RecordingUserInteraction(String dialogResult) {
            this.dialogResult = dialogResult;
        }

        @Override
        protected String openEditableSelectionDialog(
                Shell shell,
                String title,
                String message,
                List<String> values,
                String defaultValue) {
            editableSelectionDialogInvoked = true;
            lastTitle = title;
            lastMessage = message;
            lastValues = values;
            lastDefaultValue = defaultValue;
            return dialogResult;
        }

        @Override
        protected String openTextInputDialog(Shell shell, String title, String message, String initialValue) {
            textInputDialogInvoked = true;
            return dialogResult;
        }
    }

    private static final class ExposedUserInteraction extends UserInteraction {
        private InputDialog exposeTextInputDialog(Shell shell, String title, String message, String initialValue) {
            return createTextInputDialog(shell, title, message, initialValue);
        }

        private ElementListSelectionDialog exposeListSelectionDialog(Shell shell) {
            return createListSelectionDialog(shell);
        }

        private Dialog exposeEditableSelectionDialog(
                Shell shell,
                String title,
                String message,
                List<String> values,
                String defaultValue) {
            return createEditableSelectionDialog(shell, title, message, values, defaultValue);
        }

        private MessageDialog exposeConfirmDialog(Shell shell, String title, String message) {
            return createConfirmDialog(shell, title, message);
        }

        private MessageDialog exposeInformationDialog(Shell shell, String title, String message) {
            return createInformationDialog(shell, title, message);
        }

        private MessageDialog exposeWarningDialog(Shell shell, String title, String message) {
            return createWarningDialog(shell, title, message);
        }

        private MessageDialog exposeErrorDialog(Shell shell, String title, String message) {
            return createErrorDialog(shell, title, message);
        }
    }
}
