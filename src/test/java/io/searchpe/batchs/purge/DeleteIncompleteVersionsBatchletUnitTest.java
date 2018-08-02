package io.searchpe.batchs.purge;

import io.searchpe.model.Version;
import io.searchpe.services.VersionService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import javax.batch.runtime.BatchStatus;
import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class DeleteIncompleteVersionsBatchletUnitTest {

    @Mock
    private VersionService versionService;

    @Spy
    private DeleteIncompleteVersionsBatchlet batchlet = new DeleteIncompleteVersionsBatchlet();

    @Test
    public void shouldNeverDeleteAnyVersion() throws Exception {
        Mockito.when(batchlet.getDeleteIncompleteVersions()).thenReturn(false);
        String processResult = batchlet.process();
        Mockito.verify(batchlet, Mockito.atLeastOnce()).getDeleteIncompleteVersions();

        Mockito.verify(versionService, Mockito.never()).deleteVersion(null);
        Mockito.verifyNoMoreInteractions(versionService);

        Assert.assertEquals(BatchStatus.COMPLETED.toString(), processResult);
    }

    @Test
    public void deleteIncompleteAnyVersion() throws Exception {
        List<Version> versions = new ArrayList<>();

        Version version1 = new Version();
        version1.setId("1");
        version1.setNumber(1);
        version1.setComplete(true);

        Version version2 = new Version();
        version2.setId("2");
        version2.setNumber(2);
        version2.setComplete(true);

        versions.add(version1);
        versions.add(version2);


        Mockito.when(batchlet.getDeleteIncompleteVersions()).thenReturn(true);
        Mockito.when(batchlet.getVersionService()).thenReturn(versionService);
        Mockito.when(versionService.getVersionsByParameters(Mockito.notNull())).thenReturn(versions);


        String processResult = batchlet.process();


        Mockito.verify(batchlet, Mockito.atLeastOnce()).getDeleteIncompleteVersions();
        Mockito.verify(versionService, Mockito.times(2)).deleteVersion(Mockito.any());

        Assert.assertEquals(BatchStatus.COMPLETED.toString(), processResult);
    }

}