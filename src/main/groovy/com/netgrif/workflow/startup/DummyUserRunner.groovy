package com.netgrif.workflow.startup


import com.netgrif.workflow.auth.domain.User
import com.netgrif.workflow.auth.domain.UserState
import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.orgstructure.service.IGroupService
import com.netgrif.workflow.orgstructure.service.IMemberService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@ConditionalOnProperty(value = "runner.user", matchIfMissing = true)
@Profile("dev")
@Component
class DummyUserRunner extends AbstractOrderedCommandLineRunner {

    @Autowired
    private IUserService userService

    @Autowired
    private IMemberService memberService

    @Autowired
    private IGroupService groupService

    @Value('${nae.oauth.enabled}')
    private boolean oauth

    @Override
    void run(String... strings) throws Exception {
        if (oauth) return
        def group = groupService.findAll().find { it.name == DefaultGroupRunner.DEFAULT_GROUP_NAME }

        assert group != null

        def user = userService.findByEmail("dummy@netgrif.com", true)
        if (!user) {
            user = new User("dummy@netgrif.com", "password", "Dummy", "user")
            user.state = UserState.ACTIVE
            user = userService.saveNew(user)
            def member = memberService.findByEmail(user.email)
            group.addMember(member)
            memberService.save(member)
        }

        user = userService.findByEmail("customer@netgrif.com", true)
        if (!user) {
            user = new User("customer@netgrif.com", "password", "Netgrif", "customer")
            user.state = UserState.ACTIVE
            user = userService.saveNew(user)
            def member = memberService.findByEmail(user.email)
            group.addMember(member)
            memberService.save(member)
        }
    }
}
