package com.github.kubatatami.judonetworking.callbacks;

import com.github.kubatatami.judonetworking.AsyncResult;
import com.github.kubatatami.judonetworking.CacheInfo;
import com.github.kubatatami.judonetworking.builder.CallbackBuilder;
import com.github.kubatatami.judonetworking.exceptions.JudoException;

/**
 * Created by Kuba on 19/03/15.
 */
public class DecoratorCallback<T> extends CallbackBuilder.LambdaCallback<T> {

    protected Callback<T> internalCallback;

    protected MergeCallback internalMergeCallback;

    public DecoratorCallback(CallbackBuilder<T, ?> builder) {
        super(builder);
    }

    public DecoratorCallback(Callback<T> callback) {
        this.internalCallback = callback;
    }

    public DecoratorCallback(MergeCallback mergeCallback) {
        this.internalMergeCallback = mergeCallback;
    }

    @Override
    public void onStart(CacheInfo cacheInfo, AsyncResult asyncResult) {
        super.onStart(cacheInfo, asyncResult);
        if (internalCallback != null) {
            internalCallback.onStart(cacheInfo, asyncResult);
        }
        if (internalMergeCallback != null) {
            internalMergeCallback.addStart(asyncResult);
        }
    }

    @Override
    public void onProgress(int progress) {
        super.onProgress(progress);
        if (internalCallback != null) {
            internalCallback.onProgress(progress);
        }
        if (internalMergeCallback != null) {
            internalMergeCallback.addProgress(this, progress);
        }
    }

    @Override
    public void onSuccess(T result) {
        super.onSuccess(result);
        if (internalCallback != null) {
            internalCallback.onSuccess(result);
        }
        if (internalMergeCallback != null) {
            internalMergeCallback.addSuccess();
        }
    }

    @Override
    public void onError(JudoException e) {
        super.onError(e);
        if (internalCallback != null) {
            internalCallback.onError(e);
        }
        if (internalMergeCallback != null) {
            internalMergeCallback.addError(e);
        }
    }

    @Override
    public void onFinish() {
        super.onFinish();
        if (internalCallback != null) {
            internalCallback.onFinish();
        }
    }

    class Builder<T> extends CallbackBuilder<T, Builder<T>> {

        protected Callback<T> internalCallback;

        protected MergeCallback internalMergeCallback;

        public Builder(Callback<T> callback) {
            this.internalCallback = callback;
        }

        public Builder(MergeCallback callback) {
            this.internalMergeCallback = callback;
        }

        @Override
        public DecoratorCallback<T> build() {
            return new DecoratorCallback<>(this);
        }
    }
}
