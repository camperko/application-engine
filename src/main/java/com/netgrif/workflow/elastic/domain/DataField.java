package com.netgrif.workflow.elastic.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;

import static org.springframework.data.elasticsearch.annotations.FieldType.Keyword;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataField {

    public String value;

    @Field(type = Keyword)
    public String sortable;

    public DataField(String value) {
        this.value = value;
        this.sortable = value;
    }
}