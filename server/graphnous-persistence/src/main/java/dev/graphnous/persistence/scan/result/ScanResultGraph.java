package dev.graphnous.persistence.scan.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.core.model.Annotation;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.Field;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.Package;
import dev.graphnous.core.model.Parameter;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.TypeRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scan results flattened into rows per node and relationship type, so
 * each type is written with one batched query.
 * <p>
 * Node ids extend the id of their parent, which keeps them unique within
 * the graph: a class is identified by its module, a method by its class.
 * A class belongs to the file that declares it, and to the package that
 * file names. Classes nested in others belong to the same file and package.
 * Functions and variables declared outside any class, as in TypeScript,
 * Python or Go, are methods and fields of their file instead, and belong to
 * its package in the same way.
 * Types are stored by their declared names, e.g.
 * {@code java.util.List<com.example.Order>}.
 * <p>
 * Every annotation becomes a node of the class, method or field it is on;
 * annotations of parameters belong to their method. Each argument is a
 * property {@code arguments.<name>}, e.g. {@code arguments.value} for
 * {@code @Path("/orders")}, and all arguments together are kept as JSON in
 * {@code arguments}.
 */
record ScanResultGraph(
    List<Map<String, Object>> targets,
    List<Map<String, Object>> modules,
    List<Map<String, Object>> files,
    List<Map<String, Object>> packages,
    List<Map<String, Object>> classes,
    List<Map<String, Object>> fileClasses,
    List<Map<String, Object>> packageClasses,
    List<Map<String, Object>> methods,
    List<Map<String, Object>> fields,
    List<Map<String, Object>> functions,
    List<Map<String, Object>> variables,
    List<Map<String, Object>> dependencies,
    List<Map<String, Object>> extendsTypes,
    List<Map<String, Object>> implementsTypes,
    List<Map<String, Object>> classAnnotations,
    List<Map<String, Object>> methodAnnotations,
    List<Map<String, Object>> fieldAnnotations
) {

    private static final ObjectMapper JSON = new ObjectMapper();

    static ScanResultGraph of(
        final String scanId,
        final List<ScanResult> results
    ) {
        final var builder = new Builder(scanId);

        results.forEach(builder::addResult);

        return builder.build();
    }

    /**
     * The qualified name of a type without its type arguments, e.g.
     * {@code java.util.List} for {@code java.util.List<com.example.Order>}.
     */
    static String rawType(final String type) {
        final var arguments = type.indexOf('<');

        return (arguments < 0 ? type : type.substring(0, arguments)).trim();
    }

    /**
     * The type as declared, e.g. {@code java.util.List<com.example.Order>}.
     */
    private static String name(final TypeRef type) {
        return type == null ? null : type.getName();
    }

    private static List<String> names(final List<TypeRef> types) {
        return types.stream().map(ScanResultGraph::name).toList();
    }

    private static final class Builder {

        private final String scanId;

        /**
         * The target of the result being added; every node records it, so
         * enhancers can work on one target at a time.
         */
        private String targetId;

        private final List<Map<String, Object>> targets = new ArrayList<>();
        private final List<Map<String, Object>> modules = new ArrayList<>();
        private final List<Map<String, Object>> files = new ArrayList<>();
        private final List<Map<String, Object>> packages = new ArrayList<>();
        private final Map<String, Map<String, Object>> classes = new LinkedHashMap<>();
        private final List<Map<String, Object>> fileClasses = new ArrayList<>();
        private final List<Map<String, Object>> packageClasses = new ArrayList<>();
        private final List<Map<String, Object>> methods = new ArrayList<>();
        private final List<Map<String, Object>> fields = new ArrayList<>();
        private final List<Map<String, Object>> functions = new ArrayList<>();
        private final List<Map<String, Object>> variables = new ArrayList<>();
        private final List<Map<String, Object>> dependencies = new ArrayList<>();
        private final List<Map<String, Object>> extendsTypes = new ArrayList<>();
        private final List<Map<String, Object>> implementsTypes = new ArrayList<>();
        private final List<Map<String, Object>> classAnnotations = new ArrayList<>();
        private final List<Map<String, Object>> methodAnnotations = new ArrayList<>();
        private final List<Map<String, Object>> fieldAnnotations = new ArrayList<>();

        private Builder(final String scanId) {
            this.scanId = scanId;
        }

        private void addResult(final ScanResult result) {
            final var target = result.getTarget();
            targetId = scanId + "|" + target.getPath();

            targets.add(row(
                "id", targetId,
                "targetId", targetId,
                "path", target.getPath(),
                "language", string(target.getLanguage()),
                "languageVersion", target.getLanguageVersion(),
                "buildSystem", string(target.getBuildSystem()),
                "buildSystemVersion", target.getBuildSystemVersion()
            ));

            result.getModules().forEach(module -> addModule(targetId, module));
        }

        private void addModule(final String targetId, final Module module) {
            final var moduleId = targetId + "|" + module.getPath();

            modules.add(row(
                "id", moduleId,
                "targetId", targetId,
                "name", module.getName(),
                "path", module.getPath()
            ));

            module.getFiles().forEach(file -> addFile(moduleId, file));
            module.getPackages().forEach(pkg -> addPackage(moduleId, pkg));

            module.getDependencies().forEach(dependency -> dependencies.add(row(
                "moduleId", moduleId,
                "coordinates", dependency.getName() + ":" + (dependency.getVersion() == null ? "" : dependency.getVersion()),
                "name", dependency.getName(),
                "version", dependency.getVersion(),
                "scope", dependency.getScope()
            )));
        }

        private void addFile(final String moduleId, final File file) {
            final var fileId = moduleId + "|file:" + file.getPath();

            files.add(row(
                "id", fileId,
                "targetId", targetId,
                "moduleId", moduleId,
                "path", file.getPath(),
                "language", file.getLanguage(),
                "sourceSet", string(file.getSourceSet()),
                "size", file.getSize(),
                "checksum", file.getChecksum()
            ));

            final var packageId = file.getPackage() == null ? null : moduleId + "|package:" + file.getPackage();

            file.getClasses().forEach(type -> addDeclaredClass(moduleId, fileId, packageId, type));
            file.getFunctions().forEach(function -> addFunction(fileId, packageId, function));
            file.getVariables().forEach(variable -> addVariable(fileId, packageId, variable));
        }

        /**
         * Adds the class and the classes nested in it, each belonging to the
         * file and package that declare them.
         */
        private void addDeclaredClass(
            final String moduleId,
            final String fileId,
            final String packageId,
            final Class type
        ) {
            final var classId = addClass(moduleId, type);

            fileClasses.add(row(
                "fileId", fileId,
                "classId", classId
            ));

            if (packageId != null) {
                packageClasses.add(row(
                    "packageId", packageId,
                    "classId", classId
                ));
            }

            type.getClasses().forEach(nested -> addDeclaredClass(moduleId, fileId, packageId, nested));
        }

        private void addPackage(final String moduleId, final Package pkg) {
            final var name = pkg.getQualifiedName() == null ? pkg.getName() : pkg.getQualifiedName();
            final var packageId = moduleId + "|package:" + name;

            packages.add(row(
                "id", packageId,
                "targetId", targetId,
                "moduleId", moduleId,
                "name", pkg.getName(),
                "qualifiedName", pkg.getQualifiedName()
            ));
        }

        /**
         * Adds the class once, however often it is listed, and returns its id.
         */
        private String addClass(final String moduleId, final Class type) {
            final var classId = moduleId + "|class:" + type.getQualifiedName();

            if (classes.containsKey(classId)) {
                return classId;
            }

            classes.put(classId, row(
                "id", classId,
                "targetId", targetId,
                "moduleId", moduleId,
                "name", type.getName(),
                "qualifiedName", type.getQualifiedName(),
                "kind", string(type.getKind()),
                "modifiers", strings(type.getModifiers()),
                "typeParameters", type.getTypeParameters(),
                "superClass", type.getSuperClasses().isEmpty() ? null : name(type.getSuperClasses().getFirst()),
                "interfaces", names(type.getInterfaces()),
                "annotations", annotations(type.getAnnotations())
            ));

            type.getSuperClasses().forEach(superClass -> extendsTypes.add(row(
                "classId", classId,
                "qualifiedName", rawType(name(superClass))
            )));

            type.getInterfaces().forEach(anInterface -> implementsTypes.add(row(
                "classId", classId,
                "qualifiedName", rawType(name(anInterface))
            )));

            addAnnotations(classAnnotations, classId, classId, type.getAnnotations(), null, null);

            type.getMethods().forEach(method -> addMethod(classId, method));
            type.getFields().forEach(field -> addField(classId, field));

            return classId;
        }

        private void addMethod(final String classId, final Method method) {
            final var methodId = classId + "|method:" + method.getQualifiedName();
            final var row = method(methodId, method);

            row.put("classId", classId);
            methods.add(row);
        }

        /**
         * Adds a function declared outside any class; the package is the
         * one its file names, if any.
         */
        private void addFunction(final String fileId, final String packageId, final Method function) {
            final var functionId = fileId + "|function:" + function.getQualifiedName();
            final var row = method(functionId, function);

            row.put("fileId", fileId);
            row.put("packageId", packageId);
            functions.add(row);
        }

        /**
         * The row of the method or function, whose annotations it adds.
         */
        private Map<String, Object> method(final String methodId, final Method method) {
            final var row = row(
                "id", methodId,
                "targetId", targetId,
                "name", method.getName(),
                "qualifiedName", method.getQualifiedName(),
                "kind", string(method.getKind()),
                "modifiers", strings(method.getModifiers()),
                "typeParameters", method.getTypeParameters(),
                "returnType", name(method.getReturnType()),
                "parameterNames", method.getParameters().stream().map(Parameter::getName).toList(),
                "parameterTypes", method.getParameters().stream().map(parameter -> name(parameter.getType())).toList(),
                "annotations", annotations(method.getAnnotations())
            );

            addAnnotations(methodAnnotations, methodId, methodId, method.getAnnotations(), null, null);

            for (int i = 0; i < method.getParameters().size(); i++) {
                final var parameter = method.getParameters().get(i);

                addAnnotations(
                    methodAnnotations,
                    methodId,
                    methodId + "|parameter:" + i,
                    parameter.getAnnotations(),
                    parameter.getName(),
                    i
                );
            }

            return row;
        }

        private void addField(final String classId, final Field field) {
            final var fieldId = classId + "|field:" + field.getName();
            final var row = field(fieldId, field);

            row.put("classId", classId);
            fields.add(row);
        }

        /**
         * Adds a variable or constant declared outside any class; the
         * package is the one its file names, if any.
         */
        private void addVariable(final String fileId, final String packageId, final Field variable) {
            final var variableId = fileId + "|variable:" + variable.getName();
            final var row = field(variableId, variable);

            row.put("fileId", fileId);
            row.put("packageId", packageId);
            variables.add(row);
        }

        /**
         * The row of the field or variable, whose annotations it adds.
         */
        private Map<String, Object> field(final String fieldId, final Field field) {
            final var row = row(
                "id", fieldId,
                "targetId", targetId,
                "name", field.getName(),
                "qualifiedName", field.getQualifiedName(),
                "type", name(field.getType()),
                "modifiers", strings(field.getModifiers()),
                "annotations", annotations(field.getAnnotations())
            );

            addAnnotations(fieldAnnotations, fieldId, fieldId, field.getAnnotations(), null, null);

            return row;
        }

        /**
         * @param ownerId   the class, method or field the annotations belong to,
         *                  or the function or variable
         * @param idPrefix  what the annotation ids extend; differs from the
         *                  owner for the annotations of a parameter
         * @param parameter the annotated parameter of the method, if any
         */
        private void addAnnotations(
            final List<Map<String, Object>> rows,
            final String ownerId,
            final String idPrefix,
            final List<Annotation> annotations,
            final String parameter,
            final Integer parameterIndex
        ) {
            for (int i = 0; i < annotations.size(); i++) {
                final var annotation = annotations.get(i);
                final var arguments = annotation.getArguments() == null
                    ? Map.<String, Object>of()
                    : annotation.getArguments().getAdditionalProperties();

                final var properties = new HashMap<String, Object>();

                properties.put("targetId", targetId);

                properties.put("name", annotation.getName());
                properties.put("qualifiedName", annotation.getQualifiedName());

                if (!arguments.isEmpty()) {
                    properties.put("arguments", json(arguments));
                    arguments.forEach((name, value) ->
                        properties.put("arguments." + name, property(value))
                    );
                }

                rows.add(row(
                    // Annotations can repeat, so they are identified by position
                    "id", idPrefix + "|annotation:" + i,
                    "ownerId", ownerId,
                    "parameter", parameter,
                    "parameterIndex", parameterIndex,
                    "properties", properties
                ));
            }
        }

        private ScanResultGraph build() {
            return new ScanResultGraph(
                distinctById(targets),
                distinctById(modules),
                distinctById(files),
                distinctById(packages),
                List.copyOf(classes.values()),
                fileClasses,
                packageClasses,
                distinctById(methods),
                distinctById(fields),
                distinctById(functions),
                distinctById(variables),
                dependencies,
                extendsTypes,
                implementsTypes,
                distinctById(classAnnotations),
                distinctById(methodAnnotations),
                distinctById(fieldAnnotations)
            );
        }

        /**
         * Keeps the first of the rows sharing an id, so a node reported twice
         * does not fail the unique id constraint.
         */
        private static List<Map<String, Object>> distinctById(final List<Map<String, Object>> rows) {
            final var distinct = new LinkedHashMap<Object, Map<String, Object>>();

            rows.forEach(row -> distinct.putIfAbsent(row.get("id"), row));

            return List.copyOf(distinct.values());
        }

        /**
         * The names of the annotations, for simple queries; an annotation is
         * named by its qualified name, or its simple name when that is
         * unknown. The annotation nodes hold the arguments.
         */
        private static List<String> annotations(final List<Annotation> annotations) {
            return annotations.stream()
                .map(annotation -> annotation.getQualifiedName() == null
                    ? annotation.getName()
                    : annotation.getQualifiedName())
                .toList();
        }

        /**
         * The argument as a Neo4j property value. Strings, numbers, booleans
         * and lists of one of those are stored as they are; anything else,
         * such as a nested annotation or a mixed list, as JSON.
         */
        static Object property(final Object value) {
            if (value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long || value instanceof Double) {
                return value;
            }

            if (value instanceof Number number) {
                // BigInteger, BigDecimal and the like have no Neo4j type
                return number.toString();
            }

            if (value instanceof List<?> list) {
                if (list.stream().allMatch(String.class::isInstance)
                    || list.stream().allMatch(Boolean.class::isInstance)) {
                    return list;
                }

                if (list.stream().allMatch(item -> item instanceof Integer || item instanceof Long)) {
                    return list.stream().map(item -> ((Number) item).longValue()).toList();
                }

                if (list.stream().allMatch(item -> item instanceof Integer || item instanceof Long || item instanceof Double)) {
                    return list.stream().map(item -> ((Number) item).doubleValue()).toList();
                }
            }

            return json(value);
        }

        private static String json(final Object value) {
            try {
                return JSON.writeValueAsString(value);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Cannot store annotation argument " + value, e);
            }
        }

        private static List<String> strings(final List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }

        private static String string(final Object value) {
            return value == null ? null : value.toString();
        }

        /**
         * A map of alternating keys and values; unlike {@link Map#of} it
         * accepts null values, which the driver stores as absent properties.
         */
        private static Map<String, Object> row(final Object... keysAndValues) {
            final var row = new HashMap<String, Object>();

            for (int i = 0; i < keysAndValues.length; i += 2) {
                row.put((String) keysAndValues[i], keysAndValues[i + 1]);
            }

            return row;
        }
    }
}
