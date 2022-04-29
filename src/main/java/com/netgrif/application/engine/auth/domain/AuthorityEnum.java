package com.netgrif.application.engine.auth.domain;

public enum AuthorityEnum {
    PROCESS_UPLOAD("PROCESS.UPLOAD"),
    PROCESS_DELETE("PROCESS.DELETE"),
    FILTER_UPLOAD("FILTER.UPLOAD"),
    FILTER_DELETE("FILTER.DELETE"),
    USER_CREATE("USER.CREATE"),
    USER_DELETE("USER.DELETE"),
    USER_EDIT("USER.EDIT"),
    GROUP_CREATE("GROUP.CREATE"),
    GROUP_DELETE("GROUP.DELETE"),
    GROUP_ADD_USER("GROUP.ADD_USER"),
    GROUP_REMOVE_USER("GROUP.REMOVE_USER"),
    ROLE_CREATE("ROLE.CREATE"),
    ROLE_DELETE("ROLE.DELETE"),
    AUTHORITY_CREATE("AUTHORITY.CREATE"),
    AUTHORITY_DELETE("AUTHORITY.DELETE");

    AuthorityEnum(String name) {
    }
}
