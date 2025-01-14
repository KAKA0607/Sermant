/*
 * Copyright (C) 2021-2021 Huawei Technologies Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.huawei.register.service.impl;

import com.huawei.register.config.RegisterConfig;
import com.huawei.register.context.RegisterContext;
import com.huawei.register.service.register.RegisterManager;
import com.huawei.register.service.register.ScServer;
import com.huawei.register.service.utils.CommonUtils;
import com.huawei.register.services.RegisterCenterService;
import com.huawei.sermant.core.agent.common.BeforeResult;
import com.huawei.sermant.core.common.LoggerFactory;
import com.huawei.sermant.core.plugin.config.PluginConfigManager;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * 注册实现
 *
 * @author zhouss
 * @since 2021-12-16
 */
public class RegisterCenterServiceImpl implements RegisterCenterService {
    private static final Logger LOGGER = LoggerFactory.getLogger();

    private RegisterConfig registerConfig;

    @Override
    public void start() {
        RegisterManager.INSTANCE.start();
    }

    @Override
    public void stop() {
        RegisterManager.INSTANCE.stop();
    }

    @Override
    public void register(Object rawRegistration, BeforeResult result) {
        RegisterManager.INSTANCE.register(rawRegistration);
        if (!getRegisterConfig().isOpenMigration()) {
            // 阻止原注册中心注册
            result.setResult(null);
        }
    }

    @Override
    public void replaceServerList(Object target, BeforeResult beforeResult) {
        String serviceId = (String) CommonUtils.getFieldValue(target, "serviceId");
        if (serviceId == null && RegisterContext.INSTANCE.getiClientConfig() != null) {
            // 若未获取到服务名，则从注册基本信息获取
            serviceId = RegisterContext.INSTANCE.getiClientConfig().getClientName();
        }
        if (serviceId == null) {
            // 无法执行替换
            LOGGER.warning("Can not acquire the name of service, the process to replace instance won't be finished!");
            return;
        }
        if (useOriginRegisterCenter()) {
            final List<ServiceInstance> serviceInstances = queryServiceInstances(serviceId);
            if (!serviceInstances.isEmpty()) {
                final List<ScServer> servers = new ArrayList<ScServer>();
                for (ServiceInstance instance : serviceInstances) {
                    servers.add(new ScServer(instance));
                }
                beforeResult.setResult(servers);
                LOGGER.fine("Using origin register center instance list.");
            }
        }
        if (beforeResult.getResult() != null) {
            return;
        }
        List<ScServer> serverList = RegisterManager.INSTANCE.getServerList(serviceId);
        if (serverList != null) {
            beforeResult.setResult(serverList);
        }
    }

    @Override
    public void replaceServerList(String serviceId, BeforeResult beforeResult) {
        if (useOriginRegisterCenter()) {
            // 若原注册中心可用，则使用原注册中心的实例列表
            final List<ServiceInstance> originServiceInstances = queryServiceInstances(serviceId);
            if (!originServiceInstances.isEmpty()) {
                beforeResult.setResult(originServiceInstances);
                LOGGER.fine("Using origin register center instance list.");
            }
        }
        if (beforeResult.getResult() != null) {
            return;
        }
        List<ServiceInstance> serviceInstances = RegisterManager.INSTANCE.getInstanceList(serviceId);
        if (serviceInstances != null) {
            beforeResult.setResult(serviceInstances);
        }
    }

    private boolean useOriginRegisterCenter() {
        return RegisterContext.INSTANCE.isAvailable() && getRegisterConfig().isOpenMigration();
    }

    /**
     * 查询原注册中心所有的实例列表
     *
     * @param serviceId 服务id
     * @return 服务实例列表
     */
    private List<ServiceInstance> queryServiceInstances(String serviceId) {
        try {
            final CompositeDiscoveryClient discoveryClient = RegisterContext.INSTANCE.getDiscoveryClient();
            final List<DiscoveryClient> discoveryClients = discoveryClient.getDiscoveryClients();
            if (discoveryClients == null) {
                return Collections.emptyList();
            }
            for (DiscoveryClient client : discoveryClients) {
                List<ServiceInstance> instances = client.getInstances(serviceId);
                if (instances != null && !instances.isEmpty()) {
                    return instances;
                }
            }
        } catch (Exception ex) {
            // 原注册中心已经没有可用服务
            LOGGER.info("Origin register center is not available! ");
        }
        RegisterContext.INSTANCE.setAvailable(false);
        return Collections.emptyList();
    }

    private RegisterConfig getRegisterConfig() {
        if (registerConfig == null) {
            registerConfig = PluginConfigManager.getPluginConfig(RegisterConfig.class);
        }
        return registerConfig;
    }
}
