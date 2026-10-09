// A tiny, dependency-free way to get a crash's error text onto the
// screen - no account, no network, no file storage. Anything that
// reports here gets shown by <CrashGate> instead of letting the app
// just vanish with no explanation.

export type CrashInfo = {
  message: string;
  stack?: string;
  isFatal?: boolean;
};

type Listener = (info: CrashInfo) => void;

let current: CrashInfo | null = null;
const listeners = new Set<Listener>();

export function reportCrash(info: CrashInfo) {
  current = info;
  listeners.forEach((listener) => listener(info));
}

export function subscribe(listener: Listener): () => void {
  if (current) listener(current);
  listeners.add(listener);
  return () => listeners.delete(listener);
}
