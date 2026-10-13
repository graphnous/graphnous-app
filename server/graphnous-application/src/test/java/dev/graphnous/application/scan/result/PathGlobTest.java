package dev.graphnous.application.scan.result;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathGlobTest {

    @Test
    void fitsANameInAnyFolderWithoutASlash() {
        final var specs = PathGlob.of("*.spec.ts");

        assertThat(specs.matches("order.spec.ts")).isTrue();
        assertThat(specs.matches("src/app/orders/order.spec.ts")).isTrue();
        assertThat(specs.matches("src/app/orders/order.ts")).isFalse();
        assertThat(specs.matches("src/app/orders/order.spec.tsx")).isFalse();
    }

    @Test
    void fitsEverythingBelowAFolder() {
        final var generated = PathGlob.of("generated");

        assertThat(generated.matches("src/generated/api/Order.ts")).isTrue();
        assertThat(generated.matches("generated/Order.ts")).isTrue();
        // A file of that name too, as in a .gitignore
        assertThat(generated.matches("src/generated")).isTrue();
        assertThat(generated.matches("src/generated-sources/Order.ts")).isFalse();
    }

    @Test
    void fitsOnlyFoldersWithASlashAtTheEnd() {
        final var build = PathGlob.of("build/");

        assertThat(build.matches("web/build/main.js")).isTrue();
        assertThat(build.matches("scripts/build")).isFalse();
    }

    @Test
    void fitsThePathFromTheRootWithASlash() {
        final var generated = PathGlob.of("src/generated");

        assertThat(generated.matches("src/generated/Order.ts")).isTrue();
        assertThat(generated.matches("web/src/generated/Order.ts")).isFalse();

        final var root = PathGlob.of("/docs");

        assertThat(root.matches("docs/intro.md")).isTrue();
        assertThat(root.matches("web/docs/intro.md")).isFalse();
    }

    @Test
    void takesTwoStarsForAnyNumberOfFolders() {
        final var stories = PathGlob.of("src/**/*.stories.tsx");

        assertThat(stories.matches("src/Button.stories.tsx")).isTrue();
        assertThat(stories.matches("src/components/data/Button.stories.tsx")).isTrue();
        assertThat(stories.matches("lib/Button.stories.tsx")).isFalse();

        final var tests = PathGlob.of("**/test/**");

        assertThat(tests.matches("test/OrderTest.java")).isTrue();
        assertThat(tests.matches("backend/src/test/java/OrderTest.java")).isTrue();
        assertThat(tests.matches("backend/src/main/java/Order.java")).isFalse();
    }

    @Test
    void keepsAStarWithinOneName() {
        final var direct = PathGlob.of("src/*.ts");

        assertThat(direct.matches("src/main.ts")).isTrue();
        assertThat(direct.matches("src/app/main.ts")).isFalse();
        assertThat(PathGlob.of("src/?.ts").matches("src/a.ts")).isTrue();
        assertThat(PathGlob.of("src/?.ts").matches("src/ab.ts")).isFalse();
    }

    @Test
    void takesEitherOfTheAlternativesAndCharacters() {
        final var tests = PathGlob.of("*.{spec,test}.{ts,tsx}");

        assertThat(tests.matches("src/order.spec.ts")).isTrue();
        assertThat(tests.matches("src/order.test.tsx")).isTrue();
        assertThat(tests.matches("src/order.stories.tsx")).isFalse();

        assertThat(PathGlob.of("v[12]/**").matches("v1/api.ts")).isTrue();
        assertThat(PathGlob.of("v[12]/**").matches("v3/api.ts")).isFalse();
        assertThat(PathGlob.of("v[!12]/**").matches("v3/api.ts")).isTrue();
    }

    @Test
    void takesWhatIsNotAPatternAsItIs() {
        assertThat(PathGlob.of("src/app/(shop)/page.tsx").matches("src/app/(shop)/page.tsx")).isTrue();
        assertThat(PathGlob.of("a.b").matches("axb")).isFalse();
        assertThat(PathGlob.of("src/a+b.ts").matches("src/a+b.ts")).isTrue();
    }

    @Test
    void readsPathsHoweverTheyAreWritten() {
        final var specs = PathGlob.of("src/**/*.spec.ts");

        assertThat(specs.matches("./src/app/order.spec.ts")).isTrue();
        assertThat(specs.matches("src\\app\\order.spec.ts")).isTrue();
        assertThat(specs.matches(null)).isFalse();
    }

    @Test
    void refusesWhatIsNotWellFormed() {
        for (final var pattern : new String[] {" ", "/", "src/{a,b", "src/a}", "src/[ab", "x".repeat(PathGlob.MAX_LENGTH + 1)}) {
            assertThatThrownBy(() -> PathGlob.of(pattern)).isInstanceOf(IllegalArgumentException.class);
        }

        assertThatThrownBy(() -> PathGlob.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leavesBlankPatternsOut() {
        assertThat(PathGlob.all(List.of("*.spec.ts", " ", "generated/")))
            .extracting(PathGlob::pattern)
            .containsExactly("*.spec.ts", "generated/");
    }
}
