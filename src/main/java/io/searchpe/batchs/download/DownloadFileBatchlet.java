package io.searchpe.batchs.download;

import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

@Named
public class DownloadFileBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(DownloadFileBatchlet.class);

    @Inject
    @BatchProperty
    private String url;

    @Inject
    @BatchProperty
    private String fileLocation;

    @Override
    public String process() throws Exception {
        logger.info("Downloading:" + url);

        URLConnection urlCon = new URL(url).openConnection();
        InputStream is = urlCon.getInputStream();
        FileOutputStream fos = new FileOutputStream(fileLocation);

        byte[] buffer = new byte[1000];
        int bytesRead = is.read(buffer);

        while (bytesRead > 0) {
            fos.write(buffer, 0, bytesRead);
            bytesRead = is.read(buffer);
        }

        is.close();
        fos.close();

        logger.info("Download finished");
        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }

}
