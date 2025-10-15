/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useRef } from 'react';

export default function useInterval(
  delay: number,
  callback: () => void
): Readonly<[() => void, () => void]> {
  const intervalRef = useRef<number>(undefined);

  const cb = useCallback(callback, [callback]);

  const cancel = useCallback(() => {
    if (intervalRef.current) {
      globalThis.clearInterval(intervalRef.current);
      intervalRef.current = undefined;
    }
  }, []);

  const set = useCallback(() => {
    cancel();
    intervalRef.current = globalThis.setInterval(cb, delay);
  }, [cb, cancel, delay]);

  return [set, cancel];
}
