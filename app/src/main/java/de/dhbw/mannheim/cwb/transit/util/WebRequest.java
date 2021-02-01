package de.dhbw.mannheim.cwb.transit.util;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Lukas Rothenbach
 * Responsible for performing an asynch okHttp Request
 */
public class WebRequest implements Future<Response>, Callback {

    private final Object LOCK = new Object();
    private Response response;
    private IOException failure;

    // can't be cancelled
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return response != null || failure != null;
    }

    @Override
    public Response get() throws ExecutionException, InterruptedException {
        synchronized (LOCK) {
            if (!isDone()) LOCK.wait();

            if (response != null) return response;
            else throw new ExecutionException(failure);
        }
    }

    @Override
    public Response get(long timeout, @NotNull TimeUnit unit) throws ExecutionException, InterruptedException {
        synchronized (LOCK) {
            if (!isDone()) unit.timedWait(LOCK, timeout);

            if (response != null) return response;
            else throw new ExecutionException(failure);
        }
    }

    @Override
    public void onFailure(Request request, IOException e) {
        synchronized (LOCK) {
            failure = e;
            LOCK.notifyAll();
        }
    }

    @Override
    public void onResponse(Response response) {
        synchronized (LOCK) {
            this.response = response;
            LOCK.notifyAll();
        }
    }
}
