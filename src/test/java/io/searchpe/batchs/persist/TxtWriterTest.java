package io.searchpe.batchs.persist;

import io.searchpe.model.Company;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TxtWriterTest {

    @Test
    public void writeItems() throws Exception {
        EntityManager em = mock(EntityManager.class);
        doNothing().when(em).persist(any(Company.class));

        TxtVersion txtVersion = new TxtVersion();

        TxtWriter txtWriter = spy(new TxtWriter());
        txtWriter.setTxtVersion(txtVersion);
        when(txtWriter.getEntityManager()).thenReturn(em);

        List<Object> companies = new ArrayList<>();
        companies.add(new Company());
        companies.add(new Company());

        txtWriter.writeItems(companies);
    }

}