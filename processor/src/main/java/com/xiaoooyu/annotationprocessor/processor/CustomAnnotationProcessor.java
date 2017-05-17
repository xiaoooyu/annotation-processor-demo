package com.xiaoooyu.annotationprocessor.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes(value = {"com.xiaoooyu.annotationprocessor.processor.CustomAnnotation",
    "com.xiaoooyu.annotationprocessor.processor.TrackClick"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class CustomAnnotationProcessor extends AbstractProcessor {

    private Filer mFiler;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);

        mFiler = env.getFiler();
    }

    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        Map<TypeElement, BindingSet> traceMap = new HashMap<>();

        for (Element element: env.getElementsAnnotatedWith(TraceClick.class)) {
            try {
                parseTraceClick(element, traceMap);
            } catch (Exception ex) {
                logParsingError(element, TraceClick.class, ex);
            }
        }

        for(Map.Entry<TypeElement, BindingSet> entry : traceMap.entrySet()) {
            TypeElement element = entry.getKey();
            BindingSet binding = entry.getValue();
            try {
                JavaFile javaFile = binding.brewJava();
                javaFile.writeTo(mFiler);
            } catch (Exception ex) {
                error(element, "brew java file for %s failed", element.getSimpleName());
            }
        }

        return false;
    }

    private void parseTraceClick(Element element, Map<TypeElement, BindingSet> buildMap) throws Exception {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        Name qualifiedName = enclosingElement.getQualifiedName();

        note(element, "enclosingElement is %s", qualifiedName);

        if (!buildMap.containsKey(enclosingElement)) {
            BindingSet temp = new BindingSet(enclosingElement);
            buildMap.put(enclosingElement, temp);
        }

        BindingSet set = buildMap.get(enclosingElement);
        TraceClick traceInfo = element.getAnnotation(TraceClick.class);
        if (traceInfo == null) {
            return;
        }

        ExecutableElement executableElement = null;
        if (element.getKind() == ElementKind.METHOD) {
            executableElement = (ExecutableElement) element;
        }

        Integer viewId = traceInfo.id();
        set.addBinding(viewId, executableElement, traceInfo);
    }

    private void error(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.ERROR, element, message, args);
    }

    private void note(Element element, String message, Object... args) {
        printMessage(Diagnostic.Kind.NOTE, element, message, args);
    }

    private void printMessage(Diagnostic.Kind kind, Element element, String message, Object[] args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }

        processingEnv.getMessager().printMessage(kind, message, element);
    }

    private void logParsingError(Element element, Class<? extends Annotation> annotation,
                                 Exception e) {
        StringWriter stackTrace = new StringWriter();
        e.printStackTrace(new PrintWriter(stackTrace));
        error(element, "Unable to parse @%s binding.\n\n%s", annotation.getSimpleName(), stackTrace);
    }

}
