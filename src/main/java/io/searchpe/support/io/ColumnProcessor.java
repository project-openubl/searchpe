package io.searchpe.support.io;

public interface ColumnProcessor {

    Object execute(final Object value, final TxtContext context);

}
