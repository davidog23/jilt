package org.jilt.internal;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.jilt.Builder;
import org.jilt.Opt;
import org.jilt.utils.Utils;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;

abstract class AbstractBuilderGenerator implements BuilderGenerator {
    private final Elements elements;
    private final Filer filer;

    private final TypeElement targetClassType;
    private final List<? extends VariableElement> attributes;
    private final Set<VariableElement> optionalAttributes;

    private final String builderClassPackage;
    private final ClassName builderClassTypeName;

    AbstractBuilderGenerator(TypeElement targetClass, List<? extends VariableElement> attributes, Elements elements, Filer filer) {
        this.elements = elements;
        this.filer = filer;

        Builder builderAnnotation = targetClass.getAnnotation(Builder.class);

        this.targetClassType = targetClass;
        this.attributes = attributes;
        this.optionalAttributes = initOptionalAttributes();

        builderClassPackage = elements.getPackageOf(targetClassType).toString();
        String builderClassStringName = targetClassType.getSimpleName() + "Builder";
        builderClassTypeName = ClassName.get(builderClassPackage, builderClassStringName);
    }

    @Override
    public final void generateBuilderClass() throws Exception {
        generateClassesNeededByBuilder();

        // builder class
        TypeSpec.Builder builderClassBuilder = TypeSpec.classBuilder(builderClassTypeName)
                .addModifiers(Modifier.PUBLIC);

        // add a static factory method to the builder class
        builderClassBuilder.addMethod(MethodSpec
                .methodBuilder(factoryMethodName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(factoryMethodReturnType())
                .addStatement("return new $T()", builderClassTypeName())
                .build());

        for (VariableElement attribute : attributes) {
            String fieldName = attributeSimpleName(attribute);
            TypeName fieldType = TypeName.get(attribute.asType());

            builderClassBuilder.addField(FieldSpec
                    .builder(
                            fieldType,
                            fieldName,
                            Modifier.PRIVATE)
                    .build());

            builderClassBuilder.addMethod(MethodSpec
                    .methodBuilder(builderSetterMethodName(attribute))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(returnTypeForSetterFor(attribute))
                    .addParameter(fieldType, fieldName)
                    .addStatement("this.$1L = $1L", fieldName)
                    .addStatement("return this")
                    .build());
        }

        TypeName targetClassName = targetClassTypeName();
        builderClassBuilder.addMethod(MethodSpec.methodBuilder(buildMethodName())
                .addModifiers(Modifier.PUBLIC)
                .returns(targetClassName)
                .addStatement("return new $T($L)", targetClassName, Utils.join(attributeNames()))
                .build());

        enhance(builderClassBuilder);

        JavaFile javaFile = JavaFile
                .builder(builderClassPackage, builderClassBuilder.build())
                .build();
        javaFile.writeTo(filer);
    }

    private List<String> attributeNames() {
        List<String> ret = new ArrayList<String>(attributes.size());
        for (VariableElement attribute : attributes) {
            ret.add(attributeSimpleName(attribute));
        }
        return ret;
    }

    protected abstract void generateClassesNeededByBuilder() throws Exception;

    protected abstract TypeName factoryMethodReturnType();

    protected abstract TypeName returnTypeForSetterFor(VariableElement attribute);

    protected abstract void enhance(TypeSpec.Builder builderClassBuilder);

    private Set<VariableElement> initOptionalAttributes() {
        Set<VariableElement> ret = new HashSet<VariableElement>();
        for (VariableElement attribute : attributes) {
            if (attribute.getAnnotation(Opt.class) != null)
                ret.add(attribute);
        }
        return ret;
    }

    protected final Filer filer() {
        return filer;
    }

    protected final TypeElement targetClassType() {
        return targetClassType;
    }

    protected TypeName targetClassTypeName() {
        return TypeName.get(targetClassType.asType());
    }

    protected final List<? extends VariableElement> attributes() {
        return attributes;
    }

    protected final boolean isOptional(VariableElement attribute) {
        return optionalAttributes.contains(attribute);
    }

    protected final String builderClassPackage() {
        return builderClassPackage;
    }

    protected final TypeName builderClassTypeName() {
        return builderClassTypeName;
    }

    protected final String attributeSimpleName(VariableElement attribute) {
        return attribute.getSimpleName().toString();
    }

    protected final String builderSetterMethodName(VariableElement attribute) {
        return attributeSimpleName(attribute);
    }

    protected final String factoryMethodName() {
        return Utils.deCapitalize(targetClassType().getSimpleName().toString());
    }

    protected final String buildMethodName() {
        return "build";
    }
}
