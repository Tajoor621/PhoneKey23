import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import {
  EXAMPLE_WIREGUARD,
  generateWireGuard,
  importConfig,
  type Protocol,
  type VpnProfile,
  type WireGuardFields,
} from "./parse";

export type TunnelStatus = "DISCONNECTED" | "CONNECTING" | "CONNECTED" | "ERROR";

export interface AppSettings {
  autoConnectOnBoot: boolean;
  autoConnectOnLaunch: boolean;
  killSwitch: boolean;
  splitTunnelEnabled: boolean;
  splitTunnelPackages: string[];
  selectedProfileId: string | null;
}

export interface Session {
  status: TunnelStatus;
  profileId: string | null;
  profileName: string | null;
  protocol: Protocol | null;
  sinceEpochMs: number | null;
  rxBytes: number;
  txBytes: number;
  rxSpeedBps: number;
  txSpeedBps: number;
  message: string | null;
}

interface PhoneKeyState {
  profiles: VpnProfile[];
  settings: AppSettings;
  session: Session;
  banner: string | null;
  select: (id: string) => void;
  remove: (id: string) => void;
  updateSettings: (patch: Partial<AppSettings>) => void;
  importText: (text: string, suggestedName?: string | null) => boolean;
  saveManual: (name: string, fields: WireGuardFields) => boolean;
  saveRaw: (id: string | null, name: string, raw: string) => boolean;
  loadExample: () => boolean;
  connectSelected: () => void;
  disconnect: () => void;
  dismissBanner: () => void;
}

const XRAY_MESSAGE =
  "Xray/V2Ray is not bundled. The JSON stays on this device. A maintained open-source core is not wired in yet.";
const SSTP_MESSAGE =
  "SSTP is not bundled. The profile stays on this device. A maintained open-source client is not wired in yet.";

function emptySession(): Session {
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
    message: null,
  };
}

function defaultSettings(): AppSettings {
  return {
    autoConnectOnBoot: false,
    autoConnectOnLaunch: false,
    killSwitch: false,
    splitTunnelEnabled: false,
    splitTunnelPackages: [],
    selectedProfileId: null,
  };
}

const memoryStorage = {
  getItem: () => null,
  setItem: () => undefined,
  removeItem: () => undefined,
};

let bannerTimer: number | null = null;
let connectTimer: number | null = null;
let ticker: number | null = null;

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
    const t = Date.now() / 1000;
    const rxBps = Math.max(1200, 48000 + Math.sin(t * 0.8) * 32000);
    const txBps = Math.max(400, 14000 + Math.cos(t * 0.55) * 8000);
    rx += rxBps;
    tx += txBps;
    usePhoneKey.setState({
      session: {
        ...state.session,
        rxBytes: Math.round(rx),
        txBytes: Math.round(tx),
        rxSpeedBps: rxBps,
        txSpeedBps: txBps,
      },
    });
  }, 1000);
}

export const usePhoneKey = create<PhoneKeyState>()(
  persist(
    (set, get) => {
      const note = (message: string) => {
        if (bannerTimer != null) window.clearTimeout(bannerTimer);
        set({ banner: message });
        bannerTimer = window.setTimeout(() => set({ banner: null }), 4200);
      };

      const remember = (profile: VpnProfile) => {
        set((state) => ({
          profiles: [profile, ...state.profiles.filter((item) => item.id !== profile.id)],
          settings: { ...state.settings, selectedProfileId: profile.id },
        }));
        note(`Saved ${profile.name}.`);
      };

      return {
        profiles: [],
        settings: defaultSettings(),
        session: emptySession(),
        banner: null,
        dismissBanner: () => set({ banner: null }),
        select: (id) => set((state) => ({ settings: { ...state.settings, selectedProfileId: id } })),
        remove: (id) => {
          const current = get();
          if (current.session.profileId === id) {
            clearConnectTimer();
            stopTicker();
            set({ session: emptySession() });
          }
          set((state) => ({
            profiles: state.profiles.filter((profile) => profile.id !== id),
            settings: {
              ...state.settings,
              selectedProfileId: state.settings.selectedProfileId === id ? null : state.settings.selectedProfileId,
            },
          }));
        },
        updateSettings: (patch) => set((state) => ({ settings: { ...state.settings, ...patch } })),
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
              remember({ ...parsed, name: name.trim() || parsed.name });
              return true;
            }
            const existing = get().profiles.find((profile) => profile.id === id);
            const next: VpnProfile = {
              ...parsed,
              id,
              name: name.trim() || parsed.name,
              createdAt: existing?.createdAt ?? parsed.createdAt,
              updatedAt: Date.now(),
            };
            set((state) => ({
              profiles: state.profiles.map((profile) => (profile.id === id ? next : profile)),
              settings: { ...state.settings, selectedProfileId: id },
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
            set({ session: { ...emptySession(), status: "ERROR", message: "Choose a profile first." } });
            note("Choose a profile first.");
            return;
          }
          if (profile.protocol === "XRAY") {
            set({
              session: {
                ...emptySession(),
                status: "ERROR",
                profileId: profile.id,
                profileName: profile.name,
                protocol: profile.protocol,
                message: XRAY_MESSAGE,
              },
            });
            return;
          }
          if (profile.protocol === "SSTP") {
            set({
              session: {
                ...emptySession(),
                status: "ERROR",
                profileId: profile.id,
                profileName: profile.name,
                protocol: profile.protocol,
                message: SSTP_MESSAGE,
              },
            });
            return;
          }
          clearConnectTimer();
          set({
            session: {
              ...emptySession(),
              status: "CONNECTING",
              profileId: profile.id,
              profileName: profile.name,
              protocol: profile.protocol,
            },
          });
          connectTimer = window.setTimeout(() => {
            const latest = get();
            if (latest.session.status !== "CONNECTING" || latest.session.profileId !== profile.id) return;
            const excluded = latest.settings.splitTunnelEnabled ? latest.settings.splitTunnelPackages.length : 0;
            const docs = profile.endpoint?.includes("203.0.113.1");
            const parts: string[] = [];
            if (docs) parts.push("203.0.113.1 is documentation space, so a phone would not finish this handshake.");
            if (excluded > 0) {
              parts.push(
                `On Android this profile would exclude ${excluded} package${excluded === 1 ? "" : "s"}.`,
              );
            }
            if (latest.settings.killSwitch) {
              parts.push("If the tunnel drops, Android retries. This page cannot block other traffic.");
            }
            set({
              session: {
                status: "CONNECTED",
                profileId: profile.id,
                profileName: profile.name,
                protocol: profile.protocol,
                sinceEpochMs: Date.now(),
                rxBytes: 0,
                txBytes: 0,
                rxSpeedBps: 0,
                txSpeedBps: 0,
                message: parts.length ? parts.join(" ") : null,
              },
            });
            startTicker();
          }, 800);
        },
      };
    },
    {
      name: "phonekey",
      skipHydration: true,
      storage: createJSONStorage(() => (typeof window === "undefined" ? memoryStorage : localStorage)),
      partialize: (state) => ({ profiles: state.profiles, settings: state.settings }),
    },
  ),
);
