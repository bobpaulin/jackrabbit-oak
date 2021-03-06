/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.jcr.version;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.jcr.AccessDeniedException;
import javax.jcr.NodeIterator;
import javax.jcr.ReferentialIntegrityException;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.version.LabelExistsVersionException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionException;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionIterator;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;

import org.apache.jackrabbit.commons.iterator.FrozenNodeIteratorAdapter;
import org.apache.jackrabbit.commons.iterator.VersionIteratorAdapter;
import org.apache.jackrabbit.oak.jcr.NodeImpl;
import org.apache.jackrabbit.oak.jcr.SessionContext;
import org.apache.jackrabbit.oak.jcr.delegate.VersionDelegate;
import org.apache.jackrabbit.oak.jcr.delegate.VersionHistoryDelegate;
import org.apache.jackrabbit.oak.jcr.operation.SessionOperation;
import org.apache.jackrabbit.oak.util.TODO;

/**
 * {@code VersionHistoryImpl}...
 */
public class VersionHistoryImpl extends NodeImpl<VersionHistoryDelegate>
        implements VersionHistory {

    public VersionHistoryImpl(VersionHistoryDelegate dlg, SessionContext sessionContext) {
        super(dlg, sessionContext);
    }

    @Override
    public String getVersionableUUID() throws RepositoryException {
        return getVersionableIdentifier();
    }

    @Override
    public String getVersionableIdentifier() throws RepositoryException {
        return perform(new SessionOperation<String>() {
            @Override
            public String perform() throws RepositoryException {
                return dlg.getVersionableIdentifier();
            }
        });
    }

    @Override
    public Version getRootVersion() throws RepositoryException {
        return perform(new SessionOperation<Version>() {
            @Override
            public Version perform() throws RepositoryException {
                return new VersionImpl(dlg.getRootVersion(), sessionContext);
            }
        });
    }

    @Override
    public VersionIterator getAllLinearVersions() throws RepositoryException {
        return perform(new SessionOperation<VersionIterator>() {
            @Override
            public VersionIterator perform() throws RepositoryException {
                return new VersionIteratorAdapter(Iterators.transform(
                        dlg.getAllLinearVersions(), new Function<VersionDelegate, Version>() {
                    @Override
                    public Version apply(VersionDelegate input) {
                        return new VersionImpl(input, sessionContext);
                    }
                }));
            }
        });
    }

    @Override
    public VersionIterator getAllVersions() throws RepositoryException {
        return perform(new SessionOperation<VersionIterator>() {
            @Override
            public VersionIterator perform() throws RepositoryException {
                return new VersionIteratorAdapter(Iterators.transform(
                        dlg.getAllVersions(), new Function<VersionDelegate, Version>() {
                    @Override
                    public Version apply(VersionDelegate input) {
                        return new VersionImpl(input, sessionContext);
                    }
                }));
            }
        });
    }

    @Override
    public NodeIterator getAllLinearFrozenNodes() throws RepositoryException {
        return new FrozenNodeIteratorAdapter(getAllLinearVersions());
    }

    @Override
    public NodeIterator getAllFrozenNodes() throws RepositoryException {
        return new FrozenNodeIteratorAdapter(getAllVersions());
    }

    @Override
    public Version getVersion(final String versionName)
            throws VersionException, RepositoryException {
        return perform(new SessionOperation<Version>() {
            @Override
            public Version perform() throws RepositoryException {
                return new VersionImpl(dlg.getVersion(versionName), sessionContext);
            }
        });
    }

    @Override
    public Version getVersionByLabel(final String label)
            throws VersionException, RepositoryException {
        return perform(new SessionOperation<Version>() {
            @Override
            public Version perform() throws RepositoryException {
                String oakLabel = sessionContext.getOakName(label);
                return new VersionImpl(dlg.getVersionByLabel(oakLabel), sessionContext);
            }
        });
    }

    @Override
    public void addVersionLabel(final String versionName,
                                final String label,
                                final boolean moveLabel)
            throws LabelExistsVersionException, VersionException,
            RepositoryException {
        perform(new SessionOperation<Void>(true) {
            @Override
            public Void perform() throws RepositoryException {
                String oakLabel = sessionContext.getOakName(label);
                // will throw VersionException if version does not exist
                VersionDelegate version = dlg.getVersion(versionName);
                dlg.addVersionLabel(version, oakLabel, moveLabel);
                return null;
            }
        });
    }

    @Override
    public void removeVersionLabel(final String label)
            throws VersionException, RepositoryException {
        perform(new SessionOperation<Void>(true) {
            @Override
            public Void perform() throws RepositoryException {
                String oakLabel = sessionContext.getOakName(label);
                dlg.removeVersionLabel(oakLabel);
                return null;
            }
        });
    }

    @Override
    public boolean hasVersionLabel(String label) throws RepositoryException {
        return Arrays.asList(getVersionLabels()).contains(label);
    }

    @Override
    public boolean hasVersionLabel(Version version, String label)
            throws VersionException, RepositoryException {
        return Arrays.asList(getVersionLabels(version)).contains(label);
    }

    @Override
    public String[] getVersionLabels() throws RepositoryException {
        return perform(new SessionOperation<String[]>() {
            @Override
            public String[] perform() throws RepositoryException {
                List<String> labels = new ArrayList<String>();
                for (String label : dlg.getVersionLabels()) {
                    labels.add(sessionContext.getJcrName(label));
                }
                return labels.toArray(new String[labels.size()]);
            }
        });
    }

    @Override
    public String[] getVersionLabels(final Version version)
            throws VersionException, RepositoryException {
        if (!version.getContainingHistory().getPath().equals(getPath())) {
            throw new VersionException("Version is not contained in this " +
                    "VersionHistory");
        }
        return perform(new SessionOperation<String[]>() {
            @Override
            public String[] perform() throws RepositoryException {
                List<String> labels = new ArrayList<String>();
                for (String label : dlg.getVersionLabels(version.getIdentifier())) {
                    labels.add(sessionContext.getJcrName(label));
                }
                return labels.toArray(new String[labels.size()]);
            }
        });
    }

    @Override
    public void removeVersion(String versionName)
            throws ReferentialIntegrityException, AccessDeniedException,
            UnsupportedRepositoryOperationException, VersionException,
            RepositoryException {
        TODO.unimplemented().doNothing();
    }
}
