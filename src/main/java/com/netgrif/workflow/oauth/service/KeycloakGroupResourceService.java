package com.netgrif.workflow.oauth.service;

import com.netgrif.workflow.oauth.domain.KeycloakGroupResource;
import com.netgrif.workflow.oauth.domain.KeycloakUserResource;
import com.netgrif.workflow.oauth.service.interfaces.IRemoteGroupResourceService;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;

public class KeycloakGroupResourceService implements IRemoteGroupResourceService<KeycloakGroupResource, KeycloakUserResource> {

    @Value("${security.oauth2.client.realm}")
    protected String realm;

    @Autowired
    protected Keycloak keycloak;

    @Autowired
    protected KeycloakUserResourceService userResourceService;

    @Override
    public Page<KeycloakGroupResource> listGroups(Pageable pageable) {
        return page(
                groupsResource().groups((int) pageable.getOffset(), pageable.getPageSize()),
                pageable,
                countGroups()
        );
    }

    @Override
    public Page<KeycloakGroupResource> searchGroups(String searchString, Pageable pageable, boolean small) {
        return page(
                groupsResource().groups(searchString, (int) pageable.getOffset(), pageable.getPageSize()),
                pageable,
                countGroups()
        );
    }

    @Override
    public long countGroups() {
        return groupsResource().count().get("count");
    }

    @Override
    public long countGroups(String searchString) {
        return groupsResource().count(searchString).get("count");
    }

    @Override
    public KeycloakGroupResource find(String id) {
        return wrap(groupsResource().group(id).toRepresentation());
    }

    @Override
    public List<KeycloakUserResource> members(String id) {
        return groupsResource().group(id).members().stream().map(KeycloakUserResource::new).collect(Collectors.toList());
    }

    @Override
    public List<KeycloakGroupResource> groupsOfUser(String id) {
        return userResourceService.usersResource().get(id).groups().stream().map(this::wrap).collect(Collectors.toList());
    }

    protected GroupsResource groupsResource() {
        RealmResource realmResource = keycloak.realm(realm);
        return realmResource.groups();
    }

    protected Page<KeycloakGroupResource> page(List<GroupRepresentation> list, Pageable pageable, long total) {
        return new PageImpl<>(list.stream().map(this::wrap).collect(Collectors.toList()), pageable, total);
    }

    protected KeycloakGroupResource wrap(GroupRepresentation representation) {
        return new KeycloakGroupResource(representation);
    }

}
