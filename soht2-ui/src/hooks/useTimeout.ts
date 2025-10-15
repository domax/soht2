/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useRef } from 'react';

export default function useTimeout(
  delay: number,
  callback: () => void
): Readonly<[() => void, () => void]> {
  const timeoutRef = useRef<number>(undefined);

  const cancel = useCallback(() => {
    if (timeoutRef.current) {
      globalThis.clearTimeout(timeoutRef.current);
      timeoutRef.current = undefined;
    }
  }, []);

  const cb = useCallback(() => {
    cancel();
    callback();
  }, [callback, cancel]);

  const set = useCallback(() => {
    cancel();
    timeoutRef.current = globalThis.setTimeout(cb, delay);
  }, [cb, cancel, delay]);

  return [set, cancel];
}
