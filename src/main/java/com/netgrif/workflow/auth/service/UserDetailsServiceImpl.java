package com.netgrif.workflow.auth.service;

import com.netgrif.workflow.auth.domain.LoggedUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.auth.domain.repositories.UserRepository;
import com.netgrif.workflow.event.events.user.UserLoginEvent;
import com.netgrif.workflow.orgstructure.domain.Group;
import com.netgrif.workflow.orgstructure.domain.Member;
import com.netgrif.workflow.orgstructure.service.IMemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IMemberService memberService;

    @Autowired
    private ApplicationEventPublisher publisher;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        LoggedUser loggedUser = getLoggedUser(email);
        setGroups(loggedUser);

        publisher.publishEvent(new UserLoginEvent(loggedUser));

        return loggedUser;
    }

    private LoggedUser getLoggedUser(String email) {
        User user = userRepository.findByEmail(email);

        return user.transformToLoggedUser();
    }

    private void setGroups(LoggedUser loggedUser) {
        Member member = memberService.findByEmail(loggedUser.getUsername());
        if (member != null) {
            loggedUser.setGroups(member.getGroups().parallelStream().map(Group::getId).collect(Collectors.toSet()));
        }
    }
}