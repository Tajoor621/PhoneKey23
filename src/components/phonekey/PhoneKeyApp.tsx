import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  ArrowLeft,
  Download,
  FileUp,
  ImagePlus,
  KeyRound,
  Pencil,
  Plus,
  Power,
  QrCode,
  ScanLine,
  Settings,
  Trash2,
} from "lucide-react";
import jsQR from "jsqr";
import QRCode from "qrcode";
import {
  EMPTY_FIELDS,
  EXAMPLE_WIREGUARD,
  formatBytes,
  formatDuration,
  formatRate,
  protocolLabel,
  type VpnProfile,
  type WireGuardFields,
} from "@/lib/phonekey/parse";
import { usePhoneKey, type AppSettings, type Session } from "@/lib/phonekey/store";

type View = "home" | "profiles" | "settings" | "add" | "edit" | "scan";

export function PhoneKeyApp() {
  const [view, setView] = useState<View>("home");
  const [editId, setEditId] = useState<string | null>(null);
  const [qrProfile, setQrProfile] = useState<VpnProfile | null>(null);
  const [pendingDelete, setPendingDelete] = useState<VpnProfile | null>(null);
  const session = usePhoneKey((state) => state.session);
  const profiles = usePhoneKey((state) => state.profiles);
  const settings = usePhoneKey((state) => state.settings);
  const banner = usePhoneKey((state) => state.banner);
  const selected = profiles.find((profile) => profile.id === settings.selectedProfileId) ?? null;
  const showNav = view === "home" || view === "profiles" || view === "settings";

  useEffect(() => {
    let live = true;
    void Promise.resolve(usePhoneKey.persist.rehydrate()).then(() => {
      if (!live) return;
      const latest = usePhoneKey.getState();
      if (
        latest.settings.autoConnectOnLaunch &&
        latest.settings.selectedProfileId &&
        latest.session.status === "DISCONNECTED"
      ) {
        latest.connectSelected();
      }
    });
    return () => {
      live = false;
    };
  }, []);

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-lg flex-col bg-canvas text-fg">
      <main className="flex flex-1 flex-col overflow-y-auto">
        {view === "home" && (
          <HomeScreen
            session={session}
            selected={selected}
            onToggle={() => {
              if (session.status === "CONNECTED" || session.status === "CONNECTING") {
                usePhoneKey.getState().disconnect();
              } else if (!selected) {
                setView("profiles");
              } else {
                usePhoneKey.getState().connectSelected();
              }
            }}
            onChoose={() => setView("profiles")}
          />
        )}
        {view === "profiles" && (
          <ProfilesScreen
            profiles={profiles}
            selectedId={settings.selectedProfileId}
            onSelect={(id) => usePhoneKey.getState().select(id)}
            onEdit={(id) => {
              setEditId(id);
              setView("edit");
            }}
            onDelete={setPendingDelete}
            onQr={setQrProfile}
          />
        )}
        {view === "settings" && <SettingsScreen settings={settings} />}
        {view === "add" && <AddScreen onBack={() => setView("profiles")} onScan={() => setView("scan")} onSaved={() => setView("home")} />}
        {view === "edit" && (
          <EditScreen
            profile={profiles.find((profile) => profile.id === editId) ?? null}
            onBack={() => setView("profiles")}
          />
        )}
        {view === "scan" && (
          <QrScanner
            onClose={() => setView("add")}
            onText={(text) => {
              const ok = usePhoneKey.getState().importText(text, "Scanned");
              setView(ok ? "home" : "add");
            }}
          />
        )}
      </main>
      {banner && showNav && (
        <p className="border-t border-line bg-card-2 px-5 py-3 text-sm" role="status">
          {banner}
        </p>
      )}
      {view === "profiles" && (
        <div className="border-t border-line px-5 py-3">
          <button
            type="button"
            onClick={() => setView("add")}
            className="flex h-12 w-full items-center justify-center gap-2 rounded-2xl bg-brass font-semibold text-brass-ink"
          >
            <Plus className="size-5" aria-hidden="true" />
            Add profile
          </button>
        </div>
      )}
      {showNav && (
        <nav className="grid grid-cols-3 border-t border-line bg-card" aria-label="Primary">
          <NavButton label="Home" active={view === "home"} onClick={() => setView("home")} icon={<Power className="size-5" />} />
          <NavButton label="Profiles" active={view === "profiles"} onClick={() => setView("profiles")} icon={<KeyRound className="size-5" />} />
          <NavButton label="Settings" active={view === "settings"} onClick={() => setView("settings")} icon={<Settings className="size-5" />} />
        </nav>
      )}
      {qrProfile && <QrDialog profile={qrProfile} onClose={() => setQrProfile(null)} />}
      {pendingDelete && (
        <ConfirmDialog
          title={`Delete ${pendingDelete.name}?`}
          body="The keys are removed from this browser. This cannot be undone."
          confirm="Delete"
          onConfirm={() => {
            usePhoneKey.getState().remove(pendingDelete.id);
            setPendingDelete(null);
          }}
          onClose={() => setPendingDelete(null)}
        />
      )}
    </div>
  );
}

