package com.netgrif.workflow.auth.service;

import com.netgrif.workflow.auth.domain.IUser;
import com.netgrif.workflow.auth.domain.RegisteredUser;
import com.netgrif.workflow.auth.domain.User;
import com.netgrif.workflow.auth.domain.UserState;
import com.netgrif.workflow.auth.domain.repositories.UserRepository;
import com.netgrif.workflow.auth.service.interfaces.IRegistrationService;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import com.netgrif.workflow.auth.web.requestbodies.NewUserRequest;
import com.netgrif.workflow.auth.web.requestbodies.RegistrationRequest;
import com.netgrif.workflow.configuration.properties.ServerAuthProperties;
import com.netgrif.workflow.orgstructure.groups.interfaces.INextGroupService;
import com.netgrif.workflow.petrinet.service.interfaces.IProcessRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Slf4j
public class RegistrationService implements IRegistrationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IUserService userService;

    @Autowired
    private INextGroupService groupService;

    @Autowired
    private IProcessRoleService processRole;

    @Autowired
    private ServerAuthProperties serverAuthProperties;

    @Autowired
    protected BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    @Transactional
    @Scheduled(cron = "0 0 1 * * *")
    public void removeExpiredUsers() {
        log.info("Removing expired unactivated invited users");
        List<User> expired = userRepository.removeAllByStateAndExpirationDateBefore(UserState.INVITED, LocalDateTime.now());
        log.info("Removed " + expired.size() + " unactivated users");
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 1 * * *")
    public void resetExpiredToken() {
        log.info("Resetting expired user tokens");
        List<User> users = userRepository.findAllByStateAndExpirationDateBefore(UserState.BLOCKED, LocalDateTime.now());
        if (users == null || users.isEmpty()) {
            log.info("There are none expired tokens. Everything is awesome.");
            return;
        }

        users.forEach(user -> {
            user.setToken(null);
            user.setExpirationDate(null);
        });
        users = userRepository.saveAll(users);
        log.info("Reset " + users.size() + " expired user tokens");
    }

    @Override
    public void changePassword(RegisteredUser user, String newPassword) {
        user.setPassword(newPassword);
        encodeUserPassword(user);
        userService.save(user);
        log.info("Changed password for user " + user.getEmail() + ".");
    }

    @Override
    public boolean verifyToken(String token) {
        try {
            log.info("Verifying token:" + token);
            String[] tokenParts = decodeToken(token);
            User user = userRepository.findByEmail(tokenParts[0]);
            return user != null && Objects.equals(user.getToken(), tokenParts[1]) && user.getExpirationDate().isAfter(LocalDateTime.now());
        } catch (InvalidUserTokenException e) {
            log.error(e.getMessage());
            return false;
        }
    }

    @Override
    public void encodeUserPassword(RegisteredUser user) {
        String pass = user.getPassword();
        if (pass == null)
            throw new IllegalArgumentException("User has no password");
        user.setPassword(bCryptPasswordEncoder.encode(pass));
    }

    @Override
    public boolean stringMatchesUserPassword(RegisteredUser user, String passwordToCompare) {
        return bCryptPasswordEncoder.matches(passwordToCompare, user.getPassword());
    }

    @Override
    @Transactional
    public User createNewUser(NewUserRequest newUser) {
        User user;
        if (userRepository.existsByEmail(newUser.email)) {
            user = userRepository.findByEmail(newUser.email);
            if (user.isActive())
                return null;
            log.info("Renewing old user [" + newUser.email + "]");
        } else {
            user = new User(newUser.email, null, User.UNKNOWN, User.UNKNOWN);
            log.info("Creating new user [" + newUser.email + "]");
        }
        user.setToken(generateTokenKey());
        user.setPassword("");
        user.setExpirationDate(generateExpirationDate());
        user.setState(UserState.INVITED);
        userService.addDefaultAuthorities(user);

        if (newUser.processRoles != null && !newUser.processRoles.isEmpty()) {
            user.setProcessRoles(new HashSet<>(processRole.findByIds(newUser.processRoles)));
        }
        userService.addDefaultRole(user);
        user =  userRepository.save(user);
        groupService.createGroup(user);
        groupService.addUserToDefaultGroup(user);
        if (newUser.groups != null && !newUser.groups.isEmpty()) {
            for (String group : newUser.groups){
                groupService.addUser((IUser) user, group);
            }
        }

        return userRepository.save(user);
    }

    @Override
    public RegisteredUser registerUser(RegistrationRequest registrationRequest) throws InvalidUserTokenException {
        String email = decodeToken(registrationRequest.token)[0];
        log.info("Registering user " + email);
        RegisteredUser user = userRepository.findByEmail(email);
        if (user == null)
            return null;

        user.setName(registrationRequest.name);
        user.setSurname(registrationRequest.surname);
        user.setPassword(registrationRequest.password);

        user.setToken(null);
        user.setExpirationDate(null);
        user.setState(UserState.ACTIVE);

        return (RegisteredUser) userService.saveNew(user);
    }

    @Override
    public RegisteredUser resetPassword(String email) {
        log.info("Resetting password of " + email);
        User user = userRepository.findByEmail(email);
        if (user == null || !user.isActive()) {
            String state = user == null ? "Non-existing" : "Inactive";
            log.info(state + " user [" + email + "] tried to reset his password");
            return null;
        }

        user.setState(UserState.BLOCKED);
        user.setPassword(null);
        user.setToken(generateTokenKey());
        user.setExpirationDate(generateExpirationDate());
        return (RegisteredUser) userService.save(user);
    }

    @Override
    public RegisteredUser recover(String email, String newPassword) {
        log.info("Recovering user " + email);
        User user = userRepository.findByEmail(email);
        if (user == null)
            return null;

        user.setState(UserState.ACTIVE);
        user.setPassword(newPassword);
        encodeUserPassword(user);
        user.setToken(null);
        user.setExpirationDate(null);

        return (RegisteredUser) userService.save(user);
    }

    @Override
    public String generateTokenKey() {
        return new BigInteger(256, new SecureRandom()).toString(32);
    }

    @Override
    public String[] decodeToken(String token) throws InvalidUserTokenException {
        String[] parts = new String(Base64.getDecoder().decode(token)).split(":");
        if (parts.length != 2 || !parts[0].contains("@"))
            throw new InvalidUserTokenException(token);
        return parts;
    }

    @Override
    public String encodeToken(String email, String tokenKey) {
        return Base64.getEncoder().encodeToString((email + ":" + tokenKey).getBytes());
    }

    @Override
    public LocalDateTime generateExpirationDate() {
        return LocalDateTime.now().plusDays(serverAuthProperties.getTokenValidityPeriod());
    }

    @Override
    public boolean isPasswordSufficient(String password) {
        return password.length() >= serverAuthProperties.getMinimalPasswordLength();
    }
}
