/*
 * Copyright 2002-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.core;

/**
 * Default implementation of the {@link ParameterNameDiscoverer} strategy interface,
 * using the Java 8 standard reflection mechanism (if available), and falling back
 * to the ASM-based {@link LocalVariableTableParameterNameDiscoverer} for checking
 * debug information in the class file.
 *
 * <p>If a Kotlin reflection implementation is present,
 * {@link KotlinReflectionParameterNameDiscoverer} is added first in the list and
 * used for Kotlin classes and interfaces. When compiling or running as a GraalVM
 * native image, the {@code KotlinReflectionParameterNameDiscoverer} is not used.
 *
 * <p>Further discoverers may be added through {@link #addDiscoverer(ParameterNameDiscoverer)}.
 *
 * @author Juergen Hoeller
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 4.0
 * @see StandardReflectionParameterNameDiscoverer
 * @see LocalVariableTableParameterNameDiscoverer
 * @see KotlinReflectionParameterNameDiscoverer
 */
public class DefaultParameterNameDiscoverer extends PrioritizedParameterNameDiscoverer {

	public DefaultParameterNameDiscoverer() {

		// Kotlin的支持
		if (KotlinDetector.isKotlinReflectPresent() && !GraalDetector.inImageCode()) {
			addDiscoverer(new KotlinReflectionParameterNameDiscoverer());
		}

		// 标准反射参数名称解析器：只有编译时未开启parameters，才能获取到参数名称
		// 否则只能获取到Executable生成的默认参数名称（arg0、arg1...），这不是我们想要的，在当前解析器中只能返回null
		addDiscoverer(new StandardReflectionParameterNameDiscoverer());

		// LocalVariableTable参数名称解析器：从字节码的LocalVariableTable中获取参数名称
		addDiscoverer(new LocalVariableTableParameterNameDiscoverer());
	}

}
