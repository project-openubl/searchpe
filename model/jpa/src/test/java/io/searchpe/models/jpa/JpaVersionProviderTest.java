package io.searchpe.models.jpa;

import io.quarkus.test.junit.QuarkusTest;
import io.searchpe.models.Status;
import io.searchpe.models.VersionModel;
import io.searchpe.models.VersionProvider;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class JpaVersionProviderTest {

    @Inject
    VersionProvider versionProvider;

    @Test
    public void addVersion() {
        VersionModel version1 = versionProvider.addVersion();
        assertNewVersion(version1);

        VersionModel version2 = versionProvider.addVersion();
        assertNewVersion(version2);

        assertEquals(version1.getNumber(), version2.getNumber() - 1);
    }

    @Test
    public void getActiveVersion() {
        Optional<VersionModel> activeVersion = versionProvider.getActiveVersion();

        assertFalse(activeVersion.isPresent());
    }

    private void assertNewVersion(VersionModel version) {
        assertNotNull(version);
        assertNotNull(version.getCreatedAt());
        assertFalse(version.isActive());
        assertTrue(version.getId() > 0);
        assertTrue(version.getNumber() > 0);
        assertEquals(Status.NOT_PROCESSED, version.getStatus());
    }

}
