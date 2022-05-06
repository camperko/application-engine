package com.netgrif.application.engine.auth.service;

import com.netgrif.application.engine.auth.domain.Author;
import com.netgrif.application.engine.auth.domain.Authorize;
import com.netgrif.application.engine.auth.domain.AuthorizingObject;
import com.netgrif.application.engine.auth.service.interfaces.IAuthorityService;
import com.netgrif.application.engine.auth.service.interfaces.IUserService;
import com.netgrif.application.engine.configuration.ApplicationContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;


/**
 * The aspect service for authorization system.
 * */
@Slf4j
@Aspect
@Service
public class BaseAuthorizationService extends AbstractBaseAuthorizationService {

    public BaseAuthorizationService(@Autowired IUserService userService) {
        super(userService);
    }

    /**
     * Pointcut definition for {@link Authorize} annotation to connect authorization requests from join points to the
     * {@link #authorize(ProceedingJoinPoint, Authorize)} advice.
     * @param authorize the annotation with authorization requirements
     * */
    @Pointcut(value = "@annotation(authorize))")
    protected void authorizingMethod(Authorize authorize) {}


    /**
     * The advice that handles incoming authorization requests from join points from
     * {@link #authorizingMethod(Authorize)} pointcuts
     * @param joinPoint the incoming method invocation join point
     * @param authorize the incoming annotation with authorization parameters
     * */
    @Around(value = "authorizingMethod(authorize)", argNames = "joinPoint,authorize")
    protected Object authorize(ProceedingJoinPoint joinPoint, Authorize authorize) throws Throwable {
        if (isAllowedByExpression(joinPoint, authorize.expression()) || hasAuthority(authorize.authority())) {
            return joinPoint.proceed();
        } else {
            throw new AccessDeniedException("Access Denied");
        }
    }

    /**
     * Parser and evaluator function for Spring-EL expression. It creates parser for SpEL expression, context for it,
     * and evaluates the authorization SpEL expression. It resolves the arguments of invoked method and sets as
     * variables into the evaluation context, because  in the SpEL expression there can be method arguments as variables
     * alongside beans.
     * @param joinPoint the incoming method invocation join point
     * @param expression the SpEL expression
     * @return the evaluated value, whether the SpEL expression returns true or not
     * */
    private boolean isAllowedByExpression(ProceedingJoinPoint joinPoint, String expression) {
        if (expression == null || expression.equals("")) {
            return true;
        }

        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(ApplicationContextProvider.getAppContext()));

        List<Object> args = Arrays.asList(joinPoint.getArgs());
        List<String> argNames = Arrays.asList(((MethodSignature) joinPoint.getSignature()).getParameterNames());

        argNames.forEach(name -> context.setVariable(name, args.get(argNames.indexOf(name))));

        boolean allowed;
        try {
            allowed = ExpressionUtils.evaluateAsBoolean(parser.parseExpression(expression), context);
            return allowed;
        } catch (EvaluationException | NullPointerException e) {
            log.warn("Failed to parse expression '" + expression + "'.");
            return false;
        }
    }
}