function NavButton({
  label,
  active,
  onClick,
  icon,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
  icon: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-current={active ? "page" : undefined}
      className={`flex min-h-16 flex-col items-center justify-center gap-1 text-xs font-semibold ${active ? "text-brass" : "text-muted"}`}
    >
      {icon}
      {label}
    </button>
  );
}

function HomeScreen({
  session,
  selected,
  onToggle,
  onChoose,
}: {
  session: Session;
  selected: VpnProfile | null;
  onToggle: () => void;
  onChoose: () => void;
}) {
  const [now, setNow] = useState(() => Date.now());
  const busy = session.status === "CONNECTING";
  const on = session.status === "CONNECTED" || busy;
  useEffect(() => {
    if (session.status !== "CONNECTED") return;
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [session.status, session.sinceEpochMs]);

  const statusLabel =
    session.status === "CONNECTED"
      ? "Connected"
      : session.status === "CONNECTING"
        ? "Connecting"
        : session.status === "ERROR"
          ? "Not connected"
          : "Off";

  return (
    <div className="flex flex-1 flex-col items-center px-6 py-6">
      <h1 className="font-display text-3xl font-semibold">PhoneKey</h1>
      <p className="mt-1 text-center text-sm text-muted">No ads. No subscription.</p>
      <p className="mt-8 text-sm font-semibold text-muted">{statusLabel}</p>
      <p className="font-display text-5xl leading-tight">
        {session.status === "CONNECTED" ? formatDuration(session.sinceEpochMs, now) : "00:00"}
      </p>
      <button
        type="button"
        onClick={onToggle}
        disabled={busy}
        aria-label={on ? "Disconnect" : "Connect"}
        className={`mt-8 grid size-44 place-items-center rounded-full border-2 border-brass disabled:opacity-70 ${on ? "bg-brass text-brass-ink" : "bg-card text-brass"}`}
      >
        <Power className="size-16" strokeWidth={1.6} />
      </button>
      <p className="mt-3 text-sm font-semibold text-muted">{on ? "Tap to disconnect" : "Tap to connect"}</p>
      {session.message && (
        <p className={`mt-4 max-w-sm text-center text-sm ${session.status === "ERROR" ? "text-clay" : "text-muted"}`}>
          {session.message}
        </p>
      )}
      <p className="mt-4 max-w-sm text-center text-sm text-muted">
        This page keeps configs on this device and does not route traffic. The Android app opens the real tunnel. The Studio project is under Settings.
      </p>
      <div className="mt-auto w-full pt-8">
        <button type="button" onClick={onChoose} className="w-full rounded-2xl bg-card p-4 text-left">
          <span className="block text-xs font-medium text-muted">Profile</span>
          <span className="mt-1 block text-base font-semibold">{selected?.name ?? "None selected"}</span>
          <span className="mt-1 block text-sm text-muted">
            {selected
              ? `${protocolLabel(selected.protocol)}${selected.endpoint ? ` · ${selected.endpoint}` : ""}`
              : "Add a WireGuard config to start"}
          </span>
        </button>
        {session.status === "CONNECTED" && (
          <div className="mt-3 grid grid-cols-2 gap-3">
            <Stat label="Down" rate={formatRate(session.rxSpeedBps)} total={formatBytes(session.rxBytes)} />
            <Stat label="Up" rate={formatRate(session.txSpeedBps)} total={formatBytes(session.txBytes)} />
          </div>
        )}
        {session.status === "CONNECTED" && (
          <p className="mt-2 text-center text-xs text-muted">Rates on this page are simulated.</p>
        )}
      </div>
    </div>
  );
}

function Stat({ label, rate, total }: { label: string; rate: string; total: string }) {
  return (
    <div className="rounded-2xl bg-card p-4">
      <p className="text-xs font-medium text-muted">{label}</p>
      <p className="mt-1 font-semibold">{rate}</p>
      <p className="text-sm text-muted">{total}</p>
    </div>
  );
}

function ProfilesScreen({
  profiles,
  selectedId,
  onSelect,
  onEdit,
  onDelete,
  onQr,
}: {
  profiles: VpnProfile[];
  selectedId: string | null;
  onSelect: (id: string) => void;
  onEdit: (id: string) => void;
  onDelete: (profile: VpnProfile) => void;
  onQr: (profile: VpnProfile) => void;
}) {
  if (profiles.length === 0) {
    return (
      <div className="flex flex-1 flex-col justify-center px-6 py-10">
        <h2 className="font-display text-3xl font-semibold">No profiles yet</h2>
        <p className="mt-3 text-base leading-relaxed text-muted">
          Add a WireGuard config from a file, a QR code, or by typing the keys. Xray and SSTP can be stored, but those engines are not bundled.
        </p>
      </div>
    );
  }
  return (
    <ul className="flex flex-col gap-3 px-4 py-4">
      {profiles.map((profile) => {
        const selected = profile.id === selectedId;
        return (
          <li key={profile.id}>
            <div className={`rounded-2xl p-4 ${selected ? "bg-brass-soft" : "bg-card"}`}>
              <button type="button" onClick={() => onSelect(profile.id)} className="w-full text-left">
                <span className="block text-xs font-medium text-muted">{protocolLabel(profile.protocol)}</span>
                <span className="mt-1 flex items-center justify-between gap-3">
                  <span className="font-semibold">{profile.name}</span>
                  {selected && <span className="text-sm font-semibold text-brass">In use</span>}
                </span>
                {profile.endpoint && <span className="mt-1 block break-all text-sm text-muted">{profile.endpoint}</span>}
              </button>
              <div className="mt-2 flex">
                <IconAction label="Edit" onClick={() => onEdit(profile.id)}>
                  <Pencil className="size-5" />
                </IconAction>
                <IconAction label="Show QR" onClick={() => onQr(profile)}>
                  <QrCode className="size-5" />
                </IconAction>
                <IconAction label="Delete" onClick={() => onDelete(profile)}>
                  <Trash2 className="size-5" />
                </IconAction>
              </div>
            </div>
          </li>
        );
      })}
    </ul>
  );
}

function IconAction({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button type="button" aria-label={label} onClick={onClick} className="grid size-11 place-items-center rounded-xl text-fg">
      {children}
    </button>
  );
}

function AddScreen({ onBack, onScan, onSaved }: { onBack: () => void; onScan: () => void; onSaved: () => void }) {
  const [mode, setMode] = useState<"choose" | "manual" | "paste">("choose");
  const [name, setName] = useState("");
  const [fields, setFields] = useState<WireGuardFields>(EMPTY_FIELDS);
  const [raw, setRaw] = useState("");
  const fileRef = useRef<HTMLInputElement>(null);
  const banner = usePhoneKey((state) => state.banner);

  return (
    <div className="flex flex-col px-5 py-4">
      <ScreenHead
        title="Add profile"
        onBack={() => {
          if (mode === "choose") onBack();
          else setMode("choose");
        }}
      />
      {banner && <p className="mb-3 text-sm text-clay" role="status">{banner}</p>}
      {mode === "choose" && (
        <div className="flex flex-col gap-3">
          <Choice label="Import a file" icon={<FileUp className="size-5" />} onClick={() => fileRef.current?.click()} />
          <Choice label="Scan a QR code" icon={<ScanLine className="size-5" />} onClick={onScan} />
          <Choice label="WireGuard form" icon={<KeyRound className="size-5" />} onClick={() => setMode("manual")} />
          <Choice label="Paste config" icon={<Plus className="size-5" />} onClick={() => setMode("paste")} />
          <button
            type="button"
            onClick={() => {
              if (usePhoneKey.getState().loadExample()) onSaved();
            }}
            className="h-12 rounded-2xl border border-line font-semibold"
          >
            Load the example config
          </button>
          <p className="text-sm text-muted">The example uses documentation IP 203.0.113.1. It will not open a real tunnel.</p>
          <input
            ref={fileRef}
            type="file"
            accept=".conf,.json,.txt,text/plain,application/json"
            className="sr-only"
            onChange={(event) => {
              const file = event.target.files?.[0];
              event.target.value = "";
              if (!file) return;
              if (file.size > 1_000_000) {
                usePhoneKey.setState({ banner: "That file is larger than 1 MB." });
                return;
              }
              void file.text().then((text) => {
                const stem = file.name.replace(/\.[^.]+$/, "");
                if (usePhoneKey.getState().importText(text, stem)) onSaved();
              });
            }}
          />
        </div>
      )}
      {mode === "manual" && (
        <form
          className="flex flex-col gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            if (usePhoneKey.getState().saveManual(name, fields)) onSaved();
          }}
        >
          <Field label="Name" value={name} onChange={setName} />
          <Field label="Private key" value={fields.privateKey} onChange={(value) => setFields({ ...fields, privateKey: value })} />
          <Field label="Address" value={fields.address} onChange={(value) => setFields({ ...fields, address: value })} />
          <Field label="DNS" value={fields.dns} onChange={(value) => setFields({ ...fields, dns: value })} />
          <Field label="MTU" value={fields.mtu} onChange={(value) => setFields({ ...fields, mtu: value })} />
          <Field label="Peer public key" value={fields.publicKey} onChange={(value) => setFields({ ...fields, publicKey: value })} />
          <Field label="Preshared key" value={fields.presharedKey} onChange={(value) => setFields({ ...fields, presharedKey: value })} />
          <Field label="Endpoint" value={fields.endpoint} onChange={(value) => setFields({ ...fields, endpoint: value })} />
          <Field label="Allowed IPs" value={fields.allowedIps} onChange={(value) => setFields({ ...fields, allowedIps: value })} />
          <Field label="Keepalive" value={fields.keepalive} onChange={(value) => setFields({ ...fields, keepalive: value })} />
          <button type="submit" className="mt-2 h-12 rounded-2xl bg-brass font-semibold text-brass-ink">
            Save profile
          </button>
        </form>
      )}
      {mode === "paste" && (
        <form
          className="flex flex-col gap-3"
          onSubmit={(event) => {
            event.preventDefault();
            if (usePhoneKey.getState().importText(raw, name.trim() || null)) onSaved();
          }}
        >
          <Field label="Name (optional)" value={name} onChange={setName} />
          <label className="block">
            <span className="mb-1 block text-sm text-muted">WireGuard, Xray JSON, or SSTP</span>
            <textarea
              value={raw}
              onChange={(event) => setRaw(event.target.value)}
              className="h-56 w-full rounded-xl border border-line bg-card p-3 text-sm"
            />
          </label>
          <button type="submit" className="h-12 rounded-2xl bg-brass font-semibold text-brass-ink">
            Import
          </button>
          <button
            type="button"
            className="text-sm font-semibold text-brass"
            onClick={() => setRaw(EXAMPLE_WIREGUARD.trim())}
          >
            Fill with the example
          </button>
        </form>
      )}
    </div>
  );
}

