package io.searchpe.models.utils;

import io.searchpe.models.CompanyModel;
import io.searchpe.models.VersionModel;
import io.searchpe.representations.idm.CompanyRepresentation;
import io.searchpe.representations.idm.VersionRepresentation;

public class ModelToRepresentation {

    public static CompanyRepresentation toRepresentation(CompanyModel model) {
        return null;
    }

    public static VersionRepresentation toRepresentation(VersionModel model) {
        VersionRepresentation rep = new VersionRepresentation();
        rep.setId(model.getId());
        rep.setNumber(model.getNumber());
        rep.setStatus(model.getStatus().toString());
        rep.setCreatedAt(model.getCreatedAt());
        return rep;
    }
}
