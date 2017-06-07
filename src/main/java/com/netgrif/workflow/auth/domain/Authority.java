package com.netgrif.workflow.auth.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Set;

@Entity
@Table(name = "authority")
public class Authority implements GrantedAuthority {

    public static final String admin = "ROLE_ADMIN";
    public static final String user = "ROLE_USER";

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Getter
    private Long id;

    @NotNull
    @Column(unique = true)
    @JsonIgnore
    @Getter @Setter
    private String name;

    @JsonIgnore
    @ManyToMany(mappedBy = "authorities")
    @Getter @Setter
    private Set<User> users;

    public Authority(){}

    public Authority(String name) {
        this.name = name;
    }

    public void addUser(User user) {
        users.add(user);
    }

    @Override
    public String getAuthority() {
        return this.name;
    }
}