function EditScreen({ profile, onBack }: { profile: VpnProfile | null; onBack: () => void }) {
  const [name, setName] = useState(profile?.name ?? "");
  const [raw, setRaw] = useState(profile?.rawConfig.trim() ?? "");
  const banner = usePhoneKey((state) => state.banner);
  if (!profile) {
    return (
      <div className="px-5 py-6">
        <p>That profile no longer exists.</p>
        <button type="button" onClick={onBack} className="mt-4 font-semibold text-brass">
          Back
        </button>
      </div>
    );
  }
  return (
    <form
      className="flex flex-col px-5 py-4"
      onSubmit={(event) => {
        event.preventDefault();
        if (usePhoneKey.getState().saveRaw(profile.id, name, raw)) onBack();
      }}
    >
      <ScreenHead title="Edit" onBack={onBack} />
      <p className="mb-3 text-sm text-muted">{protocolLabel(profile.protocol)}</p>
      {banner && <p className="mb-3 text-sm text-clay" role="status">{banner}</p>}
      <Field label="Name" value={name} onChange={setName} />
      <label className="mt-2 block">
        <span className="mb-1 block text-sm text-muted">Config</span>
        <textarea value={raw} onChange={(event) => setRaw(event.target.value)} className="h-72 w-full rounded-xl border border-line bg-card p-3 text-sm" />
      </label>
      <button type="submit" className="mt-3 h-12 rounded-2xl bg-brass font-semibold text-brass-ink">
        Save
      </button>
    </form>
  );
}

