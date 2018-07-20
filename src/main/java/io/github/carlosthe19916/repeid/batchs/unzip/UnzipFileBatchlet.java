package io.github.carlosthe19916.repeid.batchs.unzip;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.JobContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Named
public class UnzipFileBatchlet implements Batchlet {

    @Inject
    @BatchProperty
    private String fileLocation;

    @Inject
    @BatchProperty
    private String unzipFileLocation;

    @Override
    public String process() throws Exception {
        byte[] buffer = new byte[1024];
        ZipInputStream zis = new ZipInputStream(new FileInputStream(fileLocation));
        ZipEntry zipEntry = zis.getNextEntry();
        while(zipEntry != null){
            File newFile = new File(unzipFileLocation);
            FileOutputStream fos = new FileOutputStream(newFile);
            int len;
            while ((len = zis.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            zipEntry = zis.getNextEntry();
        }
        zis.closeEntry();
        zis.close();

        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }

}
