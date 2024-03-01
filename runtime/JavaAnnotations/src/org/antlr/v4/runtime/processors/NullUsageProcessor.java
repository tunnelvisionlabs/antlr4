/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime.processors;

import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.annotations.Nullable;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.*;

/**
 * A compile-time validator for correct usage of the {@link NotNull} and
 * {@link Nullable} annotations.
 *
 * <p>The validation process checks the following items.</p>
 *
 * <ul>
 * <li><strong>Error</strong>: an element is annotated with both {@link NotNull} and {@link Nullable}.</li>
 * <li><strong>Error</strong>: an method which returns {@code void} is annotated with {@link NotNull} or {@link Nullable}.</li>
 * <li><strong>Error</strong>: an element with a primitive type is annotated with {@link Nullable}.</li>
 * <li><strong>Error</strong>: a parameter is annotated with {@link NotNull}, but the method overrides or implements a method where the parameter is annotated {@link Nullable}.</li>
 * <li><strong>Error</strong>: a method is annotated with {@link Nullable}, but the method overrides or implements a method that is annotated with {@link NotNull}.</li>
 * <li><strong>Warning</strong>: an element with a primitive type is annotated with {@link NotNull}.</li>
 * <li><strong>Warning</strong>: a parameter is annotated with {@link NotNull}, but the method overrides or implements a method where the parameter is not annotated.</li>
 * <li><strong>Warning</strong>: a method is annotated with {@link Nullable}, but the method overrides or implements a method that is not annotated.</li>
 * </ul>
 *
 * <p>In the future, the validation process may be updated to check the following additional items.</p>
 *
 * <ul>
 * <li><strong>Warning</strong>: a parameter is not annotated, but the method overrides or implements a method where the parameter is annotated with {@link NotNull} or {@link Nullable}.</li>
 * <li><strong>Warning</strong>: a method is not annotated, but the method overrides or implements a method that is annotated with with {@link NotNull} or {@link Nullable}.</li>
 * </ul>
 *
 * @author Sam Harwell
 */
@SupportedAnnotationTypes({NullUsageProcessor.NotNullClassName, NullUsageProcessor.NullableClassName})
public class NullUsageProcessor extends AbstractProcessor {
	public static final String NotNullClassName = "org.antlr.v4.runtime.annotations.NotNull";
	public static final String NullableClassName = "org.antlr.v4.runtime.annotations.Nullable";

	private TypeElement notNullType;
	private TypeElement nullableType;

	public NullUsageProcessor() {
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		SourceVersion latestSupported = SourceVersion.latestSupported();

		if (latestSupported.ordinal() <= 11) {
			return SourceVersion.RELEASE_11;
		}
		else if (latestSupported.ordinal() <= 21) {
			return latestSupported;
		}
		else {
			// this annotation processor is tested through Java 11
			return SourceVersion.values()[21];
		}
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (!checkClassNameConstants()) {
			return true;
		}

		notNullType = processingEnv.getElementUtils().getTypeElement(NotNullClassName);
		nullableType = processingEnv.getElementUtils().getTypeElement(NullableClassName);
		Set<? extends Element> notNullElements = roundEnv.getElementsAnnotatedWith(notNullType);
		Set<? extends Element> nullableElements = roundEnv.getElementsAnnotatedWith(nullableType);

		Set<Element> intersection = new HashSet<Element>(notNullElements);
		intersection.retainAll(nullableElements);
		for (Element element : intersection) {
			String error = String.format("%s cannot be annotated with both %s and %s", element.getKind().toString().replace('_', ' ').toLowerCase(), notNullType.getSimpleName(), nullableType.getSimpleName());
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error, element);
		}

		checkVoidMethodAnnotations(notNullElements, notNullType);
		checkVoidMethodAnnotations(nullableElements, nullableType);

		checkPrimitiveTypeAnnotations(nullableElements, Diagnostic.Kind.ERROR, nullableType);
		checkPrimitiveTypeAnnotations(notNullElements, Diagnostic.Kind.WARNING, notNullType);

