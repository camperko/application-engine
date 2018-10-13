package com.netgrif.workflow.event.events.model;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.event.events.user.UserEvent;
import lombok.Getter;

import java.io.File;

public class UserImportModelEvent extends UserEvent {

    @Getter
    private File model;

    @Getter
    private String title;

    @Getter
    private String initials;

    public UserImportModelEvent(LoggedUser user, File model, String title, String initials) {
        super(user);
        this.model = model;
        this.title = title;
        this.initials = initials;
    }

    @Override
    public String getMessage() {
        return "Používateľ " +
                ((LoggedUser) this.source).getUsername() +
                " importoval nový model " +
                this.model.getName() +
                " s názvom " + this.getTitle() + " a iniciálmi " + this.getInitials();
    }
}