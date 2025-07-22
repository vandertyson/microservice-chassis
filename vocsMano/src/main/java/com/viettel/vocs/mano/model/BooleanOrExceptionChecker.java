package com.viettel.vocs.mano.model;

public abstract class BooleanOrExceptionChecker implements Checker {

    public final Checkable<String> performCheck() {
        Boolean result = null;
        StringBuilder sb = new StringBuilder();
        try {
            result = isHealthy();
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

    public abstract boolean isHealthy() throws Exception;

}
