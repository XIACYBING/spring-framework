/*
 * Copyright 2002-2019 the original author or authors.
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过类文件方法字节码中的{@code LocalVariableTable}属性，获取方法参数的实际名称；
 * 关于{@code LocalVariableTable}，需要注意的是，抽象方法和本地方法，是没有{@code LocalVariableTable}信息的，因为{@code LocalVariableTable}属于
 * {@code Code Attribute}信息，但是抽象方法和本地方法的字节码是没有{@code Code Attribute}信息的
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.13">4.7.13. The LocalVariableTable Attribute</a>
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.3">4.7.3. The Code Attribute</a>
 * @see <a href="https://stackoverflow.com/questions/71515303/why-is-there-no-localvariabletable-in-the-class-file-compiled-with-javac-g-for">Why is there no LocalVariableTable in the class file compiled with javac -g for the Java interface?</a>
 * @see <a href="https://www.google.com/search?q=Why+does+the+method+not+obtain+parameter+names+from+LocalVariableTable+in+bytecode+file">Why does the method not obtain parameter names from LocalVariableTable in bytecode file</a>
 *
 * Implementation of {@link ParameterNameDiscoverer} that uses the LocalVariableTable
 * information in the method attributes to discover parameter names. Returns
 * {@code null} if the class file was compiled without debug information.
 *
 * <p>Uses ObjectWeb's ASM library for analyzing class files. Each discoverer instance
 * caches the ASM discovered information for each introspected Class, in a thread-safe
 * manner. It is recommended to reuse ParameterNameDiscoverer instances as far as possible.
 *
 * @author Adrian Colyer
 * @author Costin Leau
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Sam Brannen
 * @since 2.0
 */
public class LocalVariableTableParameterNameDiscoverer implements ParameterNameDiscoverer {

	private static final Log logger = LogFactory.getLog(LocalVariableTableParameterNameDiscoverer.class);

	// marker object for classes that do not have any debug info
	private static final Map<Executable, String[]> NO_DEBUG_INFO_MAP = Collections.emptyMap();

	// the cache uses a nested index (value is a map) to keep the top level cache relatively small in size
	private final Map<Class<?>, Map<Executable, String[]>> parameterNamesCache = new ConcurrentHashMap<>(32);


	@Override
	@Nullable
	public String[] getParameterNames(Method method) {
		Method originalMethod = BridgeMethodResolver.findBridgedMethod(method);
		return doGetParameterNames(originalMethod);
	}

	@Override
	@Nullable
	public String[] getParameterNames(Constructor<?> ctor) {
		return doGetParameterNames(ctor);
	}

	@Nullable
	private String[] doGetParameterNames(Executable executable) {
		Class<?> declaringClass = executable.getDeclaringClass();

		// 根据类获取方法和参数名称的映射，如果不存在则调用inspectClass生成对应类下的方法和参数名称的映射集合
		Map<Executable, String[]> map = this.parameterNamesCache.computeIfAbsent(declaringClass, this::inspectClass);

		// 如果映射存在，则获取对应方法的参数名称数组
		return (map != NO_DEBUG_INFO_MAP ? map.get(executable) : null);
	}

