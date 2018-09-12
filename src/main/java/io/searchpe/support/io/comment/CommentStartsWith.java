package io.searchpe.support.io.comment;

public class CommentStartsWith implements CommentMatcher {

    private final String value;

    public CommentStartsWith(final String value) {
        if (value == null) {
            throw new NullPointerException("value should not be null");
        } else if (value.length() == 0) {
            throw new IllegalArgumentException("value should not be empty");
        }
        this.value = value;
    }

    @Override
    public boolean isComment(String line) {
        return line.startsWith(value);
    }

}
