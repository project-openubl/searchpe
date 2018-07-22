package io.searchpe.batchs.persist;

import io.searchpe.utils.FileUtils;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemReader;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.nio.charset.Charset;

@Named
public class TxtReader implements ItemReader {

    private static final Logger logger = Logger.getLogger(TxtReader.class);

    @Inject
    private JobContext jobContext;

    @Inject
    @BatchProperty
    private String charsetName;

    @Inject
    @BatchProperty
    private Long skip;

    @Inject
    @BatchProperty
    private String fileLocation;

    private BufferedReader reader;
    private long readPosition;

    private String getCharset() {
        return charsetName != null ? charsetName : Charset.defaultCharset().name();
    }

    @Override
    public void open(Serializable checkpoint) throws Exception {
        File txtFile = new File(fileLocation);
        InputStream is = new FileInputStream(txtFile);

        InputStreamReader inputStreamReader = new InputStreamReader(is, getCharset());
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
                String line = reader.readLine();
                logger.debugf("Line %s ommited [%s]", readPosition, line);
            }
        }

        readPosition++;
        return reader.readLine();
    }

    @Override
    public Serializable checkpointInfo() throws Exception {
        return null;
    }

}
