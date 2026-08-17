package com.zerofinance.zerogit.eclipse.flow;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PomSnapshotSupportTest {
    public static void main(String[] args) throws Exception {
        detectsSnapshotInDependency();
        detectsSnapshotViaPropertyReference();
        detectsSnapshotInPlugin();
        ignoresCommentsAndNonSnapshot();
        detectsSnapshotRecursivelyUnderRepoRoot();
    }

    private static void detectsSnapshotInDependency() {
        assertTrue(PomSnapshotSupport.pomContentContainsSnapshot(
                "<project><dependencies><dependency><groupId>x</groupId><artifactId>y</artifactId>"
                        + "<version>1.0.0-SNAPSHOT</version></dependency></dependencies></project>"),
                "dependency snapshot");
    }

    private static void detectsSnapshotViaPropertyReference() {
        assertTrue(PomSnapshotSupport.pomContentContainsSnapshot(
                "<project><properties><foo.version>2.1.0-SNAPSHOT</foo.version></properties>"
                        + "<dependencies><dependency><groupId>x</groupId><artifactId>y</artifactId>"
                        + "<version>${foo.version}</version></dependency></dependencies></project>"),
                "property-referenced snapshot");
    }

    private static void detectsSnapshotInPlugin() {
        assertTrue(PomSnapshotSupport.pomContentContainsSnapshot(
                "<project><build><plugins><plugin><groupId>p</groupId><artifactId>q</artifactId>"
                        + "<version>0.9.0-SNAPSHOT</version></plugin></plugins></build></project>"),
                "plugin snapshot");
    }

    private static void ignoresCommentsAndNonSnapshot() {
        assertFalse(PomSnapshotSupport.pomContentContainsSnapshot(
                "<project><!-- <version>9.9.9-SNAPSHOT</version> -->"
                        + "<dependencies><dependency><groupId>x</groupId><artifactId>y</artifactId>"
                        + "<version>1.0.0</version></dependency></dependencies></project>"),
                "commented snapshot ignored");
    }

    private static void detectsSnapshotRecursivelyUnderRepoRoot() throws IOException {
        Path root = Files.createTempDirectory("pom-snapshot-test");
        Path sub = Files.createTempDirectory(root, "sub");
        writePom(sub, "<project><dependencies><dependency><groupId>x</groupId><artifactId>y</artifactId>"
                + "<version>1.0.0-SNAPSHOT</version></dependency></dependencies></project>");
        try {
            assertTrue(PomSnapshotSupport.containsSnapshot(root.toFile()), "recursive repo scan");
        } finally {
            deleteRecursively(root.toFile());
        }
    }

    private static void writePom(Path dir, String content) throws IOException {
        Files.write(dir.resolve("pom.xml"), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label + " expected true");
        }
    }

    private static void assertFalse(boolean condition, String label) {
        if (condition) {
            throw new AssertionError(label + " expected false");
        }
    }
}
