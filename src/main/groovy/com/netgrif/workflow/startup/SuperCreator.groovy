package com.netgrif.workflow.startup

import com.netgrif.workflow.auth.domain.*
import com.netgrif.workflow.auth.service.interfaces.IAuthorityService
import com.netgrif.workflow.auth.service.interfaces.IUserService
import com.netgrif.workflow.configuration.properties.NaeOAuthProperties
import com.netgrif.workflow.oauth.service.interfaces.IOAuthUserService
import com.netgrif.workflow.orgstructure.domain.Member
import com.netgrif.workflow.orgstructure.service.IGroupService
import com.netgrif.workflow.orgstructure.service.IMemberService
import com.netgrif.workflow.petrinet.domain.roles.ProcessRole
import com.netgrif.workflow.petrinet.service.interfaces.IProcessRoleService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@ConditionalOnProperty(value = "admin.create-super", matchIfMissing = true)
@Component
class SuperCreator extends AbstractOrderedCommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperCreator.class.name)

    @Autowired
    private IAuthorityService authorityService

    @Autowired
    private IUserService userService

    @Autowired
    private IMemberService memberService

    @Autowired
    private IGroupService groupService

    @Autowired
    private IProcessRoleService processRoleService

    @Autowired
    protected NaeOAuthProperties oAuthProperties

    @Value('${admin.password}')
    private String superAdminPassword

    private IUser superUser

    private Member superMember

    @Override
    void run(String... strings) {
        log.info("Creating Super user")
        oAuthProperties.enabled && oAuthProperties.remoteUserBase ? createOAuthSuperUser() : createSuperUser()
    }

    private IUser createSuperUser() {
        Authority adminAuthority = authorityService.getOrCreate(Authority.admin)
        Authority systemAuthority = authorityService.getOrCreate(Authority.systemAdmin)

        IUser superUser = userService.findByEmail("super@netgrif.com", false)
        if (superUser == null) {
            this.superUser = userService.saveNew(new User(
                    name: "Admin",
                    surname: "Netgrif",
                    email: "super@netgrif.com",
                    password: superAdminPassword,
                    state: UserState.ACTIVE,
                    authorities: [adminAuthority, systemAuthority] as Set<Authority>,
                    processRoles: processRoleService.findAll() as Set<ProcessRole>))
            this.superMember = memberService.findByEmail(this.superUser.email)
            log.info("Super user created")
        } else {
            log.info("Super user detected")
            this.superUser = superUser
            this.superMember = memberService.findByEmail(this.superUser.email)
        }

        return this.superUser
    }

    private IUser createOAuthSuperUser() {
        IUser superUser = ((IOAuthUserService) userService).findByUsername(oAuthProperties.getSuperUsername())
        this.superUser = userService.saveNew(superUser)
        this.superMember = memberService.findByEmail(superUser.email)
        return this.superUser
    }

    void setAllToSuperUser() {
        setAllGroups()
        setAllProcessRoles()
        setAllAuthorities()
        log.info("Super user updated")
    }

    void setAllGroups() {
        groupService.findAll().each {
            it.addMember(superMember)
        }
        memberService.save(superMember)
    }

    void setAllProcessRoles() {
        superUser.setProcessRoles(processRoleService.findAll() as Set<ProcessRole>)
        superUser = userService.save(superUser) as IUser
    }

    void setAllAuthorities() {
        superUser.setAuthorities(authorityService.findAll() as Set<Authority>)
        superUser = userService.save(superUser) as IUser
    }

    IUser getSuperUser() {
        return superUser
    }

    LoggedUser getLoggedSuper() {
        return superUser.transformToLoggedUser()
    }
}