		// method name -> method -> annotated elements of method
		Map<String, Map<ExecutableElement, List<Element>>> namedMethodMap =
			new HashMap<String, Map<ExecutableElement, List<Element>>>();
		addElementsToNamedMethodMap(notNullElements, namedMethodMap);
		addElementsToNamedMethodMap(nullableElements, namedMethodMap);

		for (Map.Entry<String, Map<ExecutableElement, List<Element>>> entry : namedMethodMap.entrySet()) {
			for (Map.Entry<ExecutableElement, List<Element>> subentry : entry.getValue().entrySet()) {
				checkOverriddenMethods(subentry.getKey());
			}
		}

		return true;
	}

	private boolean checkClassNameConstants() {
		boolean success = checkClassNameConstant(NotNullClassName, NotNull.class);
		success &= checkClassNameConstant(NullableClassName, Nullable.class);
		return success;
	}

	private boolean checkClassNameConstant(String className, Class<?> clazz) {
		if (className == null) {
			throw new NullPointerException("className");
		}

		if (clazz == null) {
			throw new NullPointerException("clazz");
		}

		if (!className.equals(clazz.getCanonicalName())) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("Unable to process null usage annotations due to class name mismatch: %s != %s", className, clazz.getCanonicalName()));
			return false;
		}

		return true;
	}

	private void checkVoidMethodAnnotations(Set<? extends Element> elements, TypeElement annotationType) {
		for (Element element : elements) {
			if (element.getKind() != ElementKind.METHOD) {
				continue;
			}

			ExecutableElement executableElement = (ExecutableElement)element;
			TypeMirror returnType = executableElement.getReturnType();
			if (returnType instanceof NoType && returnType.getKind() == TypeKind.VOID) {
				String error = String.format("void method cannot be annotated with %s", annotationType.getSimpleName());
				processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error, element, getAnnotationMirror(element, annotationType));
			}
		}
	}

	private void checkPrimitiveTypeAnnotations(Set<? extends Element> elements, Diagnostic.Kind kind, TypeElement annotationType) {
		for (Element element : elements) {
			TypeMirror typeToCheck;
			switch (element.getKind()) {
			case FIELD:
			case PARAMETER:
			case LOCAL_VARIABLE:
				// checking variable type
				VariableElement variableElement = (VariableElement)element;
				typeToCheck = variableElement.asType();
				break;

			case METHOD:
				// checking return type
				ExecutableElement executableElement = (ExecutableElement)element;
				typeToCheck = executableElement.getReturnType();
				break;

			default:
				continue;
			}

			if (typeToCheck instanceof PrimitiveType && typeToCheck.getKind().isPrimitive()) {
				String error = String.format("%s with a primitive type %s be annotated with %s", element.getKind().toString().replace('_', ' ').toLowerCase(), kind == Diagnostic.Kind.ERROR ? "cannot" : "should not", annotationType.getSimpleName());
				processingEnv.getMessager().printMessage(kind, error, element, getAnnotationMirror(element, annotationType));
			}
		}
	}

	private void addElementsToNamedMethodMap(Set<? extends Element> elements, Map<String, Map<ExecutableElement, List<Element>>> namedMethodMap) {
		for (Element element : elements) {
			ExecutableElement method;
			switch (element.getKind()) {
			case PARAMETER:
				method = (ExecutableElement)element.getEnclosingElement();
				assert method.getKind() == ElementKind.METHOD;
				break;

			case METHOD:
				method = (ExecutableElement)element;
				break;

			default:
				continue;
			}

			Map<ExecutableElement, List<Element>> annotatedMethodWithName =
				namedMethodMap.get(method.getSimpleName().toString());
			if (annotatedMethodWithName == null) {
				annotatedMethodWithName = new HashMap<ExecutableElement, List<Element>>();
				namedMethodMap.put(method.getSimpleName().toString(), annotatedMethodWithName);
			}

			List<Element> annotatedElementsOfMethod = annotatedMethodWithName.get(method);
			if (annotatedElementsOfMethod == null) {
				annotatedElementsOfMethod = new ArrayList<Element>();
				annotatedMethodWithName.put(method, annotatedElementsOfMethod);
			}

			annotatedElementsOfMethod.add(element);
		}
	}

	private void checkOverriddenMethods(ExecutableElement method) {
		TypeElement declaringType = (TypeElement)method.getEnclosingElement();
		Set<Element> errorElements = new HashSet<Element>();
		Set<Element> warnedElements = new HashSet<Element>();
		typeLoop:
		for (TypeMirror supertypeMirror : getAllSupertypes(processingEnv.getTypeUtils().getDeclaredType(declaringType))) {
			for (Element element : ((TypeElement)processingEnv.getTypeUtils().asElement(supertypeMirror)).getEnclosedElements()) {
				if (element instanceof ExecutableElement) {
					if (processingEnv.getElementUtils().overrides(method, (ExecutableElement)element, declaringType)) {
						checkOverriddenMethod(method, (ExecutableElement)element, errorElements, warnedElements);
						continue typeLoop;
					}
				}
			}
		}
	}

	private List<? extends TypeMirror> getAllSupertypes(TypeMirror type) {
		Set<TypeMirror> supertypes = new HashSet<TypeMirror>();
		Deque<TypeMirror> worklist = new ArrayDeque<TypeMirror>();
		worklist.add(type);
		while (!worklist.isEmpty()) {
			List<? extends TypeMirror> next = processingEnv.getTypeUtils().directSupertypes(worklist.poll());
			if (supertypes.addAll(next)) {
				worklist.addAll(next);
			}
		}

		return new ArrayList<TypeMirror>(supertypes);
	}

	private void checkOverriddenMethod(ExecutableElement overrider, ExecutableElement overridden, Set<Element> errorElements, Set<Element> warnedElements) {
		// check method annotation
		if (isNullable(overrider) && isNotNull(overridden) && errorElements.add(overrider)) {
			String error = String.format("method annotated with %s cannot override or implement a method annotated with %s", nullableType.getSimpleName(), notNullType.getSimpleName());
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error, overrider, getNullableAnnotationMirror(overrider));
		}
		else if (isNullable(overrider) && !(isNullable(overridden) || isNotNull(overridden)) && !errorElements.contains(overrider) && warnedElements.add(overrider)) {
			String error = String.format("method annotated with %s overrides a method that is not annotated", nullableType.getSimpleName());
			processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, error, overrider, getNullableAnnotationMirror(overrider));
		}

		List<? extends VariableElement> overriderParameters = overrider.getParameters();
		List<? extends VariableElement> overriddenParameters = overridden.getParameters();
		for (int i = 0; i < overriderParameters.size(); i++) {
			if (isNotNull(overriderParameters.get(i)) && isNullable(overriddenParameters.get(i)) && errorElements.add(overriderParameters.get(i))) {
				String error = String.format("parameter %s annotated with %s cannot override or implement a parameter annotated with %s", overriderParameters.get(i).getSimpleName(), notNullType.getSimpleName(), nullableType.getSimpleName());
				processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, error, overriderParameters.get(i), getNotNullAnnotationMirror(overriderParameters.get(i)));
			}
			else if (isNotNull(overriderParameters.get(i)) && !(isNullable(overriddenParameters.get(i)) || isNotNull(overriddenParameters.get(i))) && !errorElements.contains(overriderParameters.get(i)) && warnedElements.add(overriderParameters.get(i))) {
				String error = String.format("parameter %s annotated with %s overrides a parameter that is not annotated", overriderParameters.get(i).getSimpleName(), notNullType.getSimpleName());
				processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, error, overriderParameters.get(i), getNotNullAnnotationMirror(overriderParameters.get(i)));
			}
		}
	}

	private boolean isNotNull(Element element) {
		return getNotNullAnnotationMirror(element) != null;
	}

	private boolean isNullable(Element element) {
		return getNullableAnnotationMirror(element) != null;
	}

	private AnnotationMirror getNotNullAnnotationMirror(Element element) {
		return getAnnotationMirror(element, notNullType);
	}

	private AnnotationMirror getNullableAnnotationMirror(Element element) {
		return getAnnotationMirror(element, nullableType);
	}

	private AnnotationMirror getAnnotationMirror(Element element, TypeElement annotationType) {
		for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
			if (annotationMirror.getAnnotationType().asElement() == annotationType) {
				return annotationMirror;
			}
		}

		return null;
	}
}
