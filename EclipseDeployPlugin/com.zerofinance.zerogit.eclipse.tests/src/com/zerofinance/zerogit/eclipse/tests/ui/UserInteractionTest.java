package com.zerofinance.zerogit.eclipse.tests.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.swt.widgets.Shell;
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
}
