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

package org.apache.dubbo.metadata.report.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.metadata.MappingListener;
import org.apache.dubbo.metadata.definition.ServiceDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.report.identifier.KeyTypeEnum;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.apache.dubbo.rpc.model.ApplicationModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractMetadataReportTest {

    private NewMetadataReport abstractMetadataReport;

    @BeforeEach
    public void before() {
        URL url = URL.valueOf("zookeeper://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        abstractMetadataReport = new NewMetadataReport(url);
        // set the simple name of current class as the application name
        ApplicationModel.defaultModel().getConfigManager().setApplication(new ApplicationConfig(getClass().getSimpleName()));
    }

    @AfterEach
    public void reset() {
        // reset
        ApplicationModel.reset();
    }

    @Test
    void testGetProtocol() {
        URL url = URL.valueOf("dubbo://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic&side=provider");
        String protocol = abstractMetadataReport.getProtocol(url);
        assertEquals(protocol, "provider");

        URL url2 = URL.valueOf("consumer://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        String protocol2 = abstractMetadataReport.getProtocol(url2);
        assertEquals(protocol2, "consumer");
    }

    @Test
    void testStoreProviderUsual() throws ClassNotFoundException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.InterfaceNameTestService";
        String version = "1.0.0";
        String group = null;
        String application = "vic";
        MetadataIdentifier providerMetadataIdentifier = storeProvider(abstractMetadataReport, interfaceName, version, group, application);
        Thread.sleep(1500);
        Assertions.assertNotNull(abstractMetadataReport.store.get(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY)));
    }

    @Test
    void testStoreProviderSync() throws ClassNotFoundException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.InterfaceNameTestService";
        String version = "1.0.0";
        String group = null;
        String application = "vic";
        abstractMetadataReport.syncReport = true;
        MetadataIdentifier providerMetadataIdentifier = storeProvider(abstractMetadataReport, interfaceName, version, group, application);
        Assertions.assertNotNull(abstractMetadataReport.store.get(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY)));
    }

    @Test
    void testFileExistAfterPut() throws InterruptedException, ClassNotFoundException {
        //just for one method
        URL singleUrl = URL.valueOf("redis://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.metadata.store.InterfaceNameTestService?version=1.0.0&application=singleTest");
        NewMetadataReport singleMetadataReport = new NewMetadataReport(singleUrl);

        assertFalse(singleMetadataReport.file.exists());

        String interfaceName = "org.apache.dubbo.metadata.store.InterfaceNameTestService";
        String version = "1.0.0";
        String group = null;
        String application = "vic";
        MetadataIdentifier providerMetadataIdentifier = storeProvider(singleMetadataReport, interfaceName, version, group, application);

        Thread.sleep(2000);
        assertTrue(singleMetadataReport.file.exists());
        assertTrue(singleMetadataReport.properties.containsKey(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY)));
    }

    @Test
    void testRetry() throws InterruptedException, ClassNotFoundException {
        String interfaceName = "org.apache.dubbo.metadata.store.RetryTestService";
        String version = "1.0.0.retry";
        String group = null;
        String application = "vic.retry";
        URL storeUrl = URL.valueOf("retryReport://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestServiceForRetry?version=1.0.0.retry&application=vic.retry");
        RetryMetadataReport retryReport = new RetryMetadataReport(storeUrl, 2);
        retryReport.metadataReportRetry.retryPeriod = 400L;
        URL url = URL.valueOf("dubbo://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestService?version=1.0.0&application=vic");
        Assertions.assertNull(retryReport.metadataReportRetry.retryScheduledFuture);
        assertEquals(0, retryReport.metadataReportRetry.retryCounter.get());
        assertTrue(retryReport.store.isEmpty());
        assertTrue(retryReport.failedReports.isEmpty());


        storeProvider(retryReport, interfaceName, version, group, application);
        Thread.sleep(150);

        assertTrue(retryReport.store.isEmpty());
        assertFalse(retryReport.failedReports.isEmpty());
        assertNotNull(retryReport.metadataReportRetry.retryScheduledFuture);
        Thread.sleep(2000L);
        assertTrue(retryReport.metadataReportRetry.retryCounter.get() != 0);
        assertTrue(retryReport.metadataReportRetry.retryCounter.get() >= 3);
        assertFalse(retryReport.store.isEmpty());
        assertTrue(retryReport.failedReports.isEmpty());
    }

    @Test
    void testRetryCancel() throws InterruptedException, ClassNotFoundException {
        String interfaceName = "org.apache.dubbo.metadata.store.RetryTestService";
        String version = "1.0.0.retrycancel";
        String group = null;
        String application = "vic.retry";
        URL storeUrl = URL.valueOf("retryReport://" + NetUtils.getLocalAddress().getHostName() + ":4444/org.apache.dubbo.TestServiceForRetryCancel?version=1.0.0.retrycancel&application=vic.retry");
        RetryMetadataReport retryReport = new RetryMetadataReport(storeUrl, 2);
        retryReport.metadataReportRetry.retryPeriod = 150L;
        retryReport.metadataReportRetry.retryTimesIfNonFail = 2;

        storeProvider(retryReport, interfaceName, version, group, application);

        // Wait for the assignment of retryScheduledFuture to complete
        while (retryReport.metadataReportRetry.retryScheduledFuture == null) {
        }
        assertFalse(retryReport.metadataReportRetry.retryScheduledFuture.isCancelled());
        assertFalse(retryReport.metadataReportRetry.retryExecutor.isShutdown());
        Thread.sleep(1000L);
        assertTrue(retryReport.metadataReportRetry.retryScheduledFuture.isCancelled());
        assertTrue(retryReport.metadataReportRetry.retryExecutor.isShutdown());

    }

    private MetadataIdentifier storeProvider(AbstractMetadataReport abstractMetadataReport, String interfaceName, String version, String group, String application) throws ClassNotFoundException {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":4444/" + interfaceName + "?version=" + version + "&application="
            + application + (group == null ? "" : "&group=" + group) + "&testPKey=8989");

        MetadataIdentifier providerMetadataIdentifier = new MetadataIdentifier(interfaceName, version, group, PROVIDER_SIDE, application);
        Class interfaceClass = Class.forName(interfaceName);
        FullServiceDefinition fullServiceDefinition = ServiceDefinitionBuilder.buildFullDefinition(interfaceClass, url.getParameters());

        abstractMetadataReport.storeProviderMetadata(providerMetadataIdentifier, fullServiceDefinition);

        return providerMetadataIdentifier;
    }

    private MetadataIdentifier storeConsumer(AbstractMetadataReport abstractMetadataReport, String interfaceName, String version, String group, String application, Map<String, String> tmp) throws ClassNotFoundException {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":4444/" + interfaceName + "?version=" + version + "&application="
            + application + (group == null ? "" : "&group=" + group) + "&testPKey=9090");

        tmp.putAll(url.getParameters());
        MetadataIdentifier consumerMetadataIdentifier = new MetadataIdentifier(interfaceName, version, group, CONSUMER_SIDE, application);

        abstractMetadataReport.storeConsumerMetadata(consumerMetadataIdentifier, tmp);

        return consumerMetadataIdentifier;
    }

    @Test
    void testPublishAll() throws ClassNotFoundException, InterruptedException {

        assertTrue(abstractMetadataReport.store.isEmpty());
        assertTrue(abstractMetadataReport.allMetadataReports.isEmpty());
        String interfaceName = "org.apache.dubbo.metadata.store.InterfaceNameTestService";
        String version = "1.0.0";
        String group = null;
        String application = "vic";
        MetadataIdentifier providerMetadataIdentifier1 = storeProvider(abstractMetadataReport, interfaceName, version, group, application);
        Thread.sleep(1000);
        assertEquals(abstractMetadataReport.allMetadataReports.size(), 1);
        assertTrue(((FullServiceDefinition) abstractMetadataReport.allMetadataReports.get(providerMetadataIdentifier1)).getParameters().containsKey("testPKey"));

        MetadataIdentifier providerMetadataIdentifier2 = storeProvider(abstractMetadataReport, interfaceName, version + "_2", group + "_2", application);
        Thread.sleep(1000);
        assertEquals(abstractMetadataReport.allMetadataReports.size(), 2);
        assertTrue(((FullServiceDefinition) abstractMetadataReport.allMetadataReports.get(providerMetadataIdentifier2)).getParameters().containsKey("testPKey"));
        assertEquals(((FullServiceDefinition) abstractMetadataReport.allMetadataReports.get(providerMetadataIdentifier2)).getParameters().get("version"), version + "_2");

        Map<String, String> tmpMap = new HashMap<>();
        tmpMap.put("testKey", "value");
        MetadataIdentifier consumerMetadataIdentifier = storeConsumer(abstractMetadataReport, interfaceName, version + "_3", group + "_3", application, tmpMap);
        Thread.sleep(1000);
        assertEquals(abstractMetadataReport.allMetadataReports.size(), 3);

        Map tmpMapResult = (Map) abstractMetadataReport.allMetadataReports.get(consumerMetadataIdentifier);
        assertEquals(tmpMapResult.get("testPKey"), "9090");
        assertEquals(tmpMapResult.get("testKey"), "value");
        assertEquals(3, abstractMetadataReport.store.size());

        abstractMetadataReport.store.clear();

        assertEquals(0, abstractMetadataReport.store.size());

        abstractMetadataReport.publishAll();
        Thread.sleep(200);

        assertEquals(3, abstractMetadataReport.store.size());

        String v = abstractMetadataReport.store.get(providerMetadataIdentifier1.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
        FullServiceDefinition data = JsonUtils.getJson().toJavaObject(v, FullServiceDefinition.class);
        checkParam(data.getParameters(), application, version);

        String v2 = abstractMetadataReport.store.get(providerMetadataIdentifier2.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
        data = JsonUtils.getJson().toJavaObject(v2, FullServiceDefinition.class);
        checkParam(data.getParameters(), application, version + "_2");

        String v3 = abstractMetadataReport.store.get(consumerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY));
        Map v3Map = JsonUtils.getJson().toJavaObject(v3, Map.class);
        checkParam(v3Map, application, version + "_3");
    }

    @Test
    void testCalculateStartTime() {
        for (int i = 0; i < 300; i++) {
            long t = abstractMetadataReport.calculateStartTime() + System.currentTimeMillis();
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t);
            assertTrue(c.get(Calendar.HOUR_OF_DAY) >= 2);
            assertTrue(c.get(Calendar.HOUR_OF_DAY) <= 6);
        }
    }


    private void checkParam(Map<String, String> map, String application, String version) {
        assertEquals(map.get("application"), application);
        assertEquals(map.get("version"), version);
    }

    private static class NewMetadataReport extends AbstractMetadataReport {

        Map<String, String> store = new ConcurrentHashMap<>();

        public NewMetadataReport(URL metadataReportURL) {
            super(metadataReportURL);
        }

        @Override
        protected void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) {
            store.put(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), serviceDefinitions);
        }

        @Override
        protected void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString) {
            store.put(consumerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), serviceParameterString);
        }

        @Override
        protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urls) {

        }

        @Override
        protected String doGetSubscribedURLs(SubscriberMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        public String getServiceDefinition(MetadataIdentifier consumerMetadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        public void removeServiceAppMappingListener(String serviceKey, MappingListener listener) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }
    }

    private static class RetryMetadataReport extends AbstractMetadataReport {

        Map<String, String> store = new ConcurrentHashMap<>();
        int needRetryTimes;
        int executeTimes = 0;

        public RetryMetadataReport(URL metadataReportURL, int needRetryTimes) {
            super(metadataReportURL);
            this.needRetryTimes = needRetryTimes;
        }

        @Override
        protected void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) {
            ++executeTimes;
            System.out.println("***" + executeTimes + ";" + System.currentTimeMillis());
            if (executeTimes <= needRetryTimes) {
                throw new RuntimeException("must retry:" + executeTimes);
            }
            store.put(providerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), serviceDefinitions);
        }

        @Override
        protected void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String serviceParameterString) {
            ++executeTimes;
            if (executeTimes <= needRetryTimes) {
                throw new RuntimeException("must retry:" + executeTimes);
            }
            store.put(consumerMetadataIdentifier.getUniqueKey(KeyTypeEnum.UNIQUE_KEY), serviceParameterString);
        }

        @Override
        protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        protected void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urls) {

        }

        @Override
        protected String doGetSubscribedURLs(SubscriberMetadataIdentifier metadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        public String getServiceDefinition(MetadataIdentifier consumerMetadataIdentifier) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

        @Override
        public void removeServiceAppMappingListener(String serviceKey, MappingListener listener) {
            throw new UnsupportedOperationException("This extension does not support working as a remote metadata center.");
        }

    }


}
