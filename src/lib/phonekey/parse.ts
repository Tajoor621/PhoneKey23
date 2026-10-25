export type Protocol = "WIREGUARD" | "XRAY" | "SSTP";

export interface VpnProfile {
  id: string;
  name: string;
  protocol: Protocol;
  rawConfig: string;
  endpoint: string | null;
  address: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface WireGuardFields {
  privateKey: string;
  address: string;
  dns: string;
  mtu: string;
  publicKey: string;
  presharedKey: string;
  endpoint: string;
  allowedIps: string;
  keepalive: string;
}

export const EMPTY_FIELDS: WireGuardFields = {
  privateKey: "",
  address: "",
  dns: "",
  mtu: "",
  publicKey: "",
  presharedKey: "",
  endpoint: "",
  allowedIps: "0.0.0.0/0, ::/0",
  keepalive: "25",
};

export const MAX_BYTES = 1_000_000;

export const EXAMPLE_WIREGUARD = `[Interface]
# Example only. 203.0.113.1 is documentation space, not a PhoneKey server.
PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
Address = 10.0.0.2/32
DNS = 1.1.1.1

[Peer]
PublicKey = HIgo9xNzJMWLKASShiTqIybxZ0U3wGLiUeJ1PKf8ykw=
AllowedIPs = 0.0.0.0/0, ::/0
Endpoint = 203.0.113.1:51820
PersistentKeepalive = 25
`;

const PROTOCOL_LABEL: Record<Protocol, string> = {
  WIREGUARD: "WireGuard",
  XRAY: "Xray",
  SSTP: "SSTP",
};

export function protocolLabel(protocol: Protocol): string {
  return PROTOCOL_LABEL[protocol];
}

export function isPlausibleKey(value: string): boolean {
  const trimmed = value.trim();
  if (trimmed.length < 42 || trimmed.length > 44) return false;
  try {
    const binary = atob(trimmed);
    return binary.length === 32;
  } catch {
    return false;
  }
}

export function generateWireGuard(name: string, fields: WireGuardFields): VpnProfile {
  const privateKey = fields.privateKey.trim();
  const address = fields.address.trim();
  const publicKey = fields.publicKey.trim();
  const allowed = fields.allowedIps.trim();
  const endpoint = fields.endpoint.trim();
  if (!name.trim()) throw new Error("Name the profile.");
  if (!isPlausibleKey(privateKey)) throw new Error("Private key must be a 32-byte WireGuard key.");
  if (!address) throw new Error("Address is required, for example 10.0.0.2/32.");
  if (!isPlausibleKey(publicKey)) throw new Error("Peer public key must be a 32-byte WireGuard key.");
  if (!allowed) throw new Error("Allowed IPs are required.");
  if (endpoint && !endpointLooksValid(endpoint)) throw new Error("Endpoint should look like host:51820.");
  if (fields.presharedKey.trim() && !isPlausibleKey(fields.presharedKey)) {
    throw new Error("Preshared key must be a 32-byte key, or left empty.");
  }
  const lines = ["[Interface]", `PrivateKey = ${privateKey}`, `Address = ${address}`];
  if (fields.dns.trim()) lines.push(`DNS = ${fields.dns.trim()}`);
  if (fields.mtu.trim()) lines.push(`MTU = ${fields.mtu.trim()}`);
  lines.push("", "[Peer]", `PublicKey = ${publicKey}`);
  if (fields.presharedKey.trim()) lines.push(`PresharedKey = ${fields.presharedKey.trim()}`);
  lines.push(`AllowedIPs = ${allowed}`);
  if (endpoint) lines.push(`Endpoint = ${endpoint}`);
  if (fields.keepalive.trim()) lines.push(`PersistentKeepalive = ${fields.keepalive.trim()}`);
  return makeProfile({
    name: name.trim(),
    protocol: "WIREGUARD",
    rawConfig: `${lines.join("\n")}\n`,
    endpoint: endpoint || null,
    address,
  });
}

export function importConfig(text: string, suggestedName?: string | null): VpnProfile {
  const raw = text.trim().replace(/^\uFEFF/, "");
  if (!raw) throw new Error("That file was empty.");
  if (new TextEncoder().encode(raw).length > MAX_BYTES) throw new Error("Config is larger than 1 MB.");
  if (raw.startsWith("{")) return importXray(raw, suggestedName);
  if (/\[Interface\]/i.test(raw)) return importWireGuard(raw, suggestedName);
  if (isSstp(raw)) return importSstp(raw, suggestedName);
  throw new Error("Unrecognized config. Use a WireGuard .conf, Xray JSON, or an SSTP profile.");
}

function importWireGuard(raw: string, suggestedName?: string | null): VpnProfile {
  const sections = parseSections(raw);
  const iface = sections.find((section) => section.name.toLowerCase() === "interface");
  const peer = sections.find((section) => section.name.toLowerCase() === "peer");
  if (!iface) throw new Error("WireGuard config is missing [Interface].");
  if (!peer) throw new Error("WireGuard config is missing [Peer].");
  const privateKey = iface.value("PrivateKey") ?? "";
  const address = iface.value("Address") ?? "";
  const publicKey = peer.value("PublicKey") ?? "";
  const allowed = peer.value("AllowedIPs") ?? "";
  const endpoint = peer.value("Endpoint") ?? null;
  if (!isPlausibleKey(privateKey)) throw new Error("Interface private key is missing or not a WireGuard key.");
  if (!address.trim()) throw new Error("Interface address is missing.");
  if (!isPlausibleKey(publicKey)) throw new Error("Peer public key is missing or not a WireGuard key.");
  if (!allowed.trim()) throw new Error("Peer AllowedIPs is missing.");
  const host = endpoint?.split(":")[0]?.trim();
  const name = cleanName(suggestedName) ?? (host || "WireGuard");
  return makeProfile({
    name,
    protocol: "WIREGUARD",
    rawConfig: `${raw.trim()}\n`,
    endpoint,
    address,
  });
}

function importXray(raw: string, suggestedName?: string | null): VpnProfile {
  let root: Record<string, unknown>;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("not an object");
    }
    root = parsed as Record<string, unknown>;
  } catch {
    throw new Error("Xray config is not valid JSON.");
  }
  const looksLikeXray = "outbounds" in root || "inbounds" in root || "protocol" in root;
  if (!looksLikeXray) throw new Error("JSON has no Xray outbounds, inbounds, or protocol.");
  const remarks = typeof root.remarks === "string" ? root.remarks : null;
  const name = cleanName(suggestedName) ?? (remarks?.trim() || "Xray");
  return makeProfile({
    name,
    protocol: "XRAY",
    rawConfig: `${raw.trim()}\n`,
    endpoint: null,
    address: null,
  });
}

