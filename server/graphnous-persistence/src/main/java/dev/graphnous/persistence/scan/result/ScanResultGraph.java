package dev.graphnous.persistence.scan.result;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.core.model.Annotation;
import dev.graphnous.core.model.Argument;
import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.EnumConstant;
import dev.graphnous.core.model.Field;
import dev.graphnous.core.model.FieldAccess;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.Package;
import dev.graphnous.core.model.Parameter;
import dev.graphnous.core.model.RecordComponent;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.TypeRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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
 * <p>
 * What the body of a method or function uses becomes a relationship from
 * it, one for each place it does, with the line: {@code CALLS} to a method
 * or function, {@code INSTANTIATES} to a class it constructs,
 * {@code READS} and {@code WRITES} to a field or variable, and
 * {@code USES} to a class it names as a type. Only what the scanner
 * resolved to something the scan has itself is kept: a call into a library
 * has nothing to point at.
 * <p>
 * A {@code CALLS} or {@code INSTANTIATES} relationship has what the call
 * passes, when the scanner recorded the arguments of calls: each as
 * written in {@code arguments}, the name each is passed under in
 * {@code argumentNames}, empty for one passed by position, and whether
 * each is spread in {@code argumentSpreads}; all three are as long as each
 * other, and empty for a call that passes nothing. A scan whose scanner
 * did not record arguments has none of them.
 * <p>
 * A call of what a package exports, such as rendering {@code Button} of
 * {@code @acme/theme}, which the scanner names with the module it is
 * imported from, has a node of its own to point at: a
 * {@code PackageExport} of the target, one for each export the target's
 * code uses, with its {@code package} ({@code @acme/theme}), the
 * {@code module} it is imported from ({@code @acme/theme} or a subpath
 * such as {@code @acme/theme/format}), its {@code name} ({@code Button})
 * and its {@code qualifiedName} as the scanner names it
 * ({@code @acme/theme:Button}). Such a call is a {@code CALLS} or
 * {@code INSTANTIATES} relationship to it like any other, with the line
 * and what it passes.
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
    List<Map<String, Object>> fieldAnnotations,
    Map<String, List<Map<String, Object>>> usages,
    List<Map<String, Object>> packageExports,
    Map<String, List<Map<String, Object>>> packageExportUsages
) {

    /**
     * The relationships from a method or function to what its body uses,
     * each with the label of what it points at.
     */
    static final Map<String, String> USAGES = Map.of(
        "CALLS", "Method",
        "INSTANTIATES", "Class",
        "READS", "Field",
        "WRITES", "Field",
        "USES", "Class"
    );

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
        private final Map<String, Map<String, Object>> packageExports = new LinkedHashMap<>();
        private final Map<String, List<Map<String, Object>>> packageExportUsages = new LinkedHashMap<>();

        /**
         * The ids of the methods and functions, classes, and fields and
         * variables, by their target and qualified name, as calls, type
         * uses and field accesses name them; the first of a name, as for
         * overloads that share one.
         */
        private final Map<String, String> methodIds = new HashMap<>();
        private final Map<String, String> classIds = new HashMap<>();
        private final Map<String, String> fieldIds = new HashMap<>();

        /**
         * The class of each constructor, which is what constructing it
         * instantiates.
         */
        private final Map<String, String> constructorClasses = new HashMap<>();

        /**
         * The ids of the fields of classes added so far.
         */
        private final Set<String> declaredFields = new HashSet<>();

        /**
         * The ids of the fields that are record components, by the target
         * and qualified name of the accessor a record has for each, as
         * {@code com.example.Order.id()}.
         */
        private final Map<String, String> accessorFields = new HashMap<>();

        /**
         * Whether the scanner of the result being added recorded what its
         * calls pass.
         */
        private boolean argumentsRecorded;

        /**
         * What the bodies use, to resolve once everything has an id: what
         * is used may be declared after what uses it.
         */
        private final List<PendingUsage> pending = new ArrayList<>();

        /**
         * @param key what is used, by its target and qualified name
         */
        private record PendingUsage(
            String type,
            String sourceId,
            String key,
            Map<String, Object> properties
        ) {
        }

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

            // Every call lists its arguments, or none does: a result
            // read into the model cannot tell a call without arguments
            // from one whose arguments were not recorded
            argumentsRecorded = result.getModules().stream()
                .flatMap(module -> module.getFiles().stream())
                .flatMap(file -> Stream.concat(
                    file.getFunctions().stream(),
                    file.getClasses().stream().flatMap(Builder::methods)
                ))
                .flatMap(method -> method.getCalls().stream())
                .anyMatch(call -> !call.getArguments().isEmpty());

            result.getModules().forEach(module -> addModule(targetId, module));
        }

        /**
         * The methods of the class and of the classes nested in it.
         */
        private static Stream<Method> methods(final Class type) {
            return Stream.concat(
                type.getMethods().stream(),
                type.getClasses().stream().flatMap(Builder::methods)
            );
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

            classIds.putIfAbsent(key(type.getQualifiedName()), classId);

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
            type.getEnumConstants().forEach(constant -> addEnumConstant(classId, type, constant));
            type.getRecordComponents().forEach(component -> addRecordComponent(classId, type, component));

            return classId;
        }

        private void addMethod(final String classId, final Method method) {
            final var methodId = classId + "|method:" + method.getQualifiedName();
            final var row = method(methodId, method);

            row.put("classId", classId);
            methods.add(row);

            methodIds.putIfAbsent(key(method.getQualifiedName()), methodId);

            if (method.getKind() == Method.Kind.CONSTRUCTOR) {
                constructorClasses.put(methodId, classId);
            }

            addUsages(methodId, method);
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

            methodIds.putIfAbsent(key(function.getQualifiedName()), functionId);

            addUsages(functionId, function);
        }

        /**
         * What is used by its target and qualified name: names are only
         * unique within a target.
         */
        private String key(final String qualifiedName) {
            return targetId + "|" + qualifiedName;
        }

        /**
         * Keeps what the body of the method or function uses, as far as the
         * scanner resolved it.
         */
        private void addUsages(final String methodId, final Method method) {
            for (final var call : method.getCalls()) {
                if (call.getTarget() == null) {
                    continue;
                }

                final var ofPackage = call.getModule() != null && !Boolean.TRUE.equals(call.getResolved());

                if (!ofPackage && Boolean.FALSE.equals(call.getResolved())) {
                    continue;
                }

                final var properties = row("line", call.getStartLine());

                if (argumentsRecorded) {
                    final var arguments = call.getArguments();

                    properties.put("arguments", arguments.stream().map(Argument::getValue).toList());
                    properties.put("argumentNames", arguments.stream()
                        .map(argument -> argument.getName() == null ? "" : argument.getName())
                        .toList());
                    properties.put("argumentSpreads", arguments.stream()
                        .map(argument -> Boolean.TRUE.equals(argument.getSpread()))
                        .toList());
                }

                if (ofPackage) {
                    addPackageExportUsage(methodId, call, properties);
                } else if (call.getKind() == Call.Kind.CONSTRUCTOR) {
                    pending.add(new PendingUsage("INSTANTIATES", methodId, key(call.getTarget()), properties));
                } else {
                    // Passing a method on, as in onClick={this.save}, is not calling it
                    properties.put("reference", call.getKind() == Call.Kind.METHOD_REFERENCE);
                    pending.add(new PendingUsage("CALLS", methodId, key(call.getTarget()), properties));
                }
            }

            for (final var access : method.getFieldAccesses()) {
                if (Boolean.FALSE.equals(access.getResolved()) || access.getTarget() == null) {
                    continue;
                }

                final var kind = access.getAccess();

                if (kind != FieldAccess.Access.WRITE) {
                    pending.add(new PendingUsage("READS", methodId, key(access.getTarget()), row("line", access.getStartLine())));
                }

                if (kind == FieldAccess.Access.WRITE || kind == FieldAccess.Access.READ_WRITE) {
                    pending.add(new PendingUsage("WRITES", methodId, key(access.getTarget()), row("line", access.getStartLine())));
                }
            }

            for (final var use : method.getTypeUses()) {
                if (Boolean.FALSE.equals(use.getResolved()) || use.getTarget() == null) {
                    continue;
                }

                pending.add(new PendingUsage(
                    "USES",
                    methodId,
                    key(rawType(use.getTarget())),
                    row("line", use.getStartLine(), "kind", string(use.getKind()))
                ));
            }
        }

        /**
         * Keeps a call of what a package exports as a usage of the export's
         * node, which it adds the first time the target's code uses it.
         * The export's name is the target after the module, without the
         * constructor of a class: {@code Store} for
         * {@code state-kit:Store.constructor}.
         */
        private void addPackageExportUsage(
            final String methodId,
            final Call call,
            final Map<String, Object> properties
        ) {
            final var constructor = call.getKind() == Call.Kind.CONSTRUCTOR;
            final var qualifiedName = constructor && call.getTarget().endsWith(".constructor")
                ? call.getTarget().substring(0, call.getTarget().length() - ".constructor".length())
                : call.getTarget();
            final var separator = qualifiedName.indexOf(':', call.getModule().length());
            final var name = separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
            final var exportId = targetId + "|export:" + qualifiedName;

            packageExports.computeIfAbsent(exportId, id -> row(
                "id", id,
                "targetId", targetId,
                "package", packageOf(call.getModule()),
                "module", call.getModule(),
                "name", name,
                "qualifiedName", qualifiedName
            ));

            if (!constructor) {
                properties.put("reference", call.getKind() == Call.Kind.METHOD_REFERENCE);
            }

            final var usage = new HashMap<>(properties);
            usage.put("sourceId", methodId);
            usage.put("targetId", exportId);

            packageExportUsages.computeIfAbsent(constructor ? "INSTANTIATES" : "CALLS", type -> new ArrayList<>()).add(usage);
        }

        /**
         * The package a module imported is of: @acme/theme for
         * @acme/theme/format, react for react/jsx-runtime.
         */
        private static String packageOf(final String module) {
            final var parts = module.split("/");

            return module.startsWith("@") && parts.length > 1 ? parts[0] + "/" + parts[1] : parts[0];
        }

        /**
         * The usages by relationship, each from a method or function to the
         * node of the scan it uses; those of what the scan does not have
         * are left out.
         */
        private Map<String, List<Map<String, Object>>> usages() {
            final var usages = new LinkedHashMap<String, List<Map<String, Object>>>();

            for (final var usage : pending) {
                if (usage.type().equals("CALLS")
                    && !methodIds.containsKey(usage.key())
                    && accessorFields.containsKey(usage.key())) {
                    // The accessor of a record component that the record
                    // does not declare: calling it reads the component
                    usages.computeIfAbsent("READS", type -> new ArrayList<>()).add(row(
                        "line", usage.properties().get("line"),
                        "sourceId", usage.sourceId(),
                        "targetId", accessorFields.get(usage.key())
                    ));

                    continue;
                }

                final var targetId = switch (usage.type()) {
                    case "CALLS" -> methodIds.get(usage.key());
                    case "INSTANTIATES" -> instantiated(usage.key());
                    case "READS", "WRITES" -> fieldIds.get(usage.key());
                    default -> classIds.get(usage.key());
                };

                if (targetId == null) {
                    continue;
                }

                final var row = new HashMap<>(usage.properties());
                row.put("sourceId", usage.sourceId());
                row.put("targetId", targetId);

                usages.computeIfAbsent(usage.type(), type -> new ArrayList<>()).add(row);
            }

            return usages;
        }

        /**
         * The class a constructor call instantiates: that of the
         * constructor it names, or the class it names itself, also when
         * that has no constructor of its own, as {@code Order.constructor}
         * or, for the constructor a record gets from its components,
         * {@code com.example.Order.Order(java.util.UUID)}.
         */
        private String instantiated(final String key) {
            final var constructor = methodIds.get(key);

            if (constructor != null && constructorClasses.containsKey(constructor)) {
                return constructorClasses.get(constructor);
            }

            if (classIds.containsKey(key)) {
                return classIds.get(key);
            }

            // Without the parameters, whose types have dots of their own
            final var parameters = key.indexOf('(');
            final var name = parameters < 0 ? key : key.substring(0, parameters);
            final var member = name.lastIndexOf('.');

            return member < 0 ? null : classIds.get(name.substring(0, member));
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
            addField(classId, field, null);
        }

        /**
         * @param kind what the field is when not declared as one, or null
         * @return the id of the field, or null when the class already has
         *         one of its name
         */
        private String addField(final String classId, final Field field, final String kind) {
            final var fieldId = classId + "|field:" + field.getName();

            if (!declaredFields.add(fieldId) && kind != null) {
                return null;
            }

            final var row = field(fieldId, field);

            row.put("classId", classId);

            if (kind != null) {
                row.put("kind", kind);
            }

            fields.add(row);

            if (field.getQualifiedName() != null) {
                fieldIds.putIfAbsent(key(field.getQualifiedName()), fieldId);
            }

            return fieldId;
        }

        /**
         * Adds the constant of an enum as a field of it, of its own type,
         * which is what reading it accesses.
         */
        private void addEnumConstant(final String classId, final Class type, final EnumConstant constant) {
            final var field = new Field();
            field.setName(constant.getName());
            field.setQualifiedName(constant.getQualifiedName() == null
                ? type.getQualifiedName() + "." + constant.getName()
                : constant.getQualifiedName());

            final var ownType = new TypeRef();
            ownType.setName(type.getQualifiedName());
            field.setType(ownType);
            field.setAnnotations(constant.getAnnotations());

            addField(classId, field, "ENUM_CONSTANT");
        }

        /**
         * Adds the component of a record as a field of it. A record has an
         * accessor for each, {@code id()} for {@code id}, which the scan
         * lists as a method only when the record declares it itself.
         */
        private void addRecordComponent(final String classId, final Class type, final RecordComponent component) {
            final var qualifiedName = type.getQualifiedName() + "." + component.getName();

            final var field = new Field();
            field.setName(component.getName());
            field.setQualifiedName(qualifiedName);
            field.setType(component.getType());
            field.setAnnotations(component.getAnnotations());

            final var fieldId = addField(classId, field, "RECORD_COMPONENT");

            if (fieldId != null) {
                accessorFields.putIfAbsent(key(qualifiedName + "()"), fieldId);
            }
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

            if (variable.getQualifiedName() != null) {
                fieldIds.putIfAbsent(key(variable.getQualifiedName()), variableId);
            }
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
                distinctById(fieldAnnotations),
                usages(),
                List.copyOf(packageExports.values()),
                packageExportUsages
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
         * Neo4j cannot store null in a list, so a null in a list value, such
         * as the type of an untyped TypeScript parameter, becomes an empty
         * string; dropping it would misalign lists read by position, such
         * as the parameter names and types.
         */
        private static Map<String, Object> row(final Object... keysAndValues) {
            final var row = new HashMap<String, Object>();

            for (int i = 0; i < keysAndValues.length; i += 2) {
                row.put((String) keysAndValues[i], withoutNulls(keysAndValues[i + 1]));
            }

            return row;
        }

        private static Object withoutNulls(final Object value) {
            if (value instanceof List<?> list && list.contains(null)) {
                return list.stream().map(item -> item == null ? "" : item).toList();
            }

            return value;
        }
    }
}
