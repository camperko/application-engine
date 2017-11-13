package com.netgrif.workflow.event.events.model;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.event.events.user.UserEvent;
import lombok.Getter;

import java.io.File;

public class UserImportModelEvent extends UserEvent {

    @Getter
    private File model;

    public UserImportModelEvent(LoggedUser user, File model) {
        super(user);
        this.model = model;
    }

    @Override
    public String getMessage() {
        return "User " +
                ((LoggedUser) this.source).getUsername() +
                " imported new model " +
                this.model.getName();
    }
}