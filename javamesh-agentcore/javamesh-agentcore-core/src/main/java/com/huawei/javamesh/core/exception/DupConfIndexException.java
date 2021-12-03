/*
 * Copyright (C) Huawei Technologies Co., Ltd. 2021-2021. All rights reserved
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

package com.huawei.javamesh.core.exception;

import java.util.Locale;

/**
 * 配置重复索引异常，在使用同一个键解释其值时报出，如{@code config.key=prefix.${config.key}.suffix}
 *
 * @author HapThorin
 * @version 1.0.0
 * @since 2021/11/4
 */
public class DupConfIndexException extends RuntimeException {
    public DupConfIndexException(String key) {
        super(String.format(Locale.ROOT, "Unable to use [%s] to explain [%s]. ", key, key));
    }
}