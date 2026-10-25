import { createFileRoute } from "@tanstack/react-router";
import { PhoneKeyApp } from "@/components/phonekey/PhoneKeyApp";

export const Route = createFileRoute("/")({ component: Home });

function Home() {
  return <PhoneKeyApp />;
}
