package io.searchpe.services.models;

import java.util.List;

public class SearchResultModel<T> {

    private List<T> content;
    private Long totalElements;

    public SearchResultModel() {
    }

    public SearchResultModel(List<T> content, Long totalElements) {
        this.content = content;
        this.totalElements = totalElements;
    }

    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public Long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(Long totalElements) {
        this.totalElements = totalElements;
    }

}
