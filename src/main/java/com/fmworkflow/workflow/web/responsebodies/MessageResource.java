package com.fmworkflow.workflow.web.responsebodies;


import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;

import java.util.ArrayList;

public class MessageResource extends Resource<ResponseMessage>{

    public MessageResource(ResponseMessage content) {
        super(content, new ArrayList<Link>());
    }

    public static MessageResource successMessage(String msg){
        return new MessageResource(ResponseMessage.createSuccessMessage(msg));
    }

    public static MessageResource errorMessage(String msg){
        return new MessageResource(ResponseMessage.createErrorMessage(msg));
    }

}
