/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * http://oss.oracle.com/licenses/upl.
 */
package com.oracle.coherence.concurrent;

import com.oracle.bedrock.junit.CoherenceClusterExtension;

import com.oracle.bedrock.runtime.LocalPlatform;

import com.oracle.bedrock.runtime.coherence.options.ClusterPort;
import com.oracle.bedrock.runtime.coherence.options.LocalHost;
import com.oracle.bedrock.runtime.coherence.options.LocalStorage;
import com.oracle.bedrock.runtime.coherence.options.Logging;
import com.oracle.bedrock.runtime.coherence.options.Multicast;
import com.oracle.bedrock.runtime.coherence.options.Pof;
import com.oracle.bedrock.runtime.coherence.options.RoleName;

import com.oracle.bedrock.runtime.java.options.ClassName;
import com.oracle.bedrock.runtime.java.options.IPv4Preferred;

import com.oracle.bedrock.runtime.options.DisplayName;

import com.tangosol.net.Coherence;

import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test distributed countdown latch across multiple cluster members using
 * the Pof serializer.
 *
 * @author lh  2021.12.01
 *
 * @since 21.12
 */
public class PofClusteredRemoteCountDownLatchIT
        extends AbstractClusteredRemoteCountDownLatchIT
    {
    // ----- constructors ---------------------------------------------------

    /**
     * Default constructor.
     */
    public PofClusteredRemoteCountDownLatchIT()
        {
        super(f_coherenceResource);
        }

    /**
     * A Bedrock utility to capture logs of spawned processes into files
     * under target/test-output. This is added as an option to the cluster
     * and client processes.
     */
    static TestLogs logs = new TestLogs(PofClusteredRemoteCountDownLatchIT.class);

    /**
     * A Bedrock JUnit5 extension that starts a Coherence cluster made up of
     * two storage enabled members, two storage disabled members and two
     * storage disabled extend proxy members.
     */
    @RegisterExtension
    static CoherenceClusterExtension f_coherenceResource =
            new CoherenceClusterExtension()
                    .using(LocalPlatform.get())
                    .with(ClassName.of(Coherence.class),
                          Logging.at(9),
                          LocalHost.only(),
                          Multicast.ttl(0),
                          IPv4Preferred.yes(),
                          logs,
                          ClusterPort.automatic(),
                          Pof.enabled(),
                          Pof.config("coherence-concurrent-pof-config.xml"))
                    .include(2,
                             DisplayName.of("storage"),
                             RoleName.of("storage"),
                             LocalStorage.enabled())
                    .include(2,
                             DisplayName.of("application"),
                             RoleName.of("application"),
                             LocalStorage.disabled());
    }
