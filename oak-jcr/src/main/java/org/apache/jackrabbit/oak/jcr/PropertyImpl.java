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
package org.apache.jackrabbit.oak.jcr;

import static com.google.common.collect.Lists.newArrayListWithCapacity;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.List;

import javax.annotation.Nonnull;
import javax.jcr.AccessDeniedException;
import javax.jcr.Binary;
import javax.jcr.ItemNotFoundException;
import javax.jcr.ItemVisitor;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.PropertyDefinition;

import org.apache.jackrabbit.oak.api.Tree.Status;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.jcr.delegate.NodeDelegate;
import org.apache.jackrabbit.oak.jcr.delegate.PropertyDelegate;
import org.apache.jackrabbit.oak.jcr.operation.PropertyOperation;
import org.apache.jackrabbit.oak.plugins.value.ValueFactoryImpl;
import org.apache.jackrabbit.value.ValueHelper;

/**
 * TODO document
 */
public class PropertyImpl extends ItemImpl<PropertyDelegate> implements Property {

    private static final Value[] NO_VALUES = new Value[0];

    PropertyImpl(PropertyDelegate dlg, SessionContext sessionContext) {
        super(dlg, sessionContext);
    }

    //---------------------------------------------------------------< Item >---

    @Override
    public boolean isNode() {
        return false;
    }

    @Override
    @Nonnull
    public Node getParent() throws RepositoryException {
        return perform(new PropertyOperation<Node>(dlg) {
            @Override
            public Node perform() throws RepositoryException {
                NodeDelegate parent = property.getParent();
                if (parent == null) {
                    throw new AccessDeniedException();
                } else {
                    return NodeImpl.createNode(parent, sessionContext);
                }
            }
        });
    }

    @Override
    public boolean isNew() {
        return safePerform(new PropertyOperation<Boolean>(dlg) {
            @Override
            public Boolean perform() {
                return property.getStatus() == Status.NEW;
            }
        });
    }

    @Override
    public boolean isModified() {
        return safePerform(new PropertyOperation<Boolean>(dlg) {
            @Override
            public Boolean perform() {
                return property.getStatus() == Status.MODIFIED;
            }
        });
    }

    @Override
    public void remove() throws RepositoryException {
        perform(new ItemWriteOperation<Void>() {
            @Override
            public Void perform() throws RepositoryException {
                dlg.remove();
                return null;
            }
        });
    }

    @Override
    public void accept(ItemVisitor visitor) throws RepositoryException {
        visitor.visit(this);
    }

    //-----------------------------------------------------------< Property >---

    @Override
    public void setValue(Value value) throws RepositoryException {
        if (value == null) {
            remove();
        } else {
            internalSetValue(value);
        }
    }

    @Override
    public void setValue(final Value[] values) throws RepositoryException {
        if (values == null) {
            remove();
        } else {
            internalSetValue(values);
        }
    }

    @Override
    public void setValue(String value) throws RepositoryException {
        if (value == null) {
            remove();
        } else {
            setValue(getValueFactory().createValue(value));
        }
    }

    @Override
    public void setValue(String[] strings) throws RepositoryException {
        if (strings == null) {
            remove();
        } else {
            ValueFactory factory = getValueFactory();
            Value[] values = new Value[strings.length];
            for (int i = 0; i < strings.length; i++) {
                if (strings[i] != null) {
                    values[i] = factory.createValue(strings[i]);
                }
            }
            internalSetValue(values);
        }
    }

    @Override @SuppressWarnings("deprecation")
    public void setValue(InputStream value) throws RepositoryException {
        if (value == null) {
            remove();
        } else {
            setValue(getValueFactory().createValue(value));
        }
    }

    @Override
    public void setValue(Binary value) throws RepositoryException {
        if (value == null) {
            remove();
        } else {
            setValue(getValueFactory().createValue(value));
        }
    }

    @Override
    public void setValue(long value) throws RepositoryException {
        setValue(getValueFactory().createValue(value));
    }

