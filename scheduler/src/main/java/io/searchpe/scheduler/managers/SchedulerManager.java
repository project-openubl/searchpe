package io.searchpe.scheduler.managers;

import io.agroal.api.AgroalDataSource;
import io.quarkus.scheduler.Scheduled;
import io.searchpe.scheduler.utils.DataHelper;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;

@ApplicationScoped
public class SchedulerManager {

    private static final Logger LOGGER = Logger.getLogger(SchedulerManager.class);

    @Inject
    FileManager fileManager;

    @Inject
    AgroalDataSource defaultDataSource;

    @ConfigProperty(name = "searchpe.scheduler.batchSize", defaultValue = "4096")
    Integer batchSize;

    @ConfigProperty(name = "searchpe.scheduler.sequenceName", defaultValue = "hibernate_sequence")
    String sequenceName;

    @Scheduled(cron = "0 15 10 * * ?")
    public void startSync() {
        File downloadedFile;
        File unzippedFolder;
        File txtFile;

        // Download file
        try {
            downloadedFile = fileManager.downloadFile();
            unzippedFolder = fileManager.unzip(downloadedFile);
            txtFile = fileManager.getFirstTxtFileFound(unzippedFolder.listFiles());
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            return;
        }

        // Persist data
        try (Connection connection = defaultDataSource.getConnection()) {
            Long versionId = createVersionEntity(connection);
            createContribuyentesFromFile(connection, versionId, txtFile);
            updateVersionEntity(connection, versionId);

            LOGGER.infof("Import finished successfully version %s", versionId);
        } catch (SQLException | IOException e) {
            LOGGER.error(e.getMessage());
        } finally {
            // Clear folders
            try {
                LOGGER.infof("Deleting directory %s", downloadedFile.toString());
                LOGGER.infof("Deleting directory %s", unzippedFolder.toString());
                downloadedFile.delete();
                FileUtils.deleteDirectory(unzippedFolder);
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }
        }
    }

    private void createContribuyentesFromFile(Connection connection, Long versionId, File file) throws IOException, SQLException {
        LOGGER.infof("Importing contribuyentes");

        String insertTableSQL = "INSERT INTO contribuyente"
                + "(version_id,id,ruc,razon_social,estado_contribuyente,condicion_domicilio,ubigeo,tipo_via,nombre_via,codigo_zona,tipo_zona,numero,interior,lote,departamento,manzana,kilometro) VALUES"
                + "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (
                BufferedReader br = new BufferedReader(new FileReader(file));
                PreparedStatement preparedStatement = connection.prepareStatement(insertTableSQL)
        ) {
            connection.setAutoCommit(false);
            int batchTotal = 0;

            String line;
            boolean skip = true;
            while ((line = br.readLine()) != null) {
                if (skip) {
                    skip = false;
                    continue;
                }

                String[] columns = DataHelper.readLine(line, 15);

                preparedStatement.setLong(1, versionId);
                preparedStatement.setLong(2, getNextId(connection));
                for (int i = 0; i < columns.length; i++) {
                    preparedStatement.setString(i + 3, columns[i]);
                }

                preparedStatement.addBatch();
                if (batchTotal++ == batchSize) {
                    int[] result = preparedStatement.executeBatch();
                    preparedStatement.clearBatch();
                    batchTotal = 0;

                    LOGGER.infof("Executing batch...");
                }
                if (batchTotal > 0) {
                    int[] result = preparedStatement.executeBatch();
                }

                connection.commit();
            }
        }

        LOGGER.infof("Import contribuyentes finished successfully");
    }

    private Long createVersionEntity(Connection connection) throws SQLException {
        Long nextId = getNextId(connection);

        LOGGER.infof("Creating version %s", nextId);

        String insertTableSQL = "INSERT INTO version"
                + "(id,created_at,status,active) VALUES"
                + "(?,?,?,?)";

        try (PreparedStatement preparedStatement = connection.prepareStatement(insertTableSQL)) {
            preparedStatement.setLong(1, nextId);
            preparedStatement.setDate(2, Date.valueOf(java.time.LocalDate.now()));
            preparedStatement.setString(3, "IMPORT_IN_PROGRESS");
            preparedStatement.setString(4, "F");
            preparedStatement.executeUpdate();

            LOGGER.infof("Version %s created successfully", nextId);
            return nextId;
        }
    }

    private void updateVersionEntity(Connection connection, Long versionId) throws SQLException {
        String insertTableSQL = "UPDATE version set status = ? where id = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(insertTableSQL)) {
            preparedStatement.setString(1, "IMPORT_FINISHED_SUCCESSFULLY");
            preparedStatement.setLong(2, versionId);
        }
    }

    private Long getNextId(Connection connection) throws SQLException {
        String sql = "SELECT nextval('" + sequenceName + "')";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);

        synchronized (this) {
            ResultSet rs = preparedStatement.executeQuery();
            if (rs.next()) {
                return rs.getLong(1);
            }

            return 0L;
        }
    }

}
