package dev.graphnous.enhancer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WrittenUrlTest {

    @Test
    void takesTheTextOfStrings() {
        assertThat(WrittenUrl.of("\"/api/orders\"")).isEqualTo("/api/orders");
        assertThat(WrittenUrl.of("'/api/orders'")).isEqualTo("/api/orders");
        assertThat(WrittenUrl.of("`/api/orders`")).isEqualTo("/api/orders");
    }

    @Test
    void writesTheExpressionsOfTemplatesAsVariables() {
        assertThat(WrittenUrl.of("`/api/projects/${projectId}/scans/${scan.id}`"))
            .isEqualTo("/api/projects/{projectId}/scans/{scan.id}");
        assertThat(WrittenUrl.of("`${baseUrl}/api/orders?status=${status}`"))
            .isEqualTo("{baseUrl}/api/orders?status={status}");
    }

    @Test
    void writesTheVariablePartsOfConcatenationsAsVariables() {
        assertThat(WrittenUrl.of("\"/api/orders/\" + order.id + \"/lines\""))
            .isEqualTo("/api/orders/{order.id}/lines");
        assertThat(WrittenUrl.of("API + `/orders/${id}`")).isEqualTo("{API}/orders/{id}");
    }

    @Test
    void writesAnyOtherExpressionAsOneVariable() {
        assertThat(WrittenUrl.of("url")).isEqualTo("{url}");
        assertThat(WrittenUrl.of("fetchParams.url")).isEqualTo("{fetchParams.url}");
        assertThat(WrittenUrl.of("buildUrl(\"a\", b + c)")).isEqualTo("{buildUrl(\"a\", b + c)}");
    }

    @Test
    void namesVariablesWithoutTheirEncoding() {
        assertThat(WrittenUrl.of("`/api/orders/${encodeURIComponent(id)}`")).isEqualTo("/api/orders/{id}");
        assertThat(WrittenUrl.of("\"/api/orders/\" + String(id)")).isEqualTo("/api/orders/{id}");
        assertThat(WrittenUrl.of("`/api/page/${page.toString()}`")).isEqualTo("/api/page/{page}");
    }

    @Test
    void readsNestedBracesAndQuotesInTemplates() {
        assertThat(WrittenUrl.of("`/api/${map[\"}\"]}/x`")).isEqualTo("/api/{map[\"}\"]}/x");
        assertThat(WrittenUrl.of("`/api/${a ? `x${b}` : c}`")).isEqualTo("/api/{a ? `x${b}` : c}");
    }

    @Test
    void takesTheUrlAConditionGivesWhenItGivesNoneOtherwise() {
        assertThat(WrittenUrl.of("projectId ? `/api/projects/${encodeURIComponent(projectId)}/stats` : null"))
            .isEqualTo("/api/projects/{projectId}/stats");
        assertThat(WrittenUrl.of("enabled ? \"/api/enhancers\" : undefined")).isEqualTo("/api/enhancers");
        assertThat(WrittenUrl.of("!id ? null : `/api/orders/${id}`")).isEqualTo("/api/orders/{id}");
        assertThat(WrittenUrl.of("a && b ? `/x/${a ?? b}` : ''")).isEqualTo("/x/{a ?? b}");
        assertThat(WrittenUrl.of("enabled ? path : null")).isEqualTo("{path}");
    }

    @Test
    void writesAConditionOfTwoUrlsAsOneVariable() {
        assertThat(WrittenUrl.of("admin ? \"/admin\" : \"/home\"")).isEqualTo("{admin ? \"/admin\" : \"/home\"}");
        assertThat(WrittenUrl.of("a ? b ? \"/x\" : null : \"/y\"")).isEqualTo("{a ? b ? \"/x\" : null : \"/y\"}");
        assertThat(WrittenUrl.of("config?.url ?? \"/api\"")).isEqualTo("{config?.url ?? \"/api\"}");
    }

    @Test
    void keepsEscapedCharacters() {
        assertThat(WrittenUrl.of("'/api/it\\'s'")).isEqualTo("/api/it's");
        assertThat(WrittenUrl.of("`/api/\\${literal}`")).isEqualTo("/api/${literal}");
    }
}