function SettingsScreen({ settings }: { settings: AppSettings }) {
  const update = usePhoneKey((state) => state.updateSettings);
  const [packages, setPackages] = useState(settings.splitTunnelPackages.join("\n"));
  const [focused, setFocused] = useState(false);
  useEffect(() => {
    if (!focused) setPackages(settings.splitTunnelPackages.join("\n"));
  }, [settings.splitTunnelPackages, focused]);

  return (
    <div className="flex flex-col px-5 py-6">
      <h2 className="font-display text-3xl font-semibold">Settings</h2>
      <div className="mt-5 flex flex-col gap-5">
        <Toggle
          title="Auto-connect on boot"
          body="After the phone starts, the Android app brings up the selected profile if VPN permission is already granted."
          checked={settings.autoConnectOnBoot}
          onChecked={(value) => update({ autoConnectOnBoot: value })}
        />
        <Toggle
          title="Auto-connect on launch"
          body="Connect when you open PhoneKey, without asking again."
          checked={settings.autoConnectOnLaunch}
          onChecked={(value) => update({ autoConnectOnLaunch: value })}
        />
        <Toggle
          title="Kill switch"
          body="If the tunnel drops, the Android app tries to reconnect. This is not a hard block. Android can still leak during the gap."
          checked={settings.killSwitch}
          onChecked={(value) => update({ killSwitch: value })}
        />
        <p className="text-sm text-muted">
          For a real block, set PhoneKey as Always-on VPN and enable “Block connections without VPN”.
        </p>
        <Toggle
          title="Split tunneling"
          body="Packages below are written as ExcludedApplications on the WireGuard interface. Type them yourself. The app does not scan installed apps."
          checked={settings.splitTunnelEnabled}
          onChecked={(value) => update({ splitTunnelEnabled: value })}
        />
        <label className="block">
          <span className="mb-1 block text-sm text-muted">Excluded packages</span>
          <textarea
            value={packages}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            onChange={(event) => {
              const text = event.target.value;
              setPackages(text);
              const next = text
                .split(/[\n, ]+/)
                .map((item) => item.trim())
                .filter(Boolean);
              update({ splitTunnelPackages: next });
            }}
            placeholder="com.example.browser"
            className="min-h-24 w-full rounded-xl border border-line bg-card p-3 text-sm"
          />
        </label>
      </div>
      <h3 className="mt-8 font-semibold">About</h3>
      <p className="mt-2 text-sm leading-relaxed text-muted">
        PhoneKey is free. No ads. No subscriptions. No accounts. No analytics. Keys stay on your device.
      </p>
      <a
        href="/phonekey-source.zip"
        download="PhoneKey-android.zip"
        className="mt-4 flex h-12 items-center justify-center gap-2 rounded-2xl border border-line font-semibold"
      >
        <Download className="size-5" aria-hidden="true" />
        Download the Android project
      </a>
    </div>
  );
}

