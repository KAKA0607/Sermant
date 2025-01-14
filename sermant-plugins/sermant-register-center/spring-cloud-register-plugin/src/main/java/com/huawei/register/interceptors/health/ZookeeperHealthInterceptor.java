/*
 * Copyright (C) 2021-2022 Huawei Technologies Co., Ltd. All rights reserved.
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

package com.huawei.register.interceptors.health;

import com.huawei.register.context.RegisterContext;
import com.huawei.register.handler.SingleStateCloseHandler;
import com.huawei.sermant.core.agent.common.BeforeResult;
import com.huawei.sermant.core.agent.interceptor.InstanceMethodInterceptor;
import com.huawei.sermant.core.common.LoggerFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.springframework.cloud.zookeeper.discovery.ZookeeperServiceWatch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * 注册中心健康状态变更
 *
 * @author zhouss
 * @since 2021-12-13
 */
public class ZookeeperHealthInterceptor extends SingleStateCloseHandler implements InstanceMethodInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger();

    @Override
    public void before(Object obj, Method method, Object[] arguments, BeforeResult beforeResult) {
        setArguments(arguments);
        setTarget(obj);
        if (arguments.length > 1 && arguments[1] instanceof TreeCacheEvent) {
            TreeCacheEvent event = (TreeCacheEvent) arguments[1];
            if (!isAvailable(event.getType()) && RegisterContext.INSTANCE.compareAndSet(true, false)) {
                // 注册中心断开
                doChange(obj, arguments, true, false);
            } else if (isAvailable(event.getType()) && RegisterContext.INSTANCE.compareAndSet(false, true)) {
                // 注册中心可用
                doChange(obj, arguments, false, true);
            } else {
                return;
            }
        }
    }

    @Override
    public Object after(Object obj, Method method, Object[] arguments, Object result) {
        return result;
    }

    @Override
    public void onThrow(Object obj, Method method, Object[] arguments, Throwable t) {
    }

    private boolean isAvailable(TreeCacheEvent.Type eventType) {
        return eventType != TreeCacheEvent.Type.CONNECTION_LOST
                && eventType != TreeCacheEvent.Type.CONNECTION_SUSPENDED;
    }

    @Override
    protected void close() throws Exception {
        ZookeeperServiceWatch watch = (ZookeeperServiceWatch) target;
        final Field curator = watch.getClass().getDeclaredField("curator");
        curator.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        final CuratorFramework client = (CuratorFramework) curator.get(target);
        // 关闭客户端, 停止定时器
        client.close();
        final Field cache = watch.getClass().getDeclaredField("cache");
        cache.setAccessible(true);
        modifiersField.setInt(cache, cache.getModifiers() & ~Modifier.FINAL);
        // 清空缓存
        cache.set(target, null);
        LOGGER.info("Zookeeper client has been closed.");
    }
}
