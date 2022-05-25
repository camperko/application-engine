package com.netgrif.application.engine.configuration.authentication.providers;

import com.netgrif.application.engine.TestHelper;
import com.netgrif.application.engine.auth.domain.IUser;
import com.netgrif.application.engine.ldap.domain.LdapGroupRef;
import com.netgrif.application.engine.ldap.domain.LdapUser;
import com.netgrif.application.engine.ldap.service.LdapUserService;
import com.netgrif.application.engine.ldap.service.interfaces.ILdapGroupRefService;
import com.netgrif.application.engine.petrinet.domain.PetriNet;
import com.netgrif.application.engine.petrinet.domain.VersionType;
import com.netgrif.application.engine.petrinet.domain.roles.ProcessRole;
import com.netgrif.application.engine.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.application.engine.startup.SuperCreator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.FileInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles({"test"})
@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {
        "spring.ldap.embedded.base-dn=dc=netgrif,dc=com",
        "spring.ldap.embedded.credential.username=cn=admin,dc=netgrif,dc=com",
        "spring.ldap.embedded.credential.password=secret",
        "spring.ldap.embedded.ldif=file:src/test/resources/test-server.ldif",
        "spring.ldap.embedded.port=6389",
        "nae.security.providers=NetgrifBasicAuthenticationProvider,NetgrifLdapAuthenticationProvider",
        "spring.ldap.embedded.validation.enabled=false",
        "nae.ldap.enabled=true",
        "nae.ldap.url=ldap://localhost:6389",
        "nae.ldap.username=cn=admin,dc=netgrif,dc=com",
        "nae.ldap.password=secret",
        "nae.ldap.base=dc=netgrif,dc=com",
        "nae.ldap.userFilter=cn={0}",
        "nae.ldap.peopleSearchBase=ou=people",
        "nae.ldap.groupSearchBase=ou=groups",
        "nae.ldap.peopleClass=inetOrgPerson,person"})
class NetgrifLdapAuthenticationProviderTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ILdapGroupRefService ldapGroupRefService;

    @Autowired
    private LdapUserService userService;

    @Autowired
    private SuperCreator superCreator;

    @Autowired
    private IPetriNetService petriNetService;
    @Autowired
    private TestHelper testHelper;

    private static final String USER_EMAIL_Test1 = "ben@netgrif.com";
    private static final String USER_PASSWORD_Test1 = "benpassword";

    private static final String USER_EMAIL_Test2 = "simpson@netgrif.com";
    private static final String USER_PASSWORD_Test2 = "password";

    private static final String USER_EMAIL_Test3 = "watson@netgrif.com";
    private static final String USER_PASSWORD_Test3 = "password";

    private MockMvc mvc;

    @BeforeEach
    public void before() {
        testHelper.truncateDbs();
        mvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(springSecurity())
                .build();
    }

    @Test
    void testLogin() throws Exception {

        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(USER_EMAIL_Test1, USER_PASSWORD_Test1);
        user.setDetails(new WebAuthenticationDetails(new MockHttpServletRequest()));

        mvc.perform(get("/api/user/me")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("utf-8"))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void getMyLDAPGroups() throws Exception {

        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(USER_EMAIL_Test2, USER_PASSWORD_Test2);
        user.setDetails(new WebAuthenticationDetails(new MockHttpServletRequest()));
        MvcResult result = mvc.perform(get("/api/auth/login")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("utf-8"))
                .andExpect(status().isOk())
                .andReturn();

        IUser ldapUser = userService.findByEmail(USER_EMAIL_Test2, false);
        assert ldapUser != null;
        assert ldapUser instanceof LdapUser;
        assert ((LdapUser) ldapUser).getMemberOf().size() == 2;

    }

    @Test
    void noLDAPGroups() throws Exception {

        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(USER_EMAIL_Test3, USER_PASSWORD_Test3);
        user.setDetails(new WebAuthenticationDetails(new MockHttpServletRequest()));
        MvcResult result = mvc.perform(get("/api/auth/login")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("utf-8"))
                .andExpect(status().isOk())
                .andReturn();

        IUser ldapUser = userService.findByEmail(USER_EMAIL_Test3, false);
        assert ldapUser != null;
        assert ldapUser instanceof LdapUser;
        assert ((LdapUser) ldapUser).getMemberOf().size() == 0;

    }

    @Test
    void getMyProcessRole() throws Exception {
        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(USER_EMAIL_Test1, USER_PASSWORD_Test1);
        user.setDetails(new WebAuthenticationDetails(new MockHttpServletRequest()));

        MvcResult result = mvc.perform(get("/api/user/me")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("utf-8"))
                .andExpect(status().isOk())
                .andReturn();

        String string = result.getResponse().getContentAsString();

        JSONObject json = new JSONObject(string);
        JSONArray countProcessRole = (JSONArray) json.get("processRoles");
        assert countProcessRole.length() == 1;
    }

    @Test
    void getAllGroups() {
        List<LdapGroupRef> ldapGroups = ldapGroupRefService.findAllGroups();
        assert ldapGroups.size() == 3;
    }

    @Test
    void searchGroups() {
        List<LdapGroupRef> ldapGroups = ldapGroupRefService.searchGroups("Testik");
        assert ldapGroups.get(0).getCn().equals("test1");

        List<LdapGroupRef> ldapGroupsAll = ldapGroupRefService.searchGroups("tes");
        assert ldapGroupsAll.size() == 3;

        List<LdapGroupRef> ldapGroupsTest = ldapGroupRefService.searchGroups("test1");
        assert ldapGroupsTest.size() == 1;

        List<LdapGroupRef> ldapGroupsNothing = ldapGroupRefService.searchGroups("nothing");
        assert ldapGroupsNothing.size() == 0;
    }

    @Test
    void assignRoleGroup() throws Exception {
        PetriNet net = petriNetService.importPetriNet(new FileInputStream("src/test/resources/role_all_data.xml"), VersionType.MAJOR, superCreator.getLoggedSuper()).getNet();
        assert net != null;
        Map<String, ProcessRole> roles = net.getRoles();
        assert roles != null;

        List<LdapGroupRef> ldapGroupsTest = ldapGroupRefService.searchGroups("test1");
        assert ldapGroupsTest.size() == 1;
        Set<String> role = new HashSet<>();
        roles.forEach((k, v) -> {
            role.add(v.getStringId());
        });
        assert role.size() == roles.size();
        ldapGroupRefService.setRoleToLdapGroup(ldapGroupsTest.get(0).getDn().toString(), role, superCreator.getLoggedSuper());
        Set<String> group = new HashSet<>();
        group.add(ldapGroupsTest.get(0).getDn().toString());
        Set<ProcessRole> getRole = ldapGroupRefService.getProcessRoleByLdapGroup(group);
        assert getRole.size() == roles.size();
    }

    @Test
    void assignRoleGroupAndCheck() throws Exception {
        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(USER_EMAIL_Test1, USER_PASSWORD_Test1);
        user.setDetails(new WebAuthenticationDetails(new MockHttpServletRequest()));

        MvcResult result = mvc.perform(get("/api/user/me")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("utf-8"))
                .andExpect(status().isOk())
                .andReturn();

        String string = result.getResponse().getContentAsString();

        JSONObject json = new JSONObject(string);
        JSONArray countProcessRole = (JSONArray) json.get("processRoles");
        assert countProcessRole.length() == 1;

        PetriNet net = petriNetService.importPetriNet(new FileInputStream("src/test/resources/role_all_data.xml"), VersionType.MAJOR, superCreator.getLoggedSuper()).getNet();
        assert net != null;
        Map<String, ProcessRole> roles = net.getRoles();
        assert roles != null;

        List<LdapGroupRef> ldapGroupsTest = ldapGroupRefService.searchGroups("test1");
        assert ldapGroupsTest.size() == 1;
        Set<String> role = new HashSet<>();
        roles.forEach((k, v) -> {
            role.add(v.getStringId());
        });
        assert role.size() == roles.size();
        ldapGroupRefService.setRoleToLdapGroup(ldapGroupsTest.get(0).getDn().toString(), role, superCreator.getLoggedSuper());

        Set<String> group = new HashSet<>();
        group.add(ldapGroupsTest.get(0).getDn().toString());
        Set<ProcessRole> getRole = ldapGroupRefService.getProcessRoleByLdapGroup(group);
        assert getRole.size() == roles.size();


        MvcResult result2 = mvc.perform(get("/api/auth/login")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("utf-8"))
                .andExpect(status().isOk())
                .andReturn();

        String response2 = result2.getResponse().getContentAsString();

        JSONObject json2 = new JSONObject(response2);
        JSONArray countProcessRole2 = (JSONArray) json2.get("processRoles");
        assert countProcessRole2.length() == 1 + roles.size();



        MvcResult result3 = mvc.perform(get("/api/auth/login")
                        .with(authentication(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("utf-8"))
                .andExpect(status().isOk())
                .andReturn();

        String response3 = result3.getResponse().getContentAsString();

        JSONObject json3 = new JSONObject(response3);
        JSONArray countProcessRole3 = (JSONArray) json3.get("processRoles");
        assert countProcessRole3.length() == 1 + roles.size();


    }

    @Test
    void getProcessRole() {
        Set<String> findDn = Set.of("nothing");
        Set<ProcessRole> processRoles = ldapGroupRefService.getProcessRoleByLdapGroup(findDn);
        assert processRoles.size() == 0;
    }

}
