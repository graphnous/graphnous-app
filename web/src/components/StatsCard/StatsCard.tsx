import { Card, CardBody, Text } from "@graphnous/theme";
import { HTMLAttributes } from "react";

export interface StatsCardProps extends HTMLAttributes<HTMLDivElement> {
    title: string;
    stat: string | number;
    subtitle?: string;
}

export function StatsCard({ title, stat, subtitle, ...props }: StatsCardProps) {
    return (
        <Card {...props}>
            <CardBody>
                <Text as="span">
                    {title}
                </Text>
                <Text as="p" size="lg">
                    {stat}
                </Text>
            </CardBody>
        </Card>
    )
}