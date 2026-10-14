"use client";

import {
    Container,
    PageHeader,
    Stack,
    Tab,
    TabList,
    TabPanel,
    Tabs,
} from "@graphnous/theme";

import { SystemLibraries } from "@/components/SystemLibraries/SystemLibraries";
import { SystemDependencies } from "@/components/SystemDependencies/SystemDependencies";
import { useListProjects } from "@/lib/hooks/project/useListProjects";
import { useSystemStore } from "@/lib/store/systemStore";
import { breadcrumbs } from "@/lib/breadcrumbs";

export default function DependenciesPage() {
    const selectedSystem = useSystemStore(
        (state) => state.selectedSystem,
    );

    const { projects } = useListProjects();

    return (
        <Container>
            <Stack gap={8}>
                <PageHeader
                    title="Dependencies"
                    breadcrumbs={breadcrumbs(selectedSystem, { label: "Dependencies" })}
                    description={`Explore dependencies across repositories, modules, services, APIs, and infrastructure.`}
                />
                {selectedSystem && (
                    // Starts over for another system, as its search and page are of its own dependencies
                    <Tabs key={selectedSystem.id} defaultValue="libraries">
                        <TabList label="Dependencies">
                            <Tab value="libraries">Libraries</Tab>
                            <Tab value="modules">By module</Tab>
                        </TabList>
                        <TabPanel value="libraries">
                            <SystemLibraries systemId={selectedSystem.id} projects={projects} />
                        </TabPanel>
                        <TabPanel value="modules">
                            <div className="pt-4">
                                <SystemDependencies systemId={selectedSystem.id} projects={projects} />
                            </div>
                        </TabPanel>
                    </Tabs>
                )}
            </Stack>
        </Container>
    );
}
