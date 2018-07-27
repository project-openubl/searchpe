package io.searchpe.batchs.delete;

import io.searchpe.model.Version;
import io.searchpe.services.CompanyService;
import io.searchpe.services.VersionService;
import org.jboss.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.Batchlet;
import javax.batch.runtime.BatchStatus;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.NotFoundException;
import java.util.List;

@Named
public class DeleteRowsBatchlet implements Batchlet {

    private static final Logger logger = Logger.getLogger(DeleteRowsBatchlet.class);

    @Inject
    @BatchProperty
    private Integer deleteComplete;

    @Inject
    private CompanyService companyService;

    @Inject
    private VersionService versionService;

    @Override
    public String process() throws Exception {
        logger.infof("Deleting rows");
        Version lastVersion = versionService.getLastCompletedVersion().orElseThrow(NotFoundException::new);
        if (lastVersion.isComplete()) {
            List<Version> versions = versionService.getVersionByIssueDate(lastVersion.getDate());
            versions.forEach(v -> {
                companyService.deleteCompanyByVersion(v);
                versionService.deleteVersion(v);
            });
        } else {
            companyService.deleteCompanyByVersion(lastVersion);
            versionService.deleteVersion(lastVersion);
        }
        return BatchStatus.COMPLETED.toString();
    }

    @Override
    public void stop() throws Exception {

    }
}
