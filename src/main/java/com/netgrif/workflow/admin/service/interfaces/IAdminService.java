package com.netgrif.workflow.admin.service.interfaces;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.workflow.web.responsebodies.MessageResource;

public interface IAdminService {

    MessageResource run(String code, LoggedUser user);
}