/*
 * Copyright 1999-2004,2006 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.pool;

import junit.framework.TestCase;

import java.util.List;
import java.util.ArrayList;

/**
 * Abstract {@link TestCase} for {@link ObjectPool} implementations.
 * @author Rodney Waldhoff
 * @author Sandy McArthur
 * @version $Revision$ $Date$
 */
public abstract class TestKeyedObjectPool extends TestCase {
    public TestKeyedObjectPool(String testName) {
        super(testName);
    }

    /**
     * Create an <code>KeyedObjectPool</code> with the specified factory.
     * The pool should be in a default configuration and conform to the expected
     * behaviors described in {@link KeyedObjectPool}.
     * Generally speaking there should be no limits on the various object counts.
     */
    protected abstract KeyedObjectPool makeEmptyPool(KeyedPoolableObjectFactory factory);

    protected final String KEY = "key";

    public void testClosedPoolBehavior() throws Exception {
        final KeyedObjectPool pool;
        try {
            pool = makeEmptyPool(new BaseKeyedPoolableObjectFactory() {
                public Object makeObject(final Object key) throws Exception {
                    return new Object();
                }
            });
        } catch(UnsupportedOperationException uoe) {
            return; // test not supported
        }

        Object o1 = pool.borrowObject(KEY);
        Object o2 = pool.borrowObject(KEY);

        pool.close();

        try {
            pool.addObject(KEY);
            fail("A closed pool must throw an IllegalStateException when addObject is called.");
        } catch (IllegalStateException ise) {
            // expected
        }

        try {
            pool.borrowObject(KEY);
            fail("A closed pool must throw an IllegalStateException when borrowObject is called.");
        } catch (IllegalStateException ise) {
            // expected
        }

        // The following should not throw exceptions just because the pool is closed.
        assertEquals("A closed pool shouldn't have any idle objects.", 0, pool.getNumIdle(KEY));
        assertEquals("A closed pool shouldn't have any idle objects.", 0, pool.getNumIdle());
        pool.getNumActive();
        pool.getNumActive(KEY);
        pool.returnObject(KEY, o1);
        assertEquals("returnObject should not add items back into the idle object pool for a closed pool.", 0, pool.getNumIdle(KEY));
        assertEquals("returnObject should not add items back into the idle object pool for a closed pool.", 0, pool.getNumIdle());
        pool.invalidateObject(KEY, o2);
        pool.clear(KEY);
        pool.clear();
        pool.close();
    }

    private final Integer ZERO = new Integer(0);
    private final Integer ONE = new Integer(1);

    public void testKPOFAddObjectUsage() throws Exception {
        final FailingKeyedPoolableObjectFactory factory = new FailingKeyedPoolableObjectFactory();
        final KeyedObjectPool pool;
        try {
            pool = makeEmptyPool(factory);
        } catch(UnsupportedOperationException uoe) {
            return; // test not supported
        }
        final List expectedMethods = new ArrayList();

        // addObject should make a new object, pasivate it and put it in the pool
        pool.addObject(KEY);
        expectedMethods.add(new MethodCall("makeObject", KEY).returned(ZERO));
        expectedMethods.add(new MethodCall("passivateObject", KEY, ZERO));
        assertEquals(expectedMethods, factory.getMethodCalls());

        //// Test exception handling of addObject
        reset(pool, factory, expectedMethods);

        // makeObject Exceptions should be propagated to client code from addObject
        factory.setMakeObjectFail(true);
        try {
            pool.addObject(KEY);
            fail("Expected addObject to propagate makeObject exception.");
        } catch (PrivateException pe) {
            // expected
        }
        expectedMethods.add(new MethodCall("makeObject", KEY));
        assertEquals(expectedMethods, factory.getMethodCalls());

        clear(factory, expectedMethods);

        // passivateObject Exceptions should be propagated to client code from addObject
        factory.setMakeObjectFail(false);
        factory.setPassivateObjectFail(true);
        try {
            pool.addObject(KEY);
            fail("Expected addObject to propagate passivateObject exception.");
        } catch (PrivateException pe) {
            // expected
        }
        expectedMethods.add(new MethodCall("makeObject", KEY).returned(ONE));
        expectedMethods.add(new MethodCall("passivateObject", KEY, ONE));
        assertEquals(expectedMethods, factory.getMethodCalls());
    }

