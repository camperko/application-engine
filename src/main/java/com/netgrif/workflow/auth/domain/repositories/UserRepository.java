package com.netgrif.workflow.auth.domain.repositories;

import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.auth.domain.UserState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
public interface UserRepository extends MongoRepository<User, String>, QuerydslPredicateExecutor<User> {

    Page<User> findAllByIdInAndState(Set<String> ids, UserState state, Pageable pageable);

    User findByEmail(String email);

    List<User> findAllByStateAndExpirationDateBefore(UserState userState, LocalDateTime dateTime);

    Page<User> findDistinctByStateAndUserProcessRoles_RoleIdIn(UserState state, List<String> roleId, Pageable pageable);

    List<User> findAllByUserProcessRoles_RoleIdIn(List<String> roleId);

    List<User> removeAllByStateAndExpirationDateBefore(UserState state, LocalDateTime dateTime);

    boolean existsByEmail(String email);
}