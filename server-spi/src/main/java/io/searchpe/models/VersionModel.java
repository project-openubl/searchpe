package io.searchpe.models;

import java.util.Date;

public interface VersionModel {

    long getId();

    long getNumber();

    Date getCreatedAt();

    Status getStatus();

    void setStatus(Status status);

    boolean isActive();

}