    public void testKPOFBorrowObjectUsages() throws Exception {
        final FailingKeyedPoolableObjectFactory factory = new FailingKeyedPoolableObjectFactory();
        final KeyedObjectPool pool;
        try {
            pool = makeEmptyPool(factory);
        } catch(UnsupportedOperationException uoe) {
            return; // test not supported
        }
        final List expectedMethods = new ArrayList();
        Object obj;

        /// Test correct behavior code paths

        // existing idle object should be activated and validated
        pool.addObject(KEY);
        clear(factory, expectedMethods);
        obj = pool.borrowObject(KEY);
        expectedMethods.add(new MethodCall("activateObject", KEY, ZERO));
        expectedMethods.add(new MethodCall("validateObject", KEY, ZERO).returned(Boolean.TRUE));
        assertEquals(expectedMethods, factory.getMethodCalls());
        pool.returnObject(KEY, obj);

        //// Test exception handling of borrowObject
        reset(pool, factory, expectedMethods);

        // makeObject Exceptions should be propagated to client code from borrowObject
        factory.setMakeObjectFail(true);
        try {
            obj = pool.borrowObject(KEY);
            fail("Expected borrowObject to propagate makeObject exception.");
        } catch (PrivateException pe) {
            // expected
        }
        expectedMethods.add(new MethodCall("makeObject", KEY));
        assertEquals(expectedMethods, factory.getMethodCalls());


        // when activateObject fails in borrowObject, a new object should be borrowed/created
        reset(pool, factory, expectedMethods);
        pool.addObject(KEY);
        clear(factory, expectedMethods);

        factory.setActivateObjectFail(true);
        expectedMethods.add(new MethodCall("activateObject", KEY, obj));
        obj = pool.borrowObject(KEY);
        expectedMethods.add(new MethodCall("makeObject", KEY).returned(ONE));
        TestObjectPool.removeDestroyObjectCall(factory.getMethodCalls()); // The exact timing of destroyObject is flexible here.
        assertEquals(expectedMethods, factory.getMethodCalls());
        pool.returnObject(KEY, obj);

        // when validateObject fails in borrowObject, a new object should be borrowed/created
        reset(pool, factory, expectedMethods);
        pool.addObject(KEY);
        clear(factory, expectedMethods);

        factory.setValidateObjectFail(true);
        expectedMethods.add(new MethodCall("activateObject", KEY, ZERO));
        expectedMethods.add(new MethodCall("validateObject", KEY, ZERO));
        obj = pool.borrowObject(KEY);
        expectedMethods.add(new MethodCall("makeObject", KEY).returned(ONE));
        TestObjectPool.removeDestroyObjectCall(factory.getMethodCalls()); // The exact timing of destroyObject is flexible here.
        assertEquals(expectedMethods, factory.getMethodCalls());
        pool.returnObject(KEY, obj);
    }

