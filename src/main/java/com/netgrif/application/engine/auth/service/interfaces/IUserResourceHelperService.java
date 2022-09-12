package com.netgrif.application.engine.auth.service.interfaces;

import com.netgrif.application.engine.auth.domain.IUser;
import com.netgrif.application.engine.auth.domain.LoggedUser;
import com.netgrif.application.engine.auth.web.responsebodies.User;
import com.netgrif.application.engine.auth.web.responsebodies.UserResource;

import java.util.Locale;

public interface IUserResourceHelperService {
    UserResource resource(LoggedUser loggedUser, Locale locale, boolean small);

    User localisedUser(IUser user, IUser impersonated, Locale locale);

    User localisedUser(IUser user, Locale locale);
}
