package com.netgrif.application.engine.startup

import com.netgrif.application.engine.auth.domain.AuthorizingObject
import com.netgrif.application.engine.auth.service.interfaces.IAuthorityService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class AuthorityRunner extends AbstractOrderedCommandLineRunner {

    @Autowired
    private IAuthorityService service

    @Override
    void run(String... strings) throws Exception {
        AuthorizingObject.values().toList().forEach(authority -> service.getOrCreate(authority.name()))
    }
}