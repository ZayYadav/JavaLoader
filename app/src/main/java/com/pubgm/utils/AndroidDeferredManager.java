package com.pubgm.utils;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AndroidDeferredManager {
    private static final Handler gUiHandler = new Handler(Looper.getMainLooper());
    private ExecutorService executorService = Executors.newCachedThreadPool();
    
    public <D, F, P> Promise<D, F, P> when(Promise<D, F, P> promise) {
        return new DeferredObject<D, F, P>().promise();
    }
    
    public <D, F, P> Promise<D, F, P> when(Promise<D, F, P>... promises) {
        return new DeferredObject<D, F, P>().promise();
    }
    
    // New method to handle Runnable
    public Promise<Void, Void, Void> when(final Runnable runnable) {
        final DeferredObject<Void, Void, Void> deferred = new DeferredObject<>();
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                    deferred.resolve(null);
                } catch (Exception e) {
                    deferred.reject(null);
                }
            }
        });
        
        return deferred.promise();
    }
    
    // State enum
    public enum State {
        PENDING, RESOLVED, REJECTED
    }
    
    // DeferredObject class
    public static class DeferredObject<D, F, P> implements Deferred<D, F, P> {
        private State state = State.PENDING;
        private D resolveResult;
        private F rejectResult;
        private P progressResult;
        
        private DoneCallback<D> doneCallback;
        private FailCallback<F> failCallback;
        private ProgressCallback<P> progressCallback;
        private AlwaysCallback<D, F> alwaysCallback;
        
        private static final Handler gUiHandler = new Handler(Looper.getMainLooper());
        
        @Override
        public Deferred<D, F, P> resolve(D resolve) {
            this.state = State.RESOLVED;
            this.resolveResult = resolve;
            
            gUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (doneCallback != null) {
                        doneCallback.onDone(resolveResult);
                    }
                    if (alwaysCallback != null) {
                        alwaysCallback.onAlways(state, resolveResult, null);
                    }
                }
            });
            
            return this;
        }
        
        @Override
        public Deferred<D, F, P> reject(F reject) {
            this.state = State.REJECTED;
            this.rejectResult = reject;
            
            gUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (failCallback != null) {
                        failCallback.onFail(rejectResult);
                    }
                    if (alwaysCallback != null) {
                        alwaysCallback.onAlways(state, null, rejectResult);
                    }
                }
            });
            
            return this;
        }
        
        @Override
        public Deferred<D, F, P> notify(P progress) {
            this.progressResult = progress;
            
            gUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (progressCallback != null) {
                        progressCallback.onProgress(progressResult);
                    }
                }
            });
            
            return this;
        }
        
        @Override
        public Promise<D, F, P> promise() {
            return new Promise<D, F, P>() {
                
                @Override
                public Promise<D, F, P> then(final DoneCallback<D> callback) {
                    doneCallback = callback;
                    if (state == State.RESOLVED) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onDone(resolveResult);
                            }
                        });
                    }
                    return this;
                }
                
                @Override
                public Promise<D, F, P> then(final DoneCallback<D> doneCallback, final FailCallback<F> failCallback) {
                    DeferredObject.this.doneCallback = doneCallback;
                    DeferredObject.this.failCallback = failCallback;
                    
                    if (state == State.RESOLVED && doneCallback != null) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                doneCallback.onDone(resolveResult);
                            }
                        });
                    } else if (state == State.REJECTED && failCallback != null) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                failCallback.onFail(rejectResult);
                            }
                        });
                    }
                    
                    return this;
                }
                
                @Override
                public Promise<D, F, P> then(final DoneCallback<D> doneCallback, final FailCallback<F> failCallback, final ProgressCallback<P> progressCallback) {
                    DeferredObject.this.doneCallback = doneCallback;
                    DeferredObject.this.failCallback = failCallback;
                    DeferredObject.this.progressCallback = progressCallback;
                    
                    if (state == State.RESOLVED && doneCallback != null) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                doneCallback.onDone(resolveResult);
                            }
                        });
                    } else if (state == State.REJECTED && failCallback != null) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                failCallback.onFail(rejectResult);
                            }
                        });
                    }
                    
                    return this;
                }
                
                @Override
                public Promise<D, F, P> done(final DoneCallback<D> callback) {
                    DeferredObject.this.doneCallback = callback;
                    if (state == State.RESOLVED) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onDone(resolveResult);
                            }
                        });
                    }
                    return this;
                }
                
                @Override
                public Promise<D, F, P> fail(final FailCallback<F> callback) {
                    DeferredObject.this.failCallback = callback;
                    if (state == State.REJECTED) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onFail(rejectResult);
                            }
                        });
                    }
                    return this;
                }
                
                @Override
                public Promise<D, F, P> always(final AlwaysCallback<D, F> callback) {
                    DeferredObject.this.alwaysCallback = callback;
                    if (state != State.PENDING) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onAlways(state, resolveResult, rejectResult);
                            }
                        });
                    }
                    return this;
                }
                
                @Override
                public Promise<D, F, P> progress(final ProgressCallback<P> callback) {
                    DeferredObject.this.progressCallback = callback;
                    if (state == State.PENDING && progressResult != null) {
                        gUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onProgress(progressResult);
                            }
                        });
                    }
                    return this;
                }
                
                @Override
                public Promise<D, F, P> then(DoneFilter<D, D> doneFilter) {
                    return this;
                }
                
                @Override
                public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(DoneFilter<D, D_OUT> doneFilter, FailFilter<F, F_OUT> failFilter, ProgressFilter<P, P_OUT> progressFilter) {
                    return null;
                }
                
                @Override
                public Promise<D, F, P> then(DonePipe<D, D, F, P> donePipe) {
                    return this;
                }
                
                @Override
                public <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(DonePipe<D, D_OUT, F_OUT, P_OUT> donePipe, FailPipe<F, D_OUT, F_OUT, P_OUT> failPipe, ProgressPipe<P, D_OUT, F_OUT, P_OUT> progressPipe) {
                    return null;
                }
                
                @Override
                public Promise<D, F, P> then(FailPipe<F, D, F, P> failPipe) {
                    return this;
                }
                
                @Override
                public State state() {
                    return state;
                }
                
                @Override
                public boolean isPending() {
                    return state == State.PENDING;
                }
                
                @Override
                public boolean isResolved() {
                    return state == State.RESOLVED;
                }
                
                @Override
                public boolean isRejected() {
                    return state == State.REJECTED;
                }
            };
        }
        
        @Override
        public boolean isResolved() {
            return state == State.RESOLVED;
        }
        
        @Override
        public boolean isRejected() {
            return state == State.REJECTED;
        }
        
        @Override
        public boolean isPending() {
            return state == State.PENDING;
        }
        
        @Override
        public Deferred<D, F, P> resolveWith(Deferred<D, F, P> deferred) {
            return this;
        }
        
        @Override
        public Deferred<D, F, P> rejectWith(Deferred<D, F, P> deferred) {
            return this;
        }
    }
    
    // Promise interface
    public interface Promise<D, F, P> {
        Promise<D, F, P> then(DoneCallback<D> callback);
        Promise<D, F, P> then(DoneCallback<D> doneCallback, FailCallback<F> failCallback);
        Promise<D, F, P> then(DoneCallback<D> doneCallback, FailCallback<F> failCallback, ProgressCallback<P> progressCallback);
        Promise<D, F, P> done(DoneCallback<D> callback);
        Promise<D, F, P> fail(FailCallback<F> callback);
        Promise<D, F, P> always(AlwaysCallback<D, F> callback);
        Promise<D, F, P> progress(ProgressCallback<P> callback);
        Promise<D, F, P> then(DoneFilter<D, D> doneFilter);
        <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(DoneFilter<D, D_OUT> doneFilter, FailFilter<F, F_OUT> failFilter, ProgressFilter<P, P_OUT> progressFilter);
        Promise<D, F, P> then(DonePipe<D, D, F, P> donePipe);
        <D_OUT, F_OUT, P_OUT> Promise<D_OUT, F_OUT, P_OUT> then(DonePipe<D, D_OUT, F_OUT, P_OUT> donePipe, FailPipe<F, D_OUT, F_OUT, P_OUT> failPipe, ProgressPipe<P, D_OUT, F_OUT, P_OUT> progressPipe);
        Promise<D, F, P> then(FailPipe<F, D, F, P> failPipe);
        State state();
        boolean isPending();
        boolean isResolved();
        boolean isRejected();
    }
    
    // Deferred interface
    public interface Deferred<D, F, P> {
        Deferred<D, F, P> resolve(D resolve);
        Deferred<D, F, P> reject(F reject);
        Deferred<D, F, P> notify(P progress);
        Promise<D, F, P> promise();
        boolean isResolved();
        boolean isRejected();
        boolean isPending();
        Deferred<D, F, P> resolveWith(Deferred<D, F, P> deferred);
        Deferred<D, F, P> rejectWith(Deferred<D, F, P> deferred);
    }
    
    // Callback interfaces
    public interface DoneCallback<D> {
        void onDone(D result);
    }
    
    public interface FailCallback<F> {
        void onFail(F error);
    }
    
    public interface ProgressCallback<P> {
        void onProgress(P progress);
    }
    
    public interface AlwaysCallback<D, F> {
        void onAlways(State state, D resolve, F reject);
    }
    
    // Filter interfaces
    public interface DoneFilter<D, D_OUT> {
        D_OUT filterDone(D result);
    }
    
    public interface FailFilter<F, F_OUT> {
        F_OUT filterFail(F result);
    }
    
    public interface ProgressFilter<P, P_OUT> {
        P_OUT filterProgress(P progress);
    }
    
    // Pipe interfaces
    public interface DonePipe<D, D_OUT, F_OUT, P_OUT> {
        Promise<D_OUT, F_OUT, P_OUT> pipeDone(D result);
    }
    
    public interface FailPipe<F, D_OUT, F_OUT, P_OUT> {
        Promise<D_OUT, F_OUT, P_OUT> pipeFail(F result);
    }
    
    public interface ProgressPipe<P, D_OUT, F_OUT, P_OUT> {
        Promise<D_OUT, F_OUT, P_OUT> pipeProgress(P progress);
    }
}