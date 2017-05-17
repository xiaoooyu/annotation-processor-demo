package com.xiaoooyu.annotationprocessor.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import static com.google.auto.common.MoreElements.getPackage;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Copyright (c) 2017 Teambition All Rights Reserved.
 */
public class BindingSet {

    private static final ClassName VIEW = ClassName.get("android.view", "View");
    private static final ClassName UI_THREAD = ClassName.get("android.support.annotation", "UiThread");

    // enclosing class
    protected TypeElement mEnclosingElement;

    // mapping from Id to executable method
    protected Map<Integer, TraceMethod> mIdToTraceMethod;

    protected ClassName mBindingClassName;
    protected ClassName mTargetClassName;

    public BindingSet(TypeElement enclosingElement) {
        mEnclosingElement = enclosingElement;

        mIdToTraceMethod = new LinkedHashMap<>();

        String packageName = getPackage(enclosingElement).getQualifiedName().toString();
        String className = enclosingElement.getQualifiedName().toString().substring(
            packageName.length() + 1).replace('.', '$');
        mBindingClassName = ClassName.get(packageName, className + "_TraceBinding");
        mTargetClassName = ClassName.get(packageName, className);
    }

    /**
     *
     * @param id
     * @param bindingElement
     * @param traceInfo
     * @return ture if success
     */
    public boolean addBinding(int id, ExecutableElement bindingElement, TraceClick traceInfo) {
        Integer key = new Integer(id);
        if (!mIdToTraceMethod.containsKey(key)) {
            mIdToTraceMethod.put(key, new TraceMethod(traceInfo, bindingElement));
            return true;
        }
        return false;
    }

    public static class TraceMethod {
        TraceClick mTraceInfo;
        ExecutableElement mExecutableElement;

        public TraceMethod(TraceClick traceInfo, ExecutableElement executableElement) {
            mTraceInfo = traceInfo;
            mExecutableElement = executableElement;
        }
    }

    public JavaFile brewJava() {
        return JavaFile.builder(mBindingClassName.packageName(), createType())
            .addFileComment("Generated code from Trace. Do not modify!")
            .build();
    }

    public TypeSpec createType() {
        TypeVariableName targetTypeVariableName = TypeVariableName.get("T", mTargetClassName);

        TypeSpec.Builder result = TypeSpec.classBuilder(mBindingClassName.simpleName())
            .addTypeVariable(targetTypeVariableName)
            .addModifiers(Modifier.PUBLIC);

        result.addField(targetTypeVariableName, "mTarget", PRIVATE);
        result.addField(VIEW, "mView", PRIVATE);

        result.addMethod(createContructor(targetTypeVariableName));

        return result.build();
    }

    private MethodSpec createContructor(TypeName targetType) {
        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
            .addAnnotation(UI_THREAD)
            .addModifiers(PUBLIC);

        constructor.addParameter(targetType, "target");
        constructor.addParameter(VIEW, "source");

        constructor.addStatement("mTarget = target");
        constructor.addStatement("mView = source");

        return constructor.build();
    }
}