    @Override
    public void setValue(double value) throws RepositoryException {
        setValue(getValueFactory().createValue(value));
    }

    @Override
    public void setValue(BigDecimal value) throws RepositoryException {
        if (value == null) {
            remove();
        } else {
            setValue(getValueFactory().createValue(value));
        }
    }

    @Override
    public void setValue(Calendar value) throws RepositoryException {
        if (value == null) {
            remove();
        } else {
            setValue(getValueFactory().createValue(value));
        }
    }

    @Override
    public void setValue(boolean value) throws RepositoryException {
        setValue(getValueFactory().createValue(value));
    }

    @Override
    public void setValue(Node value) throws RepositoryException {
        if (value == null) {
            remove();
        } else {
            setValue(getValueFactory().createValue(value));
        }
    }

    @Override
    @Nonnull
    public Value getValue() throws RepositoryException {
        return perform(new PropertyOperation<Value>(dlg) {
            @Override
            public Value perform() throws RepositoryException {
                return ValueFactoryImpl.createValue(
                        property.getSingleState(), sessionContext);
            }
        });
    }

    @Override
    @Nonnull
    public Value[] getValues() throws RepositoryException {
        return perform(new PropertyOperation<List<Value>>(dlg) {
            @Override
            public List<Value> perform() throws RepositoryException {
                return ValueFactoryImpl.createValues(
                        property.getMultiState(), sessionContext);
            }
        }).toArray(NO_VALUES);
    }

    @Override
    @Nonnull
    public String getString() throws RepositoryException {
        return getValue().getString();
    }

    @SuppressWarnings("deprecation")
    @Override
    @Nonnull
    public InputStream getStream() throws RepositoryException {
        return getValue().getStream();
    }

    @Override
    @Nonnull
    public Binary getBinary() throws RepositoryException {
        return getValue().getBinary();
    }

    @Override
    public long getLong() throws RepositoryException {
        return getValue().getLong();
    }

    @Override
    public double getDouble() throws RepositoryException {
        return getValue().getDouble();
    }

    @Override
    @Nonnull
    public BigDecimal getDecimal() throws RepositoryException {
        return getValue().getDecimal();
    }

    @Override
    @Nonnull
    public Calendar getDate() throws RepositoryException {
        return getValue().getDate();
    }

    @Override
    public boolean getBoolean() throws RepositoryException {
        return getValue().getBoolean();
    }

    @Override
    @Nonnull
    public Node getNode() throws RepositoryException {
        return perform(new PropertyOperation<Node>(dlg) {
            @Override
            public Node perform() throws RepositoryException {
                // TODO: avoid nested calls
                Value value = getValue();
                switch (value.getType()) {
                    case PropertyType.REFERENCE:
                    case PropertyType.WEAKREFERENCE:
                        return getSession().getNodeByIdentifier(value.getString());

                    case PropertyType.PATH:
                    case PropertyType.NAME:
                        String path = value.getString();
                        if (path.startsWith("[") && path.endsWith("]")) {
                            // identifier path
                            String identifier = path.substring(1, path.length() - 1);
                            return getSession().getNodeByIdentifier(identifier);
                        }
                        else {
                            try {
                                return (path.charAt(0) == '/') ? getSession().getNode(path) : getParent().getNode(path);
                            } catch (PathNotFoundException e) {
                                throw new ItemNotFoundException(path);
                            }
                        }

                    case PropertyType.STRING:
                        try {
                            Value refValue = ValueHelper.convert(value, PropertyType.REFERENCE, getValueFactory());
                            return getSession().getNodeByIdentifier(refValue.getString());
                        } catch (ItemNotFoundException e) {
                            throw e;
                        } catch (RepositoryException e) {
                            // try if STRING value can be interpreted as PATH value
                            Value pathValue = ValueHelper.convert(value, PropertyType.PATH, getValueFactory());
                            path = pathValue.getString();
                            try {
                                return (path.charAt(0) == '/') ? getSession().getNode(path) : getParent().getNode(path);
                            } catch (PathNotFoundException e1) {
                                throw new ItemNotFoundException(pathValue.getString());
                            }
                        }

                    default:
                        throw new ValueFormatException("Property value cannot be converted to a PATH, REFERENCE or WEAKREFERENCE");
                }
            }
        });
    }

