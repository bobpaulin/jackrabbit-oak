/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.kernel;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.apache.jackrabbit.mk.api.MicroKernel;
import org.apache.jackrabbit.mk.api.MicroKernelException;
import org.apache.jackrabbit.oak.cache.CacheStats;
import org.apache.jackrabbit.oak.spi.commit.EmptyObserver;
import org.apache.jackrabbit.oak.spi.commit.Observer;
import org.apache.jackrabbit.oak.spi.state.AbstractNodeStore;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.spi.state.NodeStoreBranch;

/**
 * {@code NodeStore} implementations against {@link MicroKernel}.
 */
public class KernelNodeStore extends AbstractNodeStore {

    private static final long DEFAULT_CACHE_SIZE = 16 * 1024 * 1024;

    /**
     * The {@link MicroKernel} instance used to store the content tree.
     */
    private final MicroKernel kernel;

    /**
     * Change observer.
     */
    @Nonnull
    private volatile Observer observer = EmptyObserver.INSTANCE;

    private final LoadingCache<String, KernelNodeState> cache;

    private final CacheStats cacheStats;

    /**
     * Lock passed to branches for coordinating merges
     */
    private final Lock mergeLock = new ReentrantLock();

    /**
     * State of the current root node.
     */
    private KernelNodeState root;

    public KernelNodeStore(final MicroKernel kernel, long cacheSize) {
        this.kernel = checkNotNull(kernel);

        Weigher<String, KernelNodeState> weigher = new Weigher<String, KernelNodeState>() {
            @Override
            public int weigh(String key, KernelNodeState state) {
                return state.getMemory();
            }
        };
        this.cache = CacheBuilder.newBuilder()
                .maximumWeight(cacheSize)
                .recordStats()
                .weigher(weigher)
                .build(new CacheLoader<String, KernelNodeState>() {
                    @Override
                    public KernelNodeState load(String key) {
                        int slash = key.indexOf('/');
                        String revision = key.substring(0, slash);
                        String path = key.substring(slash);
                        return new KernelNodeState(kernel, path, revision, cache);
                    }

                    @Override
                    public ListenableFuture<KernelNodeState> reload(
                            String key, KernelNodeState oldValue) {
                        // LoadingCache.reload() is only used to re-calculate the
                        // memory usage on KernelNodeState.init(). Therefore
                        // we simply return the old value as is (OAK-643)
                        SettableFuture<KernelNodeState> future = SettableFuture.create();
                        future.set(oldValue);
                        return future;
                    }
                });

        cacheStats = new CacheStats(cache, "NodeStore", weigher, cacheSize);

        try {
            this.root = cache.get(kernel.getHeadRevision() + '/');
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public KernelNodeStore(MicroKernel kernel) {
        this(kernel, DEFAULT_CACHE_SIZE);
    }

    @Nonnull
    public Observer getObserver() {
        return observer;
    }

    public void setObserver(@Nonnull Observer observer) {
        this.observer = checkNotNull(observer);
    }

    //----------------------------------------------------------< NodeStore >---

    @Override
    public synchronized KernelNodeState getRoot() {
        String revision = kernel.getHeadRevision();
        if (!revision.equals(root.getRevision())) {
            NodeState before = root;
            root = getRootState(revision);
            observer.contentChanged(before, root);
        }
        return root;
    }

    @Override
    public NodeStoreBranch branch() {
        return new KernelNodeStoreBranch(this, getRoot(), mergeLock);
    }

    /**
     * @return An instance of {@link KernelBlob}
     */
    @Override
    public KernelBlob createBlob(InputStream inputStream) throws IOException {
        try {
            String blobId = kernel.write(inputStream);
            return new KernelBlob(blobId, kernel);
        } catch (MicroKernelException e) {
            throw new IOException(e);
        }
    }

    @Override @Nonnull
    public String checkpoint(long lifetime) {
        checkArgument(lifetime > 0);
        return kernel.checkpoint(lifetime);
    }

    @Override @CheckForNull
    public NodeState retrieve(@Nonnull String checkpoint) {
        try {
            return getRootState(checkNotNull(checkpoint));
        } catch (MicroKernelException e) {
            // TODO: caused by the checkpoint no longer being available?
            return null;
        }
    }

    public CacheStats getCacheStats(){
        return cacheStats;
    }

    //-----------------------------------------------------------< internal >---

    @Nonnull
    MicroKernel getKernel() {
        return kernel;
    }

    KernelNodeState getRootState(String revision) {
        try {
            return cache.get(revision + "/");
        } catch (ExecutionException e) {
            throw new MicroKernelException(e);
        }
    }

    NodeState commit(String jsop, String baseRevision) {
        return getRootState(kernel.commit("", jsop, baseRevision, null));
    }

    NodeState merge(String headRevision) {
        return getRootState(kernel.merge(headRevision, null));
    }

}