    public void testKPOFReturnObjectUsages() throws Exception {
        final FailingKeyedPoolableObjectFactory factory = new FailingKeyedPoolableObjectFactory();
        final KeyedObjectPool pool;
        try {
            pool = makeEmptyPool(factory);
        } catch(UnsupportedOperationException uoe) {
            return; // test not supported
        }
        final List expectedMethods = new ArrayList();
        Object obj;
        int idleCount;

        /// Test correct behavior code paths
        obj = pool.borrowObject(KEY);
        clear(factory, expectedMethods);

        // returned object should be passivated
        pool.returnObject(KEY, obj);
        expectedMethods.add(new MethodCall("passivateObject", KEY, obj));
        assertEquals(expectedMethods, factory.getMethodCalls());

        //// Test exception handling of returnObject
        reset(pool, factory, expectedMethods);

        // passivateObject should swallow exceptions and not add the object to the pool
        idleCount = pool.getNumIdle();
        obj = pool.borrowObject(KEY);
        clear(factory, expectedMethods);
        factory.setPassivateObjectFail(true);
        pool.returnObject(KEY, obj);
        expectedMethods.add(new MethodCall("passivateObject", KEY, obj));
        TestObjectPool.removeDestroyObjectCall(factory.getMethodCalls()); // The exact timing of destroyObject is flexible here.
        assertEquals(expectedMethods, factory.getMethodCalls());
        assertEquals(idleCount, pool.getNumIdle());

        // destroyObject should swallow exceptions too
        reset(pool, factory, expectedMethods);
        obj = pool.borrowObject(KEY);
        clear(factory, expectedMethods);
        factory.setPassivateObjectFail(true);
        factory.setDestroyObjectFail(true);
        pool.returnObject(KEY, obj);
    }

    public void testKPOFInvalidateObjectUsages() throws Exception {
        final FailingKeyedPoolableObjectFactory factory = new FailingKeyedPoolableObjectFactory();
        final KeyedObjectPool pool;
        try {
            pool = makeEmptyPool(factory);
        } catch(UnsupportedOperationException uoe) {
            return; // test not supported
        }
        final List expectedMethods = new ArrayList();
        Object obj;

        /// Test correct behavior code paths

        obj = pool.borrowObject(KEY);
        clear(factory, expectedMethods);

        // invalidated object should be destroyed
        pool.invalidateObject(KEY, obj);
        expectedMethods.add(new MethodCall("destroyObject", KEY, obj));
        assertEquals(expectedMethods, factory.getMethodCalls());

        //// Test exception handling of invalidateObject
        reset(pool, factory, expectedMethods);
        obj = pool.borrowObject(KEY);
        clear(factory, expectedMethods);
        factory.setDestroyObjectFail(true);
        pool.invalidateObject(KEY, obj);
        Thread.sleep(250); // could be defered
        TestObjectPool.removeDestroyObjectCall(factory.getMethodCalls());
        assertEquals(expectedMethods, factory.getMethodCalls());
    }

    public void testKPOFClearUsages() throws Exception {
        final FailingKeyedPoolableObjectFactory factory = new FailingKeyedPoolableObjectFactory();
        final KeyedObjectPool pool;
        try {
            pool = makeEmptyPool(factory);
        } catch(UnsupportedOperationException uoe) {
            return; // test not supported
        }
        final List expectedMethods = new ArrayList();

        /// Test correct behavior code paths
        PoolUtils.prefill(pool, KEY, 5);
        pool.clear();

        //// Test exception handling clear should swallow destory object failures
        reset(pool, factory, expectedMethods);
        factory.setDestroyObjectFail(true);
        PoolUtils.prefill(pool, KEY, 5);
        pool.clear();
    }

    public void testKPOFCloseUsages() throws Exception {
        final FailingKeyedPoolableObjectFactory factory = new FailingKeyedPoolableObjectFactory();
        KeyedObjectPool pool;
        try {
            pool = makeEmptyPool(factory);
        } catch(UnsupportedOperationException uoe) {
            return; // test not supported
        }
        final List expectedMethods = new ArrayList();

        /// Test correct behavior code paths
        PoolUtils.prefill(pool, KEY, 5);
        pool.close();


        //// Test exception handling close should swallow failures
        pool = makeEmptyPool(factory);
        reset(pool, factory, expectedMethods);
        factory.setDestroyObjectFail(true);
        PoolUtils.prefill(pool, KEY, 5);
        pool.close();
    }

