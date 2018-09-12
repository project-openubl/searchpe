package io.searchpe.support.io.comment;

import java.util.regex.Pattern;

public class CommentMatches implements CommentMatcher {

    private final Pattern pattern;

    public CommentMatches(final String regex) {
        if (regex == null) {
            throw new NullPointerException("regex should not be null");
        } else if (regex.length() == 0) {
            throw new IllegalArgumentException("regex should not be empty");
        }
        this.pattern = Pattern.compile(regex);
    }

    @Override
    public boolean isComment(String line) {
        return pattern.matcher(line).matches();
    }

}
