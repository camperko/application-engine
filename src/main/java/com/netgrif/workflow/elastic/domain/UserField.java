package com.netgrif.workflow.elastic.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import static org.springframework.data.elasticsearch.annotations.FieldType.Text;
import static org.springframework.data.elasticsearch.annotations.FieldType.Long;


@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserField extends DataField {

    @Field(type = Text)
    private String[] emailValue;

    @Field(type = Text)
    private String[] fullNameValue;

    @Field(type = Long)
    private Long[] userIdValue;

    public UserField(UserMappingData value) {
        super(String.format("%s %s", value.fullName, value.email));
        this.emailValue = new String[1];
        this.fullNameValue = new String[1];
        this.userIdValue = new Long[1];
        this.emailValue[0] = value.email;
        this.fullNameValue[0] = value.fullName;
        this.userIdValue[0] = value.userId;
    }

    public UserField(UserMappingData[] values) {
        super(new String[values.length]);
        this.emailValue = new String[values.length];
        this.fullNameValue = new String[values.length];
        this.userIdValue = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            this.emailValue[i] = values[i].email;
            this.fullNameValue[i] = values[i].fullName;
            this.userIdValue[i] = values[i].userId;
            super.fulltextValue[i] = String.format("%s %s", values[i].fullName, values[i].email);
        }
    }

    @AllArgsConstructor
    public static class UserMappingData {
        public long userId;
        public String email;
        public String fullName;
    }
}

