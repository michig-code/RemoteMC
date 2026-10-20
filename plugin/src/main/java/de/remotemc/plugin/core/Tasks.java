package de.remotemc.plugin.core;

import de.remotemc.plugin.api.ApiException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Waiting for work handed to the server thread, with API errors passed through. */
public final class Tasks {

    private Tasks() {
    }

    public static <T> T await(CompletableFuture<T> future) throws Exception {
        return await(future, 10);
    }

    public static <T> T await(CompletableFuture<T> future, int timeoutSeconds) throws Exception {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            // Tasks wrap checked API errors in CompletionException to pass them out.
            if (cause instanceof CompletionException ce && ce.getCause() instanceof ApiException api) {
                throw api;
            }
            if (cause instanceof ApiException api) {
                throw api;
            }
            throw cause instanceof Exception c ? c : e;
        } catch (TimeoutException e) {
            throw new ApiException(504, "timeout", "The server did not respond in time");
        }
    }

    /** For code on the server thread that has to report an API error. */
    public interface Checked<T> {
        T get() throws ApiException;
    }

    public static <T> java.util.function.Supplier<T> unchecked(Checked<T> task) {
        return () -> {
            try {
                return task.get();
            } catch (ApiException e) {
                throw new CompletionException(e);
            }
        };
    }
}
