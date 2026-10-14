package dev.graphnous.enhancer.fetch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FetchUrlTest {

    @Test
    void takesTheTextOfStrings() {
        assertThat(FetchUrl.of("\"/api/orders\"")).isEqualTo("/api/orders");
        assertThat(FetchUrl.of("'/api/orders'")).isEqualTo("/api/orders");
        assertThat(FetchUrl.of("`/api/orders`")).isEqualTo("/api/orders");
    }

    @Test
    void writesTheExpressionsOfTemplatesAsVariables() {
        assertThat(FetchUrl.of("`/api/projects/${projectId}/scans/${scan.id}`"))
            .isEqualTo("/api/projects/{projectId}/scans/{scan.id}");
        assertThat(FetchUrl.of("`${baseUrl}/api/orders?status=${status}`"))
            .isEqualTo("{baseUrl}/api/orders?status={status}");
    }

    @Test
    void writesTheVariablePartsOfConcatenationsAsVariables() {
        assertThat(FetchUrl.of("\"/api/orders/\" + order.id + \"/lines\""))
            .isEqualTo("/api/orders/{order.id}/lines");
        assertThat(FetchUrl.of("API + `/orders/${id}`")).isEqualTo("{API}/orders/{id}");
    }

    @Test
    void writesAnyOtherExpressionAsOneVariable() {
        assertThat(FetchUrl.of("url")).isEqualTo("{url}");
        assertThat(FetchUrl.of("fetchParams.url")).isEqualTo("{fetchParams.url}");
        assertThat(FetchUrl.of("buildUrl(\"a\", b + c)")).isEqualTo("{buildUrl(\"a\", b + c)}");
    }

    @Test
    void namesVariablesWithoutTheirEncoding() {
        assertThat(FetchUrl.of("`/api/orders/${encodeURIComponent(id)}`")).isEqualTo("/api/orders/{id}");
        assertThat(FetchUrl.of("\"/api/orders/\" + String(id)")).isEqualTo("/api/orders/{id}");
        assertThat(FetchUrl.of("`/api/page/${page.toString()}`")).isEqualTo("/api/page/{page}");
    }

    @Test
    void readsNestedBracesAndQuotesInTemplates() {
        assertThat(FetchUrl.of("`/api/${map[\"}\"]}/x`")).isEqualTo("/api/{map[\"}\"]}/x");
        assertThat(FetchUrl.of("`/api/${a ? `x${b}` : c}`")).isEqualTo("/api/{a ? `x${b}` : c}");
    }

    @Test
    void keepsEscapedCharacters() {
        assertThat(FetchUrl.of("'/api/it\\'s'")).isEqualTo("/api/it's");
        assertThat(FetchUrl.of("`/api/\\${literal}`")).isEqualTo("/api/${literal}");
    }
}