	/**
	 * Inspects the target class.
	 * <p>Exceptions will be logged, and a marker map returned to indicate the
	 * lack of debug information.
	 */
	private Map<Executable, String[]> inspectClass(Class<?> clazz) {

		// 生成类文件的输入流
		InputStream is = clazz.getResourceAsStream(ClassUtils.getClassFileName(clazz));
		if (is == null) {
			// We couldn't load the class file, which is not fatal as it
			// simply means this method of discovering parameter names won't work.
			if (logger.isDebugEnabled()) {
				logger.debug("Cannot find '.class' file for class [" + clazz +
						"] - unable to determine constructor/method parameter names");
			}
			return NO_DEBUG_INFO_MAP;
		}
		try {

			// 根据输入流构建类读取器
			ClassReader classReader = new ClassReader(is);

			// 初始化集合，key为方法/构造器，value为参数的实际名称
			Map<Executable, String[]> map = new ConcurrentHashMap<>(32);

			// 通过类读取器和字节Visitor读取字节码中的参数名称信息
			classReader.accept(new ParameterNameDiscoveringVisitor(clazz, map), 0);

			// 返回读取到的方法和参数名称信息
			return map;
		}
		catch (IOException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("Exception thrown while reading '.class' file for class [" + clazz +
						"] - unable to determine constructor/method parameter names", ex);
			}
		}
		catch (IllegalArgumentException ex) {
			if (logger.isDebugEnabled()) {
				logger.debug("ASM ClassReader failed to parse class file [" + clazz +
						"], probably due to a new Java class file version that isn't supported yet " +
						"- unable to determine constructor/method parameter names", ex);
			}
		}
		finally {
			try {
				// 释放资源
				is.close();
			}
			catch (IOException ex) {
				// ignore
			}
		}
		return NO_DEBUG_INFO_MAP;
	}


	/**
	 * Helper class that inspects all methods and constructors and then
	 * attempts to find the parameter names for the given {@link Executable}.
	 */
	private static class ParameterNameDiscoveringVisitor extends ClassVisitor {

		private static final String STATIC_CLASS_INIT = "<clinit>";

		private final Class<?> clazz;

		private final Map<Executable, String[]> executableMap;

		public ParameterNameDiscoveringVisitor(Class<?> clazz, Map<Executable, String[]> executableMap) {
			super(SpringAsmInfo.ASM_VERSION);
			this.clazz = clazz;
			this.executableMap = executableMap;
		}

		@Override
		@Nullable
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

			// 非合成/桥接方法，以及非静态类初始化方法的，则需要处理
			// exclude synthetic + bridged && static class initialization
			if (!isSyntheticOrBridged(access) && !STATIC_CLASS_INIT.equals(name)) {

				// 生成方法的Visitor，LocalVariableTableVisitor是读取方法中的LocalVariableTable属性
				return new LocalVariableTableVisitor(this.clazz, this.executableMap, name, desc, isStatic(access));
			}
			return null;
		}

		private static boolean isSyntheticOrBridged(int access) {
			return (((access & Opcodes.ACC_SYNTHETIC) | (access & Opcodes.ACC_BRIDGE)) > 0);
		}

		private static boolean isStatic(int access) {
			return ((access & Opcodes.ACC_STATIC) > 0);
		}
	}


	private static class LocalVariableTableVisitor extends MethodVisitor {

		private static final String CONSTRUCTOR = "<init>";

		private final Class<?> clazz;

		private final Map<Executable, String[]> executableMap;

		private final String name;

		private final Type[] args;

		private final String[] parameterNames;

		private final boolean isStatic;

		private boolean hasLvtInfo = false;

		/*
		 * The nth entry contains the slot index of the LVT table entry holding the
		 * argument name for the nth parameter.
		 */
		private final int[] lvtSlotIndex;

		public LocalVariableTableVisitor(Class<?> clazz, Map<Executable, String[]> map, String name, String desc, boolean isStatic) {
			super(SpringAsmInfo.ASM_VERSION);
			this.clazz = clazz;
			this.executableMap = map;
			this.name = name;
			this.args = Type.getArgumentTypes(desc);
			this.parameterNames = new String[this.args.length];
			this.isStatic = isStatic;
			this.lvtSlotIndex = computeLvtSlotIndices(isStatic, this.args);
		}

		@Override
		public void visitLocalVariable(String name, String description, String signature, Label start, Label end, int index) {
			this.hasLvtInfo = true;

			// 记录LocalVariableTable中的参数名称和索引位置
			for (int i = 0; i < this.lvtSlotIndex.length; i++) {

				// 读取到指定位置，则设置指定位置的参数名称
				if (this.lvtSlotIndex[i] == index) {
					this.parameterNames[i] = name;
				}
			}
		}

		@Override
		public void visitEnd() {

			// 方法读取结束，将解析完成的方法和参数名称放入集合中
			if (this.hasLvtInfo || (this.isStatic && this.parameterNames.length == 0)) {
				// visitLocalVariable will never be called for static no args methods
				// which doesn't use any local variables.
				// This means that hasLvtInfo could be false for that kind of methods
				// even if the class has local variable info.
				this.executableMap.put(resolveExecutable(), this.parameterNames);
			}
		}

		private Executable resolveExecutable() {
			ClassLoader loader = this.clazz.getClassLoader();
			Class<?>[] argTypes = new Class<?>[this.args.length];
			for (int i = 0; i < this.args.length; i++) {
				argTypes[i] = ClassUtils.resolveClassName(this.args[i].getClassName(), loader);
			}
			try {
				if (CONSTRUCTOR.equals(this.name)) {
					return this.clazz.getDeclaredConstructor(argTypes);
				}
				return this.clazz.getDeclaredMethod(this.name, argTypes);
			}
			catch (NoSuchMethodException ex) {
				throw new IllegalStateException("Method [" + this.name +
						"] was discovered in the .class file but cannot be resolved in the class object", ex);
			}
		}

		private static int[] computeLvtSlotIndices(boolean isStatic, Type[] paramTypes) {
			int[] lvtIndex = new int[paramTypes.length];
			int nextIndex = (isStatic ? 0 : 1);
			for (int i = 0; i < paramTypes.length; i++) {
				lvtIndex[i] = nextIndex;
				if (isWideType(paramTypes[i])) {
					nextIndex += 2;
				}
				else {
					nextIndex++;
				}
			}
			return lvtIndex;
		}

		private static boolean isWideType(Type aType) {
			// float is not a wide type
			return (aType == Type.LONG_TYPE || aType == Type.DOUBLE_TYPE);
		}
	}

}
