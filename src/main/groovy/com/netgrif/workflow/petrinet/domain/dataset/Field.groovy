package com.netgrif.workflow.petrinet.domain.dataset

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.node.ObjectNode
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.Action
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.mongodb.core.mapping.Document

@Document
abstract class Field<T> {

    @Id
    protected ObjectId _id

    @JsonIgnore
    protected Long importId

    private String name
    private String description
    private String placeholder
    protected FieldType type

    @Transient
    private ObjectNode behavior

    @Transient
    private T value

    private Long order

    @JsonIgnore
    private Boolean immediate

    @JsonIgnore
    private LinkedHashSet<Action> actions

    Field() {
        _id = new ObjectId()
    }

    Field(Long importId) {
        this()
        this.importId = importId
    }

    String getStringId() {
        return _id.toString()
    }

    ObjectId get_id() {
        return _id
    }

    void set_id(ObjectId _id) {
        this._id = _id
    }

    Long getImportId() {
        return importId
    }

    void setImportId(Long importId) {
        this.importId = importId
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getDescription() {
        return description
    }

    void setDescription(String description) {
        this.description = description
    }

    String getPlaceholder() {
        return placeholder
    }

    void setPlaceholder(String placeholder) {
        this.placeholder = placeholder
    }

    FieldType getType() {
        return type
    }

    void setType(FieldType type) {
        this.type = type
    }

    ObjectNode getBehavior() {
        return behavior
    }

    void setBehavior(ObjectNode behavior) {
        this.behavior = behavior
    }

    T getValue() {
        return value
    }

    void setValue(T value) {
        this.value = value
    }

    Long getOrder() {
        return order
    }

    void setOrder(Long order) {
        this.order = order
    }

    Boolean isImmediate() {
        return immediate
    }

    void setImmediate(Boolean immediate) {
        this.immediate = immediate
    }

    LinkedHashSet<Action> getActions() {
        return actions
    }

    void setActions(LinkedHashSet<Action> actions) {
        this.actions = actions
    }

    void addAction(Action action) {
        if (this.actions == null)
            this.actions = new LinkedHashSet<>()
        if (action == null) return

        this.actions.add(action)
    }

    void addAction(String action, String trigger) {
        this.addAction(new Action(action, trigger))
    }

    void clearValue() {}
//operators overloading
    T plus(final Field field) {
        return this.value + field.value
    }

    T minus(final Field field) {
        return this.value - field.value
    }

    T multiply(final Field field) {
        return this.value * field.value
    }
}