    public void testToString() throws Exception {
        final FailingKeyedPoolableObjectFactory factory = new FailingKeyedPoolableObjectFactory();
        try {
            makeEmptyPool(factory).toString();
        } catch(UnsupportedOperationException uoe) {
            return; // test not supported
        }
    }

    private void reset(final KeyedObjectPool pool, final FailingKeyedPoolableObjectFactory factory, final List expectedMethods) throws Exception {
        pool.clear();
        clear(factory, expectedMethods);
        factory.reset();
    }

    private void clear(final FailingKeyedPoolableObjectFactory factory, final List expectedMethods) {
        factory.getMethodCalls().clear();
        expectedMethods.clear();
    }

    protected static class FailingKeyedPoolableObjectFactory implements KeyedPoolableObjectFactory {
        private final List methodCalls = new ArrayList();
        private int count = 0;
        private boolean makeObjectFail;
        private boolean activateObjectFail;
        private boolean validateObjectFail;
        private boolean passivateObjectFail;
        private boolean destroyObjectFail;

        public FailingKeyedPoolableObjectFactory() {
        }

        public void reset() {
            count = 0;
            getMethodCalls().clear();
            setMakeObjectFail(false);
            setActivateObjectFail(false);
            setValidateObjectFail(false);
            setPassivateObjectFail(false);
            setDestroyObjectFail(false);
        }

        public List getMethodCalls() {
            return methodCalls;
        }

        public int getCurrentCount() {
            return count;
        }

        public void setCurrentCount(final int count) {
            this.count = count;
        }

        public boolean isMakeObjectFail() {
            return makeObjectFail;
        }

        public void setMakeObjectFail(boolean makeObjectFail) {
            this.makeObjectFail = makeObjectFail;
        }

        public boolean isDestroyObjectFail() {
            return destroyObjectFail;
        }

        public void setDestroyObjectFail(boolean destroyObjectFail) {
            this.destroyObjectFail = destroyObjectFail;
        }

        public boolean isValidateObjectFail() {
            return validateObjectFail;
        }

        public void setValidateObjectFail(boolean validateObjectFail) {
            this.validateObjectFail = validateObjectFail;
        }

        public boolean isActivateObjectFail() {
            return activateObjectFail;
        }

        public void setActivateObjectFail(boolean activateObjectFail) {
            this.activateObjectFail = activateObjectFail;
        }

        public boolean isPassivateObjectFail() {
            return passivateObjectFail;
        }

        public void setPassivateObjectFail(boolean passivateObjectFail) {
            this.passivateObjectFail = passivateObjectFail;
        }

        public Object makeObject(final Object key) throws Exception {
            final MethodCall call = new MethodCall("makeObject", key);
            methodCalls.add(call);
            int count = this.count++;
            if (makeObjectFail) {
                throw new PrivateException("makeObject");
            }
            final Integer obj = new Integer(count);
            call.setReturned(obj);
            return obj;
        }

        public void activateObject(final Object key, final Object obj) throws Exception {
            methodCalls.add(new MethodCall("activateObject", key, obj));
            if (activateObjectFail) {
                throw new PrivateException("activateObject");
            }
        }

        public boolean validateObject(final Object key, final Object obj) {
            final MethodCall call = new MethodCall("validateObject", key, obj);
            methodCalls.add(call);
            if (validateObjectFail) {
                throw new PrivateException("validateObject");
            }
            final boolean r = true;
            call.returned(Boolean.valueOf(r));
            return r;
        }

        public void passivateObject(final Object key, final Object obj) throws Exception {
            methodCalls.add(new MethodCall("passivateObject", key, obj));
            if (passivateObjectFail) {
                throw new PrivateException("passivateObject");
            }
        }

        public void destroyObject(final Object key, final Object obj) throws Exception {
            methodCalls.add(new MethodCall("destroyObject", key, obj));
            if (destroyObjectFail) {
                throw new PrivateException("destroyObject");
            }
        }
    }
}