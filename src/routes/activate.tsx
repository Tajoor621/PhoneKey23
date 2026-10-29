import { createFileRoute, Link } from "@tanstack/react-router";

export const Route = createFileRoute("/activate")({ component: Activate });

function Activate() {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-lg flex-col justify-center bg-canvas px-7 py-10 text-fg">
      <h1 className="font-display text-4xl font-semibold">PhoneKey is free. No ads. No subscriptions.</h1>
      <p className="mt-4 text-lg leading-relaxed text-muted">
        No accounts. Your WireGuard keys stay on this device.
      </p>
      <Link
        to="/"
        className="mt-8 flex h-12 items-center justify-center rounded-2xl bg-brass font-semibold text-brass-ink"
      >
        Open PhoneKey
      </Link>
    </main>
  );
}
