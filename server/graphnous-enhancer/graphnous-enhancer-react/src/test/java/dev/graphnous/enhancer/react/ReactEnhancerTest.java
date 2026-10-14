package dev.graphnous.enhancer.react;

import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.Field;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.core.model.TypeRef;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReactEnhancerTest {

    private static final String PATH = "src/components/OrderList.tsx";
    private static final String FILE = "web|.|file:" + PATH;

    private final ReactEnhancer enhancer = new ReactEnhancer();

    @Test
    void enhancesTypeScript() {
        assertThat(enhancer.forLanguage()).isEqualTo(ScanTarget.Language.TYPESCRIPT);
    }

    @Test
    void namesItsEnhancements() {
        final var enhancements = enhancer.enhance(scanResult(file(PATH)));

        assertThat(enhancer.name()).isEqualTo("react");
        assertThat(enhancer.version()).isEqualTo("1.0.0");
        assertThat(enhancements.name()).isEqualTo("react");
        assertThat(enhancements.version()).isEqualTo("1.0.0");
    }

    @Test
    void addsAComponentForEachFunctionNamedAsOne() {
        final var file = file(PATH);
        file.getFunctions().addAll(List.of(
            function("OrderList"),
            function("formatTotal"),
            function("OrderRow")
        ));

        final var orderList = FILE + "|function:src/components/OrderList:OrderList";
        final var orderRow = FILE + "|function:src/components/OrderList:OrderRow";

        assertThat(nodes(file)).containsExactly(
            new Node(orderList + "|component", orderList, List.of("Component"), Map.of("name", "OrderList", "kind", "function")),
            new Node(orderRow + "|component", orderRow, List.of("Component"), Map.of("name", "OrderRow", "kind", "function"))
        );
    }

    @Test
    void addsAComponentForAClassThatExtendsReactsComponent() {
        final var file = file("src/components/Legacy.jsx");
        file.getClasses().addAll(List.of(
            type("Legacy", "React.Component<Props, State>"),
            type("Pure", "PureComponent"),
            type("Store", "EventEmitter"),
            type("Plain")
        ));

        final var legacy = "web|.|class:src/components/Legacy:Legacy";

        assertThat(nodes(file)).first().isEqualTo(
            new Node(legacy + "|component", legacy, List.of("Component"), Map.of("name", "Legacy", "kind", "class"))
        );
        assertThat(nodes(file)).extracting(node -> node.metadata().get("name")).containsExactly("Legacy", "Pure");
    }

    @Test
    void addsAComponentForAVariableOfOneOfReactsComponentTypes() {
        final var file = file(PATH);
        file.getVariables().addAll(List.of(
            variable("OrderList", "MemoExoticComponent<Props>"),
            variable("OrderInput", "React.ForwardRefExoticComponent<Props & RefAttributes<HTMLInputElement>>"),
            variable("OrderChart", "LazyExoticComponent<typeof Chart>"),
            variable("OrderRow", "FC<Props>"),
            // Not named as a component, of another type, and of none its source says
            variable("orderRow", "FC<Props>"),
            variable("Orders", "Order[]"),
            variable("Total", null)
        ));

        final var orderList = FILE + "|variable:OrderList";

        assertThat(nodes(file)).first().isEqualTo(
            new Node(orderList + "|component", orderList, List.of("Component"), Map.of("name", "OrderList", "kind", "memo"))
        );
        assertThat(nodes(file))
            .extracting(node -> node.metadata().get("name"), node -> node.metadata().get("kind"))
            .containsExactly(
                org.assertj.core.api.Assertions.tuple("OrderList", "memo"),
                org.assertj.core.api.Assertions.tuple("OrderInput", "forwardRef"),
                org.assertj.core.api.Assertions.tuple("OrderChart", "lazy"),
                org.assertj.core.api.Assertions.tuple("OrderRow", "function")
            );
    }

    @Test
    void rendersNothingFromOrToAComponentThatIsAVariable() {
        final var page = function("Page");
        // Not a call the scanner resolves to a component, as OrderList is a variable
        page.getCalls().add(call("src/components/OrderList:OrderList", Call.Kind.METHOD));

        final var file = file(PATH);
        file.getFunctions().add(page);
        file.getVariables().add(variable("OrderList", "MemoExoticComponent<Props>"));

        final var enhancement = enhancer.enhance(scanResult(file)).enhancements().getFirst();

        assertThat(enhancement.nodes()).hasSize(2);
        assertThat(enhancement.relationships()).isEmpty();
    }

    @Test
    void findsClassComponentsNestedInOtherClasses() {
        final var outer = type("Outer");
        outer.getClasses().add(type("Inner", "Component"));

        final var file = file(PATH);
        file.getClasses().add(outer);

        assertThat(nodes(file)).extracting(Node::sourceId).containsExactly("web|.|class:src/components/Legacy:Inner");
    }

    @Test
    void enhancesOnlyFilesThatCanHoldJsx() {
        final var typescript = file("src/lib/Orders.ts");
        typescript.getFunctions().add(function("Orders"));

        final var javascript = file("src/lib/Orders.js");
        javascript.getClasses().add(type("Orders", "Component"));

        assertThat(enhancer.enhance(scanResult(typescript, javascript)).enhancements()).isEmpty();
    }

    @Test
    void addsNothingForAFileWithoutComponents() {
        final var file = file(PATH);
        file.getFunctions().add(function("useOrders"));

        assertThat(enhancer.enhance(scanResult(file)).enhancements()).isEmpty();
    }

    @Test
    void addsTheComponentsOfEachFileOnTheirOwn() {
        final var list = file(PATH);
        list.getFunctions().add(function("OrderList"));

        final var page = file("src/app/page.jsx");
        page.getFunctions().add(function("Page"));

        assertThat(enhancer.enhance(scanResult(list, page)).enhancements()).hasSize(2);
    }

    @Test
    void linksAFunctionComponentToTheComponentsItRenders() {
        final var orderList = function("OrderList");
        orderList.getCalls().addAll(List.of(
            call("src/components/OrderList:OrderRow", Call.Kind.METHOD),
            call("src/components/OrderList:formatTotal", Call.Kind.METHOD),
            call("src/components/Legacy:Legacy.constructor", Call.Kind.CONSTRUCTOR),
            // Rendered twice, linked once
            call("src/components/OrderList:OrderRow", Call.Kind.METHOD)
        ));

        final var list = file(PATH);
        list.getFunctions().addAll(List.of(orderList, function("OrderRow"), function("formatTotal")));

        final var legacy = file("src/components/Legacy.tsx");
        legacy.getClasses().add(type("Legacy", "Component"));

        final var enhancements = enhancer.enhance(scanResult(list, legacy)).enhancements();
        final var source = FILE + "|function:src/components/OrderList:OrderList|component";

        assertThat(enhancements.getFirst().relationships()).containsExactly(
            new Relationship(source, FILE + "|function:src/components/OrderList:OrderRow|component", "RENDERS"),
            new Relationship(source, "web|.|class:src/components/Legacy:Legacy|component", "RENDERS")
        );
        assertThat(enhancements.getLast().relationships()).isEmpty();
    }

    @Test
    void linksAClassComponentToWhatItsMethodsRender() {
        final var render = new Method();
        render.setName("render");
        render.setKind(Method.Kind.METHOD);
        render.getCalls().add(call("src/components/OrderList:OrderRow", Call.Kind.METHOD));

        final var legacy = type("Legacy", "Component");
        legacy.getMethods().add(render);

        final var file = file(PATH);
        file.getFunctions().add(function("OrderRow"));
        file.getClasses().add(legacy);

        assertThat(enhancer.enhance(scanResult(file)).enhancements().getFirst().relationships()).containsExactly(
            new Relationship(
                "web|.|class:src/components/Legacy:Legacy|component",
                FILE + "|function:src/components/OrderList:OrderRow|component",
                "RENDERS"
            )
        );
    }

    @Test
    void doesNotLinkCallsTheScannerDidNotResolve() {
        final var unresolved = call("src/components/OrderList:OrderRow", Call.Kind.METHOD);
        unresolved.setResolved(false);

        final var orderList = function("OrderList");
        orderList.getCalls().add(unresolved);

        final var file = file(PATH);
        file.getFunctions().addAll(List.of(orderList, function("OrderRow")));

        assertThat(enhancer.enhance(scanResult(file)).enhancements().getFirst().relationships()).isEmpty();
    }

    private static Call call(final String target, final Call.Kind kind) {
        final var call = new Call();
        call.setTarget(target);
        call.setKind(kind);

        return call;
    }

    private List<Node> nodes(final File file) {
        return enhancer.enhance(scanResult(file)).enhancements().getFirst().nodes();
    }

    private static Method function(final String name) {
        final var function = new Method();
        function.setName(name);
        function.setQualifiedName("src/components/OrderList:" + name);
        function.setKind(Method.Kind.FUNCTION);

        return function;
    }

    private static Field variable(final String name, final String type) {
        final var variable = new Field();
        variable.setName(name);
        variable.setQualifiedName("src/components/OrderList:" + name);

        if (type != null) {
            final var reference = new TypeRef();
            reference.setName(type);
            variable.setType(reference);
        }

        return variable;
    }

    private static Class type(final String name, final String... superClasses) {
        final var type = new Class();
        type.setName(name);
        type.setQualifiedName("src/components/Legacy:" + name);
        type.setKind(Class.Kind.CLASS);

        for (final var superClass : superClasses) {
            final var reference = new TypeRef();
            reference.setName(superClass);
            type.getSuperClasses().add(reference);
        }

        return type;
    }

    private static File file(final String path) {
        final var file = new File();
        file.setPath(path);
        file.setLanguage("typescript");

        return file;
    }

    private static ScanResult scanResult(final File... files) {
        final var module = new Module();
        module.setPath(".");
        module.getFiles().addAll(List.of(files));

        final var target = new ScanTarget();
        target.setPath("web");
        target.setLanguage(ScanTarget.Language.TYPESCRIPT);

        final var result = new ScanResult();
        result.setTarget(target);
        result.getModules().add(module);

        return result;
    }
}
