package io.searchpe.batchs.persist;

import io.searchpe.model.Version;
import org.jberet.cdi.StepScoped;

import javax.inject.Named;

@Named
@StepScoped
public class TxtVersion {

    private Version version;

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

}