function Toggle({
  title,
  body,
  checked,
  onChecked,
}: {
  title: string;
  body: string;
  checked: boolean;
  onChecked: (value: boolean) => void;
}) {
  return (
    <div>
      <div className="flex items-center gap-3">
        <p className="flex-1 font-semibold">{title}</p>
        <button
          type="button"
          role="switch"
          aria-checked={checked}
          onClick={() => onChecked(!checked)}
          className={`relative h-7 w-12 rounded-full ${checked ? "bg-brass" : "bg-card-2"}`}
        >
          <span
            className={`absolute top-0.5 size-6 rounded-full bg-fg ${checked ? "left-5" : "left-0.5"}`}
          />
          <span className="sr-only">{title}</span>
        </button>
      </div>
      <p className="mt-1 text-sm text-muted">{body}</p>
    </div>
  );
}

function Choice({ label, onClick, icon }: { label: string; onClick: () => void; icon: ReactNode }) {
  return (
    <button type="button" onClick={onClick} className="flex h-14 items-center gap-3 rounded-2xl bg-card px-4 text-left font-semibold">
      <span className="text-brass">{icon}</span>
      {label}
    </button>
  );
}

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm text-muted">{label}</span>
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-12 w-full rounded-xl border border-line bg-card px-3"
      />
    </label>
  );
}

function ScreenHead({ title, onBack }: { title: string; onBack: () => void }) {
  return (
    <div className="mb-4 flex items-center gap-2">
      <button type="button" onClick={onBack} aria-label="Back" className="grid size-11 place-items-center rounded-xl">
        <ArrowLeft className="size-5" />
      </button>
      <h2 className="text-xl font-semibold">{title}</h2>
    </div>
  );
}

