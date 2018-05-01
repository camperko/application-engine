package com.netgrif.workflow.startup

import com.netgrif.workflow.auth.domain.Authority
import com.netgrif.workflow.auth.domain.LoggedUser
import com.netgrif.workflow.auth.domain.User
import com.netgrif.workflow.auth.domain.UserProcessRole
import com.netgrif.workflow.auth.domain.UserState
import com.netgrif.workflow.auth.service.interfaces.IAuthorityService
import com.netgrif.workflow.auth.service.interfaces.IUserProcessRoleService
import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.orgstructure.domain.Member
import com.netgrif.workflow.orgstructure.service.IGroupService
import com.netgrif.workflow.orgstructure.service.IMemberService
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SuperCreator extends AbstractOrderedCommandLineRunner {

    private static final Logger log = Logger.getLogger(SuperCreator.class.name)

    @Autowired
    private IAuthorityService authorityService

    @Autowired
    private IUserProcessRoleService userProcessRoleService

    @Autowired
    private IUserService userService

    @Autowired
    private IMemberService memberService

    @Autowired
    private IGroupService groupService

    @Value('${admin.password}')
    private String superAdminPassword

    private User superUser

    private Member superMember

    @Override
    void run(String... strings) {
        log.info("Creating Super user")
        createSuperUser()
    }

    private User createSuperUser() {
        Authority adminAuthority = authorityService.getOrCreate(Authority.admin)

        User superUser = userService.findByEmail("super@netgrif.com",false)
        if(superUser == null) {
            this.superUser = userService.saveNew(new User(
                    name: "Super",
                    surname: "Trooper",
                    email: "super@netgrif.com",
                    password: superAdminPassword,
                    state: UserState.ACTIVE,
                    authorities: [adminAuthority] as Set<Authority>,
                    userProcessRoles: userProcessRoleService.findAll() as Set<UserProcessRole>))
            this.superMember = memberService.findByEmail(this.superUser.email)
            log.info("Super user created")
        } else {
            log.info("Super user detected")
            this.superUser = superUser
            this.superMember = memberService.findByEmail(this.superUser.email)
        }
        return this.superUser
    }

    void setAllToSuperUser() {
        setAllGroups()
        setAllProcessRoles()
        log.info("Super user updated")
    }

    void setAllGroups() {
        groupService.findAll().each {
            it.addMember(superMember)
        }
        memberService.save(superMember)
    }

    void setAllProcessRoles() {
        superUser.setUserProcessRoles(userProcessRoleService.findAll() as Set<UserProcessRole>)
        superUser = userService.save(superUser)
    }

    User getSuperUser() {
        return superUser
    }

    LoggedUser getLoggedSuper() {
        return superUser.transformToLoggedUser()
    }
}