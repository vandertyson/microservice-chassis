package com.viettel.vocs.mano.model;

public abstract class VoidOrExceptionChecker implements Checker {

    public final Checkable<String> performCheck() {
        boolean result = false;
        StringBuilder sb = new StringBuilder();
        try {
            process();
            result = true;
        } catch (Exception ex) {
            sb.append(ex.getMessage());
        }
        final boolean report = result;
        return new Checkable<>() {
            @Override
            public boolean isHealthy() {
                return report;
            }

            @Override
            public String getCheckDetail() {
                return sb.toString();
            }
        };
    }

    public abstract void process() throws Exception;

}
