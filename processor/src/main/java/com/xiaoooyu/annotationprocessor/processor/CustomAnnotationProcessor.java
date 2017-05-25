package com.xiaoooyu.annotationprocessor.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.sun.source.tree.ClassTree;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes(value = {"com.xiaoooyu.annotationprocessor.processor.CustomAnnotation",
    "com.xiaoooyu.annotationprocessor.processor.TrackClick"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class CustomAnnotationProcessor extends AbstractProcessor {

    private static final List<String> SUPPORTED_TYPES = Arrays.asList(
        "array", "attr", "bool", "color", "dimen", "drawable", "id", "integer", "string"
    );

    private Filer mFiler;
    private Elements mElementUtils;
    private Trees mTrees;
    private Types mTypeUtils;

    private final Map<QualifiedId, Id> mSymbols = new LinkedHashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);

        mFiler = env.getFiler();
        mElementUtils = env.getElementUtils();
        mTypeUtils = env.getTypeUtils();

        try {
            mTrees = Trees.instance(env);
        } catch (IllegalArgumentException ignored) {

        }
    }

    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {

        scanForRClasses(env);

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

//        note(element, "enclosingElement is %s", qualifiedName);

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

        int viewId = traceInfo.id();
        QualifiedId qualifiedId = elementToQualifiedId(element, viewId);

        set.addBinding(getId(qualifiedId), executableElement, traceInfo);
    }

    private QualifiedId elementToQualifiedId(Element element, int id) {
        return new QualifiedId(mElementUtils.getPackageOf(element).getQualifiedName().toString(), id);
    }

    private Id getId(QualifiedId qualifiedId) {
        if (mSymbols.get(qualifiedId) == null) {
            mSymbols.put(qualifiedId, new Id(qualifiedId.id));
        }
        return mSymbols.get(qualifiedId);
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

    private void scanForRClasses(RoundEnvironment env) {
        if (mTrees == null) return;

        RClassScanner scanner = new RClassScanner();

        for (Class<? extends Annotation> annotation : getSupportedAnnotations()) {
            for (Element element : env.getElementsAnnotatedWith(annotation)) {
                JCTree tree = (JCTree) mTrees.getTree(element, getMirror(element, annotation));
                if (tree != null) { // tree can be null if the references are compiled types and not source
                    String respectivePackageName =
                        mElementUtils.getPackageOf(element).getQualifiedName().toString();
                    scanner.setCurrentPackageName(respectivePackageName);
                    tree.accept(scanner);
                }
            }
        }

        for (Map.Entry<String, Set<String>> packageNameToRClassSet : scanner.getRClasses().entrySet()) {
            String respectivePackageName = packageNameToRClassSet.getKey();
            for (String rClass : packageNameToRClassSet.getValue()) {
                parseRClass(respectivePackageName, rClass);
            }
        }
    }

    private void parseRClass(String respectivePackageName, String rClass) {
        Element element;

        try {
            element = mElementUtils.getTypeElement(rClass);
        } catch (MirroredTypeException mte) {
            element = mTypeUtils.asElement(mte.getTypeMirror());
        }

        JCTree tree = (JCTree) mTrees.getTree(element);
        if (tree != null) { // tree can be null if the references are compiled types and not source
            IdScanner idScanner = new IdScanner(mSymbols, mElementUtils.getPackageOf(element)
                .getQualifiedName().toString(), respectivePackageName);
            tree.accept(idScanner);
        } else {
            parseCompiledR(respectivePackageName, (TypeElement) element);
        }
    }

    private void parseCompiledR(String respectivePackageName, TypeElement rClass) {
        for (Element element : rClass.getEnclosedElements()) {
            String innerClassName = element.getSimpleName().toString();
            if (SUPPORTED_TYPES.contains(innerClassName)) {
                for (Element enclosedElement : element.getEnclosedElements()) {
                    if (enclosedElement instanceof VariableElement) {
                        VariableElement variableElement = (VariableElement) enclosedElement;
                        Object value = variableElement.getConstantValue();

                        if (value instanceof Integer) {
                            int id = (Integer) value;
                            ClassName rClassName =
                                ClassName.get(mElementUtils.getPackageOf(variableElement).toString(), "R",
                                    innerClassName);
                            String resourceName = variableElement.getSimpleName().toString();
                            QualifiedId qualifiedId = new QualifiedId(respectivePackageName, id);
                            mSymbols.put(qualifiedId, new Id(id, rClassName, resourceName));
                        }
                    }
                }
            }
        }
    }

    private Set<Class<? extends Annotation>> getSupportedAnnotations() {
        Set<Class<? extends Annotation>> annotations = new LinkedHashSet<>();
        annotations.add(TraceClick.class);

        return annotations;
    }

    private static AnnotationMirror getMirror(Element element,
                                              Class<? extends Annotation> annotation) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().toString().equals(annotation.getCanonicalName())) {
                return annotationMirror;
            }
        }
        return null;
    }


    private static class RClassScanner extends TreeScanner {
        // Maps the currently evaulated rPackageName to R Classes
        private final Map<String, Set<String>> rClasses = new LinkedHashMap<>();
        private String currentPackageName;

        @Override public void visitSelect(JCTree.JCFieldAccess jcFieldAccess) {
            Symbol symbol = jcFieldAccess.sym;
            if (symbol != null
                && symbol.getEnclosingElement() != null
                && symbol.getEnclosingElement().getEnclosingElement() != null
                && symbol.getEnclosingElement().getEnclosingElement().enclClass() != null) {
                Set<String> rClassSet = rClasses.get(currentPackageName);
                if (rClassSet == null) {
                    rClassSet = new HashSet<>();
                    rClasses.put(currentPackageName, rClassSet);
                }
                rClassSet.add(symbol.getEnclosingElement().getEnclosingElement().enclClass().className());
            }
        }

        Map<String, Set<String>> getRClasses() {
            return rClasses;
        }

        void setCurrentPackageName(String respectivePackageName) {
            this.currentPackageName = respectivePackageName;
        }
    }

    private static class IdScanner extends TreeScanner {
        private final Map<QualifiedId, Id> ids;
        private final String rPackageName;
        private final String respectivePackageName;

        IdScanner(Map<QualifiedId, Id> ids, String rPackageName, String respectivePackageName) {
            this.ids = ids;
            this.rPackageName = rPackageName;
            this.respectivePackageName = respectivePackageName;
        }

        @Override public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
            for (JCTree tree : jcClassDecl.defs) {
                if (tree instanceof ClassTree) {
                    ClassTree classTree = (ClassTree) tree;
                    String className = classTree.getSimpleName().toString();
                    if (SUPPORTED_TYPES.contains(className)) {
                        ClassName rClassName = ClassName.get(rPackageName, "R", className);
                        VarScanner scanner = new VarScanner(ids, rClassName, respectivePackageName);
                        ((JCTree) classTree).accept(scanner);
                    }
                }
            }
        }
    }

    private static class VarScanner extends TreeScanner {
        private final Map<QualifiedId, Id> ids;
        private final ClassName className;
        private final String respectivePackageName;

        private VarScanner(Map<QualifiedId, Id> ids, ClassName className,
                           String respectivePackageName) {
            this.ids = ids;
            this.className = className;
            this.respectivePackageName = respectivePackageName;
        }

        @Override public void visitVarDef(JCTree.JCVariableDecl jcVariableDecl) {
            if ("int".equals(jcVariableDecl.getType().toString())) {
                int id = Integer.valueOf(jcVariableDecl.getInitializer().toString());
                String resourceName = jcVariableDecl.getName().toString();
                QualifiedId qualifiedId = new QualifiedId(respectivePackageName, id);
                ids.put(qualifiedId, new Id(id, className, resourceName));
            }
        }
    }
}
