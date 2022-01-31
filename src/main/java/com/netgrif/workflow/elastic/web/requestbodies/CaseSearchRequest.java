package com.netgrif.workflow.elastic.web.requestbodies;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseSearchRequest {

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<PetriNet> process;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<String> processIdentifier;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<Author> author;

    public Map<String, String> data;

    public String fullText;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<String> transition;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<String> role;

    public String query;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<String> stringId;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    public List<String> group;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PetriNet {

        public String identifier;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Author {

        public Long id;

        public String name;

        public String email;
    }
}