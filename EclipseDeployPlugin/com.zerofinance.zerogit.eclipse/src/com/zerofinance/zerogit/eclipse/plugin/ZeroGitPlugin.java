package com.zerofinance.zerogit.eclipse.plugin;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class ZeroGitPlugin extends AbstractUIPlugin {
    public static final String PLUGIN_ID = "com.zerofinance.zerogit.eclipse";

    private static ZeroGitPlugin plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static ZeroGitPlugin getDefault() {
        return plugin;
    }
}
