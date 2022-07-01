package com.aeasycredit.deployplugin.exception;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;

/**
 * Exceptionhelper
 * 
 * <p>
 * <a href="Exceptionhelper.java"><i>View Source</i></a>
 * </p>
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0 
 */
public class Exceptionhelper {
    
    public static MultiStatus createMultiStatus(String msg, Throwable t) {

        List<Status> childStatuses = new ArrayList<>();
        StackTraceElement[] stackTraces = Thread.currentThread().getStackTrace();

        for (StackTraceElement stackTrace: stackTraces) {
            Status status = new Status(IStatus.ERROR,
                    "com.example.e4.rcp.todo", stackTrace.toString());
            childStatuses.add(status);
        }

        MultiStatus ms = new MultiStatus("com.example.e4.rcp.todo",
                IStatus.ERROR, childStatuses.toArray(new Status[] {}),
                t.toString(), t);
        return ms;
    }
    
}
