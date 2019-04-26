package io.searchpe.models;

import java.util.Date;

public interface VersionModel {

    long getId();

    long getNumber();
    void setNumber(long number);

    Date getDate();
    void setDate(Date date);

    boolean isComplete();
    void setComplete(boolean complete);
}
