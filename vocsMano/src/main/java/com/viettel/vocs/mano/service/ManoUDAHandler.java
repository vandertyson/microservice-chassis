package com.viettel.vocs.mano.service;

public class ManoUDAHandler implements Runnable {
    private final String requestPath;
    private final String requestBody;
    private final String invoker;
    private final Runnable runnable;

    public ManoUDAHandler(String requestPath, String requestBody, String invoker, Runnable runnable) {
        this.requestPath = requestPath;
        this.requestBody = requestBody;
        this.invoker = invoker;
        this.runnable = runnable;
    }

    @Override
    public void run() {
        runnable.run();
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public String getInvoker() {
        return invoker;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    @Override
    public String toString() {
        return "ManoUDAHandler{" +
                "requestPath='" + requestPath + '\'' +
                ", requestBody='" + requestBody + '\'' +
                ", invoker='" + invoker + '\'' +
                ", runnable=" + runnable +
                '}';
    }
}
