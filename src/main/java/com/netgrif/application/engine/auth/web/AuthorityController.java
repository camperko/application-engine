package com.netgrif.application.engine.auth.web;

import com.netgrif.application.engine.auth.domain.Authority;
import com.netgrif.application.engine.auth.domain.Authorizations;
import com.netgrif.application.engine.auth.domain.Authorize;
import com.netgrif.application.engine.auth.service.interfaces.IAuthorityService;
import com.netgrif.application.engine.auth.web.requestbodies.NewAuthorityRequest;
import com.netgrif.application.engine.auth.web.responsebodies.AuthorityResource;
import com.netgrif.application.engine.workflow.web.responsebodies.MessageResource;
import com.netgrif.application.engine.workflow.web.responsebodies.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/authority")
@ConditionalOnProperty(
        value = "nae.user.web.enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Tag(name = "Authority")
public class AuthorityController {

    @Autowired
    private IAuthorityService authorityService;

    @Authorizations(value = {
            @Authorize(authority = "AUTHORITY_DELETE")
    })
    @Operation(description = "Delete authority", security = {@SecurityRequirement(name = "BasicAuth")})
    @DeleteMapping(value = "/delete/{name}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaTypes.HAL_JSON_VALUE)
    public MessageResource delete(@PathVariable String name) {
        try {
            authorityService.delete(name);
            log.info("Authority [" + name + "] has been deleted successfully.");
            return new MessageResource(ResponseMessage.createSuccessMessage("Authority [" + name + "] has been deleted successfully."));
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            log.error("Failed to delete authority [" + name  + "].", e);
            return new MessageResource(ResponseMessage.createErrorMessage("Failed to delete authority."));
        }
    }

    @Authorizations(value = {
            @Authorize(authority = "AUTHORITY_CREATE")
    })
    @Operation(description = "Delete authority", security = {@SecurityRequirement(name = "BasicAuth")})
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaTypes.HAL_JSON_VALUE)
    public EntityModel<Authority> create(@RequestBody NewAuthorityRequest request) {
        try {
            Authority authority = authorityService.getOrCreate(request.name);
            log.info("Authority [" + authority + "] has been created successfully.");
            return AuthorityResource.of(authority);
        } catch (IllegalArgumentException | ResourceNotFoundException e) {
            log.error("Failed to create authority [" + request.name  + "].", e);
            return null;
        }
    }
}
