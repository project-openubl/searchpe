package io.github.carlosthe19916.repeid.batchs.persist;

import io.github.carlosthe19916.repeid.batchs.BatchConstants;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemReader;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.nio.charset.Charset;
import java.util.Properties;

@Named
public class TxtReader implements ItemReader {

    @Inject
    private JobContext jobContext;

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
        File file;
        if (fileLocation != null) {
            file = new File(fileLocation);
        } else {
            Properties jobContextProperties = jobContext.getProperties();
            String downloadedFileName = jobContextProperties.getProperty(BatchConstants.UNZIP_FILE);

            file = new File(downloadedFileName);
        }

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
