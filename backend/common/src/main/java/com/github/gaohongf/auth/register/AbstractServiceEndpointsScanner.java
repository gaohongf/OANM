package com.github.gaohongf.auth.register;


public abstract class AbstractServiceEndpointsScanner<T> implements ServiceEndpointsScanner{

    protected final T handlerMapping;

    public AbstractServiceEndpointsScanner(T handlerMapping){
        this.handlerMapping = handlerMapping;
    }
}
