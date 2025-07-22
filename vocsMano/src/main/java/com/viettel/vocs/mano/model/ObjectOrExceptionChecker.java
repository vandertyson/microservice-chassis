package com.viettel.vocs.mano.model;

public abstract class ObjectOrExceptionChecker<T> implements Checker<T> {

    public final Checkable<T> performCheck() {
        T result = null;
        StringBuilder sb = new StringBuilder();
        try {
            result = process();
        } catch (Exception ex) {
            sb.append(ex.getMessage());
        }
        final T report = result;
        return new Checkable<>() {
            @Override
            public boolean isHealthy() {
                return sb.length() == 0;
            }

            @Override
            public T getCheckDetail() {
                return report;
            }
        };
    }

    public abstract T process();

}
