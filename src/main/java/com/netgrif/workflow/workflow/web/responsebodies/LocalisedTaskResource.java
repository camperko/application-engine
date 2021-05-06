package com.netgrif.workflow.workflow.web.responsebodies;

import com.netgrif.workflow.workflow.web.TaskController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.security.core.Authentication;

import java.io.FileNotFoundException;
import java.util.ArrayList;


public class LocalisedTaskResource extends Resource<Task> {

    public static final Logger log = LoggerFactory.getLogger(LocalisedTaskResource.class);

    public LocalisedTaskResource(Task content) {
        super(content, new ArrayList<Link>());
        buildLinks();
    }

    private void buildLinks() {
        Task task = getContent();
        add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(TaskController.class)
                .getOne(task.getStringId(), null)).withSelfRel());
        add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(TaskController.class)
                .assign((Authentication) null, task.getStringId(), null)).withRel("assign"));
        add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(TaskController.class)
                .delegate((Authentication) null, task.getStringId(), null, null)).withRel("delegate"));
        add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(TaskController.class)
                .finish((Authentication) null, task.getStringId(), null)).withRel("finish"));
        add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(TaskController.class)
                .cancel((Authentication) null, task.getStringId(), null)).withRel("cancel"));
        add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(TaskController.class)
                .getData(task.getStringId(), null)).withRel("data"));
        add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(TaskController.class)
                .setData(null, task.getStringId(), null)).withRel("data-edit"));
        try {
            add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(TaskController.class)
                    .getFile(task.getStringId(), "", null)).withRel("file"));
        } catch (FileNotFoundException e) {
            log.error("Building links failed: ", e);
        }
    }
}