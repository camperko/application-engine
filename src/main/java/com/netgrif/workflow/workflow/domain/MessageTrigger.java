package com.netgrif.workflow.workflow.domain;

import org.bson.types.ObjectId;

public class MessageTrigger extends Trigger {

    public MessageTrigger() {
        this._id = new ObjectId();
    }
}