function importSstp(raw: string, suggestedName?: string | null): VpnProfile {
  const endpoint = raw.match(/sstp:\/\/\S+/i)?.[0] ?? null;
  return makeProfile({
    name: cleanName(suggestedName) ?? "SSTP",
    protocol: "SSTP",
    rawConfig: `${raw.trim()}\n`,
    endpoint,
    address: null,
  });
}

function isSstp(raw: string): boolean {
  const lower = raw.toLowerCase();
  return lower.includes("sstp://") || lower.includes("<sstp") || lower.includes("protocol = sstp") || lower.includes("protocol=sstp");
}

function endpointLooksValid(endpoint: string): boolean {
  const port = endpoint.slice(endpoint.lastIndexOf(":") + 1);
  const host = endpoint.slice(0, endpoint.lastIndexOf(":"));
  const portNumber = Number(port);
  return Boolean(host) && Number.isInteger(portNumber) && portNumber >= 1 && portNumber <= 65535;
}

function cleanName(value?: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

function makeProfile(input: Omit<VpnProfile, "id" | "createdAt" | "updatedAt">): VpnProfile {
  const now = Date.now();
  return { ...input, id: crypto.randomUUID(), createdAt: now, updatedAt: now };
}

interface Section {
  name: string;
  entries: Array<[string, string]>;
  value(key: string): string | null;
}

function parseSections(text: string): Section[] {
  const sections: Section[] = [];
  let currentName: string | null = null;
  let entries: Array<[string, string]> = [];
  const flush = () => {
    if (!currentName) return;
    const saved = entries;
    sections.push({
      name: currentName,
      entries: saved,
      value(key: string) {
        return saved.find(([entry]) => entry.toLowerCase() === key.toLowerCase())?.[1] ?? null;
      },
    });
    entries = [];
  };
  for (const line of text.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#") || trimmed.startsWith(";")) continue;
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      flush();
      currentName = trimmed.slice(1, -1).trim();
      continue;
    }
    const eq = trimmed.indexOf("=");
    if (eq <= 0 || !currentName) continue;
    entries.push([trimmed.slice(0, eq).trim(), trimmed.slice(eq + 1).trim()]);
  }
  flush();
  return sections;
}

export function formatBytes(value: number): string {
  const units = ["B", "KB", "MB", "GB"];
  let amount = Math.max(0, value);
  let index = 0;
  while (amount >= 1024 && index < units.length - 1) {
    amount /= 1024;
    index += 1;
  }
  if (index === 0) return `${Math.round(amount)} ${units[index]}`;
  return `${amount.toFixed(1)} ${units[index]}`;
}

export function formatRate(bps: number): string {
  const value = Math.max(0, bps);
  if (value < 1024) return `${value.toFixed(0)} B/s`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB/s`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB/s`;
}

export function formatDuration(sinceEpochMs: number | null, now: number): string {
  if (sinceEpochMs == null) return "00:00";
  const seconds = Math.max(0, Math.floor((now - sinceEpochMs) / 1000));
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}
