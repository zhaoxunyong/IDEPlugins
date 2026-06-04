package com.zerofinance.zerogit.eclipse.tests;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class PackagingSmokeTest {

    @Test
    public void newFeatureAndUpdateSiteProjectsExist() {
        assertTrue(new File("EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.feature/feature.xml").isFile());
        assertTrue(new File("EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.updatesite/site.xml").isFile());
    }
}
