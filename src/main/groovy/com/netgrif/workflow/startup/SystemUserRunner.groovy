package com.netgrif.workflow.startup

import com.netgrif.workflow.auth.domain.User
import com.netgrif.workflow.auth.domain.UserState
import com.netgrif.workflow.auth.service.interfaces.IUserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class SystemUserRunner extends AbstractOrderedCommandLineRunner {

    public static final String SYSTEM_USER_EMAIL = "engine@netgrif.com"
    public static final String SYSTEM_USER_NAME = "application"
    public static final String SYSTEM_USER_SURNAME = "engine"

    @Autowired
    private IUserService service

    @Override
    void run(String... strings) throws Exception {
        User systemUser = service.findByEmail(SYSTEM_USER_EMAIL,false)
        if (systemUser == null) {
            User system = new User(
                    email: SYSTEM_USER_EMAIL,
                    name: SYSTEM_USER_NAME,
                    surname: SYSTEM_USER_SURNAME,
                    password: "as3f4as6d5f465d7s65d74f3tgsd5rts3d5f7gdf65tz7f65tz7f3t5zf5r3z7t",
                    state: UserState.ACTIVE
            )
            service.save(system)
        }
    }
}