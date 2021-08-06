package com.netgrif.workflow.workflow.service;

import com.netgrif.workflow.elastic.service.executors.MaxSizeHashMap;
import com.netgrif.workflow.event.IGroovyShellFactory;
import com.netgrif.workflow.petrinet.domain.Function;
import com.netgrif.workflow.petrinet.domain.FunctionScope;
import com.netgrif.workflow.petrinet.domain.PetriNet;
import com.netgrif.workflow.petrinet.domain.dataset.logic.action.Action;
import com.netgrif.workflow.petrinet.service.interfaces.IPetriNetService;
import com.netgrif.workflow.workflow.domain.CachedFunction;
import com.netgrif.workflow.workflow.service.interfaces.IFieldActionsCacheService;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FieldActionsCacheService implements IFieldActionsCacheService {

    private final Long actionCacheMaxSize;
    private final Long functionCacheMaxSize;
    private final Long namespaceFunctionCacheMaxSize;
    private IPetriNetService petriNetService;

    private Map<String, Closure> actionsCache;
    private Map<String, List<CachedFunction>> namespaceFunctionsCache;
    private Map<String, CachedFunction> functionsCache;
    private final GroovyShell shell;

    public FieldActionsCacheService(@Value("${nae.field-runner.actions.cache-size}") Long actionCacheMaxSize,
                                    @Value("${nae.field-runner.functions.namespace.cache-size}") Long namespaceFunctionCacheMaxSize,
                                    @Value("${nae.field-runner.functions.cache-size}") Long functionCacheMaxSize,
                                    IGroovyShellFactory shellFactory) {
        this.actionCacheMaxSize = actionCacheMaxSize;
        this.functionCacheMaxSize = functionCacheMaxSize;
        this.namespaceFunctionCacheMaxSize = namespaceFunctionCacheMaxSize;
        this.actionsCache = new MaxSizeHashMap<>(actionCacheMaxSize);
        this.functionsCache = new MaxSizeHashMap<>(functionCacheMaxSize);
        this.namespaceFunctionsCache = new MaxSizeHashMap<>(namespaceFunctionCacheMaxSize);
        this.shell = shellFactory.getGroovyShell();
    }

    @Autowired
    @Lazy
    public void setPetriNetService(IPetriNetService petriNetService) {
        this.petriNetService = petriNetService;
    }

    @Override
    public void cachePetriNetFunctions(PetriNet petriNet) {
        if (petriNet != null) {
            List<CachedFunction> functions = petriNet.getNamespaceFunctions().stream()
                    .map(function -> CachedFunction.build(shell, function))
                    .collect(Collectors.toList());

            if (!functions.isEmpty()) {
                namespaceFunctionsCache.put(petriNet.getIdentifier(), functions);
            }
        }
    }

    @Override
    public void removeCachePetriNetFunctions(PetriNet petriNet) {
        namespaceFunctionsCache.remove(petriNet.getIdentifier());
        cachePetriNetFunctions(petriNetService.getNewestVersionByIdentifier(petriNet.getIdentifier()));
    }

    @Override
    public Closure getCompiledAction(Action action) {
        if (!actionsCache.containsKey(action.getImportId())) {
            Closure code = (Closure) shell.evaluate("{-> " + action.getDefinition() + "}");
            actionsCache.put(action.getImportId(), code);
        }
        return actionsCache.get(action.getImportId());
    }

    @Override
    public List<CachedFunction> getCachedFunctions(List<Function> functions) {
        List<CachedFunction> cachedFunctions = new ArrayList<>(functions.size());
        functions.stream().filter(function -> FunctionScope.PROCESS.equals(function.getScope())).forEach(function -> {
            if (!functionsCache.containsKey(function.getStringId())) {
                functionsCache.put(function.getStringId(), CachedFunction.build(shell, function));
            }
            cachedFunctions.add(functionsCache.get(function.getStringId()));
        });
        return cachedFunctions;
    }

    @Override
    public Map<String, List<CachedFunction>> getNamespaceFunctionCache() {
        return new HashMap<>(namespaceFunctionsCache);
    }

    @Override
    public void clearActionCache() {
        this.actionsCache = new MaxSizeHashMap<>(actionCacheMaxSize);
    }

    @Override
    public void clearNamespaceFunctionCache() {
        this.namespaceFunctionsCache = new MaxSizeHashMap<>(namespaceFunctionCacheMaxSize);
    }

    @Override
    public void clearFunctionCache() {
        this.functionsCache = new MaxSizeHashMap<>(functionCacheMaxSize);
    }
}
