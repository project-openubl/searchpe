package io.searchpe.batchs.expired;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import javax.batch.runtime.BatchStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DeleteExpiredVersionsBatchletTest {

    @Test
    public void test_shouldDoNothingBecauseOfNoExpiration() throws Exception {
        DeleteExpiredVersionsBatchlet batchlet = Mockito.spy(new DeleteExpiredVersionsBatchlet());
        batchlet.setExpirationTimeInMillis(0);

        assertEquals(BatchStatus.COMPLETED.toString(), batchlet.process());

        verify(batchlet, never()).getVersionService();
    }

    @Test
    public void test_shouldCallToDeleteVersionService() throws Exception {
        List<Version> expiredVersions = new ArrayList<>();
        expiredVersions.add(new Version());
        expiredVersions.add(new Version());

        VersionService versionService = Mockito.mock(VersionService.class);
        when(versionService.getCompleteVersionsBefore(any(Date.class))).thenReturn(expiredVersions);


        DeleteExpiredVersionsBatchlet batchlet = new DeleteExpiredVersionsBatchlet();
        batchlet.setVersionService(versionService);

        batchlet.setExpirationTimeInMillis(100);
        assertEquals(BatchStatus.COMPLETED.toString(), batchlet.process());

        verify(versionService, times(1)).getCompleteVersionsBefore(any(Date.class));
        verify(versionService, times(1)).deleteVersion(any(Version.class)); // should skip 1 for having at least one complete version
    }
}