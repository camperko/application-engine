package com.netgrif.workflow.configuration;

import com.netgrif.workflow.auth.service.RegistrationService;
import com.netgrif.workflow.auth.service.UserService;
import com.netgrif.workflow.auth.service.interfaces.IRegistrationService;
import com.netgrif.workflow.auth.service.interfaces.IUserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserServiceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IUserService userService() {
        return new UserService();
    }

    @Bean
    @ConditionalOnMissingBean
    public IRegistrationService registrationService() {
        return new RegistrationService();
    }

}
