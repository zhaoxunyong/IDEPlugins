package com.zerofinance.zerogitdeploy.tools;

import com.intellij.notification.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang.StringUtils;

import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

public final class MessagesUtils {

    private MessagesUtils(){}

//    https://plugins.jetbrains.com/docs/intellij/notifications.html
    @SuppressWarnings("UnstableApiUsage")
    public static void showMessage(Project project, String message, String title, NotificationType type) {
/*        NotificationGroup notificationGroup = new NotificationGroup(title+"Group", NotificationDisplayType.BALLOON, true);
        Notification notification = notificationGroup.createNotification(message, type);
        Notifications.Bus.notify(notification);*/
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Zero Notification Group")
                .createNotification(title, message, type)
                .notify(project);
    }

    public static void showErrorWithDetails(Project project, String title, String summary, String details) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("Zero Notification Group")
                .createNotification(title, summary, NotificationType.ERROR);
        if (StringUtils.isNotBlank(details)) {
            notification.addAction(NotificationAction.createSimple("复制完整错误", () ->
                    CopyPasteManager.getInstance().setContents(new StringSelection(details))));
        }
        notification.notify(project);
    }

    public static String buildDetailedErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error.";
        }
        List<String> chain = new ArrayList<>();
        Throwable current = throwable;
        while (current != null) {
            String simpleName = current.getClass().getSimpleName();
            String msg = StringUtils.trimToEmpty(current.getMessage());
            if (StringUtils.isBlank(msg)) {
                msg = "(no message)";
            }
            chain.add(simpleName + ": " + msg);
            current = current.getCause();
        }
        return String.join("\nCaused by: ", chain);
    }
}
