package io.searchpe.support.io;

import io.searchpe.support.io.exceptions.SuperTxtException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Util {

    public static <T> void filterListToMap(final Map<String, T> destinationMap, final String[] nameMapping, final List<? extends T> sourceList) {
        if (destinationMap == null) {
            throw new NullPointerException("destinationMap should not be null");
        } else if (nameMapping == null) {
            throw new NullPointerException("nameMapping should not be null");
        } else if (sourceList == null) {
            throw new NullPointerException("sourceList should not be null");
        } else if (nameMapping.length != sourceList.size()) {
            throw new SuperTxtException(
                    String
                            .format(
                                    "the nameMapping array and the sourceList should be the same size (nameMapping length = %d, sourceList size = %d)",
                                    nameMapping.length, sourceList.size()));
        }

        destinationMap.clear();

        for (int i = 0; i < nameMapping.length; i++) {
            final String key = nameMapping[i];

            if (key == null) {
                continue; // null's in the name mapping means skip column
            }

            // no duplicates allowed
            if (destinationMap.containsKey(key)) {
                throw new SuperTxtException(String.format("duplicate nameMapping '%s' at index %d", key, i));
            }

            destinationMap.put(key, sourceList.get(i));
        }
    }

    public static void executeCellProcessors(final List<Object> destination, final List<?> source, final ColumnProcessor[] processors, final int lineNo, final int rowNo) {
        if( destination == null ) {
            throw new NullPointerException("destination should not be null");
        } else if( source == null ) {
            throw new NullPointerException("source should not be null");
        } else if( processors == null ) {
            throw new NullPointerException("processors should not be null");
        }

        // the context used when cell processors report exceptions
        final TxtContext context = new TxtContext(lineNo, rowNo, 1);
        context.setRowSource(new ArrayList<>(source));

        if( source.size() != processors.length ) {
            throw new SuperTxtException(String.format(
                    "The number of columns to be processed (%d) must match the number of CellProcessors (%d): check that the number"
                            + " of CellProcessors you have defined matches the expected number of columns being read/written",
                    source.size(), processors.length), context);
        }

        destination.clear();

        for( int i = 0; i < source.size(); i++ ) {

            context.setColumnNumber(i + 1); // update context (columns start at 1)

            if( processors[i] == null ) {
                destination.add(source.get(i)); // no processing required
            } else {
                destination.add(processors[i].execute(source.get(i), context)); // execute the processor chain
            }
        }
    }

}
