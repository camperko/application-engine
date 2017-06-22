package com.netgrif.workflow.petrinet.web.responsebodies;

import com.netgrif.workflow.petrinet.domain.Transaction;
import com.netgrif.workflow.petrinet.web.PetriNetController;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

public class TransactionsResource extends Resources<TransactionResource> {

    public TransactionsResource(Iterable<TransactionResource> content, String netId) {
        super(content, new ArrayList<>());
        buildLinks(netId);
    }

    public TransactionsResource(Collection<Transaction> content, String netId){
        this(content.stream().map(TransactionResource::new).collect(Collectors.toList()), netId);
    }

    private void buildLinks(String netId) {
        add(ControllerLinkBuilder.linkTo(ControllerLinkBuilder.methodOn(PetriNetController.class)
                .getTransactions(netId)).withSelfRel());
    }
}