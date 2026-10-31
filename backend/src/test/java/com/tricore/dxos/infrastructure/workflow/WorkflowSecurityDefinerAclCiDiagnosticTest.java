package com.tricore.dxos.infrastructure.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Set;

/**
 * CI-only diagnostic. Emits sanitized ACL categories, never user names,
 * directory paths, credentials, or file contents. Not for integration into main.
 */
class WorkflowSecurityDefinerAclCiDiagnosticTest {
    @TempDir Path temporary;

    @Test
    void reportWhichProtectedDirectoryPreconditionRejectsTheWindowsRunner() throws Exception {
        Path dir = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary);
        System.out.println("DIAG_ABSOLUTE=" + dir.isAbsolute());
        System.out.println("DIAG_IS_DIR=" + Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS));
        System.out.println("DIAG_REAL_EQUALS_NORMALIZED=" + dir.toRealPath().equals(dir.normalize()));
        boolean insideRepo = false;
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) insideRepo = true;
        }
        System.out.println("DIAG_HAS_GIT_PARENT=" + insideRepo);
        try (var files = Files.list(dir)) {
            System.out.println("DIAG_EMPTY=" + files.findAny().isEmpty());
        }
        var acl = Files.getFileAttributeView(dir, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        System.out.println("DIAG_ACL_VIEW=" + (acl != null));
        if (acl != null) {
            var owner = acl.getOwner();
            var entries = acl.getAcl();
            System.out.println("DIAG_ACL_COUNT=" + entries.size());
            boolean inheritableOwner = false;
            int othersAllowed = 0;
            for (AclEntry entry : entries) {
                String category = entry.principal().equals(owner) ? "OWNER"
                        : entry.principal().getName().equalsIgnoreCase("NT AUTHORITY\\SYSTEM") ? "SYSTEM"
                        : "OTHER";
                System.out.println("DIAG_ACE=" + entry.type() + ":" + category
                        + ":FILE_INHERIT=" + entry.flags().contains(AclEntryFlag.FILE_INHERIT)
                        + ":DIR_INHERIT=" + entry.flags().contains(AclEntryFlag.DIRECTORY_INHERIT)
                        + ":READ_DATA=" + entry.permissions().contains(AclEntryPermission.READ_DATA)
                        + ":WRITE_DATA=" + entry.permissions().contains(AclEntryPermission.WRITE_DATA));
                if (entry.type() == AclEntryType.ALLOW && category.equals("OTHER")) othersAllowed++;
                if (entry.type() == AclEntryType.ALLOW && category.equals("OWNER")
                        && entry.flags().contains(AclEntryFlag.FILE_INHERIT)
                        && entry.permissions().containsAll(Set.of(AclEntryPermission.READ_DATA, AclEntryPermission.WRITE_DATA)))
                    inheritableOwner = true;
            }
            System.out.println("DIAG_OTHERS_ALLOWED=" + othersAllowed);
            System.out.println("DIAG_INHERITABLE_OWNER=" + inheritableOwner);
        }
        try {
            WorkflowEventTriggerReview.verifyDirectory(dir);
            System.out.println("DIAG_VERIFY_DIRECTORY=PASS");
        } catch (Exception ex) {
            System.out.println("DIAG_VERIFY_DIRECTORY=FAIL:" + ex.getClass().getSimpleName());
        }
    }
    @Test
    void verifyCanonicalRootAndRemoveUnexpectedAllowsInTemporaryFixture() throws Exception {
        // Changes only a disposable JUnit directory, never the production security gate.
        Path dir = WorkflowSecurityDefinerReviewTest.protectedDirectory(temporary.toRealPath());
        var acl = Files.getFileAttributeView(dir, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        var owner = acl.getOwner();
        var restricted = acl.getAcl().stream()
                .filter(entry -> entry.type() != AclEntryType.ALLOW
                        || entry.principal().equals(owner)
                        || entry.principal().getName().equalsIgnoreCase("NT AUTHORITY\\SYSTEM"))
                .toList();
        acl.setAcl(restricted);
        boolean same = dir.toRealPath().equals(dir.normalize());
        long unexpectedAllow = acl.getAcl().stream().filter(entry -> entry.type() == AclEntryType.ALLOW
                && !entry.principal().equals(owner)
                && !entry.principal().getName().equalsIgnoreCase("NT AUTHORITY\\SYSTEM")).count();
        System.out.println("FIX_DIAG_REAL_EQUALS_NORMALIZED=" + same);
        System.out.println("FIX_DIAG_OTHER_ALLOWS=" + unexpectedAllow);
        try {
            WorkflowEventTriggerReview.verifyDirectory(dir);
            System.out.println("FIX_DIAG_VERIFY_DIRECTORY=PASS");
        } catch (Exception ex) {
            System.out.println("FIX_DIAG_VERIFY_DIRECTORY=FAIL:" + ex.getClass().getSimpleName());
        }
    }

}
