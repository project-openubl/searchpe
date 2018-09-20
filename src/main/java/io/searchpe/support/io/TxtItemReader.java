package io.searchpe.support.io;

import org.jberet.support._private.SupportLogger;
import org.jberet.support._private.SupportMessages;
import org.jberet.support.io.*;
import org.jboss.logging.Logger;
import org.supercsv.io.ICsvBeanReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.io.ICsvReader;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemReader;
import javax.batch.operations.BatchRuntimeException;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;

import static org.jberet.support.io.CsvProperties.BEAN_TYPE_KEY;

@Named
@Dependent
public class TxtItemReader extends TxtItemReaderWriterBase implements ItemReader {

    private static final Logger logger = Logger.getLogger(TxtItemReader.class);

    /**
     * Specifies the start position (a positive integer starting from 1) to read the data. If reading from the beginning
     * of the input CSV, there is no need to specify this property.
     */
    @Inject
    @BatchProperty
    protected int start;

    /**
     * Specify the end position in the data set (inclusive). Optional property, and defaults to {@code Integer.MAX_VALUE}.
     * If reading till the end of the input CSV, there is no need to specify this property.
     */
    @Inject
    @BatchProperty
    protected int end;

    /**
     * Indicates that the input TXT resource does not contain header row. Optional property, valid values are
     * {@code true} or {@code false}, and the default is {@code false}.
     */
    @Inject
    @BatchProperty
    protected boolean headerless;

    protected ICsvReader delegateReader;

    @Override
    public void open(final Serializable checkpoint) throws Exception {
        /*
         * The row number to start reading.  It may be different from the injected field start. During a restart,
         * we would start reading from where it ended during the last run.
         */
        if (this.end == 0) {
            this.end = Integer.MAX_VALUE;
        }
        int startRowNumber = checkpoint == null ? this.start : (Integer) checkpoint;
        if (startRowNumber < this.start || startRowNumber > this.end || startRowNumber < 0) {
            throw new BatchRuntimeException(String.format("Invalid position %s to start reading, the configured range is between %s and %s", startRowNumber, this.start, this.end));
        }
        if (headerless) {
            startRowNumber--;
            this.end--;
        }

        if (beanType == null) {
            throw new BatchRuntimeException(String.format("Invalid reader or writer property value %s for key %s", null, BEAN_TYPE_KEY));
        }
        final InputStream inputStream = getInputStream(resource, true);
        final InputStreamReader r = charset == null ? new InputStreamReader(inputStream) : new InputStreamReader(inputStream, charset);
        if (java.util.List.class.isAssignableFrom(beanType)) {
            delegateReader = new FastForwardTxtListReader(r, getCsvPreference(), startRowNumber);
        } else if (java.util.Map.class.isAssignableFrom(beanType)) {
            delegateReader = new FastForwardTxtMapReader(r, getCsvPreference(), startRowNumber);
        } else {
            delegateReader = new FastForwardTxtBeanReader(r, getCsvPreference(), startRowNumber);
        }
        logger.info(String.format("Opening resource %s in %s", resource, this.getClass()));

        if (!headerless) {
            final String[] header;
            try {
                header = delegateReader.getHeader(true);    //first line check true
            } catch (final IOException e) {
                throw SupportMessages.MESSAGES.failToReadCsvHeader(e, resource);
            }
            if (this.nameMapping == null) {
                this.nameMapping = header;
            }
        }
        this.cellProcessorInstances = getCellProcessors();
    }

    @Override
    public void close() throws Exception {
        if (delegateReader != null) {
            SupportLogger.LOGGER.closingResource(resource, this.getClass());
            delegateReader.close();
            delegateReader = null;
        }
    }

    @Override
    public Object readItem() throws Exception {
        if (delegateReader.getRowNumber() > this.end) {
            return null;
        }
        final Object result;
        if (delegateReader instanceof ICsvBeanReader) {
            if (cellProcessorInstances.length == 0) {
                result = ((ICsvBeanReader) delegateReader).read(beanType, getNameMapping());
            } else {
                result = ((ICsvBeanReader) delegateReader).read(beanType, getNameMapping(), cellProcessorInstances);
            }
            if (!skipBeanValidation) {
                ItemReaderWriterBase.validate(result);
            }
        } else if (delegateReader instanceof ICsvListReader) {
            if (cellProcessorInstances.length == 0) {
                result = ((ICsvListReader) delegateReader).read();
            } else {
                result = ((ICsvListReader) delegateReader).read(cellProcessorInstances);
            }
        } else {
            if (cellProcessorInstances.length == 0) {
                result = ((ICsvMapReader) delegateReader).read(getNameMapping());
            } else {
                result = ((ICsvMapReader) delegateReader).read(getNameMapping(), cellProcessorInstances);
            }
        }
        return result;
    }

    @Override
    public Integer checkpointInfo() {
        return delegateReader.getRowNumber();
    }

}
