type Capability =
    'SCANNING' |
    'PRIVATE_REPOSITORIES' |
    'ARCHITECTURE_ANALYSIS' |
    'ARCHITECTURE_RULES' |
    'ARCHITECTURE_VIOLATIONS' |
    'SCAN_COMPARISON' |
    'CROSS_PROJECT_ANALYSIS' |
    'CUSTOM_ENHANCERS' |
    'CUSTOM_RULESETS' |
    'AI' |
    'BYOK_AI';

export interface Capabilities {
    authorization: {
        enabled: boolean;
    }
    capabilities: Capability[];
}