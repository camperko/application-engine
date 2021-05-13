package com.netgrif.workflow.event.events.task;

import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.Task;

public class UserFinishTaskEvent extends UserTaskEvent {

    public UserFinishTaskEvent(IUser user, Task task, Case useCase) {
        super(user, task, useCase);
    }

    @Override
    public String getMessage() {
        return "User " + getEmail() + " finished task " + getTask().getTitle() + " of case " + getUseCase().getTitle();
    }
}