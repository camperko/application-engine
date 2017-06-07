package com.netgrif.workflow.history.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Map;

public class UserTaskEventLog {

    @Getter @Setter
    private String taskId;

    @Getter @Setter
    private String taskName;

    @Getter @Setter
    private String caseId;

    @Getter @Setter
    private String caseName;

    @Getter @Setter
    private String transitionId;

    @Field("activePlaces")
    @Getter @Setter
    private Map<String, Integer> activePlaces;

    @Getter @Setter
    private Map<String, Object> dataSetValues;

}
