import { i as __toESM } from "../_runtime.mjs";
import { q as require_react, x as require_jsx_runtime } from "../_libs/@tanstack/react-router+[...].mjs";
import { a as QrCode, c as Pencil, d as FileUp, f as Download, i as ScanLine, l as KeyRound, n as Trash2, o as Power, p as ArrowLeft, r as Settings, s as Plus, u as ImagePlus } from "../_libs/lucide-react.mjs";
import { t as require_jsQR } from "../_libs/jsqr.mjs";
import { t as require_lib } from "../_libs/qrcode.mjs";
import { n as persist, r as create, t as createJSONStorage } from "../_libs/zustand.mjs";
//#region node_modules/.nitro/vite/services/ssr/assets/routes-B32-mlOf.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_runtime = require_jsx_runtime();
var import_jsQR = /* @__PURE__ */ __toESM(require_jsQR());
var import_lib = /* @__PURE__ */ __toESM(require_lib());
var EMPTY_FIELDS = {
	privateKey: "",
	address: "",
	dns: "",
	mtu: "",
	publicKey: "",
	presharedKey: "",
	endpoint: "",
	allowedIps: "0.0.0.0/0, ::/0",
	keepalive: "25"
};
var EXAMPLE_WIREGUARD = `[Interface]
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
var PROTOCOL_LABEL = {
	WIREGUARD: "WireGuard",
	XRAY: "Xray",
	SSTP: "SSTP"
};
function protocolLabel(protocol) {
	return PROTOCOL_LABEL[protocol];
}
function isPlausibleKey(value) {
	const trimmed = value.trim();
	if (trimmed.length < 42 || trimmed.length > 44) return false;
	try {
		return atob(trimmed).length === 32;
	} catch {
		return false;
	}
}
function generateWireGuard(name, fields) {
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
	if (fields.presharedKey.trim() && !isPlausibleKey(fields.presharedKey)) throw new Error("Preshared key must be a 32-byte key, or left empty.");
	const lines = [
		"[Interface]",
		`PrivateKey = ${privateKey}`,
		`Address = ${address}`
	];
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
		address
	});
}
function importConfig(text, suggestedName) {
	const raw = text.trim().replace(/^\uFEFF/, "");
	if (!raw) throw new Error("That file was empty.");
	if (new TextEncoder().encode(raw).length > 1e6) throw new Error("Config is larger than 1 MB.");
	if (raw.startsWith("{")) return importXray(raw, suggestedName);
	if (/\[Interface\]/i.test(raw)) return importWireGuard(raw, suggestedName);
	if (isSstp(raw)) return importSstp(raw, suggestedName);
	throw new Error("Unrecognized config. Use a WireGuard .conf, Xray JSON, or an SSTP profile.");
}
function importWireGuard(raw, suggestedName) {
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
	return makeProfile({
		name: cleanName(suggestedName) ?? (host || "WireGuard"),
		protocol: "WIREGUARD",
		rawConfig: `${raw.trim()}\n`,
		endpoint,
		address
	});
}
function importXray(raw, suggestedName) {
	let root;
	try {
		const parsed = JSON.parse(raw);
		if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("not an object");
		root = parsed;
	} catch {
		throw new Error("Xray config is not valid JSON.");
	}
	if (!("outbounds" in root || "inbounds" in root || "protocol" in root)) throw new Error("JSON has no Xray outbounds, inbounds, or protocol.");
	const remarks = typeof root.remarks === "string" ? root.remarks : null;
	return makeProfile({
		name: cleanName(suggestedName) ?? (remarks?.trim() || "Xray"),
		protocol: "XRAY",
		rawConfig: `${raw.trim()}\n`,
		endpoint: null,
		address: null
	});
}
function importSstp(raw, suggestedName) {
	const endpoint = raw.match(/sstp:\/\/\S+/i)?.[0] ?? null;
	return makeProfile({
		name: cleanName(suggestedName) ?? "SSTP",
		protocol: "SSTP",
		rawConfig: `${raw.trim()}\n`,
		endpoint,
		address: null
	});
}
function isSstp(raw) {
	const lower = raw.toLowerCase();
	return lower.includes("sstp://") || lower.includes("<sstp") || lower.includes("protocol = sstp") || lower.includes("protocol=sstp");
}
function endpointLooksValid(endpoint) {
	const port = endpoint.slice(endpoint.lastIndexOf(":") + 1);
	const host = endpoint.slice(0, endpoint.lastIndexOf(":"));
	const portNumber = Number(port);
	return Boolean(host) && Number.isInteger(portNumber) && portNumber >= 1 && portNumber <= 65535;
}
function cleanName(value) {
	const trimmed = value?.trim();
	return trimmed ? trimmed : null;
}
function makeProfile(input) {
	const now = Date.now();
	return {
		...input,
		id: crypto.randomUUID(),
		createdAt: now,
		updatedAt: now
	};
}
function parseSections(text) {
	const sections = [];
	let currentName = null;
	let entries = [];
	const flush = () => {
		if (!currentName) return;
		const saved = entries;
		sections.push({
			name: currentName,
			entries: saved,
			value(key) {
				return saved.find(([entry]) => entry.toLowerCase() === key.toLowerCase())?.[1] ?? null;
			}
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
function formatBytes(value) {
	const units = [
		"B",
		"KB",
		"MB",
		"GB"
	];
	let amount = Math.max(0, value);
	let index = 0;
	while (amount >= 1024 && index < units.length - 1) {
		amount /= 1024;
		index += 1;
	}
	if (index === 0) return `${Math.round(amount)} ${units[index]}`;
	return `${amount.toFixed(1)} ${units[index]}`;
}
function formatRate(bps) {
	const value = Math.max(0, bps);
	if (value < 1024) return `${value.toFixed(0)} B/s`;
	if (value < 1048576) return `${(value / 1024).toFixed(1)} KB/s`;
	return `${(value / 1048576).toFixed(1)} MB/s`;
}
function formatDuration(sinceEpochMs, now) {
	if (sinceEpochMs == null) return "00:00";
	const seconds = Math.max(0, Math.floor((now - sinceEpochMs) / 1e3));
	const h = Math.floor(seconds / 3600);
	const m = Math.floor(seconds % 3600 / 60);
	const s = seconds % 60;
	const pad = (n) => String(n).padStart(2, "0");
	return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}
var XRAY_MESSAGE = "Xray/V2Ray is not bundled. The JSON stays on this device. A maintained open-source core is not wired in yet.";
var SSTP_MESSAGE = "SSTP is not bundled. The profile stays on this device. A maintained open-source client is not wired in yet.";
function emptySession() {
	return {
		status: "DISCONNECTED",
		profileId: null,
		profileName: null,
		protocol: null,
		sinceEpochMs: null,
		rxBytes: 0,
		txBytes: 0,
		rxSpeedBps: 0,
		txSpeedBps: 0,
		message: null
	};
}
function defaultSettings() {
	return {
		autoConnectOnBoot: false,
		autoConnectOnLaunch: false,
		killSwitch: false,
		splitTunnelEnabled: false,
		splitTunnelPackages: [],
		selectedProfileId: null
	};
}
var memoryStorage = {
	getItem: () => null,
	setItem: () => void 0,
	removeItem: () => void 0
};
var bannerTimer = null;
var connectTimer = null;
var ticker = null;
function stopTicker() {
	if (ticker != null) window.clearInterval(ticker);
	ticker = null;
}
function clearConnectTimer() {
	if (connectTimer != null) window.clearTimeout(connectTimer);
	connectTimer = null;
}
function startTicker() {
	stopTicker();
	let rx = 0;
	let tx = 0;
	ticker = window.setInterval(() => {
		const state = usePhoneKey.getState();
		if (state.session.status !== "CONNECTED") {
			stopTicker();
			return;
		}
		const t = Date.now() / 1e3;
		const rxBps = Math.max(1200, 48e3 + Math.sin(t * .8) * 32e3);
		const txBps = Math.max(400, 14e3 + Math.cos(t * .55) * 8e3);
		rx += rxBps;
		tx += txBps;
		usePhoneKey.setState({ session: {
			...state.session,
			rxBytes: Math.round(rx),
			txBytes: Math.round(tx),
			rxSpeedBps: rxBps,
			txSpeedBps: txBps
		} });
	}, 1e3);
}
var usePhoneKey = create()(persist((set, get) => {
	const note = (message) => {
		if (bannerTimer != null) window.clearTimeout(bannerTimer);
		set({ banner: message });
		bannerTimer = window.setTimeout(() => set({ banner: null }), 4200);
	};
	const remember = (profile) => {
		set((state) => ({
			profiles: [profile, ...state.profiles.filter((item) => item.id !== profile.id)],
			settings: {
				...state.settings,
				selectedProfileId: profile.id
			}
		}));
		note(`Saved ${profile.name}.`);
	};
	return {
		profiles: [],
		settings: defaultSettings(),
		session: emptySession(),
		banner: null,
		dismissBanner: () => set({ banner: null }),
		select: (id) => set((state) => ({ settings: {
			...state.settings,
			selectedProfileId: id
		} })),
		remove: (id) => {
			if (get().session.profileId === id) {
				clearConnectTimer();
				stopTicker();
				set({ session: emptySession() });
			}
			set((state) => ({
				profiles: state.profiles.filter((profile) => profile.id !== id),
				settings: {
					...state.settings,
					selectedProfileId: state.settings.selectedProfileId === id ? null : state.settings.selectedProfileId
				}
			}));
		},
		updateSettings: (patch) => set((state) => ({ settings: {
			...state.settings,
			...patch
		} })),
		importText: (text, suggestedName) => {
			try {
				remember(importConfig(text, suggestedName));
				return true;
			} catch (error) {
				note(error instanceof Error ? error.message : "Could not import that config.");
				return false;
			}
		},
		saveManual: (name, fields) => {
			try {
				remember(generateWireGuard(name, fields));
				return true;
			} catch (error) {
				note(error instanceof Error ? error.message : "Could not save that profile.");
				return false;
			}
		},
		saveRaw: (id, name, raw) => {
			try {
				const parsed = importConfig(raw, name.trim() || null);
				if (!id) {
					remember({
						...parsed,
						name: name.trim() || parsed.name
					});
					return true;
				}
				const existing = get().profiles.find((profile) => profile.id === id);
				const next = {
					...parsed,
					id,
					name: name.trim() || parsed.name,
					createdAt: existing?.createdAt ?? parsed.createdAt,
					updatedAt: Date.now()
				};
				set((state) => ({
					profiles: state.profiles.map((profile) => profile.id === id ? next : profile),
					settings: {
						...state.settings,
						selectedProfileId: id
					}
				}));
				note("Profile saved.");
				return true;
			} catch (error) {
				note(error instanceof Error ? error.message : "Could not save that profile.");
				return false;
			}
		},
		loadExample: () => {
			try {
				remember(importConfig(EXAMPLE_WIREGUARD, "Example"));
				return true;
			} catch (error) {
				note(error instanceof Error ? error.message : "Could not load the example.");
				return false;
			}
		},
		disconnect: () => {
			clearConnectTimer();
			stopTicker();
			set({ session: emptySession() });
		},
		connectSelected: () => {
			const state = get();
			if (state.session.status === "CONNECTED" || state.session.status === "CONNECTING") return;
			const profile = state.profiles.find((item) => item.id === state.settings.selectedProfileId);
			if (!profile) {
				set({ session: {
					...emptySession(),
					status: "ERROR",
					message: "Choose a profile first."
				} });
				note("Choose a profile first.");
				return;
			}
			if (profile.protocol === "XRAY") {
				set({ session: {
					...emptySession(),
					status: "ERROR",
					profileId: profile.id,
					profileName: profile.name,
					protocol: profile.protocol,
					message: XRAY_MESSAGE
				} });
				return;
			}
			if (profile.protocol === "SSTP") {
				set({ session: {
					...emptySession(),
					status: "ERROR",
					profileId: profile.id,
					profileName: profile.name,
					protocol: profile.protocol,
					message: SSTP_MESSAGE
				} });
				return;
			}
			clearConnectTimer();
			set({ session: {
				...emptySession(),
				status: "CONNECTING",
				profileId: profile.id,
				profileName: profile.name,
				protocol: profile.protocol
			} });
			connectTimer = window.setTimeout(() => {
				const latest = get();
				if (latest.session.status !== "CONNECTING" || latest.session.profileId !== profile.id) return;
				const excluded = latest.settings.splitTunnelEnabled ? latest.settings.splitTunnelPackages.length : 0;
				const docs = profile.endpoint?.includes("203.0.113.1");
				const parts = [];
				if (docs) parts.push("203.0.113.1 is documentation space, so a phone would not finish this handshake.");
				if (excluded > 0) parts.push(`On Android this profile would exclude ${excluded} package${excluded === 1 ? "" : "s"}.`);
				if (latest.settings.killSwitch) parts.push("If the tunnel drops, Android retries. This page cannot block other traffic.");
				set({ session: {
					status: "CONNECTED",
					profileId: profile.id,
					profileName: profile.name,
					protocol: profile.protocol,
					sinceEpochMs: Date.now(),
					rxBytes: 0,
					txBytes: 0,
					rxSpeedBps: 0,
					txSpeedBps: 0,
					message: parts.length ? parts.join(" ") : null
				} });
				startTicker();
			}, 800);
		}
	};
}, {
	name: "phonekey",
	skipHydration: true,
	storage: createJSONStorage(() => typeof window === "undefined" ? memoryStorage : localStorage),
	partialize: (state) => ({
		profiles: state.profiles,
		settings: state.settings
	})
}));
function PhoneKeyApp() {
	const [view, setView] = (0, import_react.useState)("home");
	const [editId, setEditId] = (0, import_react.useState)(null);
	const [qrProfile, setQrProfile] = (0, import_react.useState)(null);
	const [pendingDelete, setPendingDelete] = (0, import_react.useState)(null);
	const session = usePhoneKey((state) => state.session);
	const profiles = usePhoneKey((state) => state.profiles);
	const settings = usePhoneKey((state) => state.settings);
	const banner = usePhoneKey((state) => state.banner);
	const selected = profiles.find((profile) => profile.id === settings.selectedProfileId) ?? null;
	const showNav = view === "home" || view === "profiles" || view === "settings";
	(0, import_react.useEffect)(() => {
		let live = true;
		Promise.resolve(usePhoneKey.persist.rehydrate()).then(() => {
			if (!live) return;
			const latest = usePhoneKey.getState();
			if (latest.settings.autoConnectOnLaunch && latest.settings.selectedProfileId && latest.session.status === "DISCONNECTED") latest.connectSelected();
		});
		return () => {
			live = false;
		};
	}, []);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "mx-auto flex min-h-dvh w-full max-w-lg flex-col bg-canvas text-fg",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("main", {
				className: "flex flex-1 flex-col overflow-y-auto",
				children: [
					view === "home" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(HomeScreen, {
						session,
						selected,
						onToggle: () => {
							if (session.status === "CONNECTED" || session.status === "CONNECTING") usePhoneKey.getState().disconnect();
							else if (!selected) setView("profiles");
							else usePhoneKey.getState().connectSelected();
						},
						onChoose: () => setView("profiles")
					}),
					view === "profiles" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ProfilesScreen, {
						profiles,
						selectedId: settings.selectedProfileId,
						onSelect: (id) => usePhoneKey.getState().select(id),
						onEdit: (id) => {
							setEditId(id);
							setView("edit");
						},
						onDelete: setPendingDelete,
						onQr: setQrProfile
					}),
					view === "settings" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(SettingsScreen, { settings }),
					view === "add" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(AddScreen, {
						onBack: () => setView("profiles"),
						onScan: () => setView("scan"),
						onSaved: () => setView("home")
					}),
					view === "edit" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(EditScreen, {
						profile: profiles.find((profile) => profile.id === editId) ?? null,
						onBack: () => setView("profiles")
					}),
					view === "scan" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(QrScanner, {
						onClose: () => setView("add"),
						onText: (text) => {
							const ok = usePhoneKey.getState().importText(text, "Scanned");
							setView(ok ? "home" : "add");
						}
					})
				]
			}),
			banner && showNav && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "border-t border-line bg-card-2 px-5 py-3 text-sm",
				role: "status",
				children: banner
			}),
			view === "profiles" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
				className: "border-t border-line px-5 py-3",
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
					type: "button",
					onClick: () => setView("add"),
					className: "flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-brass font-semibold text-brass-ink",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Plus, {
						className: "size-5",
						"aria-hidden": "true"
					}), "Add profile"]
				})
			}),
			showNav && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("nav", {
				className: "grid grid-cols-3 border-t border-line bg-card",
				"aria-label": "Primary",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(NavButton, {
						label: "Home",
						active: view === "home",
						onClick: () => setView("home"),
						icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Power, { className: "size-5" })
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(NavButton, {
						label: "Profiles",
						active: view === "profiles",
						onClick: () => setView("profiles"),
						icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(KeyRound, { className: "size-5" })
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(NavButton, {
						label: "Settings",
						active: view === "settings",
						onClick: () => setView("settings"),
						icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Settings, { className: "size-5" })
					})
				]
			}),
			qrProfile && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(QrDialog, {
				profile: qrProfile,
				onClose: () => setQrProfile(null)
			}),
			pendingDelete && /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ConfirmDialog, {
				title: `Delete ${pendingDelete.name}?`,
				body: "The keys are removed from this browser. This cannot be undone.",
				confirm: "Delete",
				onConfirm: () => {
					usePhoneKey.getState().remove(pendingDelete.id);
					setPendingDelete(null);
				},
				onClose: () => setPendingDelete(null)
			})
		]
	});
}
function NavButton({ label, active, onClick, icon }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
		type: "button",
		onClick,
		"aria-current": active ? "page" : void 0,
		className: `flex min-h-16 flex-col items-center justify-center gap-1 text-xs font-semibold ${active ? "text-brass" : "text-muted"}`,
		children: [icon, label]
	});
}
function HomeScreen({ session, selected, onToggle, onChoose }) {
	const [now, setNow] = (0, import_react.useState)(() => Date.now());
	const busy = session.status === "CONNECTING";
	const on = session.status === "CONNECTED" || busy;
	(0, import_react.useEffect)(() => {
		if (session.status !== "CONNECTED") return;
		const id = window.setInterval(() => setNow(Date.now()), 1e3);
		return () => window.clearInterval(id);
	}, [session.status, session.sinceEpochMs]);
	const statusLabel = session.status === "CONNECTED" ? "Connected" : session.status === "CONNECTING" ? "Connecting" : session.status === "ERROR" ? "Not connected" : "Off";
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex flex-1 flex-col items-center px-6 py-6",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h1", {
				className: "font-display text-3xl font-semibold",
				children: "PhoneKey"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-1 text-center text-sm text-muted",
				children: "No ads. No subscription."
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-8 text-sm font-semibold text-muted",
				children: statusLabel
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "font-display text-5xl leading-tight",
				children: session.status === "CONNECTED" ? formatDuration(session.sinceEpochMs, now) : "00:00"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "button",
				onClick: onToggle,
				disabled: busy,
				"aria-label": on ? "Disconnect" : "Connect",
				className: `mt-8 grid size-44 place-items-center rounded-full border-2 border-brass disabled:opacity-70 ${on ? "bg-brass text-brass-ink" : "bg-card text-brass"}`,
				children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Power, {
					className: "size-16",
					strokeWidth: 1.6
				})
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-3 text-sm font-semibold text-muted",
				children: on ? "Tap to disconnect" : "Tap to connect"
			}),
			session.message && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: `mt-4 max-w-sm text-center text-sm ${session.status === "ERROR" ? "text-clay" : "text-muted"}`,
				children: session.message
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-4 max-w-sm text-center text-sm text-muted",
				children: "This page stores configs here and does not route traffic. The Android app opens the real tunnel."
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "mt-auto w-full pt-8",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
						type: "button",
						onClick: onChoose,
						className: "w-full rounded-2xl bg-card p-4 text-left",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "block text-xs font-medium text-muted",
								children: "Profile"
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "mt-1 block text-base font-semibold",
								children: selected?.name ?? "None selected"
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "mt-1 block text-sm text-muted",
								children: selected ? `${protocolLabel(selected.protocol)}${selected.endpoint ? ` · ${selected.endpoint}` : ""}` : "Add a WireGuard config to start"
							})
						]
					}),
					session.status === "CONNECTED" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "mt-3 grid grid-cols-2 gap-3",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stat, {
							label: "Down",
							rate: formatRate(session.rxSpeedBps),
							total: formatBytes(session.rxBytes)
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Stat, {
							label: "Up",
							rate: formatRate(session.txSpeedBps),
							total: formatBytes(session.txBytes)
						})]
					}),
					session.status === "CONNECTED" && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "mt-2 text-center text-xs text-muted",
						children: "Rates on this page are simulated."
					})
				]
			})
		]
	});
}
function Stat({ label, rate, total }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "rounded-2xl bg-card p-4",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "text-xs font-medium text-muted",
				children: label
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-1 font-semibold",
				children: rate
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "text-sm text-muted",
				children: total
			})
		]
	});
}
function ProfilesScreen({ profiles, selectedId, onSelect, onEdit, onDelete, onQr }) {
	if (profiles.length === 0) return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex flex-1 flex-col justify-center px-6 py-10",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
			className: "font-display text-3xl font-semibold",
			children: "No profiles yet"
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
			className: "mt-3 text-base leading-relaxed text-muted",
			children: "Add a WireGuard config from a file, a QR code, or by typing the keys. Xray and SSTP can be stored, but those engines are not bundled."
		})]
	});
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("ul", {
		className: "flex flex-col gap-3 px-4 py-4",
		children: profiles.map((profile) => {
			const selected = profile.id === selectedId;
			return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("li", { children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: `rounded-2xl p-4 ${selected ? "bg-brass-soft" : "bg-card"}`,
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
					type: "button",
					onClick: () => onSelect(profile.id),
					className: "w-full text-left",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "block text-xs font-medium text-muted",
							children: protocolLabel(profile.protocol)
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
							className: "mt-1 flex items-center justify-between gap-3",
							children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "font-semibold",
								children: profile.name
							}), selected && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "text-sm font-semibold text-brass",
								children: "In use"
							})]
						}),
						profile.endpoint && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "mt-1 block break-all text-sm text-muted",
							children: profile.endpoint
						})
					]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "mt-2 flex",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconAction, {
							label: "Edit",
							onClick: () => onEdit(profile.id),
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Pencil, { className: "size-5" })
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconAction, {
							label: "Show QR",
							onClick: () => onQr(profile),
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(QrCode, { className: "size-5" })
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)(IconAction, {
							label: "Delete",
							onClick: () => onDelete(profile),
							children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Trash2, { className: "size-5" })
						})
					]
				})]
			}) }, profile.id);
		})
	});
}
function IconAction({ label, onClick, children }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
		type: "button",
		"aria-label": label,
		onClick,
		className: "grid size-11 place-items-center rounded-xl text-fg",
		children
	});
}
function AddScreen({ onBack, onScan, onSaved }) {
	const [mode, setMode] = (0, import_react.useState)("choose");
	const [name, setName] = (0, import_react.useState)("");
	const [fields, setFields] = (0, import_react.useState)(EMPTY_FIELDS);
	const [raw, setRaw] = (0, import_react.useState)("");
	const fileRef = (0, import_react.useRef)(null);
	const banner = usePhoneKey((state) => state.banner);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex flex-col px-5 py-4",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ScreenHead, {
				title: "Add profile",
				onBack: () => {
					if (mode === "choose") onBack();
					else setMode("choose");
				}
			}),
			banner && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mb-3 text-sm text-clay",
				role: "status",
				children: banner
			}),
			mode === "choose" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex flex-col gap-3",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Choice, {
						label: "Import a file",
						icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(FileUp, { className: "size-5" }),
						onClick: () => fileRef.current?.click()
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Choice, {
						label: "Scan a QR code",
						icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ScanLine, { className: "size-5" }),
						onClick: onScan
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Choice, {
						label: "WireGuard form",
						icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(KeyRound, { className: "size-5" }),
						onClick: () => setMode("manual")
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Choice, {
						label: "Paste config",
						icon: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Plus, { className: "size-5" }),
						onClick: () => setMode("paste")
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						onClick: () => {
							if (usePhoneKey.getState().loadExample()) onSaved();
						},
						className: "h-12 rounded-2xl border border-line font-semibold",
						children: "Load the example config"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-sm text-muted",
						children: "The example uses documentation IP 203.0.113.1. It will not open a real tunnel."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
						ref: fileRef,
						type: "file",
						accept: ".conf,.json,.txt,text/plain,application/json",
						className: "sr-only",
						onChange: (event) => {
							const file = event.target.files?.[0];
							event.target.value = "";
							if (!file) return;
							if (file.size > 1e6) {
								usePhoneKey.setState({ banner: "That file is larger than 1 MB." });
								return;
							}
							file.text().then((text) => {
								const stem = file.name.replace(/\.[^.]+$/, "");
								if (usePhoneKey.getState().importText(text, stem)) onSaved();
							});
						}
					})
				]
			}),
			mode === "manual" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("form", {
				className: "flex flex-col gap-2",
				onSubmit: (event) => {
					event.preventDefault();
					if (usePhoneKey.getState().saveManual(name, fields)) onSaved();
				},
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Name",
						value: name,
						onChange: setName
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Private key",
						value: fields.privateKey,
						onChange: (value) => setFields({
							...fields,
							privateKey: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Address",
						value: fields.address,
						onChange: (value) => setFields({
							...fields,
							address: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "DNS",
						value: fields.dns,
						onChange: (value) => setFields({
							...fields,
							dns: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "MTU",
						value: fields.mtu,
						onChange: (value) => setFields({
							...fields,
							mtu: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Peer public key",
						value: fields.publicKey,
						onChange: (value) => setFields({
							...fields,
							publicKey: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Preshared key",
						value: fields.presharedKey,
						onChange: (value) => setFields({
							...fields,
							presharedKey: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Endpoint",
						value: fields.endpoint,
						onChange: (value) => setFields({
							...fields,
							endpoint: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Allowed IPs",
						value: fields.allowedIps,
						onChange: (value) => setFields({
							...fields,
							allowedIps: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Keepalive",
						value: fields.keepalive,
						onChange: (value) => setFields({
							...fields,
							keepalive: value
						})
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "submit",
						className: "mt-2 h-12 rounded-2xl bg-brass font-semibold text-brass-ink",
						children: "Save profile"
					})
				]
			}),
			mode === "paste" && /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("form", {
				className: "flex flex-col gap-3",
				onSubmit: (event) => {
					event.preventDefault();
					if (usePhoneKey.getState().importText(raw, name.trim() || null)) onSaved();
				},
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
						label: "Name (optional)",
						value: name,
						onChange: setName
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
						className: "block",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "mb-1 block text-sm text-muted",
							children: "WireGuard, Xray JSON, or SSTP"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
							value: raw,
							onChange: (event) => setRaw(event.target.value),
							className: "h-56 w-full rounded-xl border border-line bg-card p-3 text-sm"
						})]
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "submit",
						className: "h-12 rounded-2xl bg-brass font-semibold text-brass-ink",
						children: "Import"
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						className: "text-sm font-semibold text-brass",
						onClick: () => setRaw(EXAMPLE_WIREGUARD.trim()),
						children: "Fill with the example"
					})
				]
			})
		]
	});
}
function EditScreen({ profile, onBack }) {
	const [name, setName] = (0, import_react.useState)(profile?.name ?? "");
	const [raw, setRaw] = (0, import_react.useState)(profile?.rawConfig.trim() ?? "");
	const banner = usePhoneKey((state) => state.banner);
	if (!profile) return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "px-5 py-6",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", { children: "That profile no longer exists." }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
			type: "button",
			onClick: onBack,
			className: "mt-4 font-semibold text-brass",
			children: "Back"
		})]
	});
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("form", {
		className: "flex flex-col px-5 py-4",
		onSubmit: (event) => {
			event.preventDefault();
			if (usePhoneKey.getState().saveRaw(profile.id, name, raw)) onBack();
		},
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ScreenHead, {
				title: "Edit",
				onBack
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mb-3 text-sm text-muted",
				children: protocolLabel(profile.protocol)
			}),
			banner && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mb-3 text-sm text-clay",
				role: "status",
				children: banner
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
				label: "Name",
				value: name,
				onChange: setName
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
				className: "mt-2 block",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
					className: "mb-1 block text-sm text-muted",
					children: "Config"
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
					value: raw,
					onChange: (event) => setRaw(event.target.value),
					className: "h-72 w-full rounded-xl border border-line bg-card p-3 text-sm"
				})]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
				type: "submit",
				className: "mt-3 h-12 rounded-2xl bg-brass font-semibold text-brass-ink",
				children: "Save"
			})
		]
	});
}
function SettingsScreen({ settings }) {
	const update = usePhoneKey((state) => state.updateSettings);
	const [packages, setPackages] = (0, import_react.useState)(settings.splitTunnelPackages.join("\n"));
	const [focused, setFocused] = (0, import_react.useState)(false);
	(0, import_react.useEffect)(() => {
		if (!focused) setPackages(settings.splitTunnelPackages.join("\n"));
	}, [settings.splitTunnelPackages, focused]);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex flex-col px-5 py-6",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
				className: "font-display text-3xl font-semibold",
				children: "Settings"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "mt-5 flex flex-col gap-5",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Toggle, {
						title: "Auto-connect on boot",
						body: "After the phone starts, the Android app brings up the selected profile if VPN permission is already granted.",
						checked: settings.autoConnectOnBoot,
						onChecked: (value) => update({ autoConnectOnBoot: value })
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Toggle, {
						title: "Auto-connect on launch",
						body: "Connect when you open PhoneKey, without asking again.",
						checked: settings.autoConnectOnLaunch,
						onChecked: (value) => update({ autoConnectOnLaunch: value })
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Toggle, {
						title: "Kill switch",
						body: "If the tunnel drops, the Android app tries to reconnect. This is not a hard block. Android can still leak during the gap.",
						checked: settings.killSwitch,
						onChecked: (value) => update({ killSwitch: value })
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-sm text-muted",
						children: "For a real block, set PhoneKey as Always-on VPN and enable “Block connections without VPN”."
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Toggle, {
						title: "Split tunneling",
						body: "Packages below are written as ExcludedApplications on the WireGuard interface. Type them yourself. The app does not scan installed apps.",
						checked: settings.splitTunnelEnabled,
						onChecked: (value) => update({ splitTunnelEnabled: value })
					}),
					/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
						className: "block",
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
							className: "mb-1 block text-sm text-muted",
							children: "Excluded packages"
						}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
							value: packages,
							onFocus: () => setFocused(true),
							onBlur: () => setFocused(false),
							onChange: (event) => {
								const text = event.target.value;
								setPackages(text);
								const next = text.split(/[\n, ]+/).map((item) => item.trim()).filter(Boolean);
								update({ splitTunnelPackages: next });
							},
							placeholder: "com.example.browser",
							className: "min-h-24 w-full rounded-xl border border-line bg-card p-3 text-sm"
						})]
					})
				]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h3", {
				className: "mt-8 font-semibold",
				children: "About"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mt-2 text-sm leading-relaxed text-muted",
				children: "PhoneKey is free. No ads. No subscriptions. No accounts. No analytics. Keys stay on your device."
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("a", {
				href: "/phonekey-source.zip",
				download: "PhoneKey-android.zip",
				className: "mt-4 flex h-12 items-center justify-center gap-2 rounded-2xl border border-line font-semibold",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Download, {
					className: "size-5",
					"aria-hidden": "true"
				}), "Download the Android project"]
			})
		]
	});
}
function Toggle({ title, body, checked, onChecked }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex items-center gap-3",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
			className: "flex-1 font-semibold",
			children: title
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
			type: "button",
			role: "switch",
			"aria-checked": checked,
			onClick: () => onChecked(!checked),
			className: `relative h-7 w-12 rounded-full ${checked ? "bg-brass" : "bg-card-2"}`,
			children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", { className: `absolute top-0.5 size-6 rounded-full bg-fg ${checked ? "left-5" : "left-0.5"}` }), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
				className: "sr-only",
				children: title
			})]
		})]
	}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
		className: "mt-1 text-sm text-muted",
		children: body
	})] });
}
function Choice({ label, onClick, icon }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
		type: "button",
		onClick,
		className: "flex h-14 items-center gap-3 rounded-2xl bg-card px-4 text-left font-semibold",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "text-brass",
			children: icon
		}), label]
	});
}
function Field({ label, value, onChange }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
		className: "block",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: "mb-1 block text-sm text-muted",
			children: label
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
			value,
			onChange: (event) => onChange(event.target.value),
			className: "h-12 w-full rounded-xl border border-line bg-card px-3"
		})]
	});
}
function ScreenHead({ title, onBack }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "mb-4 flex items-center gap-2",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
			type: "button",
			onClick: onBack,
			"aria-label": "Back",
			className: "grid size-11 place-items-center rounded-xl",
			children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(ArrowLeft, { className: "size-5" })
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
			className: "text-xl font-semibold",
			children: title
		})]
	});
}
function QrDialog({ profile, onClose }) {
	const [src, setSrc] = (0, import_react.useState)(null);
	const [failed, setFailed] = (0, import_react.useState)(false);
	(0, import_react.useEffect)(() => {
		const text = profile.rawConfig.trim();
		if (text.length > 1800) {
			setFailed(true);
			return;
		}
		let live = true;
		import_lib.toDataURL(text, {
			margin: 1,
			width: 720,
			errorCorrectionLevel: "M",
			color: {
				dark: "#10140F",
				light: "#F4F1EA"
			}
		}).then((url) => {
			if (live) setSrc(url);
		}).catch(() => {
			if (live) setFailed(true);
		});
		return () => {
			live = false;
		};
	}, [profile.rawConfig]);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "fixed inset-0 z-30 grid place-items-end bg-brass-ink/70 p-4 sm:place-items-center",
		role: "dialog",
		"aria-modal": "true",
		"aria-labelledby": "qr-title",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "w-full max-w-md rounded-3xl bg-card p-5",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h3", {
					id: "qr-title",
					className: "text-lg font-semibold",
					children: profile.name
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-2 text-sm text-muted",
					children: "This code contains the private key. Anyone who scans it can use the tunnel."
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "mt-4",
					children: [failed && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "text-sm text-clay",
						children: "This config is too large for a QR code."
					}), src && /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
						src,
						alt: `QR code for ${profile.name}`,
						className: "w-full rounded-xl"
					})]
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
					type: "button",
					onClick: onClose,
					className: "mt-4 h-11 w-full rounded-2xl font-semibold text-brass",
					children: "Close"
				})
			]
		})
	});
}
function ConfirmDialog({ title, body, confirm, onConfirm, onClose }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
		className: "fixed inset-0 z-30 grid place-items-end bg-brass-ink/70 p-4 sm:place-items-center",
		role: "dialog",
		"aria-modal": "true",
		"aria-labelledby": "confirm-title",
		children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
			className: "w-full max-w-md rounded-3xl bg-card p-5",
			children: [
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h3", {
					id: "confirm-title",
					className: "text-lg font-semibold",
					children: title
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
					className: "mt-2 text-sm text-muted",
					children: body
				}),
				/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "mt-4 flex justify-end gap-2",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						onClick: onClose,
						className: "h-11 rounded-xl px-4 font-semibold",
						children: "Cancel"
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
						type: "button",
						onClick: onConfirm,
						className: "h-11 rounded-xl px-4 font-semibold text-clay",
						children: confirm
					})]
				})
			]
		})
	});
}
function QrScanner({ onClose, onText }) {
	const videoRef = (0, import_react.useRef)(null);
	const onTextRef = (0, import_react.useRef)(onText);
	onTextRef.current = onText;
	const [error, setError] = (0, import_react.useState)(null);
	(0, import_react.useEffect)(() => {
		let stopped = false;
		let stream = null;
		let raf = 0;
		const canvas = document.createElement("canvas");
		const context = canvas.getContext("2d", { willReadFrequently: true });
		(async () => {
			try {
				if (!navigator.mediaDevices?.getUserMedia) throw new Error("This browser has no camera API.");
				stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
				const video = videoRef.current;
				if (stopped || !video || !context) return;
				video.srcObject = stream;
				await video.play();
				const tick = () => {
					if (stopped || !videoRef.current) return;
					if (videoRef.current.readyState >= 2 && videoRef.current.videoWidth > 0) {
						canvas.width = videoRef.current.videoWidth;
						canvas.height = videoRef.current.videoHeight;
						context.drawImage(videoRef.current, 0, 0);
						const frame = context.getImageData(0, 0, canvas.width, canvas.height);
						const code = (0, import_jsQR.default)(frame.data, frame.width, frame.height);
						if (code?.data) {
							onTextRef.current(code.data);
							return;
						}
					}
					raf = requestAnimationFrame(tick);
				};
				raf = requestAnimationFrame(tick);
			} catch (caught) {
				if (!stopped) setError(caught instanceof Error ? caught.message : "Camera permission is required to scan a code.");
			}
		})();
		return () => {
			stopped = true;
			cancelAnimationFrame(raf);
			stream?.getTracks().forEach((track) => track.stop());
		};
	}, []);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex flex-1 flex-col px-5 py-4",
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ScreenHead, {
				title: "Scan",
				onBack: onClose
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "mb-3 text-sm text-muted",
				children: "Point the camera at a WireGuard or Xray QR code. Nothing is uploaded."
			}),
			error ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
				className: "text-sm text-clay",
				role: "alert",
				children: error
			}) : /* @__PURE__ */ (0, import_jsx_runtime.jsx)("video", {
				ref: videoRef,
				muted: true,
				playsInline: true,
				className: "aspect-square w-full rounded-2xl bg-card object-cover"
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
				className: "mt-4 flex h-12 cursor-pointer items-center justify-center gap-2 rounded-2xl border border-line font-semibold",
				children: [
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ImagePlus, {
						className: "size-5",
						"aria-hidden": "true"
					}),
					"Scan a photo instead",
					/* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
						type: "file",
						accept: "image/*",
						className: "sr-only",
						onChange: (event) => {
							const file = event.target.files?.[0];
							event.target.value = "";
							if (!file) return;
							decodeQrFile(file).then((text) => {
								if (!text) {
									usePhoneKey.setState({ banner: "No QR code in that image." });
									return;
								}
								onText(text);
							});
						}
					})
				]
			})
		]
	});
}
async function decodeQrFile(file) {
	const bitmap = await createImageBitmap(file);
	const canvas = document.createElement("canvas");
	canvas.width = bitmap.width;
	canvas.height = bitmap.height;
	const context = canvas.getContext("2d", { willReadFrequently: true });
	if (!context) return null;
	context.drawImage(bitmap, 0, 0);
	const frame = context.getImageData(0, 0, canvas.width, canvas.height);
	return (0, import_jsQR.default)(frame.data, frame.width, frame.height)?.data ?? null;
}
function Home() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PhoneKeyApp, {});
}
//#endregion
export { Home as component };
