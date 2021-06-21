package com.netgrif.workflow.workflow.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.petrinet.domain.I18nString;
import com.netgrif.workflow.petrinet.domain.dataset.Field;
import com.netgrif.workflow.petrinet.domain.events.EventType;
import com.netgrif.workflow.petrinet.domain.layout.TaskLayout;
import com.netgrif.workflow.petrinet.domain.policies.AssignPolicy;
import com.netgrif.workflow.petrinet.domain.policies.DataFocusPolicy;
import com.netgrif.workflow.petrinet.domain.policies.FinishPolicy;
import com.netgrif.workflow.workflow.domain.triggers.Trigger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.*;

@Document
@AllArgsConstructor
@Builder(builderMethodName = "with")
public class Task {

    @Id
    @Builder.Default
    private ObjectId _id = new ObjectId();

    @Indexed
    @Getter
    @Setter
    private String processId;

    @Indexed
    @Getter
    @Setter
    private String caseId;

    @Indexed
    @Setter
    private String transitionId;

    @Getter
    @Setter
    private TaskLayout layout;

    @Getter
    @Setter
    private I18nString title;

    @Getter
    @Setter
    private String caseColor;

    @Getter
    @Setter
    private String caseTitle;

    @Getter
    @Setter
    private Integer priority;

    @Indexed
    @Setter
    private String userId;

    @org.springframework.data.annotation.Transient
    @Getter
    @Setter
    private User user;

    @DBRef
    @Setter
    @Builder.Default
    private List<Trigger> triggers = new LinkedList<>();

    /**
     * Role ObjectId : [ RolePermission, true/false ]
     */
    @Getter
    @Setter
    @Builder.Default
    private Map<String, Map<String, Boolean>> roles = new HashMap<>();

    @Getter
    @Setter
    @Builder.Default
    private List<String> negativeViewRoles = new LinkedList<>();

    @Getter
    @Setter
    @Builder.Default
    private Map<String, Map<String, Boolean>> users = new HashMap<>();

    @Getter
    @Setter
    @Builder.Default
    private Map<String, Map<String, Boolean>> userRefs = new HashMap<>();

    @Getter
    @Setter
    @Builder.Default
    private List<Long> negativeViewUsers = new LinkedList<>();

    @Getter
    @Setter
    private LocalDateTime startDate;

    @Getter
    @Setter
    private LocalDateTime finishDate;

    @Getter
    @Setter
    private String finishedBy;

    @Getter
    @Setter
    private String transactionId;

    /**
     * transient
     */
    @Getter
    @Setter
    private Boolean requiredFilled;

    /**
     * ???
     */
    @Getter
    @Setter
    @JsonIgnore
    @Builder.Default
    private LinkedHashSet<String> immediateDataFields = new LinkedHashSet<>();

    @Getter
    @Setter
    @Transient
    @Builder.Default
    private List<Field> immediateData = new LinkedList<>();

    @Setter
    private String icon;

    @Getter
    @Setter
    @Builder.Default
    private AssignPolicy assignPolicy = AssignPolicy.MANUAL;

    @Getter
    @Setter
    @Builder.Default
    private DataFocusPolicy dataFocusPolicy = DataFocusPolicy.MANUAL;

    @Getter
    @Setter
    @Builder.Default
    private FinishPolicy finishPolicy = FinishPolicy.MANUAL;

    @Getter
    @Setter
    @Builder.Default
    private Map<EventType, I18nString> eventTitles = new HashMap<>();

    @Getter
    @Setter
    @Builder.Default
    private Map<String, Boolean> assignedUserPolicy = new HashMap<>();

    private Map<String, Integer> consumedTokens = new HashMap<>();

    public Task() {
    }

    @JsonIgnore
    public ObjectId getObjectId() {
        return _id;
    }

    public String getStringId() {
        return _id.toString();
    }

    public String getTransitionId() {
        return transitionId;
    }

    public String getIcon() {
        return icon;
    }

    public void addRole(String roleId, Map<String, Boolean> permissions) {
        if (roles.containsKey(roleId) && roles.get(roleId) != null)
            roles.get(roleId).putAll(permissions);
        else
            roles.put(roleId, permissions);
    }

    public void addNegativeViewRole(String roleId) {
        negativeViewRoles.add(roleId);
    }

    public void addUserRef(String userRefId, Map<String, Boolean> permissions) {
        userRefs.put(userRefId, permissions);
    }

    public void addUsers(Set<String> userIds, Map<String, Boolean> permissions) {
        userIds.forEach(userId -> {
            if (users.containsKey(userId) && users.get(userId) != null) {
                users.get(userId).putAll(permissions);
            } else {
                users.put(userId, permissions);
            }
        });
    }

    public void addAssignedUserPolicy(Map<String, Boolean> assignedUser) {
        assignedUserPolicy.putAll(assignedUser);
    }

    @JsonIgnore
    public List<Trigger> getTriggers() {
        return triggers;
    }

    public void addTrigger(Trigger trigger) {
        triggers.add(trigger);
    }

    public void addEventTitle(EventType type, I18nString title) {
        if (type == null || title == null)
            return;
        eventTitles.put(type, title);
    }

    @JsonIgnore
    public String getUserId() {
        return userId;
    }

    public String getTranslatedEventTitle(EventType assign, Locale locale) {
        if (eventTitles == null || !eventTitles.containsKey(assign))
            return null;
        return eventTitles.get(assign).getTranslation(locale);
    }

    public enum Type {
        USER,
        AUTO,
        TIME,
        MESSAGE,
    }
}