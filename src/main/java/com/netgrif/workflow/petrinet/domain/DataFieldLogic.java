package com.netgrif.workflow.petrinet.domain;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netgrif.workflow.petrinet.domain.dataset.logic.FieldBehavior;
import com.netgrif.workflow.petrinet.domain.dataset.logic.FieldLayout;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;


public class DataFieldLogic {

    @Getter
    @Setter
    private Set<FieldBehavior> behavior;

    @Getter
    @Setter
    @Deprecated
    private LinkedHashSet<Action> actions;

    @Getter
    @Setter
    private LinkedHashSet<DataEvent> events;

    @Getter
    @Setter
    private FieldLayout layout;

    public DataFieldLogic() {
        this.behavior = new HashSet<>();
        this.actions = new LinkedHashSet<>();
        this.events = new LinkedHashSet<>();
        this.layout = new FieldLayout();
    }

    public DataFieldLogic(Set<FieldBehavior> behavior, Set<Action> actions, FieldLayout layout) {
        this();
        if (behavior != null)
            this.behavior.addAll(behavior);
        if (actions != null)
            this.actions.addAll(actions);
        if (layout != null)
            this.layout = layout;
    }

    public DataFieldLogic(Set<FieldBehavior> behavior, Set<Action> actions, Set<DataEvent> events, FieldLayout layout) {
        this();
        if (behavior != null)
            this.behavior.addAll(behavior);
        if (actions != null)
            this.actions.addAll(actions);
        if (events != null)
            this.events.addAll(events);
        if (layout != null)
            this.layout = layout;
    }

    @Deprecated
    public void addActions(Collection<Action> actions) {
        this.actions.addAll(actions);
    }

    public void addDataEvents(Collection<DataEvent> events){
        this.events.addAll(events);
    }

    public ObjectNode applyBehavior(ObjectNode jsonNode) {
        behavior.forEach(fieldBehavior -> jsonNode.put(fieldBehavior.toString(), true));
        return jsonNode;
    }

    public ObjectNode applyBehavior() {
        return applyBehavior(JsonNodeFactory.instance.objectNode());
    }

    public void merge(DataFieldLogic other) {
        this.behavior.addAll(other.behavior);
        this.actions.addAll(other.actions);
    }

    public boolean isDisplayable() {
        return behavior.contains(FieldBehavior.EDITABLE) || behavior.contains(FieldBehavior.VISIBLE) || behavior.contains(FieldBehavior.HIDDEN);
    }

    public boolean isDisplayableForCase() {
        return behavior.contains(FieldBehavior.EDITABLE) || behavior.contains(FieldBehavior.VISIBLE) || behavior.contains(FieldBehavior.HIDDEN);
    }

    public static List<Action> getActionByTrigger(Set<Action> actions, Action.ActionTrigger trigger) {
        return actions.stream().filter(action -> action.isTriggeredBy(trigger)).collect(Collectors.toList());
    }

    public static List<Action> getEventAction(LinkedHashSet<DataEvent> events, Action.ActionTrigger trigger, DataEventPhase phase){
        List<Action> actionList = new ArrayList<>();
        events.stream().filter(event -> event.getTrigger().equals(trigger))
                .flatMap(event -> event.getActions().entrySet().stream()
                        .filter(actions -> actions.getKey().equals(phase))
                        .map(Map.Entry::getValue)).forEach(actionList::addAll);
        return actionList;
    }

    public boolean isRequired() {
        return behavior.contains(FieldBehavior.REQUIRED);
    }

    @Override
    public String toString() {
        return behavior.stream().map(FieldBehavior::toString).collect(Collectors.joining(", "));
    }

    public boolean isForbidden() {
        return behavior.contains(FieldBehavior.FORBIDDEN);
    }

    public boolean layoutExist() {
        return this.layout != null;
    }
}