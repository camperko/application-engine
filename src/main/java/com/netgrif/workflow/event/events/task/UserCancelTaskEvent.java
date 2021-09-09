package com.netgrif.workflow.event.events.task;

import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.workflow.domain.Case;
import com.netgrif.workflow.workflow.domain.Task;

public class UserCancelTaskEvent extends UserTaskEvent {

    public UserCancelTaskEvent(IUser user, Task task, Case useCase) {
        super(user, task, useCase);
    }

    @Override
    public String getMessage() {
        return "User " + getEmail() + " canceled task " + getTask().getTitle() + " of case " + getUseCase().getTitle();
    }
}