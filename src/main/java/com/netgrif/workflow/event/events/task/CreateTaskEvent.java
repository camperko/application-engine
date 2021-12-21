package com.netgrif.workflow.event.events.task;

import com.netgrif.workflow.utils.DateUtils;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.Task;

public class CreateTaskEvent extends TaskEvent {

    public CreateTaskEvent(Task task, Case useCase) {
        super(task, useCase);
    }

    @Override
    public String getMessage() {
        return "LocalisedTask " + getTask().getTitle() + " of case " + useCase.getTitle() + " created on " + DateUtils.toString(time);
    }
}