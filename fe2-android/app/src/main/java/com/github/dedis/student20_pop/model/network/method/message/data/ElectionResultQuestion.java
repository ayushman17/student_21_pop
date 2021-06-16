package com.github.dedis.student20_pop.model.network.method.message.data;

import java.util.List;

public class ElectionResultQuestion {

    private String id;
    private List<QuestionResult> results;

    public ElectionResultQuestion(String id, List<QuestionResult> results) {
        if (id == null || results == null || results.isEmpty()) throw new IllegalArgumentException();
        this.id = id;
        this.results = results;
    }

    public String getId() {
        return id;
    }

    public List<QuestionResult> getResults() {
        return results;
    }
}

