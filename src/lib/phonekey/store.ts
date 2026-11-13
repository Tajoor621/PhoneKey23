import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";
import {
  generateWireGuard,
  implemented,
  importConfig,
  importProfileBundle,
  isDocumentationEndpoint,
  peerPublicKey,
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
  splitIncludeOnly: boolean;
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
  exportBundle: () => string;
  importBundle: (text: string) => boolean;
  connectSelected: () => void;
  disconnect: () => void;
  dismissBanner: () => void;
}

const NOT_IMPLEMENTED =
  "Xray and SSTP are not implemented. Only WireGuard can connect.";

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
    splitIncludeOnly: false,
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
          const trimmed = text.trim().replace(/^\uFEFF/, "");
          if (trimmed.startsWith("{") && trimmed.includes('"phonekey"')) {
            return get().importBundle(trimmed);
          }
          try {
            remember(importConfig(trimmed, suggestedName));
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
        exportBundle: () =>
          JSON.stringify({ phonekey: 1, profiles: get().profiles }, null, 2),
        importBundle: (text: string) => {
          try {
            const existing = get()
              .profiles.map((profile) => peerPublicKey(profile.rawConfig))
              .filter((key): key is string => Boolean(key));
            const report = importProfileBundle(text, existing);
            if (report.profiles.length > 0) {
              set((state) => ({
                profiles: [...report.profiles, ...state.profiles],
                settings: {
                  ...state.settings,
                  selectedProfileId: report.profiles[0]?.id ?? state.settings.selectedProfileId,
                },
              }));
            }
            const parts = [`Imported ${report.imported}`];
            if (report.skipped > 0) parts.push(`skipped ${report.skipped} already imported`);
            if (report.failed > 0) parts.push(`failed ${report.failed}`);
            note(`${parts.join(", ")}.`);
            return report.imported > 0;
          } catch (error) {
            note(error instanceof Error ? error.message : "Could not import that file.");
            return false;
          }
        },
        disconnect: () => {
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
          if (!implemented(profile.protocol) || isDocumentationEndpoint(profile.endpoint)) {
            const message = !implemented(profile.protocol)
              ? NOT_IMPLEMENTED
              : "This endpoint is a documentation address, not a server.";
            set({
              session: {
                ...emptySession(),
                status: "ERROR",
                profileId: profile.id,
                profileName: profile.name,
                protocol: profile.protocol,
                message,
              },
            });
            note(message);
            return;
          }
          const excluded = state.settings.splitTunnelEnabled ? state.settings.splitTunnelPackages.length : 0;
          const parts: string[] = [
            "This config is valid WireGuard. A browser cannot open the tunnel or wait for a handshake. Install PhoneKey on Android and import this profile there.",
          ];
          if (excluded > 0) {
            parts.push(
              `On Android this profile would ${state.settings.splitIncludeOnly ? "include only" : "bypass"} ${excluded} package${excluded === 1 ? "" : "s"}.`,
            );
          }
          if (state.settings.killSwitch) {
            parts.push("If the tunnel drops, Android retries up to 3 times. This page cannot block other traffic.");
          }
          set({
            session: {
              ...emptySession(),
              profileId: profile.id,
              profileName: profile.name,
              protocol: profile.protocol,
              message: parts.join(" "),
            },
          });
        },
      };
    },
    {
      name: "phonekey",
      skipHydration: true,
      storage: createJSONStorage(() => (typeof window === "undefined" ? memoryStorage : localStorage)),
      partialize: (state) => ({ profiles: state.profiles, settings: state.settings }),
      merge: (persisted, current) => {
        const saved = (persisted ?? {}) as Partial<PhoneKeyState>;
        return {
          ...current,
          ...saved,
          settings: { ...defaultSettings(), ...(saved.settings ?? {}) },
          session: emptySession(),
          banner: null,
        };
      },
    },
  ),
);
