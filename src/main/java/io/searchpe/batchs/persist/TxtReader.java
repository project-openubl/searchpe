package io.searchpe.batchs.persist;

import io.searchpe.batchs.BatchConstants;
import io.searchpe.batchs.BatchUtils;
import io.searchpe.batchs.persist.exceptions.MoreThanOneFileToChooseException;
import io.searchpe.batchs.persist.exceptions.NothingToReadException;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemReader;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Named
public class TxtReader implements ItemReader {

    private static final Logger logger = Logger.getLogger(TxtReader.class);

    @Inject
    @BatchProperty
    private Long skip;

    @Inject
    @BatchProperty
    private String charsetName;

    @Inject
    @BatchProperty
    private File workingDirectory;

    private BufferedReader reader;
    private InputStream inputStream;

    @Override
    public void open(Serializable checkpoint) throws Exception {
        Path unzipDirPath = BatchUtils.getWorkingPath(getWorkingDirectory(), BatchConstants.BATCH_UNZIP_FOLDER);
        List<Path> txtFiles = Files.walk(unzipDirPath)
                .filter(path -> path.toFile().getName().endsWith(".txt"))
                .collect(Collectors.toList());

        if (txtFiles.isEmpty()) {
            throw new NothingToReadException("Could not find any *.txt file to open()");
        }
        if (txtFiles.size() > 1) {
            throw new MoreThanOneFileToChooseException("Found more than one *.txt file to open()");
        }

        Path txtPath = txtFiles.get(0);
        inputStream = new FileInputStream(txtPath.toFile());

        String charset = getCharsetName() != null ? getCharsetName() : Charset.defaultCharset().name();
        InputStreamReader inputStreamReader = new InputStreamReader(inputStream, charset);

        reader = new BufferedReader(inputStreamReader);
    }

    @Override
    public void close() throws Exception {
        if (inputStream != null) {
            inputStream.close();
        }
        if (reader != null) {
            reader.close();
        }
    }

    @Override
    public Object readItem() throws Exception {
        if (getSkip() != null) {
            long count = 0L;
            while (count < getSkip()) {
                String line = reader.readLine();
                logger.debugf("Line %s omitted [%s]", count, line);
                count++;
            }
        }
        return reader.readLine();
    }

    @Override
    public Serializable checkpointInfo() throws Exception {
        // restart not supported
        return null;
    }

    public Long getSkip() {
        return skip;
    }

    public void setSkip(Long skip) {
        this.skip = skip;
    }

    public String getCharsetName() {
        return charsetName;
    }

    public void setCharsetName(String charsetName) {
        this.charsetName = charsetName;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }
}
