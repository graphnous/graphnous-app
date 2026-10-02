import { Alert, CodeBlock, CopyButton } from "@graphnous/theme";

import type { ScanStep, ScanStepType } from "@/types";

const titles: Record<ScanStepType, string> = {
  CHECKOUT: "Checking out the repository failed",
  PLAN: "Finding the targets to scan failed",
  SCAN: "Scanning failed",
  STORE: "Storing the results failed",
  ENHANCE_RESULTS: "Enhancing the results failed",
  ENHANCE_SCAN: "Enhancing the scan failed",
};

const enhanceSteps: ScanStepType[] = ["ENHANCE_RESULTS", "ENHANCE_SCAN"];

export type ScanStepErrorProps = {
  step: Pick<ScanStep, "type" | "error">;
  className?: string;
};

/**
 * Why a scan step failed: the step, and its error in full, scrolling when
 * it is long, such as a stack trace, with a button to copy it. A failed
 * enhance step is a warning, as the scan's results were stored. Nothing
 * for a step without an error.
 */
export function ScanStepError({ step, className }: ScanStepErrorProps) {
  if (!step.error) {
    return null;
  }

  const enhance = enhanceSteps.includes(step.type);

  return (
    <Alert tone={enhance ? "warning" : "error"} title={titles[step.type]} className={className}>
      <div className="flex flex-col gap-2">
        {enhance && <p>The scan&apos;s results were stored; the scan is complete.</p>}
        <div className="relative">
          <div
            role="region"
            aria-label="Error details"
            tabIndex={0}
            className="max-h-64 overflow-y-auto rounded-card"
          >
            <CodeBlock wrap className="pr-12 text-xs">
              {step.error}
            </CodeBlock>
          </div>
          <CopyButton value={step.error} label="Copy the error" size="sm" className="absolute top-2 right-2" />
        </div>
      </div>
    </Alert>
  );
}
