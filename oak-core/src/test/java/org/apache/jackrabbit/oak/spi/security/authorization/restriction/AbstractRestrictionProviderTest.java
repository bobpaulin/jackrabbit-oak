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
package org.apache.jackrabbit.oak.spi.security.authorization.restriction;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.PropertyType;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.security.AccessControlException;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.AbstractSecurityTest;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.api.Tree;
import org.apache.jackrabbit.oak.api.Type;
import org.apache.jackrabbit.oak.plugins.memory.PropertyStates;
import org.apache.jackrabbit.oak.plugins.value.ValueFactoryImpl;
import org.apache.jackrabbit.oak.spi.security.authorization.accesscontrol.AccessControlConstants;
import org.apache.jackrabbit.oak.util.NodeUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractRestrictionProviderTest extends AbstractSecurityTest implements AccessControlConstants {

    private String unsupportedPath = null;
    private String testPath = "/testRoot";

    private Value globValue;
    private Value[] nameValues;
    private Value nameValue;

    private ValueFactory valueFactory;
    private AbstractRestrictionProvider restrictionProvider;

    @Before
    @Override
    public void before() throws Exception {
        super.before();

        valueFactory = new ValueFactoryImpl(root.getBlobFactory(), namePathMapper);
        globValue = valueFactory.createValue("*");
        nameValue = valueFactory.createValue("nt:file", PropertyType.NAME);
        nameValues = new Value[] {
                valueFactory.createValue("nt:folder", PropertyType.NAME),
                valueFactory.createValue("nt:file", PropertyType.NAME)
        };

        restrictionProvider = new TestProvider();
    }

    @After
    @Override
    public void after() throws Exception {
        try {
            root.refresh();
        } finally {
            super.after();
        }
    }

    private Tree getAceTree(Restriction... restrictions) throws Exception {
        NodeUtil rootNode = new NodeUtil(root.getTree("/"));
        NodeUtil tmp = rootNode.addChild("testRoot", JcrConstants.NT_UNSTRUCTURED);
        Tree ace = tmp.addChild("rep:policy", NT_REP_ACL).addChild("ace0", NT_REP_GRANT_ACE).getTree();
        restrictionProvider.writeRestrictions(tmp.getTree().getPath(), ace, ImmutableSet.copyOf(restrictions));
        return ace;
    }


    @Test
    public void testGetSupportedRestrictions() throws Exception {
        Set<RestrictionDefinition> defs = restrictionProvider.getSupportedRestrictions(testPath);
        assertNotNull(defs);
        assertEquals(TestProvider.supportedRestrictions().size(), defs.size());
        for (RestrictionDefinition def : TestProvider.supportedRestrictions().values()) {
            assertTrue(defs.contains(def));
        }
    }

    @Test
    public void testGetSupportedRestrictionsForUnsupportedPath() throws Exception {
        Set<RestrictionDefinition> defs = restrictionProvider.getSupportedRestrictions(unsupportedPath);
        assertNotNull(defs);
        assertTrue(defs.isEmpty());
    }

    @Test
    public void testCreateForUnsupportedPath() throws Exception {
        try {
            restrictionProvider.createRestriction(unsupportedPath, REP_GLOB, globValue);
            fail();
        } catch (AccessControlException e) {
            // success
        }

        try {
            restrictionProvider.createRestriction(unsupportedPath, REP_NT_NAMES, nameValues);
            fail();
        } catch (AccessControlException e) {
            // success
        }
    }

    @Test
    public void testCreateForUnsupportedName() throws Exception {
        try {
            restrictionProvider.createRestriction(unsupportedPath, "unsupported", globValue);
            fail();
        } catch (AccessControlException e) {
            // success
        }

        try {
            restrictionProvider.createRestriction(unsupportedPath, "unsupported", nameValues);
            fail();
        } catch (AccessControlException e) {
            // success
        }
    }

    @Test
    public void testCreateForUnsupportedType() throws Exception {
        try {
            restrictionProvider.createRestriction(unsupportedPath, REP_GLOB, valueFactory.createValue(true));
            fail();
        } catch (AccessControlException e) {
            // success
        }
        try {
            restrictionProvider.createRestriction(unsupportedPath, REP_NT_NAMES,
                    valueFactory.createValue("nt:file", PropertyType.NAME),
                    valueFactory.createValue(true));
            fail();
        } catch (AccessControlException e) {
            // success
        }
    }

    @Test
    public void testCreateForUnsupportedMultiValues() throws Exception {
        try {
            restrictionProvider.createRestriction(unsupportedPath, REP_GLOB,
                    valueFactory.createValue("*"),
                    valueFactory.createValue("/a/*"));
            fail();
        } catch (AccessControlException e) {
            // success
        }
    }

    @Test
    public void testCreateRestriction() throws Exception {
        Restriction r = restrictionProvider.createRestriction(testPath, REP_GLOB, globValue);
        assertNotNull(r);
        assertEquals(REP_GLOB, r.getName());
        assertEquals(globValue.getString(), r.getProperty().getValue(Type.STRING));
    }

    @Test
    public void testCreateMvRestriction() throws Exception {
        Restriction r = restrictionProvider.createRestriction(testPath, REP_NT_NAMES,
                valueFactory.createValue("nt:folder", PropertyType.NAME),
                valueFactory.createValue("nt:file", PropertyType.NAME));
        assertNotNull(r);
        assertEquals(REP_NT_NAMES, r.getName());
        assertEquals(Type.NAMES, r.getRequiredType());

        PropertyState ps = r.getProperty();
        assertTrue(ps.isArray());
        assertEquals(Type.NAMES, ps.getType());

        List<Value> vs = ValueFactoryImpl.createValues(ps, namePathMapper);
        assertArrayEquals(nameValues, vs.toArray(new Value[vs.size()]));
    }

    @Test
    public void testCreateMvRestriction2() throws Exception {
        Restriction r = restrictionProvider.createRestriction(testPath, REP_NT_NAMES, nameValues);
        assertNotNull(r);
        assertEquals(REP_NT_NAMES, r.getName());
        assertEquals(Type.NAMES, r.getRequiredType());

        PropertyState ps = r.getProperty();
        assertTrue(ps.isArray());
        assertEquals(Type.NAMES, ps.getType());

        List<Value> vs = ValueFactoryImpl.createValues(ps, namePathMapper);
        assertArrayEquals(nameValues, vs.toArray(new Value[vs.size()]));
    }

    @Test
    public void testCreateMvRestriction3() throws Exception {
        Restriction r = restrictionProvider.createRestriction(testPath, REP_NT_NAMES, nameValue);
        assertNotNull(r);
        assertEquals(REP_NT_NAMES, r.getName());
        assertEquals(Type.NAMES, r.getRequiredType());

        assertTrue(r.getProperty().isArray());
        assertEquals(Type.NAMES, r.getProperty().getType());

        List<Value> vs = ValueFactoryImpl.createValues(r.getProperty(), namePathMapper);
        assertArrayEquals(new Value[] {nameValue}, vs.toArray(new Value[vs.size()]));
    }

    @Test
    public void testCreateEmptyMvRestriction() throws Exception {
        Restriction r = restrictionProvider.createRestriction(testPath, REP_NT_NAMES);
        assertNotNull(r);
        assertEquals(REP_NT_NAMES, r.getName());
        assertEquals(Type.NAMES, r.getRequiredType());

        assertTrue(r.getProperty().isArray());
        assertEquals(Type.NAMES, r.getProperty().getType());

        List<Value> vs = ValueFactoryImpl.createValues(r.getProperty(), namePathMapper);
        assertNotNull(vs);
        assertEquals(0, vs.size());
    }

    @Test
    public void testCreateEmptyMvRestriction2() throws Exception {
        Restriction r = restrictionProvider.createRestriction(testPath, REP_NT_NAMES, new Value[0]);
        assertNotNull(r);
        assertEquals(REP_NT_NAMES, r.getName());
        assertEquals(Type.NAMES, r.getRequiredType());

        assertTrue(r.getProperty().isArray());
        assertEquals(Type.NAMES, r.getProperty().getType());

        List<Value> vs = ValueFactoryImpl.createValues(r.getProperty(), namePathMapper);
        assertNotNull(vs);
        assertEquals(0, vs.size());
    }

    @Test
    public void testReadRestrictionsForUnsupportedPath() throws Exception {
        Set<Restriction> restrictions = restrictionProvider.readRestrictions(unsupportedPath, getAceTree());
        assertTrue(restrictions.isEmpty());
    }

    @Test
    public void testReadRestrictions() throws Exception {
        Restriction r = restrictionProvider.createRestriction(testPath, REP_GLOB, globValue);
        Tree aceTree = getAceTree(r);

        Set<Restriction> restrictions = restrictionProvider.readRestrictions(testPath, aceTree);
        assertEquals(1, restrictions.size());
        assertTrue(restrictions.contains(r));
    }

    @Test
    public void testWriteRestrictions() throws Exception {
        Restriction r = restrictionProvider.createRestriction(testPath, REP_GLOB, globValue);
        Tree aceTree = getAceTree();

        restrictionProvider.writeRestrictions(testPath, aceTree, ImmutableSet.<Restriction>of(r));

        assertTrue(aceTree.hasChild(REP_RESTRICTIONS));
        Tree restr = aceTree.getChild(REP_RESTRICTIONS);
        assertEquals(r.getProperty(), restr.getProperty(REP_GLOB));
    }

    @Test
    public void testWriteInvalidRestrictions() throws Exception {
        PropertyState ps = PropertyStates.createProperty(REP_GLOB, valueFactory.createValue(false));
        Tree aceTree = getAceTree();

        restrictionProvider.writeRestrictions(testPath, aceTree, ImmutableSet.<Restriction>of(new RestrictionImpl(ps, false)));

        assertTrue(aceTree.hasChild(REP_RESTRICTIONS));
        Tree restr = aceTree.getChild(REP_RESTRICTIONS);
        assertEquals(ps, restr.getProperty(REP_GLOB));
    }

    @Test
    public void testValidateRestrictionsUnsupportedPath() throws Exception {
        // empty restrictions => must succeed
        restrictionProvider.validateRestrictions(null, getAceTree());


        // non-empty restrictions => must fail
        try {
            Restriction restr = restrictionProvider.createRestriction(testPath, REP_GLOB, globValue);
            restrictionProvider.validateRestrictions(null, getAceTree(restr));
            fail();
        } catch (AccessControlException e) {
            // success
        }
    }

    @Test
    public void testValidateRestrictionsWrongType() throws Exception {
        Restriction mand = restrictionProvider.createRestriction(testPath, "mandatory", valueFactory.createValue(true));
        try {
            Tree ace = getAceTree(mand);
            new NodeUtil(ace).getChild(REP_RESTRICTIONS).setBoolean(REP_GLOB, true);

            restrictionProvider.validateRestrictions(testPath, ace);
            fail("wrong type with restriction 'rep:glob");
        } catch (AccessControlException e) {
            // success
        }
    }

    @Test
    public void testValidateRestrictionsUnsupportedRestriction() throws Exception {
        Restriction mand = restrictionProvider.createRestriction(testPath, "mandatory", valueFactory.createValue(true));
        try {
            Tree ace = getAceTree(mand);
            new NodeUtil(ace).getChild(REP_RESTRICTIONS).setString("Unsupported", "value");

            restrictionProvider.validateRestrictions(testPath, ace);
            fail("wrong type with restriction 'rep:glob");
        } catch (AccessControlException e) {
            // success
        }
    }

    @Test
    public void testValidateRestrictionsMissingMandatory() throws Exception {
        Restriction glob = restrictionProvider.createRestriction(testPath, REP_GLOB, globValue);
        try {
            restrictionProvider.validateRestrictions(testPath, getAceTree(glob));
            fail("missing mandatory restriction");
        } catch (AccessControlException e) {
            // success
        }
    }

    @Test
    public void testValidateRestrictions() throws Exception {
        Restriction glob = restrictionProvider.createRestriction(testPath, REP_GLOB, globValue);
        Restriction ntNames = restrictionProvider.createRestriction(testPath, REP_NT_NAMES, nameValues);
        Restriction mand = restrictionProvider.createRestriction(testPath, "mandatory", valueFactory.createValue(true));

        restrictionProvider.validateRestrictions(testPath, getAceTree(mand));
        restrictionProvider.validateRestrictions(testPath, getAceTree(mand, glob));
        restrictionProvider.validateRestrictions(testPath, getAceTree(mand, ntNames));
        restrictionProvider.validateRestrictions(testPath, getAceTree(mand, glob, ntNames));
    }

    private static final class TestProvider extends AbstractRestrictionProvider {

        private TestProvider() {
            super(supportedRestrictions());
        }

        private static Map<String, RestrictionDefinition> supportedRestrictions() {
            RestrictionDefinition glob = new RestrictionDefinitionImpl(REP_GLOB, Type.STRING, false);
            RestrictionDefinition nts  = new RestrictionDefinitionImpl(REP_NT_NAMES, Type.NAMES, false);
            RestrictionDefinition mand = new RestrictionDefinitionImpl("mandatory", Type.BOOLEAN, true);
            return ImmutableMap.of(glob.getName(), glob, nts.getName(), nts, mand.getName(), mand);
        }

        @Nonnull
        @Override
        public RestrictionPattern getPattern(@Nullable String oakPath, @Nonnull Tree tree) {
            throw new UnsupportedOperationException();
        }
    }
}