function QrDialog({ profile, onClose }: { profile: VpnProfile; onClose: () => void }) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const text = profile.rawConfig.trim();
    if (text.length > 1800) {
      setFailed(true);
      return;
    }
    let live = true;
    void QRCode.toDataURL(text, {
      margin: 1,
      width: 720,
      errorCorrectionLevel: "M",
      color: { dark: "#10140F", light: "#F4F1EA" },
    })
      .then((url) => {
        if (live) setSrc(url);
      })
      .catch(() => {
        if (live) setFailed(true);
      });
    return () => {
      live = false;
    };
  }, [profile.rawConfig]);

  return (
    <div className="fixed inset-0 z-30 grid place-items-end bg-brass-ink/70 p-4 sm:place-items-center" role="dialog" aria-modal="true" aria-labelledby="qr-title">
      <div className="w-full max-w-md rounded-3xl bg-card p-5">
        <h3 id="qr-title" className="text-lg font-semibold">{profile.name}</h3>
        <p className="mt-2 text-sm text-muted">This code contains the private key. Anyone who scans it can use the tunnel.</p>
        <div className="mt-4">
          {failed && <p className="text-sm text-clay">This config is too large for a QR code.</p>}
          {src && <img src={src} alt={`QR code for ${profile.name}`} className="w-full rounded-xl" />}
        </div>
        <button type="button" onClick={onClose} className="mt-4 h-11 w-full rounded-2xl font-semibold text-brass">
          Close
        </button>
      </div>
    </div>
  );
}

function ConfirmDialog({
  title,
  body,
  confirm,
  onConfirm,
  onClose,
}: {
  title: string;
  body: string;
  confirm: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <div className="fixed inset-0 z-30 grid place-items-end bg-brass-ink/70 p-4 sm:place-items-center" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
      <div className="w-full max-w-md rounded-3xl bg-card p-5">
        <h3 id="confirm-title" className="text-lg font-semibold">{title}</h3>
        <p className="mt-2 text-sm text-muted">{body}</p>
        <div className="mt-4 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="h-11 rounded-xl px-4 font-semibold">
            Cancel
          </button>
          <button type="button" onClick={onConfirm} className="h-11 rounded-xl px-4 font-semibold text-clay">
            {confirm}
          </button>
        </div>
      </div>
    </div>
  );
}

function QrScanner({ onClose, onText }: { onClose: () => void; onText: (text: string) => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const onTextRef = useRef(onText);
  onTextRef.current = onText;
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let stopped = false;
    let stream: MediaStream | null = null;
    let raf = 0;
    const canvas = document.createElement("canvas");
    const context = canvas.getContext("2d", { willReadFrequently: true });
    void (async () => {
      try {
        if (!navigator.mediaDevices?.getUserMedia) {
          throw new Error("This browser has no camera API.");
        }
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
            const code = jsQR(frame.data, frame.width, frame.height);
            if (code?.data) {
              onTextRef.current(code.data);
              return;
            }
          }
          raf = requestAnimationFrame(tick);
        };
        raf = requestAnimationFrame(tick);
      } catch (caught) {
        if (!stopped) {
          setError(caught instanceof Error ? caught.message : "Camera permission is required to scan a code.");
        }
      }
    })();
    return () => {
      stopped = true;
      cancelAnimationFrame(raf);
      stream?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  return (
    <div className="flex flex-1 flex-col px-5 py-4">
      <ScreenHead title="Scan" onBack={onClose} />
      <p className="mb-3 text-sm text-muted">Point the camera at a WireGuard or Xray QR code. Nothing is uploaded.</p>
      {error ? (
        <p className="text-sm text-clay" role="alert">{error}</p>
      ) : (
        <video ref={videoRef} muted playsInline className="aspect-square w-full rounded-2xl bg-card object-cover" />
      )}
      <label className="mt-4 flex h-12 cursor-pointer items-center justify-center gap-2 rounded-2xl border border-line font-semibold">
        <ImagePlus className="size-5" aria-hidden="true" />
        Scan a photo instead
        <input
          type="file"
          accept="image/*"
          className="sr-only"
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = "";
            if (!file) return;
            void decodeQrFile(file).then((text) => {
              if (!text) {
                usePhoneKey.setState({ banner: "No QR code in that image." });
                return;
              }
              onText(text);
            });
          }}
        />
      </label>
    </div>
  );
}

async function decodeQrFile(file: File): Promise<string | null> {
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement("canvas");
  canvas.width = bitmap.width;
  canvas.height = bitmap.height;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) return null;
  context.drawImage(bitmap, 0, 0);
  const frame = context.getImageData(0, 0, canvas.width, canvas.height);
  return jsQR(frame.data, frame.width, frame.height)?.data ?? null;
}
