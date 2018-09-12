package io.searchpe.support.io;

import java.io.Serializable;
import java.util.List;

public class TxtContext implements Serializable {
    
    private static final long serialVersionUID = 1L;

    /** the line number of the file being read/written */
    private int lineNumber;

    /** the TXT row number (TXT rows can span multiple lines) */
    private int rowNumber;

    /** the TXT column number */
    private int columnNumber;

    /** the row just read in, or to be written */
    private List<Object> rowSource;

    /**
     * Constructs a new <tt>TxtContext</tt>.
     *
     * @param lineNumber
     *            the current line number
     * @param rowNumber
     *            the current TXT row number
     * @param columnNumber
     *            the current TXT column number
     */
    public TxtContext(final int lineNumber, final int rowNumber, final int columnNumber) {
        this.lineNumber = lineNumber;
        this.rowNumber = rowNumber;
        this.columnNumber = columnNumber;
    }

    /**
     * @return the lineNumber
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * @param lineNumber
     *            the lineNumber to set
     */
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    /**
     * @return the rowNumber
     */
    public int getRowNumber() {
        return rowNumber;
    }

    /**
     * @param rowNumber
     *            the rowNumber to set
     */
    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    /**
     * @return the columnNumber
     */
    public int getColumnNumber() {
        return columnNumber;
    }

    /**
     * @param columnNumber
     *            the columnNumber to set
     */
    public void setColumnNumber(int columnNumber) {
        this.columnNumber = columnNumber;
    }

    /**
     * @return the rowSource
     */
    public List<Object> getRowSource() {
        return rowSource;
    }

    /**
     * @param rowSource
     *            the rowSource to set
     */
    public void setRowSource(List<Object> rowSource) {
        this.rowSource = rowSource;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return String.format("{lineNo=%d, rowNo=%d, columnNo=%d, rowSource=%s}", lineNumber, rowNumber, columnNumber, rowSource);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + columnNumber;
        result = prime * result + rowNumber;
        result = prime * result + lineNumber;
        result = prime * result + ((rowSource == null) ? 0 : rowSource.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if( this == obj ) {
            return true;
        }
        if( obj == null ) {
            return false;
        }
        if( getClass() != obj.getClass() ) {
            return false;
        }
        final TxtContext other = (TxtContext) obj;
        if( columnNumber != other.columnNumber ) {
            return false;
        }
        if( rowNumber != other.rowNumber ) {
            return false;
        }
        if( lineNumber != other.lineNumber ) {
            return false;
        }
        if( rowSource == null ) {
            if( other.rowSource != null ) {
                return false;
            }
        } else if( !rowSource.equals(other.rowSource) ) {
            return false;
        }
        return true;
    }

}
