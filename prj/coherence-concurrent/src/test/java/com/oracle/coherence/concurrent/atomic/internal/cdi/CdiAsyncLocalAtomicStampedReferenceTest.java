/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package com.oracle.coherence.concurrent.atomic.internal.cdi;

import com.oracle.coherence.concurrent.atomic.AsyncAtomicStampedReference;
import com.oracle.coherence.concurrent.atomic.AsyncAtomicStampedReferenceTest;
import com.oracle.coherence.concurrent.atomic.AsyncLocalAtomicStampedReference;

import javax.inject.Inject;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * {@link AsyncLocalAtomicStampedReference} CDI tests.
 * 
 * @author Aleks Seovic  2020.12.09
 */
@ExtendWith(WeldJunit5Extension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CdiAsyncLocalAtomicStampedReferenceTest
        extends AsyncAtomicStampedReferenceTest
    {
    @WeldSetup
    private WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                          .addBeanClass(AsyncAtomicStampedReferenceProducer.class));

    @Inject
    AsyncAtomicStampedReference<String> value;

    protected AsyncAtomicStampedReference<String> asyncValue()
        {
        return value;
        }
    }
