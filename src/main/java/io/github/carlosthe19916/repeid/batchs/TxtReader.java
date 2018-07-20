package io.github.carlosthe19916.repeid.batchs;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemReader;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Optional;

@Named
public class TxtReader implements ItemReader {

    @Inject
    @BatchProperty
    private String charsetName;

    @Inject
    @BatchProperty
    private String fileLocation;

    @Inject
    @BatchProperty
    private Long skip;

    @Inject
    @BatchProperty
    private Long limit;

    private BufferedReader reader;
    private long readPosition;

    @Override
    public void open(Serializable checkpoint) throws Exception {
        File file = new File(fileLocation);
        FileInputStream fileInputStream = new FileInputStream(file);

        String charset = charsetName != null ? charsetName : Charset.defaultCharset().name();

        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, charset);
        reader = new BufferedReader(inputStreamReader);
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }

    @Override
    public Object readItem() throws Exception {
        Long start = skip != null ? skip : 0L;
        if (readPosition < start) {
            while (readPosition < start) {
                readPosition++;
                reader.readLine();
            }
        }
        if (limit != null && limit > readPosition) {
            return null;
        }

        readPosition++;
        return reader.readLine();
    }

    @Override
    public Serializable checkpointInfo() throws Exception {
        return null;
    }

}
