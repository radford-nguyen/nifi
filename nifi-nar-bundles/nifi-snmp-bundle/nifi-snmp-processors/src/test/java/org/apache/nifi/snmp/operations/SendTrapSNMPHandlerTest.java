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
package org.apache.nifi.snmp.operations;

import org.apache.nifi.snmp.configuration.V1TrapConfiguration;
import org.apache.nifi.snmp.configuration.V2TrapConfiguration;
import org.apache.nifi.snmp.exception.SNMPException;
import org.apache.nifi.snmp.factory.trap.V1TrapPDUFactory;
import org.apache.nifi.snmp.factory.trap.V2TrapPDUFactory;
import org.apache.nifi.util.MockComponentLog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.TransportIpAddress;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SendTrapSNMPHandlerTest {

    private Target mockTarget;
    private Snmp mockSnmpManager;
    private PDU mockPdu;
    private ResponseEvent mockResponseEvent;
    private MockComponentLog mockComponentLog;
    private V1TrapConfiguration mockV1TrapConfiguration;
    private V2TrapConfiguration mockV2TrapConfiguration;
    private SNMPResourceHandler snmpResourceHandler;
    private SendTrapSNMPHandler sendTrapSNMPHandler;

    @Before
    public void init() throws IOException {
        mockTarget = mock(Target.class);
        mockSnmpManager = mock(Snmp.class);
        mockPdu = mock(PDU.class);
        mockResponseEvent = mock(ResponseEvent.class);
        mockComponentLog = new MockComponentLog("id", new Object());
        mockV1TrapConfiguration = mock(V1TrapConfiguration.class);
        mockV2TrapConfiguration = mock(V2TrapConfiguration.class);
        V1TrapPDUFactory mockV1TrapPDUFactory = mock(V1TrapPDUFactory.class);
        when(mockV1TrapPDUFactory.get(mockV1TrapConfiguration)).thenReturn(mockPdu);
        V2TrapPDUFactory mockV2TrapPDUFactory = mock(V2TrapPDUFactory.class);
        when(mockV2TrapPDUFactory.get(mockV2TrapConfiguration)).thenReturn(mockPdu);

        when(mockSnmpManager.send(mockPdu, mockTarget)).thenReturn(mockResponseEvent);

        snmpResourceHandler = new SNMPResourceHandler(mockSnmpManager, mockTarget);

        sendTrapSNMPHandler = new SendTrapSNMPHandler(snmpResourceHandler, Instant.now(), mockComponentLog) {
            @Override
            V1TrapPDUFactory createV1TrapPduFactory(final Instant startTime) {
                return mockV1TrapPDUFactory;
            }

            @Override
            V2TrapPDUFactory createV2TrapPduFactory(final Instant startTime) {
                return mockV2TrapPDUFactory;
            }
        };
    }

    @After
    public void tearDown() {
        snmpResourceHandler.close();
    }

    @Test
    public void testSendV1TrapWithValidFlowfile() throws IOException {
        final String flowFileOid = "1.3.6.1.2.1.1.1.0";
        sendTrapSNMPHandler.sendTrap(Collections.singletonMap("snmp$" + flowFileOid, "OID value"), mockV1TrapConfiguration);

        verify(mockSnmpManager).send(mockPdu, mockTarget);
    }

    @Test
    public void testSendV2TrapWithValidFlowfile() throws IOException {
        final String flowFileOid = "1.3.6.1.2.1.1.1.0";
        sendTrapSNMPHandler.sendTrap(Collections.singletonMap("snmp$" + flowFileOid, "OID value"), mockV2TrapConfiguration);

        verify(mockSnmpManager).send(mockPdu, mockTarget);
    }

    @Test
    public void testSendV1TrapWithFlowfileWithoutOptionalSnmpAttributes() throws IOException {
        sendTrapSNMPHandler.sendTrap(Collections.singletonMap("invalid key", "invalid value"), mockV1TrapConfiguration);

        verify(mockSnmpManager).send(mockPdu, mockTarget);

        final String expectedDebugLog = "{} No optional SNMP specific variables found in flowfile.";
        assertEquals(expectedDebugLog, mockComponentLog.getDebugMessages().get(0).getMsg());
    }

    @Test
    public void testSendTrapToInvalidPort() throws IOException {
        when(mockSnmpManager.send(mockPdu, mockTarget)).thenReturn(null);
        final TransportIpAddress mockAddress = mock(TransportIpAddress.class);
        final int port = 55555;
        when(mockAddress.getPort()).thenReturn(port);
        when(mockTarget.getAddress()).thenReturn(mockAddress);

        final String flowFileOid = "1.3.6.1.2.1.1.1.0";
        Exception e = assertThrows(IOException.class,
                () -> sendTrapSNMPHandler.sendTrap(Collections.singletonMap("snmp$" + flowFileOid, "OID value"), mockV2TrapConfiguration)
        );

        assertTrue(e.getMessage().contains(String.valueOf(port)));
    }

    @Test
    public void testSendTrapInvalidResponse() throws IOException {
        final String trapSnmpError = "Test Trap SNMP Error";
        when(mockResponseEvent.getError()).thenReturn(new SNMPException(trapSnmpError));
        when(mockSnmpManager.send(mockPdu, mockTarget)).thenReturn(mockResponseEvent);

        final String flowFileOid = "1.3.6.1.2.1.1.1.0";
        Exception e = assertThrows(IOException.class,
                () -> sendTrapSNMPHandler.sendTrap(Collections.singletonMap("snmp$" + flowFileOid, "OID value"), mockV2TrapConfiguration)
        );

        assertTrue(e.getCause().getMessage().contains(trapSnmpError));
    }
}