    @Override
    @Nonnull
    public Property getProperty() throws RepositoryException {
        return perform(new PropertyOperation<Property>(dlg) {
            @Override
            public Property perform() throws RepositoryException {
                // TODO: avoid nested calls
                Value value = getValue();
                Value pathValue = ValueHelper.convert(value, PropertyType.PATH, getValueFactory());
                String path = pathValue.getString();
                try {
                    return (path.charAt(0) == '/') ? getSession().getProperty(path) : getParent().getProperty(path);
                } catch (PathNotFoundException e) {
                    throw new ItemNotFoundException(path);
                }
            }
        });
    }

    @Override
    public long getLength() throws RepositoryException {
        return getLength(getValue());
    }

    @Override
    @Nonnull
    public long[] getLengths() throws RepositoryException {
        Value[] values = getValues();
        long[] lengths = new long[values.length];

        for (int i = 0; i < values.length; i++) {
            lengths[i] = getLength(values[i]);
        }
        return lengths;
    }

    @Override
    @Nonnull
    public PropertyDefinition getDefinition() throws RepositoryException {
        return perform(new PropertyOperation<PropertyDefinition>(dlg) {
            @Override
            public PropertyDefinition perform() throws RepositoryException {
                return getNodeTypeManager().getDefinition(
                        property.getParent().getTree(),
                        property.getPropertyState(), true);
            }
        });
    }

    @Override
    public int getType() throws RepositoryException {
        return perform(new PropertyOperation<Integer>(dlg) {
            @Override
            public Integer perform() throws RepositoryException {
                return property.getPropertyState().getType().tag();
            }
        });
    }

    @Override
    public boolean isMultiple() throws RepositoryException {
        return perform(new PropertyOperation<Boolean>(dlg) {
            @Override
            public Boolean perform() throws RepositoryException {
                return property.getPropertyState().isArray();
            }
        });
    }

    //------------------------------------------------------------< internal >---

    /**
     * Return the length of the specified JCR value object.
     *
     * @param value The value.
     * @return The length of the given value.
     * @throws RepositoryException If an error occurs.
     */
    private static long getLength(Value value) throws RepositoryException {
        if (value.getType() == PropertyType.BINARY) {
            return value.getBinary().getSize();
        } else {
            return value.getString().length();
        }
    }

    private void internalSetValue(final @Nonnull Value value)
            throws RepositoryException {
        perform(new ItemWriteOperation<Void>() {
            @Override
            public Void perform() throws RepositoryException {
                Type<?> type = dlg.getPropertyState().getType();
                if (type.isArray()) {
                    throw new ValueFormatException(
                            "This is a multi-valued property");
                }

                Value converted = ValueHelper.convert(
                        value, type.tag(), getValueFactory());
                dlg.setState(createSingleState(dlg.getName(), converted, type));
                return null;
            }
        });
    }

    private void internalSetValue(final @Nonnull Value[] values)
            throws RepositoryException {
        perform(new ItemWriteOperation<Void>() {
            @Override
            public Void perform() throws RepositoryException {
                Type<?> type = dlg.getPropertyState().getType();
                if (!type.isArray()) {
                    throw new ValueFormatException(
                            "This is a single-valued property");
                }

                List<Value> converted = newArrayListWithCapacity(values.length);
                ValueFactory factory = getValueFactory();
                for (int i = 0; i < values.length; i++) {
                    if (values[i] != null) {
                        converted.add(ValueHelper.convert(
                                values[i], type.tag(), factory));
                    }
                }
                dlg.setState(createMultiState(dlg.getName(), converted, type));
                return null;
            }
        });
    }

}
